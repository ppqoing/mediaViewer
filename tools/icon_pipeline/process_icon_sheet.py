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


def _manifest_inventory(manifest: object) -> tuple[list[str], list[tuple[str, int, int, list[str]]]]:
    if not isinstance(manifest, dict) or not manifest:
        raise ValueError("Manifest must contain at least one icon group")
    names: list[str] = []
    groups: list[tuple[str, int, int, list[str]]] = []
    for group, definition in manifest.items():
        if not isinstance(group, str) or not group or not isinstance(definition, dict):
            raise ValueError("Manifest groups must be named objects")
        rows = definition.get("rows")
        columns = definition.get("columns")
        icons = definition.get("icons")
        if (
            not isinstance(rows, int)
            or isinstance(rows, bool)
            or rows <= 0
            or not isinstance(columns, int)
            or isinstance(columns, bool)
            or columns <= 0
            or not isinstance(icons, list)
            or not icons
            or len(icons) > rows * columns
            or any(not isinstance(name, str) or not name for name in icons)
        ):
            raise ValueError(f"Invalid manifest contract for group: {group}")
        names.extend(icons)
        groups.append((group, rows, columns, icons))
    if len(names) != len(set(names)):
        raise ValueError("Manifest icon names must be globally unique")
    return names, groups


def _read_png(path: Path) -> tuple[Image.Image, tuple[str, ...], dict[str, object]]:
    if not path.is_file():
        raise ValueError(f"Missing PNG: {path}")
    try:
        with Image.open(path) as opened:
            if opened.format != "PNG":
                raise ValueError(f"Expected PNG file: {path}")
            bands = opened.getbands()
            metadata = dict(opened.info)
            image = opened.convert("RGBA")
            image.load()
    except (OSError, ValueError) as error:
        raise ValueError(f"Invalid PNG {path}: {error}") from error
    return image, bands, metadata


def _threshold_bounds(alpha: Image.Image) -> tuple[int, int, int, int] | None:
    return alpha.point(lambda value: 255 if value >= ALPHA_THRESHOLD else 0).getbbox()


def verify_existing_artifacts(
    manifest: object,
    source_dir: Path,
    output_dir: Path,
    report_dir: Path,
) -> int:
    """Validate existing icon inputs and outputs without rewriting any artifact."""
    names, groups = _manifest_inventory(manifest)
    for group, rows, columns, icons in groups:
        source_path = source_dir / f"{group}.png"
        source, bands, metadata = _read_png(source_path)
        if "A" not in bands and "transparency" not in metadata:
            raise ValueError(f"Transparent source PNG has no alpha channel: {source_path}")
        for index, name in enumerate(icons):
            row, column = divmod(index, columns)
            cell = source.crop(
                (
                    source.width * column // columns,
                    source.height * row // rows,
                    source.width * (column + 1) // columns,
                    source.height * (row + 1) // rows,
                )
            )
            if _threshold_bounds(cell.getchannel("A")) is None:
                raise ValueError(f"Transparent source cell is empty: {group}/{name}")

    overrides = source_dir / "individual"
    if overrides.exists():
        for override in sorted(overrides.glob("*.png")):
            if override.stem not in names:
                raise ValueError(f"Unknown individual icon override: {override.stem}")
            _read_png(override)

    expected_paths = {output_dir / f"ic_wp_{name}.png" for name in names}
    actual_paths = set(output_dir.glob("ic_wp_*.png")) if output_dir.is_dir() else set()
    if actual_paths != expected_paths:
        missing = sorted(path.name for path in expected_paths - actual_paths)
        unexpected = sorted(path.name for path in actual_paths - expected_paths)
        raise ValueError(f"Output PNG inventory mismatch; missing={missing}, unexpected={unexpected}")

    actual_quality: list[dict[str, object]] = []
    for name in names:
        output_path = output_dir / f"ic_wp_{name}.png"
        icon, _, _ = _read_png(output_path)
        if icon.size != (TARGET_SIZE, TARGET_SIZE):
            raise ValueError(f"Output PNG must be {TARGET_SIZE}x{TARGET_SIZE}: {output_path}")
        alpha = icon.getchannel("A")
        bounds = _threshold_bounds(alpha)
        if bounds is None:
            raise ValueError(f"Output PNG has no visible foreground: {output_path}")
        longest_edge_ratio = max(bounds[2] - bounds[0], bounds[3] - bounds[1]) / TARGET_SIZE
        quality = inspect_icon(name, icon)
        if quality.corner_alpha != 0:
            raise ValueError(f"Output PNG corners must be transparent: {output_path}")
        if not 0.70 <= longest_edge_ratio <= 0.74:
            raise ValueError(
                f"Output PNG foreground edge ratio must be 70%-74%: "
                f"{output_path} ({longest_edge_ratio:.3f})"
            )
        if abs(quality.center_dx) > 4 or abs(quality.center_dy) > 4:
            raise ValueError(f"Output PNG optical offset exceeds 4px: {output_path}")
        actual_quality.append(asdict(quality))

    quality_report_path = report_dir / "quality-report.json"
    if not quality_report_path.is_file():
        raise ValueError(f"Missing quality-report.json: {quality_report_path}")
    try:
        reported_quality = json.loads(quality_report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Invalid quality-report.json {quality_report_path}: {error}") from error
    if reported_quality != actual_quality:
        raise ValueError(f"quality-report.json does not match output PNGs: {quality_report_path}")

    contact_sheet_path = report_dir / "contact-sheet.png"
    contact_sheet, _, _ = _read_png(contact_sheet_path)
    if contact_sheet.width <= 0 or contact_sheet.height <= 0:
        raise ValueError(f"Contact sheet PNG is empty: {contact_sheet_path}")

    print(f"Verified {len(names)} icons; contact sheet: {contact_sheet_path}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument(
        "--verify-only",
        action="store_true",
        help="validate existing inputs, outputs and reports without rewriting them",
    )
    arguments = parser.parse_args()

    try:
        manifest = json.loads(arguments.manifest.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        parser.error(f"Invalid manifest {arguments.manifest}: {error}")
    if arguments.verify_only:
        try:
            return verify_existing_artifacts(
                manifest,
                arguments.source,
                arguments.output,
                arguments.report,
            )
        except ValueError as error:
            parser.error(str(error))

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
