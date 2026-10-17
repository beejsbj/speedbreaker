"""Protect the signing-host boundary with actual ZIP inputs, without network/keys."""
import hashlib
import importlib.util
from io import BytesIO
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location('inputs', Path(__file__).parents[1] / 'fetch_store_artifacts.py')
inputs = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inputs)


def zipped(name='app.apk', symlink=False):
    out = BytesIO()
    with zipfile.ZipFile(out, 'w') as archive:
        entry = zipfile.ZipInfo(name)
        if symlink:
            entry.create_system = 3
            entry.external_attr = 0o120777 << 16
        archive.writestr(entry, b'known payload')
    return out.getvalue()


class ArtifactBoundaryTest(unittest.TestCase):
    def extract(self, data, destination, digest=None):
        artifact = {'expired': False, 'digest': 'sha256:' + (digest or hashlib.sha256(data).hexdigest()),
                    'id': 123, 'name': 'test'}
        def download(command, stdout, check):
            self.assertEqual(command[-1], 'repos/beejsbj/speedbreaker/actions/artifacts/123/zip')
            stdout.write(data)
        with patch.object(inputs.subprocess, 'run', download):
            return inputs.archive(artifact, destination)

    def test_verified_archive_preserves_exact_payload_and_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'input'
            data = zipped()
            receipt = self.extract(data, target)
            self.assertEqual((target / 'app.apk').read_bytes(), b'known payload')
            self.assertEqual(receipt['archiveSha256'], hashlib.sha256(data).hexdigest())

    def test_tampered_archive_never_extracts(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'input'
            with self.assertRaisesRegex(ValueError, 'digest mismatch'):
                self.extract(zipped(), target, '0' * 64)
            self.assertFalse(target.exists())

    def test_path_escape_and_symlink_never_extract(self):
        for data in (zipped('../outside.apk'), zipped('/absolute.apk'), zipped('folder\\outside.apk'), zipped(symlink=True)):
            with self.subTest(data=data[-16:]), tempfile.TemporaryDirectory() as temporary:
                target = Path(temporary) / 'input'
                with self.assertRaises(ValueError):
                    self.extract(data, target)
                self.assertFalse(target.exists())

    def test_signer_rejects_modified_payload_even_with_matching_local_receipt(self):
        payload = b'known payload'
        digest = hashlib.sha256(payload).hexdigest()
        commit, tree, built = 'source', 'tree', 'merge'
        provenance = {'sourceTree': tree, 'githubRunId': '7', 'sourceHead': built,
                      'artifactSha256': {'apk': digest}}
        def zip_contents(name, content):
            out = BytesIO()
            with zipfile.ZipFile(out, 'w') as source:
                source.writestr(name, content)
            return out.getvalue()
        import json
        archives = {1: zip_contents('provenance.json', json.dumps(provenance)),
                    2: zip_contents('app.apk', payload)}
        artifacts = [{'id': key, 'expired': False,
                      'name': 'store-inventory-github-merge' if key == 1 else 'unsigned-github-release-merge',
                      'digest': 'sha256:' + hashlib.sha256(data).hexdigest()}
                     for key, data in archives.items()]
        run = {'status': 'completed', 'conclusion': 'success', 'head_sha': commit,
               'workflow_id': 3, 'html_url': 'https://github.com/beejsbj/speedbreaker/actions/runs/7'}
        responses = {
            'repos/beejsbj/speedbreaker/actions/runs/7': run,
            'repos/beejsbj/speedbreaker/actions/workflows/3': {'path': '.github/workflows/android.yml'},
            'repos/beejsbj/speedbreaker/actions/runs/7/artifacts?per_page=100': {'artifacts': artifacts},
            'repos/beejsbj/speedbreaker/git/commits/merge': {'tree': {'sha': tree}},
        }
        receipt = {'repository': inputs.REPOSITORY, 'source_commit': commit, 'source_tree': tree,
                   'build_checkout': built, 'run_id': 7, 'run_url': run['html_url'],
                   'input_sha256': digest, 'channel': 'github', 'kind': 'apk',
                   'artifactId': 2, 'name': artifacts[1]['name'], 'archiveSha256': artifacts[1]['digest'][7:]}
        def download(command, stdout, check):
            artifact_id = int(command[-1].split('/')[-2])
            stdout.write(archives[artifact_id])
        with tempfile.TemporaryDirectory() as temporary, patch.object(inputs, 'gh', side_effect=lambda path: responses[path]), patch.object(inputs.subprocess, 'run', download):
            apk = Path(temporary) / 'app.apk'
            apk.write_bytes(payload)
            self.assertEqual(inputs.verify_signing_input(receipt, apk, commit, tree), receipt)
            apk.write_bytes(payload + b'altered')
            forged = dict(receipt, input_sha256=inputs.sha(apk))
            with self.assertRaisesRegex(ValueError, 'Local APK differs'):
                inputs.verify_signing_input(forged, apk, commit, tree)
            apk.write_bytes(payload)
            with self.assertRaisesRegex(ValueError, 'Unexpected build workflow'):
                responses['repos/beejsbj/speedbreaker/actions/workflows/3']['path'] = '.github/workflows/other.yml'
                inputs.verify_signing_input(receipt, apk, commit, tree)

    def test_expired_archive_never_downloads(self):
        with patch.object(inputs.subprocess, 'run') as download:
            with self.assertRaisesRegex(ValueError, 'Expired artifact'):
                inputs.archive({'expired': True}, Path('unused'))
            download.assert_not_called()


if __name__ == '__main__':
    unittest.main()
