import copy
import json
import tempfile
import unittest
from pathlib import Path
from test_delivery_support import module, DEPLOY, PRODUCTION

SHA = "a" * 40
NEW_SHA = "b" * 40
REPO = "Honggeer/snowboardLessonBookingV2"


def event():
    return {"action": "completed", "repository": {"full_name": REPO}, "workflow_run": {
        "name": "CI", "conclusion": "success", "event": "push", "head_branch": "main",
        "head_sha": SHA, "head_repository": {"full_name": REPO}}}


class ReleaseGateTest(unittest.TestCase):
    def test_only_enabled_successful_current_main_push_is_accepted(self):
        gate = module(self, "release_gate")
        self.assertTrue(gate.allowed(event(), REPO, SHA, "true"))
        for field, value in [("conclusion", "failure"), ("event", "pull_request"),
                             ("head_branch", "other"), ("head_sha", "../../etc/passwd"),
                             ("head_repository", {"full_name": "fork/repo"}), ("name", "Other")]:
            bad = event()
            bad["workflow_run"][field] = value
            self.assertFalse(gate.allowed(bad, REPO, SHA, "true"), field)
        self.assertFalse(gate.allowed(event(), REPO, NEW_SHA, "true"))
        self.assertFalse(gate.allowed(event(), REPO, SHA, "false"))
        self.assertFalse(gate.allowed({}, REPO, SHA, "true"))

    def test_existing_registry_digest_is_reused_and_access_errors_are_not_hidden(self):
        from types import SimpleNamespace
        registry = module(self, "ecr_existing")
        repository = PRODUCTION["BACKEND_IMAGE"].split("@")[0]
        found = SimpleNamespace(returncode=0, stdout=json.dumps({"imageDetails": [{"imageDigest": "sha256:" + "c" * 64}]}).encode(), stderr=b"")
        self.assertEqual(registry.lookup(repository, SHA, lambda command, **kwargs: found), "sha256:" + "c" * 64)
        missing = SimpleNamespace(returncode=1, stdout=b"", stderr=b"ImageNotFoundException")
        self.assertEqual(registry.lookup(repository, SHA, lambda command, **kwargs: missing), "")
        denied = SimpleNamespace(returncode=1, stdout=b"", stderr=b"AccessDeniedException")
        with self.assertRaises(RuntimeError):
            registry.lookup(repository, SHA, lambda command, **kwargs: denied)


class FakeRuntime:
    def __init__(self):
        self.events = []
        self.fail = set()
        self.schema = "10"

    def start(self, version):
        self.events.append(("start", version["sha"]))
        if version["sha"] in self.fail:
            raise RuntimeError("health failed")

    def health(self, version):
        self.events.append(("health", version["sha"]))

    def schema_version(self, version):
        return self.schema

    def schema_fingerprint(self, version):
        return "fingerprint-" + self.schema

    def stop_application(self, version):
        self.events.append(("stop", version["sha"]))


