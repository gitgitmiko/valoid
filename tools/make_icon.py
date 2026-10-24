"""Membuat ikon Valoid (nuansa Killjoy, desain orisinal): vektor adaptif, monokrom, PNG legacy, dan pratinjau."""
import math, os, sys
from PIL import Image, ImageDraw

RES = sys.argv[1]          # folder app/src/main/res
PREVIEW = sys.argv[2]      # file pratinjau PNG

YELLOW, PURPLE, BG, GLOW = "#FFD23F", "#A78BFA", "#16141C", "#241F33"
C, RING_R, RING_W = 54.0, 31.0, 3.2
V = [(36, 36), (45, 36), (54, 61), (63, 36), (72, 36), (58, 76), (50, 76)]
LENS = [(54, 31.5), (60.5, 38), (54, 44.5), (47.5, 38)]
ARCS = [(300, 420), (120, 240)]  # derajat; celah di atas dan bawah

def pt(deg, r=RING_R):
    a = math.radians(deg)
    return C + r * math.cos(a), C + r * math.sin(a)

def poly_path(points):
    return "M" + " L".join(f"{x:g},{y:g}" for x, y in points) + " Z"

def arc_path(a0, a1):
    (x0, y0), (x1, y1) = pt(a0), pt(a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M{x0:.2f},{y0:.2f} A{RING_R:g},{RING_R:g} 0 {large},1 {x1:.2f},{y1:.2f}"

def vector(body):
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '</vector>\n')

def foreground(color_v, color_lens, color_ring):
    rings = "".join(
        f'    <path android:pathData="{arc_path(a0, a1)}" android:strokeColor="{color_ring}"\n'
        f'        android:strokeWidth="{RING_W}" android:strokeLineCap="round" android:fillColor="#00000000"/>\n'
        for a0, a1 in ARCS)
    return vector(rings +
                  f'    <path android:pathData="{poly_path(V)}" android:fillColor="{color_v}"/>\n'
                  f'    <path android:pathData="{poly_path(LENS)}" android:fillColor="{color_lens}"/>\n')

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)

write(f"{RES}/drawable/ic_launcher_foreground.xml", foreground(YELLOW, PURPLE, YELLOW))
write(f"{RES}/drawable/ic_launcher_monochrome.xml", foreground("#FFFFFFFF", "#FFFFFFFF", "#FFFFFFFF"))
write(f"{RES}/drawable/ic_launcher_background.xml", vector(
    f'    <path android:pathData="M0,0h108v108h-108z" android:fillColor="{BG}"/>\n'
    f'    <path android:pathData="M54,54m-40,0a40,40 0 1,1 80,0a40,40 0 1,1 -80,0" android:fillColor="{GLOW}"/>\n'))
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
            '</adaptive-icon>\n')
write(f"{RES}/mipmap-anydpi-v26/ic_launcher.xml", adaptive)
write(f"{RES}/mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

def render(px):
    """Gambar kanvas adaptif 108x108 pada resolusi px (dengan supersampling)."""
    ss = 4
    s = px * ss / 108.0
    img = Image.new("RGBA", (px * ss, px * ss), BG)
    d = ImageDraw.Draw(img)
    d.ellipse([(C - 40) * s, (C - 40) * s, (C + 40) * s, (C + 40) * s], fill=GLOW)
    box = [(C - RING_R) * s, (C - RING_R) * s, (C + RING_R) * s, (C + RING_R) * s]
    half = RING_W * s / 2
    outer = [box[0] - half, box[1] - half, box[2] + half, box[3] + half]
    for a0, a1 in ARCS:
        d.arc(outer, a0, a1, fill=YELLOW, width=round(RING_W * s))
        for deg in (a0, a1):
            x, y = pt(deg)
            d.ellipse([x * s - half, y * s - half, x * s + half, y * s + half], fill=YELLOW)
    d.polygon([(x * s, y * s) for x, y in V], fill=YELLOW)
    d.polygon([(x * s, y * s) for x, y in LENS], fill=PURPLE)
    return img.resize((px, px), Image.LANCZOS)

def legacy(size, round_mask):
    # Ikon lama: ambil area tengah 72/108 dari kanvas adaptif lalu beri sudut membulat atau lingkaran.
    full = render(round(size * 108 / 72))
    off = (full.width - size) // 2
    img = full.crop((off, off, off + size, off + size))
    mask = Image.new("L", (size * 4, size * 4), 0)
    md = ImageDraw.Draw(mask)
    if round_mask:
        md.ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 * 0.22, fill=255)
    img.putalpha(mask.resize((size, size), Image.LANCZOS))
    return img

for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    folder = f"{RES}/mipmap-{density}"
    os.makedirs(folder, exist_ok=True)
    legacy(size, False).save(f"{folder}/ic_launcher.png")
    legacy(size, True).save(f"{folder}/ic_launcher_round.png")

# Pratinjau: kanvas penuh, bentuk bulat, dan bentuk squircle seperti di launcher.
big = render(432)
prev = Image.new("RGBA", (432 * 3 + 80, 432 + 40), "#0B0A0F")
prev.paste(big, (20, 20))
for i, shape in enumerate(("circle", "squircle")):
    m = Image.new("L", (432 * 4, 432 * 4), 0)
    md = ImageDraw.Draw(m)
    inset = 432 * 4 * 18 / 108
    box = [inset, inset, 432 * 4 - inset, 432 * 4 - inset]
    md.ellipse(box, fill=255) if shape == "circle" else md.rounded_rectangle(box, radius=(box[2] - box[0]) * 0.3, fill=255)
    tile = big.copy()
    tile.putalpha(m.resize((432, 432), Image.LANCZOS))
    prev.paste(tile, (40 + 432 * (i + 1), 20), tile)
prev.save(PREVIEW)
print("ok")
