import sys
import unittest
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from process_icon_sheet import (
    corner_alphas,
    count_components,
    extract_icon,
    inspect_icon,
)


class ProcessIconSheetTest(unittest.TestCase):
    def test_keeps_all_semantic_components_and_centers_union_bounds(self):
        cell = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
        draw = ImageDraw.Draw(cell)
        for x in (88, 160, 232):
            draw.ellipse((x - 12, 142, x + 12, 166), fill="#201711")

        result = extract_icon(cell)
        bbox = result.getchannel("A").getbbox()

        self.assertEqual((192, 192), result.size)
        self.assertIsNotNone(bbox)
        self.assertLessEqual(abs((bbox[0] + bbox[2]) / 2 - 96), 1)
        self.assertEqual(3, count_components(result.getchannel("A")))
        self.assertEqual(0, max(corner_alphas(result)))

    def test_keeps_muted_speaker_diagonal_as_a_semantic_component(self):
        cell = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
        draw = ImageDraw.Draw(cell)
        draw.polygon(((80, 132), (132, 132), (188, 88), (188, 232), (132, 188), (80, 188)), fill="#201711")
        draw.line(((204, 104), (252, 216)), fill="#201711", width=16)

        result = extract_icon(cell)

        self.assertEqual(2, count_components(result.getchannel("A")))
        self.assertEqual(0, max(corner_alphas(result)))

    def test_optical_shift_is_clamped_to_four_pixels(self):
        cell = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
        draw = ImageDraw.Draw(cell)
        draw.polygon(((70, 72), (250, 160), (70, 248)), fill="#201711")
        result = extract_icon(cell)
        quality = inspect_icon("forward", result)
        self.assertLessEqual(abs(quality.center_dx), 4.0)
        self.assertLessEqual(abs(quality.center_dy), 4.0)


if __name__ == "__main__":
    unittest.main()
