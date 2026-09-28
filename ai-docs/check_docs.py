#!/usr/bin/env python3
"""Check v2 Markdown links, paired records, tickets, indexes, and review gates.

This validates document structure. It never grants plan approval or tests the app.
"""

from pathlib import Path
import re
import sys
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parent.parent
FEATURE_DIR = ROOT / "ai-docs" / "features"
PLAN_DIR = ROOT / "ai-docs" / "implement-plan"
TODO_DIR = ROOT / "ai-docs" / "todo"
STATES = {
    "DRAFT", "AWAITING_REVIEW", "APPROVED", "IMPLEMENTING",
    "IMPLEMENTED", "VERIFIED", "RELEASED", "REJECTED", "CANCELLED",
}
APPROVED_STATES = {"APPROVED", "IMPLEMENTING", "IMPLEMENTED", "VERIFIED", "RELEASED"}
TODO_STATES = {"OPEN", "IN_PROGRESS", "DONE", "CANCELLED"}
TODO_SECTIONS = [
    "来源与关联", "背景", "问题与影响", "期望结果", "完成判定", "未决事项与状态记录",
]
TODO_NAME = re.compile(r"\d{4}-[a-z0-9]+(?:-[a-z0-9]+)*\.md")
LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
HEADING = re.compile(r"^## (\d+)\. ", re.MULTILINE)
errors = []
IGNORED_DIRS = {".git", "node_modules", "dist", "target", "coverage", ".cache", ".local"}


def fail(message):
    errors.append(message)


def metadata(path):
    content = path.read_text(encoding="utf-8")
    if not content.startswith("---\n"):
        fail(f"{path.relative_to(ROOT)}: missing frontmatter")
        return {}, content
    closing = content.find("\n---\n", 4)
    if closing == -1:
        fail(f"{path.relative_to(ROOT)}: unclosed frontmatter")
        return {}, content
    values = {}
    for line in content[4:closing].splitlines():
        if ":" not in line:
            fail(f"{path.relative_to(ROOT)}: invalid frontmatter line")
            continue
        key, value = line.split(":", 1)
        values[key.strip()] = value.strip().strip('"\'')
    return values, content[closing + 5:]


def check_links():
    for path in sorted(ROOT.rglob("*.md")):
        if any(part in IGNORED_DIRS for part in path.relative_to(ROOT).parts[:-1]):
            continue
        content = path.read_text(encoding="utf-8")
        if "\x00" in content:
            fail(f"{path.relative_to(ROOT)}: contains NUL")
        # Template examples intentionally link to files that will exist only after copying.
        if path.name in {"FEATURE_TEMPLATE.md", "TEMPLATE.md"}:
            continue
        for raw in LINK.findall(content):
            destination = raw.split("#", 1)[0]
            if not destination or "://" in destination or destination.startswith("mailto:"):
                continue
            linked = (path.parent / unquote(destination)).resolve()
            if not linked.is_file():
                fail(f"{path.relative_to(ROOT)}: missing local link {raw}")


