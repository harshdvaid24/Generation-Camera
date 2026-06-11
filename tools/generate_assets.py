#!/usr/bin/env python3
"""Generate all Generation Camera era assets, deterministically (seeded).

Outputs into app/src/main/assets/:
  luts/era_<era>.cube      17^3 3D LUTs — full color science per era at degree 10
  overlays/dust_{0..2}.png  1024^2 RGBA dust/fiber/scratch overlays
  overlays/leak_{0..1}.png  1024^2 RGB warm light-leak (additive)
  frames/frame_<era>.png    1080x1440 RGBA era frames (transparent center)

Color-science recipes implement the Era Spec Table in ERA_ANALYSIS.md §0.2.
Run: python3 tools/generate_assets.py   (requires numpy, Pillow)
"""

import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
LUT_SIZE = 17
ERAS = ["1900s", "1910s", "1920s", "1930s", "1940s", "1950s", "1960s",
        "1970s", "1980s", "1990s", "2000s", "2010s", "2020s"]

rng = np.random.default_rng(42)


# ---------------------------------------------------------------- color ops
# All ops take/return float arrays of shape (..., 3) in [0, 1].

def clip(x):
    return np.clip(x, 0.0, 1.0)


def white_balance(rgb, gains):
    return clip(rgb * np.asarray(gains))


def bw_mix(rgb, weights):
    """Channel-mixer monochrome (e.g. orthochromatic = blue-heavy)."""
    g = clip(rgb @ np.asarray(weights))
    return np.stack([g, g, g], axis=-1)


def saturation(rgb, s):
    luma = rgb @ np.array([0.299, 0.587, 0.114])
    return clip(luma[..., None] + (rgb - luma[..., None]) * s)


def lift_gain(rgb, lift, gain):
    """Map [0,1] -> [lift, gain]: lifted blacks / faded highlights."""
    return clip(lift + rgb * (gain - lift))


def contrast(rgb, c, pivot=0.5):
    """c > 0: S-curve toward smoothstep; c < 0: flatten toward pivot."""
    if c >= 0:
        s = rgb * rgb * (3.0 - 2.0 * rgb)  # smoothstep
        return clip(rgb + (s - rgb) * c)
    return clip(pivot + (rgb - pivot) * (1.0 + c * 0.6))


def gamma(rgb, g):
    return clip(np.power(np.maximum(rgb, 1e-6), g))


def split_tone(rgb, shadow_rgb, highlight_rgb, amount):
    """Luminance-weighted duotone tint (multiplicative shadows, screen highs)."""
    luma = (rgb @ np.array([0.299, 0.587, 0.114]))[..., None]
    sh = np.asarray(shadow_rgb)
    hi = np.asarray(highlight_rgb)
    tinted = rgb * (sh + (1.0 - sh) * luma) \
        + (1.0 - rgb) * 0.0
    tinted = 1.0 - (1.0 - tinted) * (1.0 - hi * luma * 0.6)
    return clip(rgb + (tinted - rgb) * amount)


def duotone(rgb, shadow_rgb, highlight_rgb):
    """Full duotone: gray value indexes a gradient shadow->highlight (sepia)."""
    luma = (rgb @ np.array([0.299, 0.587, 0.114]))[..., None]
    return clip(np.asarray(shadow_rgb) + (np.asarray(highlight_rgb) - np.asarray(shadow_rgb)) * luma)


def channel_matrix(rgb, m):
    return clip(rgb @ np.asarray(m).T)


# ------------------------------------------------------------- era recipes
# Each recipe = full color science at degree 10 (ERA_ANALYSIS.md table).

def era_1900s(rgb):
    """Ortho B&W dry plate, sepia duotone: red->dark, blue->blown, low contrast."""
    x = bw_mix(rgb, [0.05, 0.35, 0.60])
    x = contrast(x, -0.35)
    x = lift_gain(x, 0.06, 0.95)
    return duotone(x, [0.231, 0.173, 0.125], [0.941, 0.890, 0.784])


