import json
import re
import unittest
from test_delivery_support import ROOT, DEPLOY, render


class CleanCIConfigTest(unittest.TestCase):
    def test_workflow_configures_clean_compose_without_local_secrets(self):
        text = (ROOT / ".github/workflows/ci.yml").read_text()
        block = text.split("  docs-and-compose:", 1)[1].split("    steps:", 1)[0]
        env = dict(re.findall(r"^      ([A-Z_]+): ([^\n]+)$", block, re.M))
        result = render(DEPLOY / "compose.yaml", env)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertGreaterEqual(len(json.loads(result.stdout)["services"]["backend"]["environment"]["VERIFICATION_KEY"]), 32)

    def test_normal_deployment_still_rejects_missing_key(self):
        result = render(DEPLOY / "compose.yaml", {"DB_PASSWORD": "test-only", "MYSQL_ROOT_PASSWORD": "test-root-only"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("VERIFICATION_KEY", result.stderr)


if __name__ == "__main__":
    unittest.main()
