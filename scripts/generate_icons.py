#!/usr/bin/env python3
"""Generate launcher mipmaps and the 512px Play Store icon from the chip mark."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
PLAY = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"

INK = (11, 17, 24, 255)
TEAL = (30, 200, 176, 255)
WHITE = (255, 255, 255, 255)


def rounded_rect(draw: ImageDraw.ImageDraw, xy, radius: float, fill) -> None:
    draw.rounded_rectangle(xy, radius=radius, fill=fill)


def draw_chip(img: Image.Image, color, margin_ratio: float = 0.22) -> None:
    """Chip glyph centered in the canvas. margin_ratio keeps adaptive-icon safe zone."""
    draw = ImageDraw.Draw(img)
    w, h = img.size
    m = w * margin_ratio
    body_w = w - 2 * m
    body_h = body_w * 1.22
    x0 = (w - body_w) / 2
    y0 = (h - body_h) / 2
    x1 = x0 + body_w
    y1 = y0 + body_h
    radius = body_w * 0.16
    pin_w = body_w * 0.28
    pin_h = body_h * 0.12
    gap = (body_h - 3 * pin_h) / 4

    rounded_rect(draw, (x0, y0, x1, y1), radius, color)

    for i in range(3):
        py = y0 + gap * (i + 1) + pin_h * i
        rounded_rect(draw, (x0 - pin_w * 0.72, py, x0 + 1, py + pin_h), pin_h * 0.35, color)
        rounded_rect(draw, (x1 - 1, py, x1 + pin_w * 0.72, py + pin_h), pin_h * 0.35, color)


def save_launcher(size: int, dest: Path, round_mask: bool = False) -> None:
    img = Image.new("RGBA", (size, size), INK)
    draw_chip(img, TEAL, margin_ratio=0.22)
    if round_mask:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(img, mask=mask)
        img = out
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, "PNG")


def save_play_icon(dest: Path, size: int = 512) -> None:
    """Full-bleed square, opaque. Play applies the squircle mask."""
    img = Image.new("RGBA", (size, size), INK)
    draw_chip(img, TEAL, margin_ratio=0.20)
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(dest, "PNG")  # 24-bit opaque; Play also accepts 32-bit


def main() -> None:
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        save_launcher(size, RES / folder / "ic_launcher.png", round_mask=False)
        save_launcher(size, RES / folder / "ic_launcher_round.png", round_mask=True)

    save_play_icon(PLAY / "icon.png", 512)
    save_play_icon(ROOT / "store" / "icon-512.png", 512)
    print("icons written")


if __name__ == "__main__":
    main()
