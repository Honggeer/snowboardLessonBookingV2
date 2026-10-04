"""Single-host publication. CLI never accepts shell fragments or database restore."""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
import re
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path


def atomic_json(path, value):
    temporary = path.with_suffix(".tmp")
    with temporary.open("w") as target:
        os.chmod(temporary, 0o600)
        json.dump(value, target, sort_keys=True)
        target.flush()
        os.fsync(target.fileno())
    temporary.replace(path)


@contextlib.contextmanager
def publication_lock(state_dir):
    state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (state_dir / "publication.lock").open("a") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("publication/backup busy") from None
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


def validate_version(version, config):
    if not re.fullmatch(r"[0-9a-f]{40}", version.get("sha", "")):
        raise ValueError("invalid commit SHA")
    if not re.fullmatch(r"[1-9][0-9]*", version.get("schema_version", "")):
        raise ValueError("invalid schema version")
    for kind in ["backend", "frontend"]:
        repository = config[kind + "_repository"]
        if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.ca-central-1\.amazonaws\.com/[a-z0-9/_-]+", repository):
            raise ValueError("repository must be configured Canada Central ECR")
        if not re.fullmatch(re.escape(repository) + r"@sha256:[0-9a-f]{64}", version.get(kind + "_image", "")):
            raise ValueError("image must use configured repository and digest")


def apply_release(version, state_dir, runtime):
    """Caller owns publication_lock across preflight, dump/migration and recovery."""
    current_file = state_dir / "current.json"
    previous = json.loads(current_file.read_text()) if current_file.exists() else None
    if previous and all(previous.get(key) == value for key, value in version.items()):
        runtime.health(previous)
        return "already deployed"
    if hasattr(runtime, "prepare"):
        runtime.prepare(version)  # Failure here has not changed running containers.
    # All failures after starting can require recovery, including incomplete migration.
    try:
        runtime.start(version)
        runtime.health(version)
        if runtime.schema_version(version) != version["schema_version"]:
            raise RuntimeError("unexpected database schema")
        fingerprint = runtime.schema_fingerprint(version)
    except Exception:
        compatible = False
        if previous:
            try:
                compatible = (runtime.schema_version(version) == previous["schema_version"]
                    and runtime.schema_fingerprint(version) == previous.get("schema_fingerprint"))
            except Exception:
                pass
        if compatible:
            try:
                runtime.start(previous)
                runtime.health(previous)
            except Exception:
                runtime.stop_application(version)
                atomic_json(state_dir / "last-release.json", {"sha": version["sha"], "status": "rollback failed"})
                raise RuntimeError("release and rollback failed; application stopped, database preserved") from None
            atomic_json(state_dir / "last-release.json", {"sha": version["sha"], "status": "rolled back"})
            raise RuntimeError("release failed; rolled back to previous images and configuration") from None
        runtime.stop_application(version)
        atomic_json(state_dir / "last-release.json", {"sha": version["sha"], "status": "no compatible rollback"})
        raise RuntimeError("release failed; no compatible rollback, application stopped, database preserved") from None
    if previous:
        atomic_json(state_dir / "previous.json", previous)
    atomic_json(current_file, version | {"schema_fingerprint": fingerprint})
    atomic_json(state_dir / "last-release.json", {"sha": version["sha"], "status": "success"})
    return "deployed"


class Runtime:
    def __init__(self, config):
        self.config = config

    def compose(self, version, arguments, **kwargs):
        env = os.environ.copy()
        # Images are validated before the runtime receives them; never taken from .env.
        env.update(BACKEND_IMAGE=version["backend_image"], FRONTEND_IMAGE=version["frontend_image"])
        cmd = ["docker", "compose", "--project-name", self.config["project"],
               "--env-file", self.config["env_file"], "-f",
               str(Path(version["bundle"]) / "deploy/compose.production.yaml")]
        # Discard container/CLI diagnostics that might include provider or config secrets.
        return subprocess.run(cmd + arguments, env=env, check=True, stderr=subprocess.DEVNULL, **kwargs)

    def prepare(self, version):
        if shutil.disk_usage(version["bundle"]).free < 4 * 1024 ** 3:
            raise RuntimeError("preflight disk reserve below 4 GiB")
        self.compose(version, ["config", "--quiet"], stdout=subprocess.DEVNULL)
        self.compose(version, ["pull", "--quiet"], stdout=subprocess.DEVNULL)

    def start(self, version):
        self.compose(version, ["up", "-d", "--wait", "--wait-timeout", "240"], stdout=subprocess.DEVNULL)

    def health(self, version):
        url = self.config["public_url"] + "/api/actuator/health"
        with urllib.request.urlopen(url, timeout=10) as response:
            if response.status != 200 or json.load(response).get("status") != "UP":
                raise RuntimeError("HTTPS health failed")

    def query(self, version, sql):
        command = ['exec', '-T', 'db', 'sh', '-c',
                   'export MYSQL_PWD="$(cat /run/secrets/app_password)"; exec mysql -usnowboard_v2 -N -B snowboard_v2',
                   ]
        result = self.compose(version, command, input=sql.encode(), stdout=subprocess.PIPE)
        return result.stdout.decode().strip()

    def schema_version(self, version):
        if self.query(version, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;") != "0":
            raise RuntimeError("database contains failed migration")
        return self.query(version, "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;")

    def schema_fingerprint(self, version):
        sql = """
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,''),EXTRA
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX,COLUMN_NAME,NON_UNIQUE
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;
SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME;
SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,COALESCE(REFERENCED_TABLE_NAME,''),COALESCE(REFERENCED_COLUMN_NAME,'')
FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION;
SELECT CONSTRAINT_NAME,CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY CONSTRAINT_NAME;
SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank;
"""
        return hashlib.sha256(self.query(version, sql).encode()).hexdigest()

    def stop_application(self, version):
        self.compose(version, ["stop", "frontend", "backend"], stdout=subprocess.DEVNULL)


def load_config(path):
    path = Path(path)
    if path.stat().st_mode & 0o077:
        raise ValueError("host config must be mode 0600")
    config = json.loads(path.read_text())
    if config.get("project") != "snowboard-v2-production":
        raise ValueError("host config must target the production project")
    if not re.fullmatch(r"https://[a-z0-9.-]+", config.get("public_url", "")):
        raise ValueError("public URL must be HTTPS with no path")
    for key in ["env_file", "state_dir", "releases_dir"]:
        if not Path(config[key]).is_absolute():
            raise ValueError("host paths must be absolute")
    return config


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="/etc/snowboard-v2/host.json")
    parser.add_argument("--manifest", required=True)
    args = parser.parse_args()
    config = load_config(args.config)
    version = json.loads(Path(args.manifest).read_text())
    validate_version(version, config)
    bundle = Path(version["bundle"]).resolve()
    if bundle != Path(config["releases_dir"]).resolve() / version["sha"]:
        raise ValueError("bundle outside configured release path")
    with publication_lock(Path(config["state_dir"])):
        print(apply_release(version, Path(config["state_dir"]), Runtime(config)))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError):
        # Full raw exceptions can contain secret paths/provider diagnostics.
        print("release failed; inspect protected state and service status")
        raise SystemExit(1)
