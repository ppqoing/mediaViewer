"""Extract, centre and inspect Image2 warm-paper icon sprite sheets."""

from __future__ import annotations

import argparse
import json
from collections import deque
from dataclasses import asdict, dataclass
from pathlib import Path

from PIL import Image, ImageDraw


ALPHA_THRESHOLD = 16
TARGET_SIZE = 192
FOREGROUND_LONGEST_EDGE = 138
MIN_COMPONENT_RATIO = 0.0005


@dataclass(frozen=True)
class IconSpec:
    name: str
    row: int
    column: int
    optical: bool = True


@dataclass(frozen=True)
class QualityResult:
    name: str
    width: int
    height: int
    corner_alpha: int
    center_dx: float
    center_dy: float
    foreground_ratio: float


def _foreground_components(alpha: Image.Image) -> list[list[tuple[int, int]]]:
    """Return all 8-connected alpha regions above the edge alpha threshold."""
    width, height = alpha.size
    pixels = alpha.load()
    seen: set[tuple[int, int]] = set()
    components: list[list[tuple[int, int]]] = []
    for y in range(height):
        for x in range(width):
            point = (x, y)
            if point in seen or pixels[x, y] < ALPHA_THRESHOLD:
                continue
            seen.add(point)
            queue = deque([point])
            component: list[tuple[int, int]] = []
            while queue:
                current_x, current_y = queue.popleft()
                component.append((current_x, current_y))
                for y_offset in (-1, 0, 1):
                    for x_offset in (-1, 0, 1):
                        if x_offset == 0 and y_offset == 0:
                            continue
                        next_x, next_y = current_x + x_offset, current_y + y_offset
                        next_point = (next_x, next_y)
                        if (
                            0 <= next_x < width
                            and 0 <= next_y < height
                            and next_point not in seen
                            and pixels[next_x, next_y] >= ALPHA_THRESHOLD
                        ):
                            seen.add(next_point)
                            queue.append(next_point)
            components.append(component)
    return components


def count_components(alpha: Image.Image) -> int:
    """Count semantic connected components using the pipeline alpha threshold."""
    return len(_foreground_components(alpha.convert("L")))


def alpha_centroid(alpha: Image.Image) -> tuple[float, float]:
    """Return alpha-weighted foreground centre; use canvas centre when empty."""
    alpha = alpha.convert("L")
    total = 0
    sum_x = 0
    sum_y = 0
    for y in range(alpha.height):
        for x in range(alpha.width):
            value = alpha.getpixel((x, y))
            total += value
            sum_x += x * value
            sum_y += y * value
    if not total:
        return alpha.width / 2, alpha.height / 2
    return sum_x / total, sum_y / total


def center_offset(alpha: Image.Image) -> tuple[int, int]:
    bbox = alpha.getbbox()
    if bbox is None:
        return 0, 0
    geometric_x = (bbox[0] + bbox[2]) / 2
    geometric_y = (bbox[1] + bbox[3]) / 2
    mass_x, mass_y = alpha_centroid(alpha)
    return (
        round(max(-4, min(4, geometric_x - mass_x))),
        round(max(-4, min(4, geometric_y - mass_y))),
    )


def _remove_noise(cell: Image.Image) -> Image.Image:
    """Remove only components smaller than 0.05% of the source cell."""
    rgba = cell.convert("RGBA")
    alpha = rgba.getchannel("A")
    minimum_area = alpha.width * alpha.height * MIN_COMPONENT_RATIO
    retained = {
        point
        for component in _foreground_components(alpha)
        if len(component) >= minimum_area
        for point in component
    }
    clean_alpha = Image.new("L", alpha.size, 0)
    for x, y in retained:
        clean_alpha.putpixel((x, y), alpha.getpixel((x, y)))
    rgba.putalpha(clean_alpha)
    return rgba


