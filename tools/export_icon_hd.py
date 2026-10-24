"""Ekspor ikon Valoid resolusi tinggi (SVG + PNG) dari geometri yang sama dengan tools/make_icon.py."""
import importlib.util, math, sys
from PIL import Image, ImageDraw

spec = importlib.util.spec_from_file_location("mk", sys.argv[1])
src = open(sys.argv[1]).read()
# Ambil konstanta & fungsi geometri tanpa menjalankan bagian penulisan file.
geom = src[: src.index("def poly_path")]
ns = {}
exec(geom, ns)
OUT = sys.argv[2]
YELLOW, PURPLE, BG, GLOW = ns["YELLOW"], ns["PURPLE"], ns["BG"], ns["GLOW"]
C, R, W, V, LENS, ARCS, pt = ns["C"], ns["RING_R"], ns["RING_W"], ns["V"], ns["LENS"], ns["ARCS"], ns["pt"]

def arc_d(a0, a1):
    (x0, y0), (x1, y1) = pt(a0), pt(a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M{x0:.3f},{y0:.3f} A{R},{R} 0 {large},1 {x1:.3f},{y1:.3f}"

def poly(points):
    return " ".join(f"{x:g},{y:g}" for x, y in points)

fg = "".join(f'  <path d="{arc_d(a0, a1)}" fill="none" stroke="{YELLOW}" stroke-width="{W}" stroke-linecap="round"/>\n' for a0, a1 in ARCS)
fg += f'  <polygon points="{poly(V)}" fill="{YELLOW}"/>\n  <polygon points="{poly(LENS)}" fill="{PURPLE}"/>\n'
bg = f'  <rect width="108" height="108" fill="{BG}"/>\n  <circle cx="54" cy="54" r="40" fill="{GLOW}"/>\n'
head = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1024" height="1024">\n'
open(f"{OUT}/valoid-icon.svg", "w").write(head + "  <!-- Latar -->\n" + bg + "  <!-- Logo -->\n" + fg + "</svg>\n")
open(f"{OUT}/valoid-icon-foreground.svg", "w").write(head + fg + "</svg>\n")

def render(px, with_bg):
    ss = 4
    s = px * ss / 108.0
    img = Image.new("RGBA", (px * ss, px * ss), BG if with_bg else (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if with_bg:
        d.ellipse([(C - 40) * s, (C - 40) * s, (C + 40) * s, (C + 40) * s], fill=GLOW)
    half = W * s / 2
    box = [(C - R) * s - half, (C - R) * s - half, (C + R) * s + half, (C + R) * s + half]
    for a0, a1 in ARCS:
        d.arc(box, a0, a1, fill=YELLOW, width=round(W * s))
        for deg in (a0, a1):
            x, y = pt(deg)
            d.ellipse([x * s - half, y * s - half, x * s + half, y * s + half], fill=YELLOW)
    d.polygon([(x * s, y * s) for x, y in V], fill=YELLOW)
    d.polygon([(x * s, y * s) for x, y in LENS], fill=PURPLE)
    return img.resize((px, px), Image.LANCZOS)

render(1024, True).save(f"{OUT}/valoid-icon-1024.png")
render(1024, False).save(f"{OUT}/valoid-icon-foreground-1024.png")
# Play Store: 512x512 penuh tanpa transparansi (Play menambahkan sudut membulat sendiri).
full = render(512 * 108 // 72 + 1, True)
off = (full.width - 512) // 2
full.crop((off, off, off + 512, off + 512)).convert("RGB").save(f"{OUT}/valoid-icon-playstore-512.png")
print("ok")
