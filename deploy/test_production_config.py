import json
import unittest
from test_delivery_support import ROOT, DEPLOY, PRODUCTION, render


class ProductionConfigTest(unittest.TestCase):
    def config(self, values=PRODUCTION):
        path = DEPLOY / "compose.production.yaml"
        self.assertTrue(path.is_file(), "production isolation/TLS configuration is not implemented")
        result = render(path, values)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def test_only_frontend_exposes_ports_and_all_services_are_bounded(self):
        services = self.config()["services"]
        self.assertEqual(set(services), {"frontend", "backend", "db"})
        self.assertEqual({port["target"] for port in services["frontend"]["ports"]}, {80, 443})
        for name, service in services.items():
            self.assertNotIn("build", service)
            self.assertIn("healthcheck", service)
            self.assertEqual(service["restart"], "unless-stopped")
            self.assertIn("mem_limit", service)
            self.assertIn("max-size", service["logging"]["options"])
            if name != "frontend":
                self.assertFalse(service.get("ports"))
        self.assertIn("@sha256:", services["backend"]["image"])

    def test_secrets_and_secure_settings_reach_application(self):
        services = self.config()["services"]
        env = services["backend"]["environment"]
        self.assertNotIn("local", env.get("SPRING_PROFILES_ACTIVE", ""))
        self.assertEqual(env["SPRING_CONFIG_IMPORT"], "configtree:/run/secrets/spring/")
        self.assertNotIn("DB_PASSWORD", env)
        self.assertNotIn("MAIL_PASSWORD", env)
        self.assertEqual(env["MEDIA_STORAGE_MODE"], "aws")
        self.assertEqual(env["IDENTITY_COOKIE_SECURE"], "true")
        self.assertEqual(env["IDENTITY_TRUSTED_PROXY_HOST"], "frontend")
        self.assertEqual(env.get("MANAGEMENT_HEALTH_MAIL_ENABLED"), "false",
                         "SMTP outages must not mark the HTTP/database service unavailable")
        self.assertFalse(services["db"].get("ports"))
        self.assertIn("MYSQL_PASSWORD_FILE", services["db"]["environment"])
        for mount in services["backend"]["volumes"]:
            if mount["target"].startswith("/run/secrets"):
                self.assertTrue(mount["read_only"])

    def test_missing_parameters_fail_closed(self):
        self.config()
        for key in ["BACKEND_IMAGE", "SECRETS_DIR", "APP_PUBLIC_URL", "MAIL_HOST", "MEDIA_FROZEN_BUCKET"]:
            values = dict(PRODUCTION)
            values.pop(key)
            self.assertNotEqual(render(DEPLOY / "compose.production.yaml", values).returncode, 0, key)


if __name__ == "__main__":
    unittest.main()
