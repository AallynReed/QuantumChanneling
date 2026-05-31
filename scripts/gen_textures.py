"""
Generate textured 16x16 PNGs for Quantum Channeling block textures.

Design philosophy:
- Replace flat solid colors with subtle gradients + structural detail.
- 3-5 distinct shades per texture so the eye registers depth.
- Minecraft-typical chunky pixel detail (not noisy, not flat).
- Material-appropriate: dark core reads as machined metal panel; accents read as
  glowing energy panels; storage fill reads as contained luminous matter.

All textures: 16x16 RGBA.

Run with: python scripts/gen_textures.py
"""
from PIL import Image
import random
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "quantumchanneling", "textures", "block")


def clamp(v):
    return max(0, min(255, int(v)))


def shade(rgb, dv):
    return tuple(clamp(c + dv) for c in rgb)


def lerp(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"), "PNG", optimize=True)
    print("  wrote " + name + ".png")


def make_core_dark():
    """Dark machined panel. Diagonal gradient (top-left lit), panel seams, corner rivets."""
    hi = (44, 50, 62)
    mid = (32, 36, 46)
    shadow = (10, 12, 18)
    rivet = (56, 62, 76)

    img = Image.new("RGBA", (16, 16))
    random.seed(7)
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            col = lerp(hi, shadow, t)
            col = lerp(col, mid, 0.55)
            col = shade(col, random.randint(-3, 3))
            img.putpixel((x, y), col + (255,))

    for i in range(16):
        img.putpixel((7, i), shadow + (255,))
        img.putpixel((i, 7), shadow + (255,))
        img.putpixel((8, i), shade(mid, 4) + (255,))
        img.putpixel((i, 8), shade(mid, 4) + (255,))

    for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img.putpixel((cx, cy), rivet + (255,))
        if cy + 1 < 16:
            img.putpixel((cx, cy + 1), shadow + (255,))

    save(img, "photon_core_dark")


def make_accent(name, base_rgb, sparkle_color, seed):
    """Emissive accent panel. Center glow, diagonal energy lines, sparkles."""
    img = Image.new("RGBA", (16, 16))
    random.seed(seed)
    dark = shade(base_rgb, -45)
    edge = shade(base_rgb, -25)
    bright = shade(base_rgb, +35)
    hot = lerp(base_rgb, sparkle_color, 0.45)

    for y in range(16):
        for x in range(16):
            cx, cy = 7.5, 7.5
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / 10.6
            d = min(1.0, d)
            col = lerp(hot, edge, d)
            if (x + y) % 5 == 0:
                col = lerp(col, bright, 0.55)
            if (x - y) % 7 == 0:
                col = lerp(col, dark, 0.18)
            col = shade(col, random.randint(-3, 3))
            img.putpixel((x, y), col + (255,))

    spots = [(4, 11), (11, 4), (12, 12)]
    for sx, sy in spots:
        existing = img.getpixel((sx, sy))[:3]
        img.putpixel((sx, sy), lerp(existing, sparkle_color, 0.75) + (255,))
        for hx, hy in [(sx - 1, sy), (sx + 1, sy), (sx, sy - 1), (sx, sy + 1)]:
            if 0 <= hx < 16 and 0 <= hy < 16:
                h_existing = img.getpixel((hx, hy))[:3]
                img.putpixel((hx, hy), lerp(h_existing, sparkle_color, 0.20) + (255,))

    save(img, name)


def make_storage_fill():
    """Contained luminous matter — brighter / more sparkly than the accent panels."""
    hot = (200, 240, 255)
    dim = (40, 130, 180)

    img = Image.new("RGBA", (16, 16))
    random.seed(99)
    for y in range(16):
        for x in range(16):
            cx, cy = 7.5, 7.5
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / 10.6
            d = min(1.0, d)
            col = lerp(hot, dim, d * 0.9)
            if y % 3 == 0:
                col = lerp(col, hot, 0.25)
            col = shade(col, random.randint(-6, 6))
            img.putpixel((x, y), col + (255,))

    for sx, sy in [(3, 5), (11, 10), (8, 3), (4, 13), (13, 7)]:
        existing = img.getpixel((sx, sy))[:3]
        img.putpixel((sx, sy), lerp(existing, (255, 255, 255), 0.85) + (255,))

    save(img, "photon_storage_fill")


if __name__ == "__main__":
    print("Generating photon textures into " + OUT)
    make_core_dark()
    make_accent("photon_accent_blue",    (0x4F, 0xA0, 0xFF), (0xE0, 0xF0, 0xFF), 11)
    make_accent("photon_accent_red",     (0xFF, 0x55, 0x60), (0xFF, 0xE0, 0xE0), 13)
    make_accent("photon_accent_gold",    (0xFF, 0xD8, 0x60), (0xFF, 0xFA, 0xC0), 17)
    make_accent("photon_accent_copper",  (0xC8, 0x75, 0x33), (0xFF, 0xE0, 0xB0), 19)
    make_accent("photon_accent_iron",    (0xC0, 0xD0, 0xDC), (0xFF, 0xFF, 0xFF), 23)
    make_accent("photon_accent_diamond", (0x80, 0xE0, 0xF0), (0xFF, 0xFF, 0xFF), 29)
    make_accent("photon_accent_emerald", (0x50, 0xC8, 0x80), (0xD0, 0xFF, 0xE0), 31)
    make_storage_fill()
    print("Done.")