def era_1910s(rgb):
    """Autochrome: pastel, warm amber, muted, lifted blacks, soft highlights."""
    x = white_balance(rgb, [1.06, 1.00, 0.88])
    x = saturation(x, 0.55)
    x = channel_matrix(x, [[0.96, 0.04, 0.00],   # slight magenta-green crossover
                           [0.03, 0.94, 0.03],
                           [0.00, 0.06, 0.94]])
    x = contrast(x, -0.35)
    x = lift_gain(x, 0.08, 0.93)
    return split_tone(x, [1.00, 0.92, 0.78], [1.00, 0.97, 0.88], 0.35)


def era_1920s(rgb):
    """Panchromatic B&W 35mm: natural mix, stronger contrast, hint of warm paper."""
    x = bw_mix(rgb, [0.25, 0.55, 0.20])
    x = contrast(x, 0.35)
    x = lift_gain(x, 0.03, 0.99)
    return duotone(x, [0.043, 0.039, 0.031], [0.992, 0.976, 0.945])


def era_1930s(rgb):
    """Pale, muted, grainy B&W (newsreel/press)."""
    x = bw_mix(rgb, [0.299, 0.587, 0.114])
    x = contrast(x, -0.30)
    x = lift_gain(x, 0.09, 0.88)
    return gamma(x, 0.95)


def era_1940s(rgb):
    """High-contrast neutral press B&W, flashbulb punch."""
    x = bw_mix(rgb, [0.30, 0.59, 0.11])
    x = contrast(x, 0.65)
    return lift_gain(x, 0.00, 1.00)


def era_1950s(rgb):
    """Kodachrome-like: warm, saturated reds, deep contrast, slight age fade."""
    x = white_balance(rgb, [1.08, 1.00, 0.92])
    x = channel_matrix(x, [[1.10, -0.05, -0.05],
                           [-0.02, 1.02, 0.00],
                           [-0.03, 0.02, 1.01]])
    x = saturation(x, 1.25)
    x = contrast(x, 0.45)
    return lift_gain(x, 0.02, 0.97)


def era_1960s(rgb):
    """Super-8 reversal: warm orange cast, saturated, crushed shadows."""
    x = white_balance(rgb, [1.12, 1.02, 0.84])
    x = saturation(x, 1.18)
    x = contrast(x, 0.40)
    x = gamma(x, 1.06)  # crush shadows slightly
    return split_tone(x, [1.00, 0.85, 0.65], [1.00, 0.96, 0.85], 0.30)


def era_1970s(rgb):
    """Faded warm print: lifted faded blacks, yellow cast, muted blues."""
    x = white_balance(rgb, [1.07, 1.02, 0.86])
    x = saturation(x, 0.85)
    x = channel_matrix(x, [[1.00, 0.00, 0.00],
                           [0.00, 1.00, 0.00],
                           [0.04, 0.06, 0.88]])
    x = contrast(x, -0.25)
    x = lift_gain(x, 0.10, 0.93)
    return split_tone(x, [1.00, 0.90, 0.72], [1.00, 0.98, 0.90], 0.30)


def era_1980s(rgb):
    """35mm color negative: vibrant, punchy, teal shadows / warm highlights."""
    x = saturation(rgb, 1.30)
    x = contrast(x, 0.50)
    x = split_tone(x, [0.85, 0.95, 1.00], [1.00, 0.97, 0.90], 0.22)
    return lift_gain(x, 0.01, 1.00)


def era_1990s(rgb):
    """VHS: slight desat, green-magenta tint, raised blacks, bloomy highs."""
    x = saturation(rgb, 0.88)
    x = channel_matrix(x, [[1.02, 0.02, 0.00],
                           [0.00, 1.00, 0.03],
                           [0.02, 0.00, 1.00]])
    x = lift_gain(x, 0.06, 0.96)
    return contrast(x, 0.10)


