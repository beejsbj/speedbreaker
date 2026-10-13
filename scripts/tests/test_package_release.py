"""The local release path must enforce the CI policy before it can ask for a credential."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).parents[1]
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location('package_release', SCRIPTS / 'package_release.py')
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleasePolicyTest(unittest.TestCase):
    def test_local_build_collects_dependencies_then_verifies_manifest(self):
        calls = []
        release.enforce_release_policy(Path('a.apk'), Path('aapt'), True, runner=lambda c, **k: calls.append(c))
        self.assertEqual([c[2] for c in calls], ['collect', 'verify'])
        self.assertIn('--dependencies', calls[0])
        self.assertEqual(calls[1][calls[1].index('--channel') + 1], 'community')

    def test_hosted_input_only_verifies_final_manifest(self):
        calls = []
        release.enforce_release_policy(Path('a.apk'), Path('aapt'), False, runner=lambda c, **k: calls.append(c))
        self.assertEqual([c[2] for c in calls], ['verify'])

    def test_violation_propagates(self):
        def reject(command, **kwargs):
            raise subprocess.CalledProcessError(1, command)
        with self.assertRaises(subprocess.CalledProcessError):
            release.enforce_release_policy(Path('a.apk'), Path('aapt'), True, runner=reject)

    def test_policy_runs_before_password_prompt(self):
        source = (SCRIPTS / 'package_release.py').read_text()
        main = source[source.index('def main'):]
        self.assertLess(main.index('enforce_release_policy('), main.index('getpass.getpass'))
        self.assertLess(main.index('enforce_release_policy('), main.index('SPEEDBREAKER_RELEASE_PASSWORD'))
