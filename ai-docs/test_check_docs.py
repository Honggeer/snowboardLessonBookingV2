"""Behavior checks for the active and completed ticket indexes."""

from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main
from unittest.mock import patch

import check_docs


class TicketIndexTest(TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.todos = self.root / "ai-docs" / "todo"
        self.todos.mkdir(parents=True)
        (self.todos / "TEMPLATE.md").write_text("template\n")
        self.ticket("0001", "Open task", "OPEN")
        self.ticket("0002", "Done task", "DONE")

    def ticket(self, number, title, status):
        sections = "\n".join(
            f"## {index}. {name}\n\nExample.\n"
            for index, name in enumerate(check_docs.TODO_SECTIONS, 1)
        )
        (self.todos / f"{number}-{title.lower().replace(' ', '-')}.md").write_text(
            f"---\nid: TODO-{number}\ntitle: \"{title}\"\nstatus: {status}\n"
            "created: 2026-09-29\nupdated: 2026-09-29\n---\n\n"
            f"# TODO-{number} — {title}\n\n{sections}"
        )

    def index(self, filename, rows):
        lines = ["| ID | Ticket | 状态 | 关联功能/计划 | 更新时间 |",
                 "|---|---|---|---|---|"]
        for number, title, status in rows:
            slug = title.lower().replace(" ", "-")
            lines.append(f"| TODO-{number} | [{title}]({number}-{slug}.md) | {status} | N/A | 2026-09-29 |")
        (self.todos / filename).write_text("\n".join(lines) + "\n")

    def errors(self):
        with patch.object(check_docs, "ROOT", self.root), patch.object(check_docs, "TODO_DIR", self.todos):
            check_docs.errors.clear()
            check_docs.check_todos()
            return list(check_docs.errors)

    def test_active_and_completed_tickets_have_separate_valid_indexes(self):
        self.index("README.md", [("0001", "Open task", "OPEN")])
        self.index("DONE.md", [("0002", "Done task", "DONE")])
        self.assertEqual([], self.errors())

    def test_completed_ticket_in_active_index_is_rejected(self):
        self.index("README.md", [("0001", "Open task", "OPEN"), ("0002", "Done task", "DONE")])
        self.index("DONE.md", [])
        self.assertTrue(self.errors())

    def test_duplicate_across_indexes_is_rejected(self):
        self.index("README.md", [("0001", "Open task", "OPEN"), ("0002", "Done task", "DONE")])
        self.index("DONE.md", [("0002", "Done task", "DONE")])
        self.assertTrue(self.errors())


if __name__ == "__main__":
    main()
