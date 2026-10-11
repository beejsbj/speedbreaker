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

    def test_expired_archive_never_downloads(self):
        with patch.object(inputs.subprocess, 'run') as download:
            with self.assertRaisesRegex(ValueError, 'Expired artifact'):
                inputs.archive({'expired': True}, Path('unused'))
            download.assert_not_called()


if __name__ == '__main__':
    unittest.main()
