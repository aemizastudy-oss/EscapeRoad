#!/usr/bin/env python3
"""Crop real artwork from the PDF's flattened JPEGs; never render a screen as app UI.

Requires PyMuPDF and Pillow. Crop coordinates refer to the existing page-XX.png
previews (1104x621, except page 7 at 1656x777), then map to the original image
xref before cropping. RGB pixels are preserved; optional masks only remove the
composited background. No replacement drawings or downloaded assets are used.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from functools import lru_cache
import hashlib
from io import BytesIO
import json
import math
from pathlib import Path

import PIL
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps
import pymupdf as fitz


@dataclass(frozen=True)
class Asset:
    name: str
    page: int
    box: tuple[int, int, int, int]
    mask: str = "none"
    fill_below: int | None = None
    max_edge: int = 600
    notes: str = ""


ASSETS = (
    Asset("wallpaper", 12, (25, 135, 520, 390), max_edge=1400,
          notes="Text-free burgundy textile/spade patch. Opaque texture; not a complete page or a seamless tile."),
    Asset("paper", 3, (85, 330, 575, 510), max_edge=1024,
          notes="Text-free interior parchment patch; excludes the letter, signature, seal and borders."),
    Asset("logo", 1, (386, 125, 715, 319), "cream", max_edge=1100,
          notes="ESCAPE ROAD wordmark only. Excludes welcome text, tagline, buttons and framing."),
    Asset("envelope", 4, (496, 179, 608, 257), "light", fill_below=179,
          notes="Envelope plus its wax seal. Enclosed dark details retain their original RGB pixels."),
    Asset("clock", 21, (510, 128, 594, 219), "light",
          notes="Stopwatch icon only; transparent interior, hands and rings retained."),
    Asset("key", 7, (1373, 430, 1474, 465), "light",
          notes="Gold key only. Excludes the adjacent owned-key label."),
    Asset("book", 18, (106, 159, 143, 202), "light",
          notes="The PDF has no separate book illustration: this is its text-free paper-fragment icon."),
    Asset("lock", 7, (1388, 220, 1534, 415), "light", fill_below=309,
          notes="Keyhole padlock only. No digits, words, example inputs or controls are baked in."),
    Asset("compass", 13, (600, 404, 687, 511), "compass",
          notes="Map compass rose and N orientation mark, selected from parchment. Fine original map lines within the rose remain."),
    Asset("wax", 4, (532, 203, 572, 245), "wax",
          notes="The circular red seal from the envelope. No status checkmark or completion text is invented."),
    Asset("lighthouse", 12, (540, 58, 976, 400), max_edge=1200,
          notes="Lighthouse, moon and hillside crop with its original background. The skip button and all narrative text are excluded; the beam is cropped at the right edge."),
    Asset("direction_lock", 7, (761, 220, 895, 417), "light", fill_below=287,
          notes="Direction padlock with its four fixed direction triangles, no entered sequence or other text. Live hit targets/sequence remain native controls."),
    Asset("lock_shackle", 7, (144, 220, 248, 309), "silver",
          notes="Silver shackle from numeric lock only; colour mask excludes its brass body and all example digits."),
    Asset("brass_plate", 7, (1403, 322, 1438, 382), max_edge=400,
          notes="Opaque, text-free brass material patch from the left side of the keyhole lock. Does not contain a keyhole, frame, digit or button."),
)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def ramp(channel: Image.Image, start: int, end: int) -> Image.Image:
    return channel.point(lambda value: max(0, min(255, round((value - start) * 255 / (end - start)))))


def fill_enclosed_details(alpha: Image.Image, top: int) -> Image.Image:
    """Keep source RGB inside opaque bodies, without filling the open shackle area."""
    top = max(0, min(alpha.height - 1, top))
    region = alpha.crop((0, top, alpha.width, alpha.height))
    binary = region.point(lambda value: 255 if value >= 16 else 0)
    flooded = ImageOps.expand(binary, border=1, fill=0)
    ImageDraw.floodfill(flooded, (0, 0), 128)
    interior = flooded.point(lambda value: 0 if value == 128 else 255).crop((1, 1, region.width + 1, region.height + 1))
    # Fill the entire body's interior, including partly selected dark details, while
    # retaining the original matte at its exterior anti-aliased edge.
    interior = interior.filter(ImageFilter.MinFilter(5))
    result = alpha.copy()
    result.paste(ImageChops.lighter(region, interior), (0, top))
    return result


def apply_mask(image: Image.Image, asset: Asset, scale: tuple[float, float]) -> Image.Image:
    if asset.mask == "none":
        return image.convert("RGB")
    red, green, blue = image.split()
    if asset.mask == "light":
        alpha = ramp(green, 36, 86)
    elif asset.mask == "cream":
        alpha = ramp(green, 75, 170)
    elif asset.mask == "silver":
        high = ImageChops.lighter(red, ImageChops.lighter(green, blue))
        low = ImageChops.darker(red, ImageChops.darker(green, blue))
        chroma = ImageChops.subtract(high, low)
        neutral = chroma.point(lambda value: 255 if value <= 40 else 0)
        alpha = ImageChops.multiply(ramp(green, 40, 105), neutral)
    elif asset.mask == "wax":
        excess_red = ImageChops.subtract(red, ImageChops.lighter(green, blue))
        alpha = ramp(excess_red, 40, 72)
    elif asset.mask == "compass":
        alpha = ImageOps.invert(ramp(green, 130, 190))
        selection = Image.new("L", image.size)
        draw = ImageDraw.Draw(selection)
        left, top, _, _ = asset.box
        def point(x: int, y: int) -> tuple[float, float]:
            return ((x - left) * scale[0], (y - top) * scale[1])
        draw.ellipse((*point(606, 430), *point(679, 502)), fill=255)
        draw.polygon([point(x, y) for x, y in ((643, 423), (652, 459), (684, 466), (652, 473), (643, 508), (636, 473), (602, 466), (636, 458))], fill=255)
        draw.rectangle((*point(635, 405), *point(652, 422)), fill=255)
        alpha = ImageChops.multiply(alpha, selection)
    else:
        raise ValueError(asset.mask)
    if asset.fill_below is not None:
        alpha = fill_enclosed_details(alpha, round((asset.fill_below - asset.box[1]) * scale[1]))
    result = image.convert("RGBA")
    result.putalpha(alpha)
    return result


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, default=root / "docs/Escape Road 화면 디자인.pdf")
    parser.add_argument("--output-dir", type=Path, default=root / "app/src/main/res/drawable-nodpi")
    parser.add_argument("--manifest", type=Path, default=root / "docs/pdf-assets.json")
    args = parser.parse_args()
    source_bytes = args.pdf.read_bytes()
    document = fitz.open(stream=source_bytes, filetype="pdf")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    inventory = []
    for number, page in enumerate(document, 1):
        images = page.get_images(full=True)
        inventory.append({"page": number, "image_count": len(images), "vector_paths": len(page.get_drawings()), "xrefs": [item[0] for item in images]})

    @lru_cache(maxsize=2)
    def original(page_number: int):
        page = document[page_number - 1]
        images = page.get_images(full=True)
        if len(images) != 1:
            raise ValueError(f"Page {page_number} no longer has one flattened source image")
        xref = images[0][0]
        source = document.extract_image(xref)
        return Image.open(BytesIO(source["image"])).convert("RGB"), xref, source

    results = []
    for asset in ASSETS:
        image, xref, source = original(asset.page)
        preview_size = (1656, 777) if asset.page == 7 else (1104, 621)
        scale = (image.width / preview_size[0], image.height / preview_size[1])
        box = tuple((math.floor if index < 2 else math.ceil)(value * scale[index % 2]) for index, value in enumerate(asset.box))
        crop = image.crop(box)
        output = apply_mask(crop, asset, scale)
        before_resize = output.size
        if max(output.size) > asset.max_edge:
            output.thumbnail((asset.max_edge, asset.max_edge), Image.Resampling.LANCZOS)
        destination = args.output_dir / f"pdf_{asset.name}.png"
        encoded = BytesIO()
        output.save(encoded, format="PNG", optimize=True)
        data = encoded.getvalue()
        if not destination.exists() or destination.read_bytes() != data:
            temporary = destination.with_name("." + destination.name + ".tmp")
            temporary.write_bytes(data)
            temporary.replace(destination)
        results.append({
            "name": destination.name,
            "source_page": asset.page,
            "source_xref": xref,
            "source_image_size": list(image.size),
            "source_image_sha256": digest(source["image"]),
            "preview_size": list(preview_size),
            "preview_crop_px_ltrb": list(asset.box),
            "source_crop_px_ltrb": list(box),
            "crop_size_before_resize": list(before_resize),
            "mask": asset.mask,
            "fill_enclosed_source_details_below_preview_y": asset.fill_below,
            "output_size": list(output.size),
            "output_mode": output.mode,
            "output_bytes": len(data),
            "output_sha256": digest(data),
            "alpha": output.mode == "RGBA",
            "notes": asset.notes,
        })
        print(f"{destination.name}: {output.width}x{output.height}, {len(data):,} bytes, mask={asset.mask}")
    manifest = {
        "source_pdf": str(args.pdf.relative_to(root)) if args.pdf.is_relative_to(root) else args.pdf.name,
        "source_pdf_sha256": digest(source_bytes),
        "script": "tools/extract_pdf_assets.py",
        "script_sha256": digest(Path(__file__).read_bytes()),
        "tools": {"pymupdf": fitz.VersionBind, "pillow": PIL.__version__},
        "method": "Original embedded JPEG xref -> source-resolution rectangular crop -> documented alpha selection -> optional downscale -> PNG. No AI generation, new artwork or whole-screen UI images.",
        "mask_limitations": "The PDF contains composited JPEGs, not transparent original sprites. Alpha is recovered approximately by colour/brightness selection; original anti-aliased pixels can retain a small background fringe. RGB pixels are not repainted. The paper, wallpaper, brass material and lighthouse crop remain opaque intentionally.",
        "inventory": inventory,
        "assets": results,
        "missing": [{"name": "pdf_car.png", "reason": "The PDF has no car illustration. Page 25 contains only a labelled photograph placeholder; no substitute drawing was generated."}],
        "total_output_bytes": sum(item["output_bytes"] for item in results),
        "originals_modified": False,
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"TOTAL: {manifest['total_output_bytes']:,} bytes; manifest={args.manifest}")


if __name__ == "__main__":
    main()
