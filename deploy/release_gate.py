"""Fail closed before a release can request AWS credentials."""
import argparse
import json
import re


def allowed(event, repository, current_sha, enabled):
    run = event.get("workflow_run", {})
    sha = run.get("head_sha", "")
    return bool(enabled == "true" and event.get("action") == "completed"
                and event.get("repository", {}).get("full_name") == repository
                and run.get("head_repository", {}).get("full_name") == repository
                and run.get("name") == "CI" and run.get("conclusion") == "success"
                and run.get("event") == "push" and run.get("head_branch") == "main"
                and re.fullmatch(r"[0-9a-f]{40}", sha) and sha == current_sha)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--current-sha", required=True)
    parser.add_argument("--enabled", required=True)
    args = parser.parse_args()
    with open(args.event) as source:
        accepted = allowed(json.load(source), args.repository, args.current_sha, args.enabled)
    print("release allowed" if accepted else "release skipped")
    raise SystemExit(0 if accepted else 1)
