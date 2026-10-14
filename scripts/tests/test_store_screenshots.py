"""Regression for accepting JPEG headers without a decodable pixel stream."""
from pathlib import Path
import sys
import tempfile
import unittest

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_store_metadata import check_phone_screenshots


class ScreenshotPacketTests(unittest.TestCase):
    def packet(self, directory):
        for name in ("1.jpg", "2.jpg"):
            Image.new("RGB", (320, 640), (40, 80, 120)).save(directory / name)

    def test_complete_jpeg_packet_decodes_and_passes(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.packet(directory)
            check_phone_screenshots(directory, required=True)

    def test_truncated_jpeg_with_valid_headers_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.packet(directory)
            for path in directory.glob("*.jpg"):
                path.write_bytes(path.read_bytes()[:-300])
                with Image.open(path) as image:
                    self.assertEqual((image.format, image.mode, image.size),
                                     ("JPEG", "RGB", (320, 640)))
            with self.assertRaises(OSError):
                check_phone_screenshots(directory, required=True)


if __name__ == "__main__":
    unittest.main()
