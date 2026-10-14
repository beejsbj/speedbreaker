"""A complete stdout payload must not hide a failed ADB shell transfer."""
import importlib.util
from pathlib import Path
import struct
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('native', Path(__file__).parents[1] / 'check_native_pages.py')
native = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(native)


class NativeCollectionTest(unittest.TestCase):
    def test_failed_transfer_rejects_intact_payload_and_preserves_diagnostics(self):
        for failed in ('native-pages.json', 'native-pages-maps.txt'):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                apk = root / 'app.apk'
                apk.write_bytes(b'no device or key involved')
                audit = native.Audit(SimpleNamespace(output=root / 'evidence', mode='runtime', apk=apk, adb='mock-adb'))
                def transfer(command, **kwargs):
                    payload = b'{"result":"PASS"}' if command[-1].endswith('.json') else b'complete executable map'
                    code = 255 if command[-1] == 'files/' + failed else 0
                    return subprocess.CompletedProcess(command, code, payload, b'exit packet lost' if code else b'')
                with patch.object(native.subprocess, 'run', side_effect=transfer):
                    with self.assertRaisesRegex(RuntimeError, 'Command failed \\(255\\)'):
                        audit.collect_native_evidence()
                self.assertTrue(any(p.read_bytes() == b'exit packet lost' for p in audit.output.glob('*.stderr')))
                self.assertTrue(any(p.stat().st_size for p in audit.output.glob('*.stdout')))

    def test_successful_transfers_return_and_persist_both_payloads(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / 'app.apk'
            apk.write_bytes(b'no device or key involved')
            audit = native.Audit(SimpleNamespace(output=root / 'evidence', mode='runtime', apk=apk, adb='mock-adb'))
            payloads = [b'{"result":"PASS"}', b'complete executable map']
            with patch.object(native.subprocess, 'run', side_effect=[
                subprocess.CompletedProcess([], 0, payload, b'') for payload in payloads
            ]):
                self.assertEqual(audit.collect_native_evidence(), [p.decode() for p in payloads])
            for filename, payload in zip(('native-pages.json', 'native-pages-maps.txt'), payloads):
                self.assertEqual((audit.output / filename).read_bytes(), payload + b'\n')


def elf64(loads):
    """Minimal little-endian ELF64 with (flags, p_offset) PT_LOAD headers."""
    header = bytearray(b'\x7fELF\x02\x01\x01' + bytes(9))
    header += struct.pack('<HHIQQQIHHHHHH', 3, 183, 1, 0, 64, 0, 0, 64, 56, len(loads), 0, 0, 0)
    for flags, offset in loads:
        header += struct.pack('<IIQQQQQQ', 1, flags, offset, offset, offset, 0x1000, 0x1000, 0x4000)
    return bytes(header)


class ExecutableOffsetTest(unittest.TestCase):
    def test_executable_segment_beyond_elf_header_is_offset_from_zip_data(self):
        data = elf64([(4, 0), (5, 0x5000), (6, 0x9000)])  # R, R+X, RW
        self.assertEqual(native.executable_map_offsets(data, 0x40000), [0x44000])

    def test_segment_offset_is_page_aligned_down(self):
        self.assertEqual(native.executable_map_offsets(elf64([(5, 0x5123)]), 0x40000), [0x44000])

    def test_header_resident_executable_segment_maps_at_zip_offset(self):
        self.assertEqual(native.executable_map_offsets(elf64([(5, 0)]), 0x40000), [0x40000])

    def test_library_without_executable_load_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'No executable'):
            native.executable_map_offsets(elf64([(4, 0), (6, 0x4000)]), 0x40000)
