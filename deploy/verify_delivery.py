"""Opt-in real Docker/TLS/MySQL exercise using only new volumes and fake secrets.

Run after building snowboard-v2-delivery-{backend,frontend}:local on ARM64.
No AWS calls, external mail, developer database or developer credentials.
"""
import json
import os
import shutil
import socket
import ssl
import subprocess
import tempfile
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from backup import create_backup, export, validate_backup
from release import Runtime, apply_release, publication_lock
from restore_check import restore
from renew_certificate import deploy_certificate
from test_delivery_support import COMPOSE, PRODUCTION, ROOT


def port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


class IsolatedRuntime(Runtime):
    def __init__(self, config, values, overlays, certificate):
        super().__init__(config)
        self.values = values
        self.overlays = overlays
        self.context = ssl.create_default_context(cafile=str(certificate))

    def compose(self, version, arguments, **kwargs):
        # Exact production Compose and NGINX with local built images and loopback
        # ports; the override network blocks SMTP, AWS and all external traffic.
        env = {key: value for key, value in os.environ.items()
               if key in {"PATH", "HOME", "DOCKER_HOST", "DOCKER_CONFIG", "TMPDIR"}}
        cmd = COMPOSE + ["--project-name", self.config["project"], "--env-file", "/dev/null", "-f",
                        str(Path(version["bundle"]) / "deploy/compose.production.yaml"),
                        "-f", str(self.overlays[version["sha"]])]
        try:
            return subprocess.run(cmd + arguments, env=env | self.values, check=True,
                                  stderr=subprocess.PIPE, **kwargs)
        except subprocess.CalledProcessError as error:
            print("isolated Compose diagnostics:", error.stderr.decode()[-3000:], flush=True)
            raise

    def start(self, version):
        self.compose(version, ["up", "-d", "--wait", "--wait-timeout", "180"], stdout=subprocess.DEVNULL)

    def prepare(self, version):
        self.compose(version, ["config", "--quiet"], stdout=subprocess.DEVNULL)

    def health(self, version):
        try:
            with urllib.request.urlopen(self.config["public_url"] + "/api/actuator/health",
                                        context=self.context, timeout=10) as response:
                if json.load(response).get("status") != "UP":
                    raise RuntimeError("HTTPS health failed")
        except Exception as error:
            print("isolated HTTPS diagnostics:", type(error).__name__, str(error), flush=True)
            raise