def era_2000s(rgb):
    """Early digicam: oversaturated primaries, cool flash WB, high contrast."""
    x = white_balance(rgb, [0.96, 1.00, 1.08])
    x = saturation(x, 1.35)
    x = contrast(x, 0.55)
    return gamma(x, 1.04)


def era_2010s(rgb):
    """Early smartphone: neutral-warm, mild HDR shadow lift, vibrance."""
    x = white_balance(rgb, [1.02, 1.00, 0.98])
    x = lift_gain(x, 0.03, 1.00)   # HDR-ish shadow lift
    x = saturation(x, 1.12)
    return contrast(x, 0.20)


def era_2020s(rgb):
    """Modern computational: near-identity, micro-contrast + slight vibrance."""
    x = saturation(rgb, 1.06)
    return contrast(x, 0.12)


RECIPES = {
    "1900s": era_1900s, "1910s": era_1910s, "1920s": era_1920s,
    "1930s": era_1930s, "1940s": era_1940s, "1950s": era_1950s,
    "1960s": era_1960s, "1970s": era_1970s, "1980s": era_1980s,
    "1990s": era_1990s, "2000s": era_2000s, "2010s": era_2010s,
    "2020s": era_2020s,
}


def write_cube(era, fn):
    n = LUT_SIZE
    grid = np.linspace(0.0, 1.0, n)
    b, g, r = np.meshgrid(grid, grid, grid, indexing="ij")  # red fastest
    rgb = np.stack([r, g, b], axis=-1).reshape(-1, 3)
    out = fn(rgb)
    path = os.path.join(ASSETS, "luts", f"era_{era}.cube")
    with open(path, "w") as f:
        f.write(f"TITLE \"Generation Camera {era}\"\n")
        f.write(f"LUT_3D_SIZE {n}\n")
        f.write("DOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
        for row in out:
            f.write("%.6f %.6f %.6f\n" % (row[0], row[1], row[2]))
    print("lut ", path)


# ------------------------------------------------------------- overlays
SZ = 1024


def gen_dust(idx):
    """RGBA white-on-transparent: specks (+fibers, +vertical scratches)."""
    img = Image.new("L", (SZ, SZ), 0)
    d = ImageDraw.Draw(img)
    for _ in range(220 + idx * 60):                       # specks
        x, y = rng.uniform(0, SZ, 2)
        rad = rng.uniform(0.5, 2.5 + idx)
        a = int(rng.uniform(60, 200))
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=a)
    for _ in range(10 + idx * 6):                         # fibers
        x, y = rng.uniform(0, SZ, 2)
        pts = [(x, y)]
        ang = rng.uniform(0, 2 * np.pi)
        for _s in range(int(rng.uniform(8, 26))):
            ang += rng.uniform(-0.7, 0.7)
            x += np.cos(ang) * 6
            y += np.sin(ang) * 6
            pts.append((x, y))
        d.line(pts, fill=int(rng.uniform(70, 150)), width=1)
    if idx >= 1:                                          # vertical scratches
        for _ in range(idx * 3):
            x = rng.uniform(0, SZ)
            wob = rng.uniform(0.5, 2.0)
            pts = [(x + np.sin(t * 0.05) * wob, t) for t in range(0, SZ, 8)]
            d.line(pts, fill=int(rng.uniform(50, 120)), width=1)
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    rgba = Image.merge("RGBA", [Image.new("L", (SZ, SZ), 255)] * 3 + [img])
    path = os.path.join(ASSETS, "overlays", f"dust_{idx}.png")
    rgba.save(path)
    print("dust", path)