def extract_icon(cell: Image.Image, target_size: int = TARGET_SIZE) -> Image.Image:
    """Create a transparent, tight-boundary target-size icon from one sheet cell."""
    cleaned = _remove_noise(cell)
    bbox = cleaned.getchannel("A").getbbox()
    result = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
    if bbox is None:
        return result

    cropped = cleaned.crop(bbox)
    scale = FOREGROUND_LONGEST_EDGE / max(cropped.size)
    resized_size = tuple(max(1, round(edge * scale)) for edge in cropped.size)
    glyph = cropped.resize(resized_size, Image.Resampling.LANCZOS)
    position = ((target_size - glyph.width) // 2, (target_size - glyph.height) // 2)
    result.alpha_composite(glyph, dest=position)

    shift_x, shift_y = center_offset(result.getchannel("A"))
    if shift_x or shift_y:
        shifted = Image.new("RGBA", result.size, (0, 0, 0, 0))
        shifted.alpha_composite(result, dest=(shift_x, shift_y))
        result = shifted
    return result


def corner_alphas(image: Image.Image) -> tuple[int, int, int, int]:
    alpha = image.convert("RGBA").getchannel("A")
    return (
        alpha.getpixel((0, 0)),
        alpha.getpixel((alpha.width - 1, 0)),
        alpha.getpixel((0, alpha.height - 1)),
        alpha.getpixel((alpha.width - 1, alpha.height - 1)),
    )


def inspect_icon(name: str, image: Image.Image) -> QualityResult:
    alpha = image.convert("RGBA").getchannel("A")
    foreground = sum(1 for value in alpha.getdata() if value >= ALPHA_THRESHOLD)
    optical_dx, optical_dy = center_offset(alpha)
    return QualityResult(
        name=name,
        width=image.width,
        height=image.height,
        corner_alpha=max(corner_alphas(image)),
        center_dx=float(optical_dx),
        center_dy=float(optical_dy),
        foreground_ratio=foreground / (image.width * image.height),
    )


def process_sheet(
    sheet_path: Path,
    specs: list[IconSpec],
    rows: int,
    columns: int,
    output_dir: Path,
) -> list[QualityResult]:
    """Extract manifest cells from an alpha sheet and save Android drawable PNGs."""
    output_dir.mkdir(parents=True, exist_ok=True)
    with Image.open(sheet_path) as raw_sheet:
        sheet = raw_sheet.convert("RGBA")
    quality: list[QualityResult] = []
    for spec in specs:
        left = sheet.width * spec.column // columns
        right = sheet.width * (spec.column + 1) // columns
        top = sheet.height * spec.row // rows
        bottom = sheet.height * (spec.row + 1) // rows
        icon = extract_icon(sheet.crop((left, top, right, bottom)))
        icon.save(output_dir / f"ic_wp_{spec.name}.png")
        quality.append(inspect_icon(spec.name, icon))
    return quality


def _specs(names: list[str], columns: int) -> list[IconSpec]:
    return [IconSpec(name, index // columns, index % columns) for index, name in enumerate(names)]


def _write_contact_sheet(output_path: Path, icons: list[tuple[str, Path]]) -> None:
    columns = 6
    cell = 220
    rows = (len(icons) + columns - 1) // columns
    contact = Image.new("RGBA", (columns * cell, rows * cell), "#f7ecd5")
    draw = ImageDraw.Draw(contact)
    for index, (name, path) in enumerate(icons):
        x, y = (index % columns) * cell, (index // columns) * cell
        with Image.open(path) as opened:
            icon = opened.convert("RGBA")
        contact.alpha_composite(icon, dest=(x + 14, y + 4))
        draw.text((x + 12, y + 198), name, fill="#201711")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    contact.convert("RGB").save(output_path)


def _apply_individual_overrides(
    source_dir: Path, output_dir: Path, quality: list[QualityResult]
) -> list[QualityResult]:
    """Replace only visually failed sheet cells with audited single-icon sources."""
    overrides = source_dir / "individual"
    if not overrides.exists():
        return quality
    by_name = {item.name: item for item in quality}
    for source in sorted(overrides.glob("*.png")):
        name = source.stem
        if name not in by_name:
            raise ValueError(f"Unknown individual icon override: {name}")
        with Image.open(source) as opened:
            icon = extract_icon(opened.convert("RGBA"))
        icon.save(output_dir / f"ic_wp_{name}.png")
        by_name[name] = inspect_icon(name, icon)
    return [by_name[item.name] for item in quality]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    arguments = parser.parse_args()

    manifest = json.loads(arguments.manifest.read_text(encoding="utf-8"))
    all_quality: list[QualityResult] = []
    contact_icons: list[tuple[str, Path]] = []
    for group, definition in manifest.items():
        specs = _specs(definition["icons"], definition["columns"])
        all_quality.extend(
            process_sheet(
                arguments.source / f"{group}.png",
                specs,
                definition["rows"],
                definition["columns"],
                arguments.output,
            )
        )
        contact_icons.extend((item.name, arguments.output / f"ic_wp_{item.name}.png") for item in specs)

    all_quality = _apply_individual_overrides(arguments.source, arguments.output, all_quality)
    _write_contact_sheet(arguments.report / "contact-sheet.png", contact_icons)
    (arguments.report / "quality-report.json").write_text(
        json.dumps([asdict(item) for item in all_quality], indent=2), encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
