"""Consistent logical backups, snapshot row counts, and a last-upload success marker."""
import argparse
import datetime as dt
import gzip
import hashlib
import json
import os
import re
import resource
import shutil
import subprocess
import tempfile
import uuid
from pathlib import Path
from release import Runtime, atomic_json, load_config, publication_lock

MAX_DUMP_BYTES = 2 * 1024 ** 3


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def table_counts(path):
    # --skip-extended-insert produces one escaped SQL row per line. Counts describe
    # the dump's transaction snapshot, not a second query against changing data.
    tables = {}
    with path.open("rb") as source:
        for line in source:
            created = re.match(rb"CREATE TABLE `([a-zA-Z0-9_]+)`", line)
            inserted = re.match(rb"INSERT INTO `([a-zA-Z0-9_]+)` VALUES", line)
            if created:
                tables[created[1].decode()] = 0
            if inserted:
                name = inserted[1].decode()
                if name not in tables:
                    raise ValueError("dump insert without schema")
                tables[name] += 1
    if "flyway_schema_history" not in tables:
        raise ValueError("dump missing migration history")
    return tables


def create_backup(directory, schema_version, exporter, uploader):
    if not re.fullmatch(r"[1-9][0-9]*", schema_version):
        raise ValueError("invalid backup schema")
    started = dt.datetime.now(dt.timezone.utc)
    package = directory / (started.strftime("%Y%m%dT%H%M%SZ-") + uuid.uuid4().hex[:12])
    package.mkdir(mode=0o700)
    sql = package / "database.sql"
    exporter(sql)  # Export failure prevents even the first upload.
    if sql.stat().st_size > MAX_DUMP_BYTES:
        raise ValueError("backup exceeds 2 GiB limit")
    metadata = {"schema_version": schema_version, "snapshot_started_at": started.isoformat(),
                "tables": table_counts(sql), "uncompressed_bytes": sql.stat().st_size}
    compressed = package / "database.sql.gz"
    with sql.open("rb") as source, gzip.open(compressed, "wb") as destination:
        shutil.copyfileobj(source, destination)
    sql.unlink()
    metadata["sha256"] = checksum(compressed)
    atomic_json(package / "manifest.json", metadata)
    marker = {"manifest_sha256": checksum(package / "manifest.json"),
              "snapshot_started_at": started.isoformat()}
    atomic_json(package / "SUCCESS.json", marker)
    # Marker is visible only after both payload and metadata uploads succeeded.
    for filename in ["database.sql.gz", "manifest.json", "SUCCESS.json"]:
        uploader(package / filename)
    return package


def validate_backup(package, expected_schema):
    try:
        marker = json.loads((package / "SUCCESS.json").read_text())
        if marker["manifest_sha256"] != checksum(package / "manifest.json"):
            raise ValueError("backup manifest checksum mismatch")
        metadata = json.loads((package / "manifest.json").read_text())
        if metadata["schema_version"] != expected_schema:
            raise ValueError("backup schema mismatch")
        if not 0 < metadata["uncompressed_bytes"] <= MAX_DUMP_BYTES:
            raise ValueError("invalid backup size")
        if metadata["sha256"] != checksum(package / "database.sql.gz"):
            raise ValueError("backup checksum mismatch")
        if not metadata["tables"] or any(not re.fullmatch(r"[a-zA-Z0-9_]+", key)
                                         or type(count) is not int or count < 0
                                         for key, count in metadata["tables"].items()):
            raise ValueError("invalid table metadata")
        return metadata
    except (OSError, KeyError, TypeError, json.JSONDecodeError):
        raise ValueError("backup is missing or invalid") from None


def export(runtime, version, destination):
    def bound_output():
        resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_DUMP_BYTES, MAX_DUMP_BYTES))
    with destination.open("wb") as target:
        os.chmod(destination, 0o600)
        runtime.compose(version, ["exec", "-T", "db", "sh", "-c",
            'export MYSQL_PWD="$(cat /run/secrets/app_password)"; exec mysqldump '
            '--single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF '
            '--hex-blob --skip-extended-insert -usnowboard_v2 snowboard_v2'], stdout=target, preexec_fn=bound_output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["run", "status"], nargs="?", default="run")
    parser.add_argument("--config", default="/etc/snowboard-v2/host.json")
    args = parser.parse_args()
    config = load_config(args.config)
    state = Path(config["state_dir"])
    if args.command == "status":
        record = json.loads((state / "last-backup.json").read_text())
        if record["status"] != "success" or (dt.datetime.now(dt.timezone.utc)
            - dt.datetime.fromisoformat(record["snapshot_started_at"])).total_seconds() > 86400:
            raise RuntimeError("backup failed or older than 24 hours")
        print("backup within 24-hour target")
        return
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", config["backup_bucket"]):
        raise ValueError("invalid backup bucket")
    with publication_lock(state):
        try:
            if shutil.disk_usage(config["backup_work_dir"]).free < 5 * 1024 ** 3:
                raise RuntimeError("backup disk reserve below 5 GiB")
            version = json.loads((state / "current.json").read_text())
            runtime = Runtime(config)
            schema = runtime.schema_version(version)
            if schema != version["schema_version"]:
                raise RuntimeError("backup source schema differs from active release")
            with tempfile.TemporaryDirectory(prefix="geer-backup-", dir=config["backup_work_dir"]) as directory:
                def upload(path):
                    subprocess.run(["aws", "s3", "cp", str(path),
                        "s3://" + config["backup_bucket"] + "/db-backups/" + path.parent.name + "/" + path.name,
                        "--region", "ca-central-1", "--sse", "AES256", "--only-show-errors"],
                        check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                package = create_backup(Path(directory), schema,
                    lambda destination: export(runtime, version, destination), upload)
                metadata = validate_backup(package, schema)
                atomic_json(state / "last-backup.json", {"status": "success", "key": package.name,
                    "snapshot_started_at": metadata["snapshot_started_at"]})
            print("backup uploaded with success marker")
        except Exception:
            atomic_json(state / "last-backup.json", {"status": "failed"})
            raise RuntimeError("backup failed; no successful completion recorded") from None


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError):
        print("backup failed or overdue; inspect protected status")
        raise SystemExit(1)
