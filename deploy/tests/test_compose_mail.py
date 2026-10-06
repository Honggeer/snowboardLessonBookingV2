"""Check Compose passes mail settings through without using real credentials."""

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from test_delivery_support import ROOT, COMPOSE as DOCKER_COMPOSE


COMPOSE = ROOT / "deploy" / "compose.yaml"
BASE = {
    "DB_PASSWORD": "fake-db-password",
    "MYSQL_ROOT_PASSWORD": "fake-root-password",
    "VERIFICATION_KEY": "fake-verification-key-for-config-test",
}


def backend_environment(extra):
    with tempfile.TemporaryDirectory() as directory:
        config = Path(directory) / "compose.env"
        config.write_text("\n".join(f"{key}={value}" for key, value in (BASE | extra).items()) + "\n")
        process_env = os.environ.copy()
        for key in (set(BASE) | set(extra) | {
            "MAIL_HOST", "MAIL_PORT", "MAIL_USER", "MAIL_PASSWORD", "MAIL_FROM",
            "MAIL_SMTP_AUTH", "MAIL_STARTTLS", "V2_PUBLIC_URL", "APP_PUBLIC_URL",
            "BOOKING_REMINDERS_ENABLED", "BOOKING_MAIL_WORKER_ENABLED",
        }):
            process_env.pop(key, None)
        result = subprocess.run(
            DOCKER_COMPOSE + ["--env-file", str(config), "-f", str(COMPOSE),
             "config", "--format", "json"],
            cwd=ROOT, env=process_env, capture_output=True, text=True, check=True,
        )
        return json.loads(result.stdout)["services"]["backend"]["environment"]


class ComposeMailTest(unittest.TestCase):
    def test_configured_smtp_and_public_url_reach_backend(self):
        expected = {
            "MAIL_HOST": "smtp.example.test",
            "MAIL_PORT": "587",
            "MAIL_USER": "sender@example.test",
            "MAIL_PASSWORD": "fake-mail-password",
            "MAIL_FROM": "sender@example.test",
            "MAIL_SMTP_AUTH": "true",
            "MAIL_STARTTLS": "true",
            "APP_PUBLIC_URL": "https://lessons.example.test",
        }
        rendered = backend_environment({**{k: v for k, v in expected.items()
                                           if k != "APP_PUBLIC_URL"},
                                        "V2_PUBLIC_URL": expected["APP_PUBLIC_URL"]})
        for key, value in expected.items():
            self.assertEqual(rendered.get(key), value, f"Compose did not pass {key}")

    def test_missing_smtp_settings_keep_mailpit_for_local_tests(self):
        rendered = backend_environment({})
        self.assertEqual(rendered.get("MAIL_HOST"), "mailpit")
        self.assertEqual(str(rendered.get("MAIL_PORT")), "1025")
        self.assertEqual(rendered.get("MAIL_FROM"), "geer@local.test")
        self.assertEqual(rendered.get("APP_PUBLIC_URL"), "http://localhost:8088")

    def test_reminders_and_worker_can_be_paused_without_new_credentials(self):
        for value in ("true", "false"):
            rendered = backend_environment({"BOOKING_REMINDERS_ENABLED": value,
                                            "BOOKING_MAIL_WORKER_ENABLED": value})
            self.assertEqual(rendered.get("BOOKING_REMINDERS_ENABLED"), value)
            self.assertEqual(rendered.get("BOOKING_MAIL_WORKER_ENABLED"), value)
        defaults = backend_environment({})
        self.assertEqual(defaults.get("BOOKING_REMINDERS_ENABLED"), "true")
        self.assertEqual(defaults.get("BOOKING_MAIL_WORKER_ENABLED"), "true")


if __name__ == "__main__":
    unittest.main()
