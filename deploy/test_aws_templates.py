import json
import tempfile
import unittest
from pathlib import Path
from test_delivery_support import module, DEPLOY


class AWSTemplateTest(unittest.TestCase):
    def test_ci_and_instance_have_distinct_scoped_permissions_and_safe_ssm_parameters(self):
        templates = module(self, "render_aws_templates")
        result = templates.render({
            "account_id": "123456789012", "instance_id": "i-0c7978984740cbd58",
            "oidc_subject": "repo:Honggeer/snowboardLessonBookingV2:environment:production",
            "staging_bucket": "geer-test-staging", "frozen_bucket": "geer-test-frozen",
            "operations_bucket": "geer-test-operations", "origin": "https://lessons.example.test",
            "distribution_id": "ETESTEXAMPLE"})
        ci = result["ci-permissions.json"]["Statement"]
        sends = [statement for statement in ci if "ssm:SendCommand" in statement["Action"]]
        self.assertEqual(len(sends), 1)
        self.assertIn("document/snowboard-v2-release", sends[0]["Resource"][0])
        self.assertIn("instance/i-0c7978984740cbd58", sends[0]["Resource"][1])
        self.assertNotIn("AWS-RunShellScript", json.dumps(ci))
        self.assertNotIn("s3:DeleteObject", json.dumps(ci))
        document = result["ssm-document.json"]
        for parameter in document["parameters"].values():
            self.assertEqual(parameter["interpolationType"], "ENV_VAR")
            self.assertTrue(parameter["allowedPattern"].startswith("^"))
        self.assertIn("db-backups/", json.dumps(result["operations-lifecycle.json"]))
        self.assertEqual(result["operations-lifecycle.json"]["Rules"][0]["Expiration"]["Days"], 7)
        self.assertIn("cloudfront-distribution.json", result, "signed viewer distribution template is missing")
        distribution = result["cloudfront-distribution.json"]["DistributionConfig"]
        self.assertTrue(distribution["DefaultCacheBehavior"]["TrustedKeyGroups"]["Enabled"])
        self.assertTrue(distribution["Origins"]["Items"][0]["OriginAccessControlId"])

    def test_invalid_account_origin_and_subject_are_rejected(self):
        templates = module(self, "render_aws_templates")
        params = json.loads((DEPLOY / "aws/parameters.example.json").read_text())
        for key, value in [("account_id", "*"), ("origin", "http://example.test"),
                           ("oidc_subject", "repo:*:environment:production")]:
            with self.assertRaises(ValueError):
                templates.render(params | {key: value})


if __name__ == "__main__":
    unittest.main()
