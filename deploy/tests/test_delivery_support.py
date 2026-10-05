"""Shared fixtures for delivery tests; never reads developer/production secrets."""
import importlib.util
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / "deploy"
RUNTIME = DEPLOY / "runtime"
sys.path.insert(0, str(RUNTIME))
COMPOSE = ["docker", "compose"] if shutil.which("docker") and subprocess.run(
    ["docker", "compose", "version"], capture_output=True).returncode == 0 else ["docker-compose"]


def module(test, name):
    path = next((folder / (name + ".py") for folder in
                 [RUNTIME, DEPLOY / "ci", DEPLOY / "aws"]
                 if (folder / (name + ".py")).is_file()), RUNTIME / (name + ".py"))
    test.assertTrue(path.is_file(), name + " delivery behavior is not implemented")
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def render(path, values):
    # Empty env-file and a clean environment prevent deploy/.env masking omissions.
    env = {key: value for key, value in os.environ.items()
           if key in {"PATH", "HOME", "DOCKER_HOST", "DOCKER_CONFIG", "TMPDIR"}}
    return subprocess.run(COMPOSE + ["--env-file", "/dev/null", "-f", str(path),
                                    "config", "--format", "json"],
                          env=env | values, capture_output=True, text=True)


PRODUCTION = {
    "BACKEND_IMAGE": "123456789012.dkr.ecr.ca-central-1.amazonaws.com/geer-backend@sha256:" + "a" * 64,
    "FRONTEND_IMAGE": "123456789012.dkr.ecr.ca-central-1.amazonaws.com/geer-frontend@sha256:" + "b" * 64,
    "SECRETS_DIR": "/tmp/delivery-test-secrets",
    "TLS_DIR": "/tmp/delivery-test-tls",
    "ACME_DIR": "/tmp/delivery-test-acme",
    "APP_PUBLIC_URL": "https://lessons.example.test",
    "MAIL_HOST": "smtp.example.test", "MAIL_PORT": "587",
    "MAIL_USER": "sender@example.test", "MAIL_FROM": "sender@example.test",
    "MEDIA_STAGING_BUCKET": "delivery-test-staging",
    "MEDIA_FROZEN_BUCKET": "delivery-test-frozen",
    "MEDIA_CDN_BASE_URL": "https://cdn.example.test",
    "MEDIA_KEY_PAIR_ID": "KTESTEXAMPLE",
}
