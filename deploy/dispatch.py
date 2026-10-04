"""Preinstalled SSM entrypoint: fixed bucket, bounded archive, serialized dispatch."""
import argparse
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import tarfile
import tempfile
import urllib.request
from pathlib import Path
from release import Runtime, apply_release, atomic_json, load_config, publication_lock, validate_version

FILES = {"deploy/compose.production.yaml", "frontend/nginx.production.conf", "version.json",
         "deploy/release.py", "deploy/release.sh", "deploy/backup.py", "deploy/backup.sh",
         "deploy/restore_check.py", "deploy/restore-check.sh"}


def unpack(archive_path, destination):
    with tarfile.open(archive_path, "r:gz") as archive:
        members = archive.getmembers()
        names = [member.name for member in members]
        if set(names) != FILES or len(names) != len(FILES):
            raise ValueError("unexpected bundle files")
        if sum(member.size for member in members) > 2 * 1024 ** 2:
            raise ValueError("bundle exceeds 2 MiB limit")
        if any(not member.isfile() or member.size < 0 for member in members):
            raise ValueError("bundle links/devices are forbidden")
        destination.mkdir(mode=0o700)
        for member in members:
            target = destination / member.name
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as source, target.open("wb") as output:
                output.write(source.read())
            target.chmod(0o755 if member.name.endswith(".sh") else 0o644)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--bundle-sha256", required=True)
    parser.add_argument("--backend-digest", required=True)
    parser.add_argument("--frontend-digest", required=True)
    parser.add_argument("--config", default="/etc/snowboard-v2/host.json")
    args = parser.parse_args()
    for value, pattern in [(args.sha, r"[0-9a-f]{40}"), (args.bundle_sha256, r"[0-9a-f]{64}"),
                           (args.backend_digest, r"sha256:[0-9a-f]{64}"), (args.frontend_digest, r"sha256:[0-9a-f]{64}")]:
        if not re.fullmatch(pattern, value):
            raise ValueError("invalid SSM release parameter")
    config = load_config(args.config)
    state = Path(config["state_dir"])
    with publication_lock(state):
        # A queued SSM command can become stale after CI's last check.
        request = urllib.request.Request("https://api.github.com/repos/" + config["repository"] + "/commits/main",
                                         headers={"User-Agent": "snowboard-v2-dispatch"})
        with urllib.request.urlopen(request, timeout=15) as response:
            if json.load(response)["sha"] != args.sha:
                raise ValueError("superseded main commit")
        releases = Path(config["releases_dir"])
        releases.mkdir(parents=True, exist_ok=True, mode=0o700)
        bundle = releases / args.sha
        if bundle.exists():
            if (bundle / ".bundle-sha256").read_text() != args.bundle_sha256:
                raise ValueError("existing release has a different bundle checksum")
        else:
            with tempfile.TemporaryDirectory(prefix="delivery-", dir=releases) as directory:
                archive = Path(directory) / "bundle.tar.gz"
                subprocess.run(["aws", "s3", "cp", "s3://" + config["release_bucket"] + "/releases/" + args.sha + ".tar.gz",
                    str(archive), "--region", "ca-central-1", "--only-show-errors"], check=True,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if archive.stat().st_size > 2 * 1024 ** 2 or hashlib.sha256(archive.read_bytes()).hexdigest() != args.bundle_sha256:
                    raise ValueError("bundle checksum/size mismatch")
                extracted = Path(directory) / "extracted"
                unpack(archive, extracted)
                (extracted / ".bundle-sha256").write_text(args.bundle_sha256)
                extracted.rename(bundle)
        version = json.loads((bundle / "version.json").read_text())
        if version["sha"] != args.sha:
            raise ValueError("bundle commit mismatch")
        version.update(backend_image=config["backend_repository"] + "@" + args.backend_digest,
                       frontend_image=config["frontend_repository"] + "@" + args.frontend_digest,
                       bundle=str(bundle))
        validate_version(version, config)
        registry = config["backend_repository"].split("/")[0]
        if config["frontend_repository"].split("/")[0] != registry:
            raise ValueError("frontend and backend must use the same ECR registry")
        password = subprocess.run(["aws", "ecr", "get-login-password", "--region", "ca-central-1"],
                                  stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True).stdout
        subprocess.run(["docker", "login", "--username", "AWS", "--password-stdin", registry],
                       input=password, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        del password
        # Execute the reviewed coordinator from the same immutable bundle as its
        # Compose and NGINX files. The installed dispatcher keeps the outer lock.
        spec = importlib.util.spec_from_file_location("bundled_release", bundle / "deploy/release.py")
        coordinator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(coordinator)
        print(coordinator.apply_release(version, state, coordinator.Runtime(config)))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("SSM publication rejected/failed; inspect protected state")
        raise SystemExit(1)