def verify():
    project = "geer-delivery-check-" + uuid.uuid4().hex[:10]
    first_sha, second_sha = "a" * 40, "b" * 40
    work = ROOT / ".local"
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="geer-delivery-verification-", dir=work) as directory:
        root = Path(directory)
        secrets_dir = root / "secrets"
        for folder in ["spring", "media"]:
            (secrets_dir / folder).mkdir(parents=True, mode=0o755)
        for name, value in {"spring/spring.datasource.password": "isolated-db-test-only",
                            "spring/identity.verification-key": "isolated-identity-test-only-key-32-bytes",
                            "spring/spring.mail.password": "isolated-smtp-test-only",
                            "mysql.root.password": "isolated-root-test-only"}.items():
            path = secrets_dir / name
            path.write_text(value)
            path.chmod(0o444)  # Fake test secrets, under a private temporary parent.
        tls = root / "tls"
        tls.mkdir()
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "2",
                        "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1",
                        "-keyout", str(tls / "privkey.pem"), "-out", str(tls / "fullchain.pem")],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                        "-out", str(secrets_dir / "media/cloudfront.pem")],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        (secrets_dir / "media/cloudfront.pem").chmod(0o444)
        (root / "acme").mkdir()
        state = root / "state"
        state.mkdir()
        http, https = port(), port()
        values = PRODUCTION | {"SECRETS_DIR": str(secrets_dir), "TLS_DIR": str(tls),
                               "ACME_DIR": str(root / "acme"), "APP_PUBLIC_URL": "https://localhost:" + str(https)}
        overlays = {}
        versions = []
        for sha in [first_sha, second_sha]:
            bundle = root / sha
            (bundle / "deploy").mkdir(parents=True)
            (bundle / "frontend").mkdir()
            shutil.copyfile(ROOT / "deploy/compose.production.yaml", bundle / "deploy/compose.production.yaml")
            shutil.copyfile(ROOT / "frontend/nginx.production.conf", bundle / "frontend/nginx.production.conf")
            overlay = root / (sha + ".yaml")
            overlay.write_text("services:\n  backend:\n    image: snowboard-v2-delivery-backend:local\n" +
                ("    entrypoint: ['/bin/sh', '-c', 'exit 42']\n" if sha == second_sha else "") +
                "  frontend:\n    image: snowboard-v2-delivery-frontend:local\n    ports: !override\n" +
                f"      - '127.0.0.1:{http}:80'\n      - '127.0.0.1:{https}:443'\n" +
                "    networks: [default, edge]\nnetworks:\n  default:\n    internal: true\n  edge: {}\n")
            overlays[sha] = overlay
            versions.append({"sha": sha, "schema_version": "10", "bundle": str(bundle),
                             "backend_image": values["BACKEND_IMAGE"], "frontend_image": values["FRONTEND_IMAGE"]})
        runtime = IsolatedRuntime({"project": project, "public_url": values["APP_PUBLIC_URL"]},
                                  values, overlays, tls / "fullchain.pem")
        try:
            with publication_lock(state):
                result = apply_release(versions[0], state, runtime)
            print("production Compose / HTTPS / MySQL 8.4:", result, flush=True)
            with publication_lock(state):
                deploy_certificate(tls / "fullchain.pem", tls / "privkey.pem", tls, runtime, versions[0])
            runtime.health(versions[0])
            url = values["APP_PUBLIC_URL"]
            with urllib.request.urlopen(url + "/api/auth/csrf", context=runtime.context, timeout=10) as response:
                cookie = response.headers.get("Set-Cookie", "")
                assert all(flag in cookie for flag in ["Secure", "HttpOnly", "SameSite=Lax"]), "insecure session cookie"
                assert json.load(response)["token"], "missing CSRF token"
            try:
                urllib.request.urlopen(url + "/api/auth/me", context=runtime.context, timeout=10)
                raise AssertionError("anonymous authentication unexpectedly succeeded")
            except urllib.error.HTTPError as error:
                assert error.code == 401
            marker = "delivery-verification-token-must-not-be-logged"
            request = urllib.request.Request(url + "/api/auth/csrf?token=" + marker,
                                             headers={"Cookie": cookie.split(";", 1)[0]})
            with urllib.request.urlopen(request,
                                        context=runtime.context, timeout=10) as response:
                assert response.status == 200
            logs = runtime.compose(versions[0], ["logs", "--no-color", "frontend"], stdout=subprocess.PIPE).stdout.decode()
            assert "/api/auth/csrf" in logs, "request was not logged"
            assert marker not in logs, "NGINX access log leaked verification query token"
            # Seed snapshot data without generating email tasks or real accounts.
            runtime.query(versions[0], "INSERT INTO identity_rate_limit (rate_key,attempts,expires_at) VALUES ('delivery-check',7,DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 DAY));")
            saved = json.loads((state / "current.json").read_text())
            with publication_lock(state):
                try:
                    apply_release(versions[1], state, runtime)
                    raise AssertionError("failed candidate unexpectedly succeeded")
                except RuntimeError as error:
                    assert "rolled back" in str(error)
            assert json.loads((state / "current.json").read_text()) == saved
            assert runtime.query(versions[0], "SELECT attempts FROM identity_rate_limit WHERE rate_key='delivery-check';") == "7"
            print("actual failed container restored previous configuration; database row preserved", flush=True)
            with publication_lock(state):
                assert apply_release(versions[0], state, runtime) == "already deployed"
                uploaded = []
                package = create_backup(root, "10", lambda p: export(runtime, versions[0], p),
                                        lambda p: uploaded.append(p.name))
            assert uploaded[-1] == "SUCCESS.json"
            metadata = validate_backup(package, "10")
            restored = restore(package, "10")
            print("real independent restore:", json.dumps(restored), flush=True)
            assert "identity_rate_limit" in metadata["tables"]
            # Check volume and environment isolation; no backend/database host ports.
            config = json.loads(runtime.compose(versions[0], ["config", "--format", "json"], stdout=subprocess.PIPE).stdout)
            assert not config["services"]["backend"].get("ports")
            assert not config["services"]["db"].get("ports")
            assert config["networks"]["default"]["internal"]
            print("HTTPS Cookie/CSRF/401, repeat release, rollback, snapshot counts, schema and isolation: PASS", flush=True)
        finally:
            # Only this newly generated project and its fresh volume are removed.
            runtime.compose(versions[0], ["down", "--volumes", "--remove-orphans"], stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    verify()
