"""Grouped sources must keep the bundle contract used by the installed EC2 dispatcher."""
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path
from test_delivery_support import DEPLOY, ROOT, module


INSTALLED_BUNDLE_FILES = {
    "deploy/compose.production.yaml", "frontend/nginx.production.conf", "version.json",
    "deploy/release.py", "deploy/release.sh", "deploy/backup.py", "deploy/backup.sh",
    "deploy/restore_check.py", "deploy/restore-check.sh",
}


class BundleCompatibilityTest(unittest.TestCase):
    def grouped_checkout(self, root):
        for name in INSTALLED_BUNDLE_FILES - {"version.json"}:
            if name.startswith("deploy/") and name != "deploy/compose.production.yaml":
                source = DEPLOY / "runtime" / Path(name).name
                target = root / "deploy/runtime" / Path(name).name
            else:
                source, target = ROOT / name, root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
        migration = root / "backend/src/main/resources/db/migration/V10__test.sql"
        migration.parent.mkdir(parents=True)
        migration.write_text("-- test-only migration marker\n")
        (root / "deploy/.env").write_text("DO_NOT_PACKAGE_TEST_SECRET=fixture-secret\n")

    def build(self, root, destination):
        builder = module(self, "bundle_delivery")
        try:
            builder.package(root, "a" * 40, destination)
        except OSError as error:
            self.fail("packaging does not support grouped runtime sources: " + str(error))

    def test_grouped_checkout_builds_a_reproducible_secret_free_compatible_bundle(self):
        dispatch = module(self, "dispatch")
        self.assertEqual(dispatch.FILES, INSTALLED_BUNDLE_FILES)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.grouped_checkout(root)
            first, second = root / "first.tar.gz", root / "second.tar.gz"
            self.build(root, first)
            self.build(root, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            dispatch.unpack(first, root / "extracted")
            with tarfile.open(first) as archive:
                self.assertEqual(set(archive.getnames()), INSTALLED_BUNDLE_FILES)
                for member in archive.getmembers():
                    self.assertNotIn(b"fixture-secret", archive.extractfile(member).read())

    def test_packaged_commands_resolve_imports_outside_the_repository(self):
        dispatch = module(self, "dispatch")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.grouped_checkout(root)
            archive = root / "bundle.tar.gz"
            self.build(root, archive)
            dispatch.unpack(archive, root / "extracted")
            for command in ["release.sh", "backup.sh", "restore-check.sh"]:
                result = subprocess.run([str(root / "extracted/deploy" / command), "--help"],
                                        cwd=root, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
