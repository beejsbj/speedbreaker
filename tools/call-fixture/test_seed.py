"""Fast host-only checks for the database handed to the real Android test."""
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

from run import FIXTURE, seed_database


class SeedTest(unittest.TestCase):
    def test_seed_is_room_v1_compatible_and_selects_only_inert_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "speedbreaker.db"
            seed_database(path)
            with sqlite3.connect(path) as db:
                columns = db.execute("PRAGMA table_info(speedbreaker_state)").fetchall()
                self.assertEqual([(c[1], c[2], c[3], c[5]) for c in columns],
                                 [("key", "TEXT", 1, 1), ("value", "TEXT", 1, 0)])
                self.assertEqual(db.execute("PRAGMA user_version").fetchone()[0], 1)
                rows = dict(db.execute("SELECT key, value FROM speedbreaker_state"))
                self.assertEqual(set(rows), {"settings", "pauses"})
                self.assertEqual(json.loads(rows["pauses"]), {})
                self.assertEqual(json.loads(rows["settings"]), {
                    "consent": True, "breath": 30, "redirects": [], "schedule": None,
                    "apps": {FIXTURE: {"continuous": None, "schedule": None, "redirects": None}},
                })
                # Room must validate and supply its own hash, not trust a fabricated identity.
                self.assertIsNone(db.execute("SELECT name FROM sqlite_master WHERE name='room_master_table'").fetchone())
                self.assertEqual(db.execute("PRAGMA integrity_check").fetchone()[0], "ok")


if __name__ == "__main__":
    unittest.main()