def check_records():
    features = {p.stem: p for p in FEATURE_DIR.glob("[0-9][0-9][0-9][0-9]-*.md")}
    plans = {p.stem: p for p in PLAN_DIR.glob("[0-9][0-9][0-9][0-9]-*.md")}
    if not features or not plans:
        fail("feature and plan records must both exist")
    if features.keys() != plans.keys():
        fail(f"feature/plan record mismatch: features={sorted(features)}, plans={sorted(plans)}")

    feature_index = (FEATURE_DIR / "README.md").read_text(encoding="utf-8")
    plan_index = (PLAN_DIR / "README.md").read_text(encoding="utf-8")

    for stem in sorted(features.keys() & plans.keys()):
        feature, plan = features[stem], plans[stem]
        fmeta, fbody = metadata(feature)
        pmeta, pbody = metadata(plan)
        identity = stem[:4]
        if fmeta.get("id") != identity or pmeta.get("id") != identity:
            fail(f"{stem}: id does not match filename")
        fstate, pstate = fmeta.get("status"), pmeta.get("status")
        if fstate not in STATES or pstate not in STATES or fstate != pstate:
            fail(f"{stem}: invalid or mismatched state {fstate}/{pstate}")
        expected_plan = f"../implement-plan/{stem}.md"
        expected_feature = f"../features/{stem}.md"
        if fmeta.get("plan") != expected_plan or pmeta.get("feature") != expected_feature:
            fail(f"{stem}: frontmatter pair links do not match")
        if f"{stem}.md" not in feature_index or f"{stem}.md" not in plan_index:
            fail(f"{stem}: missing from one or both indexes")
        for index_name, index_text in (("features", feature_index), ("implement-plan", plan_index)):
            row = next((line for line in index_text.splitlines()
                        if line.startswith(f"| {identity} |")), None)
            if not row or f"| {fstate} |" not in row:
                fail(f"{stem}: {index_name} index state does not match")
        if sorted({int(n) for n in HEADING.findall(fbody)}) != list(range(1, 12)):
            fail(f"{stem}: feature must have sections 1 through 11")
        if sorted({int(n) for n in HEADING.findall(pbody)}) != list(range(1, 10)):
            fail(f"{stem}: plan must have sections 1 through 9")
        try:
            revision = int(pmeta.get("revision", ""))
            if revision < 1:
                raise ValueError()
        except ValueError:
            fail(f"{stem}: revision must be a positive integer")
            revision = None
        approved = pmeta.get("approved_revision")
        if pstate in APPROVED_STATES:
            if approved != str(revision):
                fail(f"{stem}: current revision lacks matching user approval")
            if "| 尚未批准 |" in pbody or not re.search(r"\| 20\d\d-\d\d-\d\d \|[^\n]+\| " + re.escape(str(revision)) + r" \|", pbody):
                fail(f"{stem}: missing concrete user approval record")
        elif approved != "null":
            fail(f"{stem}: unapproved state must have approved_revision: null")


def check_todos():
    index_path = TODO_DIR / "README.md"
    template_path = TODO_DIR / "TEMPLATE.md"
    if not index_path.is_file() or not template_path.is_file():
        fail("todo directory must contain README.md and TEMPLATE.md")
        return

    index = index_path.read_text(encoding="utf-8")
    tickets = sorted(p for p in TODO_DIR.glob("*.md")
                     if p.name not in {"README.md", "TEMPLATE.md"})
    expected_ids = set()
    for path in tickets:
        if not TODO_NAME.fullmatch(path.name):
            fail(f"{path.relative_to(ROOT)}: invalid ticket filename")
            continue
        ticket_id = f"TODO-{path.stem[:4]}"
        if ticket_id in expected_ids:
            fail(f"{ticket_id}: duplicate ticket number")
        expected_ids.add(ticket_id)

        values, body = metadata(path)
        if values.get("id") != ticket_id:
            fail(f"{path.relative_to(ROOT)}: id must be {ticket_id}")
        title = values.get("title", "")
        if not title or not body.lstrip("\n").startswith(f"# {ticket_id} — {title}\n"):
            fail(f"{path.relative_to(ROOT)}: title and H1 must match")
        status = values.get("status")
        if status not in TODO_STATES:
            fail(f"{path.relative_to(ROOT)}: invalid status {status}")
        for field in ("created", "updated"):
            if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", values.get(field, "")):
                fail(f"{path.relative_to(ROOT)}: invalid {field} date")
        headings = re.findall(r"^## (\d+)\. ([^\n]+)$", body, re.MULTILINE)
        if headings != [(str(n), name) for n, name in enumerate(TODO_SECTIONS, 1)]:
            fail(f"{path.relative_to(ROOT)}: ticket sections must match template")

        row = next((line for line in index.splitlines()
                    if line.startswith(f"| {ticket_id} |")), None)
        cells = [cell.strip() for cell in row.strip().strip("|").split("|")] if row else []
        if (len(cells) != 5 or f"({path.name})" not in cells[1]
                or cells[2] != status or not cells[3]
                or cells[4] != values.get("updated")):
            fail(f"{ticket_id}: index row missing or out of sync")

    indexed_ids = re.findall(r"^\| (TODO-\d{4}) \|", index, re.MULTILINE)
    if len(indexed_ids) != len(expected_ids) or set(indexed_ids) != expected_ids:
        fail("todo index IDs do not match ticket files")


def main():
    check_links()
    check_records()
    check_todos()
    if errors:
        for message in errors:
            print("ERROR:", message, file=sys.stderr)
        return 1
    count = len(list(FEATURE_DIR.glob("[0-9][0-9][0-9][0-9]-*.md")))
    todo_count = len(list(TODO_DIR.glob("[0-9][0-9][0-9][0-9]-*.md")))
    print(f"OK: Markdown links, {count} paired records, {todo_count} tickets, indexes, states and review gates")
    return 0


if __name__ == "__main__":
    sys.exit(main())