def gen_leak(idx):
    """RGB warm gradient blobs hugging one edge; shader adds it."""
    yy, xx = np.mgrid[0:SZ, 0:SZ].astype(np.float32) / SZ
    img = np.zeros((SZ, SZ, 3), np.float32)
    blobs = [((1.05, 0.25), 0.35, (1.0, 0.45, 0.15)),
             ((1.00, 0.70), 0.25, (1.0, 0.25, 0.10))] if idx == 0 else \
            [((-0.05, 0.80), 0.40, (1.0, 0.55, 0.20)),
             ((0.10, 0.15), 0.22, (1.0, 0.75, 0.30))]
    for (cx, cy), s, col in blobs:
        dist2 = (xx - cx) ** 2 + (yy - cy) ** 2
        w = np.exp(-dist2 / (2 * s * s))
        img += w[..., None] * np.asarray(col, np.float32)
    img = np.clip(img, 0, 1)
    out = Image.fromarray((img * 255).astype(np.uint8), "RGB")
    out = out.filter(ImageFilter.GaussianBlur(18))
    path = os.path.join(ASSETS, "overlays", f"leak_{idx}.png")
    out.save(path)
    print("leak", path)


# ------------------------------------------------------------- frames
FW, FH = 1080, 1440


def _font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def _sprockets(d, x0, x1, hole_h, gap, color=(235, 235, 235, 255)):
    y = gap
    while y + hole_h < FH:
        d.rounded_rectangle([x0, y, x1, y + hole_h], radius=8, fill=color)
        y += hole_h + gap


def frame_1900s():
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bw = 64
    d.rectangle([0, 0, FW, FH], fill=(28, 20, 14, 255))
    d.rectangle([bw, bw, FW - bw, FH - bw], fill=(0, 0, 0, 0))
    for _ in range(900):                                   # roughen inner edge
        side = rng.integers(0, 4)
        t = rng.uniform(bw, (FW if side < 2 else FH) - bw)
        r = rng.uniform(1, 7)
        if side == 0: x, y = t, bw + rng.uniform(-4, 6)
        elif side == 1: x, y = t, FH - bw + rng.uniform(-6, 4)
        elif side == 2: x, y = bw + rng.uniform(-4, 6), t
        else: x, y = FW - bw + rng.uniform(-6, 4), t
        d.ellipse([x - r, y - r, x + r, y + r], fill=(28, 20, 14, 255))
    return img


def frame_1910s():
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bw = 70
    d.rectangle([0, 0, FW, FH], fill=(18, 16, 14, 255))
    d.rectangle([bw, bw, FW - bw, FH - bw], fill=(0, 0, 0, 0))
    d.rectangle([bw - 6, bw - 6, FW - bw + 6, FH - bw + 6],
                outline=(196, 164, 90, 255), width=3)
    return img