class ReleaseBehaviorTest(unittest.TestCase):
    def version(self, sha=SHA):
        return {"sha": sha, "backend_image": PRODUCTION["BACKEND_IMAGE"],
                "frontend_image": PRODUCTION["FRONTEND_IMAGE"], "schema_version": "10",
                "bundle": "/test/" + sha}

    def test_success_repeat_and_failure_restore_previous_full_configuration(self):
        release = module(self, "release")
        with tempfile.TemporaryDirectory() as directory:
            runtime = FakeRuntime()
            path = Path(directory)
            release.apply_release(self.version(), path, runtime)
            saved = json.loads((path / "current.json").read_text())
            self.assertEqual(saved["bundle"], "/test/" + SHA)
            self.assertEqual(release.apply_release(self.version(), path, runtime), "already deployed")
            runtime.fail.add(NEW_SHA)
            with self.assertRaisesRegex(RuntimeError, "rolled back"):
                release.apply_release(self.version(NEW_SHA), path, runtime)
            self.assertEqual(json.loads((path / "current.json").read_text()), saved)
            self.assertEqual(runtime.events[-2:], [("start", SHA), ("health", SHA)])

    def test_first_failure_and_incompatible_schema_stop_application_without_deleting_database(self):
        release = module(self, "release")
        with tempfile.TemporaryDirectory() as directory:
            runtime = FakeRuntime()
            runtime.fail.add(SHA)
            with self.assertRaisesRegex(RuntimeError, "no compatible rollback"):
                release.apply_release(self.version(), Path(directory), runtime)
            self.assertFalse((Path(directory) / "current.json").exists())
            self.assertIn(("stop", SHA), runtime.events)
            runtime.fail.clear()
            release.apply_release(self.version(), Path(directory), runtime)
            runtime.schema = "11"
            runtime.fail.add(NEW_SHA)
            with self.assertRaisesRegex(RuntimeError, "no compatible rollback"):
                release.apply_release(self.version(NEW_SHA), Path(directory), runtime)
            self.assertNotIn(("start", SHA), runtime.events[4:])

    def test_host_lock_rejects_concurrent_publication_or_backup(self):
        release = module(self, "release")
        with tempfile.TemporaryDirectory() as directory:
            with release.publication_lock(Path(directory)):
                with self.assertRaisesRegex(RuntimeError, "busy"):
                    with release.publication_lock(Path(directory)):
                        self.fail("two publishers acquired lock")

    def test_partial_failed_migration_prevents_rollback_even_when_version_is_unchanged(self):
        release = module(self, "release")
        with tempfile.TemporaryDirectory() as directory:
            runtime = FakeRuntime()
            release.apply_release(self.version(), Path(directory), runtime)
            runtime.fail.add(NEW_SHA)
            runtime.schema_fingerprint = lambda version: "changed-schema-with-same-version"
            with self.assertRaisesRegex(RuntimeError, "no compatible rollback"):
                release.apply_release(self.version(NEW_SHA), Path(directory), runtime)

    def test_preflight_failure_keeps_existing_application_running(self):
        release = module(self, "release")
        with tempfile.TemporaryDirectory() as directory:
            runtime = FakeRuntime()
            release.apply_release(self.version(), Path(directory), runtime)
            saved = (Path(directory) / "current.json").read_text()
            def fail(version):
                raise RuntimeError("preflight image pull failed")
            runtime.prepare = fail
            with self.assertRaisesRegex(RuntimeError, "preflight"):
                release.apply_release(self.version(NEW_SHA), Path(directory), runtime)
            self.assertEqual((Path(directory) / "current.json").read_text(), saved)
            self.assertNotIn(("stop", NEW_SHA), runtime.events)


class DispatchBehaviorTest(unittest.TestCase):
    def test_archive_paths_and_links_are_rejected_before_extraction(self):
        import io
        import tarfile
        dispatch = module(self, "dispatch")
        for member_name, link in [("../../outside", False), ("/etc/passwd", False), ("deploy/release.py", True)]:
            with tempfile.TemporaryDirectory() as directory:
                archive = Path(directory) / "bundle.tar.gz"
                with tarfile.open(archive, "w:gz") as target:
                    member = tarfile.TarInfo(member_name)
                    if link:
                        member.type = tarfile.SYMTYPE
                        member.linkname = "/etc/passwd"
                        target.addfile(member)
                    else:
                        member.size = 1
                        target.addfile(member, io.BytesIO(b"x"))
                with self.assertRaises(ValueError):
                    dispatch.unpack(archive, Path(directory) / "release")

    def test_invalid_images_and_sha_are_rejected_before_commands(self):
        release = module(self, "release")
        version = ReleaseBehaviorTest().version
        repos = {"backend_repository": PRODUCTION["BACKEND_IMAGE"].split("@")[0],
                 "frontend_repository": PRODUCTION["FRONTEND_IMAGE"].split("@")[0]}
        release.validate_version(version(), repos)
        for key, value in [("sha", "a;id"), ("backend_image", "evil/repo:latest"),
                           ("frontend_image", "../../image"), ("schema_version", "10;id")]:
            bad = version()
            bad[key] = value
            with self.assertRaises(ValueError):
                release.validate_version(bad, repos)


if __name__ == "__main__":
    unittest.main()
