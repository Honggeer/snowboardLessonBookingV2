"""Restore only into a new isolated MySQL container, never an existing database."""
import argparse
import gzip
import json
import os
import re
import secrets
import subprocess
import tempfile
import time
import uuid
from pathlib import Path
from backup import MAX_DUMP_BYTES, validate_backup


def validate_target(name):
    if not re.fullmatch(r"geer-restore-[0-9a-f]{12}", name):
        raise ValueError("restore target must be a new isolated container")


def restore(package, expected_schema, name=None):
    metadata = validate_backup(package, expected_schema)
    name = name or "geer-restore-" + uuid.uuid4().hex[:12]
    validate_target(name)
    # Refuse existing containers even when their names have the test prefix.
    if subprocess.run(["docker", "container", "inspect", name], capture_output=True).returncode == 0:
        raise ValueError("restore target already exists")
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="geer-restore-", dir=package.parent) as directory:
        root = Path(directory) / "password"
        root.write_text(secrets.token_urlsafe(32))
        root.chmod(0o600)
        sql = Path(directory) / "database.sql"
        with gzip.open(package / "database.sql.gz", "rb") as source, sql.open("wb") as destination:
            total = 0
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                total += len(chunk)
                if total > MAX_DUMP_BYTES or total > metadata["uncompressed_bytes"]:
                    raise ValueError("expanded dump exceeds declared limit")
                destination.write(chunk)
        if total != metadata["uncompressed_bytes"]:
            raise ValueError("expanded dump size mismatch")
        created = False
        try:
            subprocess.run(["docker", "run", "--detach", "--name", name, "--network", "none",
                "--memory", "640m", "--mount", "type=bind,source=" + str(root) + ",target=/run/secrets/password,readonly",
                "-e", "MYSQL_ROOT_PASSWORD_FILE=/run/secrets/password", "-e", "MYSQL_DATABASE=snowboard_v2",
                "mysql:8.4", "--innodb-buffer-pool-size=134217728"], check=True,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            created = True
            command = ["docker", "exec", "-i", name, "sh", "-c",
                'export MYSQL_PWD="$(cat /run/secrets/password)"; exec mysql --binary-mode -uroot -N -B snowboard_v2']
            deadline = time.monotonic() + 150
            while subprocess.run(command, input=b"SELECT 1;", capture_output=True).returncode:
                if time.monotonic() > deadline:
                    raise RuntimeError("restore database startup timed out")
                time.sleep(2)
            with sql.open("rb") as source:
                subprocess.run(command, stdin=source, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
            tables = subprocess.run(command, input=b"SHOW TABLES;", capture_output=True, check=True).stdout.decode().split()
            if set(tables) != set(metadata["tables"]):
                raise RuntimeError("restored table set mismatch")
            for table, expected in metadata["tables"].items():
                actual = subprocess.run(command, input=("SELECT COUNT(*) FROM `" + table + "`;").encode(),
                                        capture_output=True, check=True).stdout.decode().strip()
                if actual != str(expected):
                    raise RuntimeError("restored row counts mismatch")
            schema = subprocess.run(command, input=b"SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;",
                                    capture_output=True, check=True).stdout.decode().strip()
            if schema != expected_schema:
                raise RuntimeError("restored migration version mismatch")
            return {"tables": len(tables), "rows": sum(metadata["tables"].values()),
                    "schema_version": schema, "elapsed_seconds": round(time.monotonic() - started, 2)}
        finally:
            if created:
                subprocess.run(["docker", "rm", "--force", "--volumes", name], check=True,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--backup", required=True)
    parser.add_argument("--schema-version", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(restore(Path(args.backup), args.schema_version)))
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError):
        print("isolated restore failed; production database untouched")
        raise SystemExit(1)
