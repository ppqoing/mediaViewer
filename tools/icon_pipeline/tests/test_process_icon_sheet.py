import json
import subprocess
import sys
import tempfile
import unittest
from dataclasses import asdict
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
    def test_verify_only_validates_existing_artifacts_without_rewriting_them(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "source"
            output = root / "output"
            report = root / "report"
            source.mkdir()
            output.mkdir()
            report.mkdir()

            manifest = root / "manifest.json"
            manifest.write_text(
                json.dumps(
                    {
                        "common": {
                            "rows": 1,
                            "columns": 2,
                            "icons": ["sample"],
                        }
                    }
                ),
                encoding="utf-8",
            )
            cell = Image.new("RGBA", (319, 317), (0, 0, 0, 0))
            ImageDraw.Draw(cell).ellipse((80, 79, 239, 238), fill="#201711")
            source_icon = source / "common.png"
            cell.save(source_icon)

            generated = extract_icon(cell)
            output_icon = output / "ic_wp_sample.png"
            generated.save(output_icon)
            quality_report = report / "quality-report.json"
            quality_report.write_text(
                json.dumps([asdict(inspect_icon("sample", generated))]),
                encoding="utf-8",
            )
            contact_sheet = report / "contact-sheet.png"
            Image.new("RGB", (220, 220), "#f7ecd5").save(contact_sheet)

            tracked_files = [source_icon, output_icon, quality_report, contact_sheet]
            before = {
                path: (path.read_bytes(), path.stat().st_mtime_ns)
                for path in tracked_files
            }

            command = [
                sys.executable,
                str(Path(__file__).resolve().parents[1] / "process_icon_sheet.py"),
                "--manifest",
                str(manifest),
                "--source",
                str(source),
                "--output",
                str(output),
                "--report",
                str(report),
                "--verify-only",
            ]
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertIn("Verified 1 icons", completed.stdout)
            self.assertEqual(
                before,
                {
                    path: (path.read_bytes(), path.stat().st_mtime_ns)
                    for path in tracked_files
                },
            )

            invalid_quality = asdict(inspect_icon("sample", generated))
            invalid_quality["width"] = 191
            quality_report.write_text(
                json.dumps([invalid_quality]),
                encoding="utf-8",
            )
            rejected = subprocess.run(
                command,
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("quality-report.json", rejected.stderr)

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
