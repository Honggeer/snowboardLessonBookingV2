import subprocess
import tempfile
import unittest
from pathlib import Path
from test_delivery_support import module


class CertificateRenewalTest(unittest.TestCase):
    def test_mismatched_key_is_rejected_before_frontend_reload(self):
        renewal = module(self, "renew_certificate")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ["first", "second"]:
                subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "2",
                    "-subj", "/CN=lessons.example.test", "-keyout", str(root / (name + ".key")),
                    "-out", str(root / (name + ".pem"))], check=True, capture_output=True)
            renewal.validate_pair(root / "first.pem", root / "first.key")
            with self.assertRaisesRegex(ValueError, "match"):
                renewal.validate_pair(root / "first.pem", root / "second.key")


if __name__ == "__main__":
    unittest.main()
