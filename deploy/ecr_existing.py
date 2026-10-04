"""Reuse images of an already built SHA when retrying an immutable ECR tag."""
import json
import os
import re
import subprocess


def lookup(repository, sha, runner=subprocess.run):
    if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.ca-central-1\.amazonaws\.com/[a-z0-9/_-]+", repository) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("invalid image lookup")
    result = runner(["aws", "ecr", "describe-images", "--registry-id", repository.split(".", 1)[0],
        "--repository-name", repository.split("/", 1)[1],
        "--image-ids", "imageTag=" + sha, "--region", "ca-central-1", "--output", "json"], capture_output=True)
    if result.returncode:
        if b"ImageNotFoundException" in result.stderr:
            return ""
        raise RuntimeError("ECR image lookup failed")
    digest = json.loads(result.stdout)["imageDetails"][0]["imageDigest"]
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("invalid registry digest")
    return digest


if __name__ == "__main__":
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        for kind in ["backend", "frontend"]:
            digest = lookup(os.environ[kind.upper() + "_REPOSITORY"], os.environ["RUN_SHA"])
            output.write(kind + "=" + digest + "\n")
