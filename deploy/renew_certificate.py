"""Certbot hook: validate key pair, replace mounted files, test/reload NGINX."""
import argparse
import json
import os
import subprocess
import tempfile
from pathlib import Path
from release import Runtime, load_config, publication_lock


def validate_pair(certificate, key):
    cert_public = subprocess.run(["openssl", "x509", "-in", str(certificate), "-pubkey", "-noout"],
                                 check=True, capture_output=True).stdout
    key_public = subprocess.run(["openssl", "pkey", "-in", str(key), "-pubout"],
                                check=True, capture_output=True).stdout
    if cert_public != key_public:
        raise ValueError("certificate and private key do not match")
    subprocess.run(["openssl", "x509", "-in", str(certificate), "-checkend", "86400", "-noout"],
                   check=True, capture_output=True)


def replace(path, contents, mode):
    descriptor, temporary = tempfile.mkstemp(prefix=".renew-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as target:
            os.fchmod(target.fileno(), mode)
            target.write(contents)
            target.flush()
            os.fsync(target.fileno())
        Path(temporary).replace(path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def deploy_certificate(certificate, key, tls_dir, runtime, version):
    validate_pair(certificate, key)
    new_cert, new_key = certificate.read_bytes(), key.read_bytes()
    cert_target, key_target = tls_dir / "fullchain.pem", tls_dir / "privkey.pem"
    old_cert, old_key = cert_target.read_bytes(), key_target.read_bytes()
    replace(cert_target, new_cert, 0o644)
    replace(key_target, new_key, 0o600)
    try:
        runtime.compose(version, ["exec", "-T", "frontend", "nginx", "-t"], stdout=subprocess.DEVNULL)
        runtime.compose(version, ["exec", "-T", "frontend", "nginx", "-s", "reload"], stdout=subprocess.DEVNULL)
    except Exception:
        replace(cert_target, old_cert, 0o644)
        replace(key_target, old_key, 0o600)
        raise RuntimeError("certificate reload failed; original mounted files restored") from None


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="/etc/snowboard-v2/host.json")
    args = parser.parse_args()
    try:
        config = load_config(args.config)
        source = Path(os.environ["RENEWED_LINEAGE"]).resolve()
        if source != Path(config["certificate_lineage"]).resolve():
            raise ValueError("unexpected certificate lineage")
        with publication_lock(Path(config["state_dir"])):
            version = json.loads((Path(config["state_dir"]) / "current.json").read_text())
            deploy_certificate(source / "fullchain.pem", source / "privkey.pem", Path(config["tls_dir"]), Runtime(config), version)
        print("renewed certificate validated and frontend reloaded")
    except Exception:
        print("certificate renewal hook failed; inspect protected configuration and certbot status")
        raise SystemExit(1)
