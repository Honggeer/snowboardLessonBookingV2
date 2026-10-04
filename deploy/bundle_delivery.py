"""Build a reproducible, secret-free delivery archive from the tested checkout."""
import argparse
import gzip
import io
import json
import re
import tarfile
from pathlib import Path

FILES = ["deploy/compose.production.yaml", "frontend/nginx.production.conf",
         "deploy/release.py", "deploy/release.sh", "deploy/backup.py", "deploy/backup.sh",
         "deploy/restore_check.py", "deploy/restore-check.sh"]


def package(root, sha, destination):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("invalid bundle SHA")
    migrations = (root / "backend/src/main/resources/db/migration").glob("V*__*.sql")
    schema = str(max(int(path.name.split("__")[0][1:]) for path in migrations))
    contents = {name: (root / name).read_bytes() for name in FILES}
    contents["version.json"] = json.dumps({"sha": sha, "schema_version": schema}, sort_keys=True).encode()
    with destination.open("wb") as target, gzip.GzipFile(fileobj=target, mode="wb", mtime=0, filename="") as compressed:
        with tarfile.open(fileobj=compressed, mode="w") as archive:
            for name, payload in sorted(contents.items()):
                member = tarfile.TarInfo(name)
                member.size = len(payload)
                member.mode = 0o755 if name.endswith(".sh") else 0o644
                archive.addfile(member, io.BytesIO(payload))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    package(Path(__file__).resolve().parents[1], args.sha, Path(args.output))
