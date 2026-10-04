import gzip
import hashlib
import json
import tempfile
import unittest
import subprocess
from unittest.mock import patch
from pathlib import Path
from test_delivery_support import module

DUMP = b'CREATE TABLE `sample` (id int);\nINSERT INTO `sample` VALUES (1);\nCREATE TABLE `flyway_schema_history` (version varchar(20));\nINSERT INTO `flyway_schema_history` VALUES (\'10\');\n'


class BackupRestoreBehaviorTest(unittest.TestCase):
    def test_success_manifest_counts_and_upload_marker_only_after_complete_backup(self):
        backup = module(self, "backup")
        with tempfile.TemporaryDirectory() as directory:
            uploaded = []
            def exporter(destination):
                destination.write_bytes(DUMP)
            package = backup.create_backup(Path(directory), "10", exporter, lambda p: uploaded.append(p.name))
            metadata = backup.validate_backup(package, "10")
            self.assertEqual(metadata["tables"], {"sample": 1, "flyway_schema_history": 1})
            self.assertEqual(uploaded[-1], "SUCCESS.json")
            self.assertEqual(len(uploaded), 3)

    def test_dump_and_upload_failure_never_publish_success(self):
        backup = module(self, "backup")
        with tempfile.TemporaryDirectory() as directory:
            def failed_export(path):
                path.write_bytes(DUMP[:10])
                raise RuntimeError("dump failed")
            uploaded = []
            with self.assertRaises(RuntimeError):
                backup.create_backup(Path(directory), "10", failed_export, lambda p: uploaded.append(p.name))
            self.assertFalse(uploaded)
            def exporter(path):
                path.write_bytes(DUMP)
            def failed_upload(path):
                uploaded.append(path.name)
                raise RuntimeError("S3 unavailable")
            with self.assertRaises(RuntimeError):
                backup.create_backup(Path(directory), "10", exporter, failed_upload)
            self.assertNotIn("SUCCESS.json", uploaded)

    def test_corrupt_missing_or_schema_mismatch_backup_is_rejected_before_restore(self):
        backup = module(self, "backup")
        with tempfile.TemporaryDirectory() as directory:
            package = backup.create_backup(Path(directory), "10", lambda p: p.write_bytes(DUMP), lambda p: None)
            with self.assertRaisesRegex(ValueError, "schema"):
                backup.validate_backup(package, "9")
            (package / "database.sql.gz").write_bytes(b"corrupt")
            with self.assertRaisesRegex(ValueError, "checksum"):
                backup.validate_backup(package, "10")
            (package / "database.sql.gz").unlink()
            with self.assertRaises(ValueError):
                backup.validate_backup(package, "10")

    def test_restore_target_cannot_be_production(self):
        restore = module(self, "restore_check")
        for name in ["snowboard-v2-db-1", "production", "../db", "geer-restore-;id"]:
            with self.assertRaises(ValueError):
                restore.validate_target(name)
        restore.validate_target("geer-restore-" + "a" * 12)

    def test_dump_size_is_bounded_while_exporting(self):
        backup = module(self, "backup")
        class DumpRuntime:
            def compose(self, version, arguments, **kwargs):
                return subprocess.run(["python3", "-c", "import sys; sys.stdout.buffer.write(b'x' * 1024)"],
                                      check=True, stderr=subprocess.DEVNULL, **kwargs)
        with tempfile.TemporaryDirectory() as directory, patch.object(backup, "MAX_DUMP_BYTES", 64):
            destination = Path(directory) / "dump.sql"
            with self.assertRaises(subprocess.CalledProcessError):
                backup.export(DumpRuntime(), {}, destination)
            self.assertLessEqual(destination.stat().st_size, 64)


if __name__ == "__main__":
    unittest.main()
