"""Version bumps and release notes are generated, so their edge cases are tested."""
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).parents[1]
sys.path.insert(0, str(SCRIPTS))
import bump_version  # noqa: E402
import release_notes  # noqa: E402

GRADLE = '''    defaultConfig {
        versionCode = 7
        versionName = "1.0.3"
    }
'''
CHANGELOG = '''# Changelog

## 1.0.3 — October 17, 2026

Version code: **7**. [GitHub release](https://example.org), source `abc`.

- Home redesign.

## 1.0.2 — October 16, 2026

- Older change.
'''


class BumpVersionTest(unittest.TestCase):
    def test_sets_name_and_increments_code_by_one(self):
        text, code = bump_version.bump_gradle(GRADLE, "1.0.4")
        self.assertEqual(code, 8)
        self.assertIn("versionCode = 8", text)
        self.assertIn('versionName = "1.0.4"', text)

    def test_rejects_same_older_or_malformed_versions(self):
        for version in ("1.0.3", "1.0.2", "1.0", "v1.0.4", "1.0.4-rc1"):
            with self.assertRaises(ValueError, msg=version):
                bump_version.bump_gradle(GRADLE, version)

    def test_compares_numerically_not_lexically(self):
        text, _ = bump_version.bump_gradle(GRADLE.replace("1.0.3", "1.9.0"), "1.10.0")
        self.assertIn('versionName = "1.10.0"', text)

    def test_adds_unreleased_section_above_the_newest(self):
        text = bump_version.open_changelog(CHANGELOG, "1.0.4", 8)
        self.assertLess(text.index("## 1.0.4 — unreleased"), text.index("## 1.0.3"))
        self.assertIn("Version code: **8**.", text)
        self.assertTrue(text.startswith("# Changelog\n\n## 1.0.4"))

    def test_keeps_an_existing_unreleased_section_and_refuses_a_dated_one(self):
        once = bump_version.open_changelog(CHANGELOG, "1.0.4", 8)
        self.assertEqual(bump_version.open_changelog(once, "1.0.4", 8), once)
        with self.assertRaises(ValueError):
            bump_version.open_changelog(CHANGELOG, "1.0.3", 8)


class ReleaseNotesTest(unittest.TestCase):
    def test_extracts_only_the_requested_section_without_bookkeeping(self):
        self.assertEqual(release_notes.changelog_section(CHANGELOG, "1.0.3"), "- Home redesign.")
        self.assertEqual(release_notes.changelog_section(CHANGELOG, "1.0.2"), "- Older change.")

    def test_refuses_missing_empty_or_undated_sections_when_publishing(self):
        with self.assertRaises(ValueError):
            release_notes.changelog_section(CHANGELOG, "9.9.9")
        unreleased = bump_version.open_changelog(CHANGELOG, "1.0.4", 8)
        with self.assertRaises(ValueError):
            release_notes.changelog_section(unreleased, "1.0.4")
        filled = unreleased.replace("Version code: **8**.", "Version code: **8**.\n\n- A change.")
        with self.assertRaises(ValueError):
            release_notes.changelog_section(filled, "1.0.4", require_dated=True)
        self.assertEqual(release_notes.changelog_section(filled, "1.0.4"), "- A change.")

    def test_notes_carry_install_line_source_and_checksum(self):
        notes = release_notes.compose("1.0.3", "7", "be9b746b1d2ca7bc", "ab" * 32, "- Home redesign.")
        self.assertIn("Download **Speedbreaker-1.0.3.apk** under Assets. Android 8.0+; version code **7**.", notes)
        self.assertIn("Same release signing identity.", notes)
        self.assertIn("Source: `be9b746`.", notes)
        self.assertIn("```\n" + "ab" * 32 + "\n```", notes)
