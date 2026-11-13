#!/usr/bin/env python3
"""A TV-kezdőképernyő bannerét (320×180) és az ikont (96×96) rajzolja: a startlap
színeiből kirakott kockák, saját felirattal. Nincs benne védett logó.
Futtatás: python3 tools/make_banner.py   (Pillow kell hozzá)"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
BG = "#0e1514"
TILES = ["#3fd2bb", "#f0b441", "#8cc95c", "#0f8f7e", "#c98a12", "#4f8a2c", "#e6efec"]
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def mosaic(d, x0, y0, cols, rows, size, gap):
    for r in range(rows):
        for c in range(cols):
            col = TILES[(r * 3 + c * 5 + r * c) % len(TILES)]
            x, y = x0 + c * (size + gap), y0 + r * (size + gap)
            d.rounded_rectangle([x, y, x + size, y + size], radius=size // 5, fill=col)


def banner():
    img = Image.new("RGB", (320, 180), BG)
    d = ImageDraw.Draw(img)
    mosaic(d, 22, 42, 3, 3, 28, 6)
    font = ImageFont.truetype(FONT, 34)
    d.text((140, 52), "MINI", font=font, fill="#e6efec")
    d.text((140, 92), "GAMES", font=font, fill="#3fd2bb")
    small = ImageFont.truetype(FONT, 12)
    d.text((142, 136), "TV", font=small, fill="#93a6a2")
    out = RES / "drawable-xhdpi/banner.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, optimize=True)


def icon():
    img = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, 95, 95], radius=20, fill=BG)
    mosaic(d, 14, 14, 3, 3, 18, 5)
    out = RES / "mipmap-xhdpi/ic_launcher.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, optimize=True)


if __name__ == "__main__":
    banner()
    icon()