def _film_gate(numbers_text=None):
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    side = 120
    d.rectangle([0, 0, side, FH], fill=(8, 8, 8, 255))
    d.rectangle([FW - side, 0, FW, FH], fill=(8, 8, 8, 255))
    d.rectangle([0, 0, FW, 36], fill=(8, 8, 8, 255))
    d.rectangle([0, FH - 36, FW, FH], fill=(8, 8, 8, 255))
    _sprockets(d, 30, 90, 56, 64)
    _sprockets(d, FW - 90, FW - 30, 56, 64)
    if numbers_text:
        f = _font(34)
        d.text((FW - side + 14, FH // 2), numbers_text, font=f,
               fill=(228, 150, 40, 255))
    return img


def frame_1920s():
    return _film_gate()


def frame_1930s():
    img = Image.new("RGBA", (FW, FH), (10, 10, 10, 255))
    d = ImageDraw.Draw(img)
    bw = 56
    d.rounded_rectangle([bw, bw, FW - bw, FH - bw], radius=60, fill=(0, 0, 0, 0))
    return img


def _print_border(color, radius=0, bottom_extra=0, line=None):
    img = Image.new("RGBA", (FW, FH), color)
    d = ImageDraw.Draw(img)
    bw = 56
    d.rounded_rectangle([bw, bw, FW - bw, FH - bw - bottom_extra],
                        radius=radius, fill=(0, 0, 0, 0))
    if line:
        d.rounded_rectangle([bw - 3, bw - 3, FW - bw + 3, FH - bw - bottom_extra + 3],
                            radius=radius, outline=line, width=2)
    return img


def frame_1940s():
    return _print_border((248, 248, 246, 255), bottom_extra=70,
                         line=(200, 200, 200, 255))


def frame_1950s():
    return _print_border((250, 246, 232, 255), radius=28,
                         line=(214, 206, 180, 255))


def frame_1960s():
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    side = 140
    d.rectangle([0, 0, side, FH], fill=(6, 6, 6, 255))
    d.rectangle([FW - 40, 0, FW, FH], fill=(6, 6, 6, 255))
    d.rectangle([0, 0, FW, 30], fill=(6, 6, 6, 255))
    d.rectangle([0, FH - 30, FW, FH], fill=(6, 6, 6, 255))
    _sprockets(d, 34, 110, 110, 130)                       # big super-8 holes
    return img


def frame_1970s():
    return _print_border((247, 240, 218, 255), radius=44,
                         line=(216, 204, 170, 255))


def frame_1980s():
    return _film_gate("22  22A")


def frame_1990s():
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    f_big, f_sm = _font(56), _font(40)
    d.ellipse([60, 70, 92, 102], fill=(255, 40, 40, 255))  # REC dot
    d.text((110, 64), "REC", font=f_big, fill=(245, 245, 245, 255))
    d.text((FW - 180, 70), "SP", font=f_sm, fill=(245, 245, 245, 255))
    d.text((60, FH - 190), "JAN. 1 1995", font=f_sm, fill=(245, 245, 245, 255))
    d.text((60, FH - 130), "PM 3:42", font=f_sm, fill=(245, 245, 245, 255))
    cl, m = 56, 40                                         # safe-area corners
    w = 5
    for (x, y, dx, dy) in [(m, m, 1, 1), (FW - m, m, -1, 1),
                           (m, FH - m, 1, -1), (FW - m, FH - m, -1, -1)]:
        d.line([x, y, x + dx * cl, y], fill=(245, 245, 245, 220), width=w)
        d.line([x, y, x, y + dy * cl], fill=(245, 245, 245, 220), width=w)
    return img


def frame_2000s():
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, FW, FH], outline=(0, 0, 0, 255), width=18)
    f = _font(52)
    d.text((FW - 380, FH - 110), "01 01 2002", font=f,
           fill=(255, 150, 30, 255))
    return img


def frame_2010s():
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    ImageDraw.Draw(img).rectangle([0, 0, FW, FH],
                                  outline=(250, 250, 250, 255), width=24)
    return img


def frame_2020s():
    img = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    ImageDraw.Draw(img).rectangle([0, 0, FW, FH],
                                  outline=(255, 255, 255, 255), width=6)
    return img


FRAME_FNS = {
    "1900s": frame_1900s, "1910s": frame_1910s, "1920s": frame_1920s,
    "1930s": frame_1930s, "1940s": frame_1940s, "1950s": frame_1950s,
    "1960s": frame_1960s, "1970s": frame_1970s, "1980s": frame_1980s,
    "1990s": frame_1990s, "2000s": frame_2000s, "2010s": frame_2010s,
    "2020s": frame_2020s,
}


def main():
    for sub in ("luts", "overlays", "frames"):
        os.makedirs(os.path.join(ASSETS, sub), exist_ok=True)
    for era in ERAS:
        write_cube(era, RECIPES[era])
    for i in range(3):
        gen_dust(i)
    for i in range(2):
        gen_leak(i)
    for era, fn in FRAME_FNS.items():
        path = os.path.join(ASSETS, "frames", f"frame_{era}.png")
        fn().save(path)
        print("frame", path)


if __name__ == "__main__":
    main()
