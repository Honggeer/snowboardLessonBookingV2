"""CI submission uses JSON parameters; waits for remote completion, never shell text."""
import json
import os
import re
import subprocess
import time


def main():
    params = {}
    for key, variable, pattern in [
        ("CommitSha", "RUN_SHA", r"[0-9a-f]{40}"),
        ("BundleSha256", "BUNDLE_CHECKSUM", r"[0-9a-f]{64}"),
        ("BackendDigest", "BACKEND_DIGEST", r"sha256:[0-9a-f]{64}"),
        ("FrontendDigest", "FRONTEND_DIGEST", r"sha256:[0-9a-f]{64}")]:
        value = os.environ[variable]
        if not re.fullmatch(pattern, value):
            raise ValueError("invalid publication parameter")
        params[key] = [value]
    instance = os.environ["INSTANCE_ID"]
    document = os.environ["SSM_DOCUMENT"]
    if not re.fullmatch(r"i-[0-9a-f]{17}", instance) or not re.fullmatch(r"snowboard-v2-release(?:-[0-9]+)?", document):
        raise ValueError("unexpected publication target/document")
    base = ["aws", "ssm"]
    result = subprocess.run(base + ["send-command", "--instance-ids", instance, "--document-name", document,
        "--parameters", json.dumps(params), "--timeout-seconds", "900", "--region", "ca-central-1",
        "--output", "json"], capture_output=True, check=True)
    command_id = json.loads(result.stdout)["Command"]["CommandId"]
    print("SSM command:", command_id)
    deadline = time.monotonic() + 1200
    while time.monotonic() < deadline:
        result = subprocess.run(base + ["get-command-invocation", "--command-id", command_id,
            "--instance-id", instance, "--region", "ca-central-1", "--output", "json"], capture_output=True)
        if result.returncode == 0:
            invocation = json.loads(result.stdout)
            status = invocation["Status"]
            if status == "Success" and invocation["ResponseCode"] == 0:
                print("publication succeeded:", os.environ["RUN_SHA"])
                return
            if status not in {"Pending", "InProgress", "Delayed", "Cancelling"}:
                raise RuntimeError("SSM publication failed: " + status)
        elif b"InvocationDoesNotExist" not in result.stderr:
            raise RuntimeError("SSM status query failed")
        time.sleep(5)
    raise RuntimeError("SSM result timed out; remote command may still be running; inspect before retry")


if __name__ == "__main__":
    main()
