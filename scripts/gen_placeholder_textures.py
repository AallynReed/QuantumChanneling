"""
Generate placeholder 16x16 textures for crafting-progression items not yet designed.

Each gets a recognisable colour palette + minimal shape so they're distinguishable in inventory
but visibly "WIP" so we remember to replace them.

Run with: python scripts/gen_placeholder_textures.py
"""
from PIL import Image
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "quantumchanneling", "textures")
ITEM_DIR = os.path.join(OUT, "item")
BLOCK_DIR = os.path.join(OUT, "block")
os.makedirs(ITEM_DIR, exist_ok=True)


def clamp(v): return max(0, min(255, int(v)))
def shade(rgb, dv): return tuple(clamp(c + dv) for c in rgb)
def lerp(a, b, t): return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def save_item(img, name):
    img.save(os.path.join(ITEM_DIR, name + ".png"), "PNG", optimize=True)
    print("  item/" + name + ".png")


def save_block(img, name):
    img.save(os.path.join(BLOCK_DIR, name + ".png"), "PNG", optimize=True)
    print("  block/" + name + ".png")


def diamond_outline(img, color):
    """A simple rotated-square outline at the center — generic "tech ingot/component" silhouette."""
    pts = [
        (7,2),(8,2),
        (5,3),(6,3),(9,3),(10,3),
        (4,4),(11,4),
        (3,5),(12,5),
        (2,6),(13,6),
        (2,7),(13,7),
        (2,8),(13,8),
        (3,9),(12,9),
        (4,10),(11,10),
        (5,11),(6,11),(9,11),(10,11),
        (7,12),(8,12),
    ]
    for x, y in pts:
        img.putpixel((x, y), color + (255,))


def ring_outline(img, color):
    """Annular ring — for Dyson Ring."""
    pts = []
    for theta_step in range(24):
        import math
        t = theta_step / 24.0 * 2 * math.pi
        for r in [5.5, 6.0]:
            x = int(round(7.5 + r * math.cos(t)))
            y = int(round(7.5 + r * math.sin(t)))
            pts.append((x, y))
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), color + (255,))


def hammer_outline(img, head_color, handle_color):
    """T-shape hammer silhouette."""
    # Hammer head (rows 2-5, cols 3-12)
    for y in range(2, 6):
        for x in range(3, 13):
            img.putpixel((x, y), head_color + (255,))
    # Handle (rows 6-14, cols 7-8)
    for y in range(6, 15):
        for x in range(7, 9):
            img.putpixel((x, y), handle_color + (255,))


def make_placeholder(name, base, accent, kind="diamond"):
    """Fill background base, dark border, then overlay the requested motif in accent color."""
    img = Image.new("RGBA", (16, 16), base + (255,))
    border = shade(base, -45)
    for i in range(16):
        img.putpixel((i, 0), border + (255,))
        img.putpixel((i, 15), border + (255,))
        img.putpixel((0, i), border + (255,))
        img.putpixel((15, i), border + (255,))
    if kind == "diamond":
        diamond_outline(img, accent)
    elif kind == "ring":
        ring_outline(img, accent)
    elif kind == "hammer":
        hammer_outline(img, accent, shade(accent, -50))
    elif kind == "blackhole":
        # Black hole — dark center with bright ring
        import math
        for y in range(16):
            for x in range(16):
                d = math.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
                if d < 2.5:
                    img.putpixel((x, y), (8, 8, 12, 255))           # void
                elif 3.0 < d < 4.5:
                    img.putpixel((x, y), accent + (255,))            # accretion ring
                elif d < 6.5:
                    rim = shade(accent, -30)
                    img.putpixel((x, y), rim + (255,))
    return img


if __name__ == "__main__":
    print("Generating placeholder item textures...")
    # Photon Manager Container — gray/silver with a diamond core (glass+diamond+obsidian recipe)
    save_item(make_placeholder("photon_manager_container",
                                base=(70, 76, 86),
                                accent=(180, 220, 240),
                                kind="diamond"),
              "photon_manager_container")
    # Dyson Ring — gold ring
    save_item(make_placeholder("dyson_ring",
                                base=(60, 50, 30),
                                accent=(255, 200, 70),
                                kind="ring"),
              "dyson_ring")
    # Star Shaper's Hammer — netherite-ish hammer
    save_item(make_placeholder("star_shapers_hammer",
                                base=(40, 35, 38),
                                accent=(120, 130, 145),
                                kind="hammer"),
              "star_shapers_hammer")
    # Uncontained Black Hole — dark with violet accretion ring
    save_item(make_placeholder("uncontained_black_hole",
                                base=(20, 14, 30),
                                accent=(180, 130, 255),
                                kind="blackhole"),
              "uncontained_black_hole")

    # White Dwarf — bright white-yellow with hot core, intermediate product after the hammer
    # crushes a nether star.
    save_item(make_placeholder("white_dwarf",
                                base=(220, 215, 180),
                                accent=(255, 250, 220),
                                kind="diamond"),
              "white_dwarf")

    print("Generating placeholder block textures...")
    # Star Alloy Block — bright with star-burst (placeholder full-cube block)
    img = make_placeholder("star_alloy_block",
                            base=(140, 150, 170),
                            accent=(240, 230, 200),
                            kind="diamond")
    save_block(img, "star_alloy_block")
    save_item(img.copy(), "star_alloy_block")

    # Star Shaper's Hammer — netherite-toned block texture for the hammer-block faces. The
    # block itself is shaped via the model (wide head + narrow stem + wide base); this texture
    # just tiles across each face.
    save_block(make_placeholder("star_shapers_hammer",
                                base=(48, 44, 50),
                                accent=(160, 150, 120),
                                kind="diamond"),
              "star_shapers_hammer")
    print("Done.")
