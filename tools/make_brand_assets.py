"""Aset merek Valoid HD dari gambar sumber di docs/icon (dibuat dengan Google Flow lewat gflow):
logo transparan untuk aplikasi, latar layar pembuka, ikon launcher adaptif + legacy, dan ikon Play Store.

Pemakaian: python3 tools/make_brand_assets.py   (dari root repo)
Ikon monokrom (ikon bertema Android 13) tetap vektor dari tools/make_icon.py.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
LOGO_SRC = os.path.join(ROOT, "docs/icon/valoid-logo-hd-2048.jpg")
SPLASH_SRC = os.path.join(ROOT, "docs/icon/valoid-splash-hd.jpg")
BG = np.array([15, 11, 28], dtype=float)  # warna latar gambar logo sumber
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def transparent_logo(src: Image.Image) -> Image.Image:
    """Memisahkan emblem bercahaya dari latar gelap: c = a*F + (1-a)*BG, a sekecil mungkin."""
    c = np.asarray(src.convert("RGB")).astype(float)
    a = np.clip(((c - BG) / (255 - BG)).max(axis=2), 0, 1)
    # Pola latar yang sangat redup dibuang agar tepi logo bersih.
    a = np.clip((a - 0.04) / 0.96, 0, 1)
    safe = np.maximum(a, 1e-4)[..., None]
    f = np.clip((c - (1 - a[..., None]) * BG) / safe, 0, 255)
    h, w = a.shape
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.hypot(xx - w / 2, yy - h / 2) / (w / 2)
    a *= np.clip((1.0 - r) / 0.08, 0, 1)  # pudar di luar cincin dan cahayanya
    return Image.fromarray(np.dstack([f, a * 255]).astype(np.uint8), "RGBA")


def save_webp(im: Image.Image, path: str, **kw):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, "WEBP", method=6, **kw)


def main():
    src = Image.open(LOGO_SRC)
    logo = transparent_logo(src)

    # Logo di dalam aplikasi (layar pembuka, login, Pro, Tentang).
    save_webp(logo.resize((1024, 1024), Image.LANCZOS), f"{RES}/drawable-nodpi/valoid_logo.webp", quality=92)

    # Latar layar pembuka.
    save_webp(Image.open(SPLASH_SRC).convert("RGB"), f"{RES}/drawable-nodpi/splash_bg.webp", quality=85)

    # Foreground ikon adaptif 108dp; emblem dijaga di dalam zona aman 66dp.
    for name, scale in DENSITIES.items():
        canvas = round(108 * scale)
        size = round(62 * scale)
        fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        fg.alpha_composite(logo.resize((size, size), Image.LANCZOS), ((canvas - size) // 2,) * 2)
        save_webp(fg, f"{RES}/mipmap-{name}/ic_launcher_foreground.webp", lossless=True)

        # Ikon legacy (Android 7): gambar penuh, persegi membulat dan bulat.
        px = round(48 * scale)
        full = src.convert("RGB").resize((px, px), Image.LANCZOS)
        for out, shape in (("ic_launcher", "rounded"), ("ic_launcher_round", "circle")):
            mask = Image.new("L", (px * 4, px * 4), 0)
            d = ImageDraw.Draw(mask)
            if shape == "circle":
                d.ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
            else:
                d.rounded_rectangle((0, 0, px * 4 - 1, px * 4 - 1), radius=px * 4 * 0.18, fill=255)
            icon = full.convert("RGBA")
            icon.putalpha(mask.resize((px, px), Image.LANCZOS))
            path = f"{RES}/mipmap-{name}/{out}.png"
            icon.save(path)

    # Ikon Play Store dan pratinjau.
    full = src.convert("RGB")
    full.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "docs/icon/valoid-icon-playstore-512.png"))
    full.resize((1024, 1024), Image.LANCZOS).save(os.path.join(ROOT, "docs/icon/valoid-icon-1024.png"))


if __name__ == "__main__":
    main()
