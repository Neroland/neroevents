#!/usr/bin/env python3
"""Generate the NeroEvents texture set (blocks, items, entities, particles, GUI glyphs).

Standard library only (zlib + struct); no Pillow needed. Deterministic: every painter seeds its RNG
from the texture name, so re-runs are byte-identical. Output paths are relative to the repo root
(the parent of this tools/ folder), under common/src/main/resources/assets/neroevents/textures/.

Conventions (shared with the rest of the Nero family):
  * blocks and items are 16x16, entities are 64x64 UV sheets (parachute 64x32), particles are 8x8
    frames (impact flash 16x16), animated block textures are vertical 16x(16*N) strips + .png.mcmeta
  * 1-texel G_DARK outline on silhouette edges facing air; top-left light; <= 4 tones per material
  * emissive parts live in a separate *_glow.png layer (never baked into the base)
  * severity colours are never baked into a texture: anything severity-tinted is painted white-grey
    and tinted by the renderer

usage: python tools/gen_event_assets.py [--force] [--only NAME[,NAME...]] [--check]
  --force   overwrite existing PNGs (default is additive-only: existing files are kept)
  --only    paint a subset (names as listed by --list)
  --list    print every texture name this script owns and exit
  --check   paint nothing; verify every texture exists with the expected size and >= 5 colours
"""
import hashlib
import json
import math
import os
import random
import struct
import sys
import zlib

sys.dont_write_bytecode = True

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_ROOT = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "neroevents", "textures")

# ---- Palette. Mirrors client/EventColours byte-for-byte (one "NAME": "#RRGGBB" entry per line).
PALETTE = {
    "G_DARK": "#1A1D22",
    "GRAPHITE": "#2C3137",
    "GRAPHITE_2": "#33393F",
    "GRAPHITE_4": "#3A4047",
    "G_LIGHT": "#8E98A4",
    "TRIM": "#5B6670",
    "STEEL": "#7A8492",
    "STEEL_DK": "#424A56",
    "NERO_ALLOY": "#26A69A",
    "PLASMA": "#60D4E8",
    "GLASS": "#0B1119",
    "HAZ_Y": "#FFCE2C",
    "HAZ_K": "#18181E",
    "GOLD": "#D9B24C",
    "EVENT_MAGENTA": "#DC54B0",
    "EVENT_MAGENTA_BRIGHT": "#FF9CE0",
    "MAGMA": "#FF6A1A",
    "MAGMA_HOT": "#FFD27A",
    "SLAG": "#3B2F2E",
    "SLAG_DK": "#231B1A",
    "CRUST": "#5A4C44",
    "EMBER": "#FF8C3A",
    "SCORCH": "#141214",
    "RUST": "#8A4B2E",
    "CANOPY": "#E8EDF2",
    "DUST": "#C9A066",
    "ION": "#7FD7FF",
    "CRYO": "#BFE9FF",
    "SOLAR": "#FFB347",
}


def _rgba(hexstr, a=255):
    h = hexstr.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


P = {k: _rgba(v) for k, v in PALETTE.items()}
CLEAR = (0, 0, 0, 0)
WHITE = (255, 255, 255, 255)
GREY_LT = (214, 220, 228, 255)   # white-grey tint base (tinted by renderer)
GREY_MD = (160, 168, 178, 255)
G_DARK = P["G_DARK"]
GRAPHITE = P["GRAPHITE"]
GRAPHITE_2 = P["GRAPHITE_2"]
GRAPHITE_4 = P["GRAPHITE_4"]
G_LIGHT = P["G_LIGHT"]
TRIM = P["TRIM"]
STEEL = P["STEEL"]
STEEL_DK = P["STEEL_DK"]
STEEL_LT = (146, 156, 170, 255)
NERO_ALLOY = P["NERO_ALLOY"]
PLASMA = P["PLASMA"]
GLASS = P["GLASS"]
HAZ_Y = P["HAZ_Y"]
HAZ_K = P["HAZ_K"]
GOLD = P["GOLD"]
EVENT_MAGENTA = P["EVENT_MAGENTA"]
EVENT_MAGENTA_BRIGHT = P["EVENT_MAGENTA_BRIGHT"]
MAGMA = P["MAGMA"]
MAGMA_HOT = P["MAGMA_HOT"]
SLAG = P["SLAG"]
SLAG_DK = P["SLAG_DK"]
SLAG_LT = (74, 60, 58, 255)
CRUST = P["CRUST"]
CRUST_LT = (110, 94, 84, 255)
EMBER = P["EMBER"]
SCORCH = P["SCORCH"]
RUST = P["RUST"]
CANOPY = P["CANOPY"]
CANOPY_SH = (196, 204, 214, 255)
CANOPY_DK = (150, 160, 172, 255)


# ---------------- tiny image + PNG codec (stdlib only) ----------------

class Img:
    """RGBA image with px[x, y] access; out-of-range writes are ignored."""

    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.data = [fill] * (w * h)

    def __getitem__(self, xy):
        x, y = xy
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.data[y * self.w + x]
        return CLEAR

    def __setitem__(self, xy, c):
        x, y = xy
        if 0 <= x < self.w and 0 <= y < self.h:
            self.data[y * self.w + x] = c

    def save(self, path):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                raw.extend(self.data[y * self.w + x])

        def chunk(tag, body):
            c = tag + body
            return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(png)


def png_info(path):
    """(width, height, distinct_colours) of a PNG written by this script (8-bit RGBA, no filters)."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    w, h = struct.unpack(">II", data[16:24])
    pos, idat = 8, b""
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        if tag == b"IDAT":
            idat += data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
    raw = zlib.decompress(idat)
    colours = set()
    stride = w * 4 + 1
    for y in range(h):
        row = raw[y * stride:(y + 1) * stride]
        if row[0] != 0:
            return (w, h, -1)
        for x in range(w):
            colours.add(bytes(row[1 + x * 4:5 + x * 4]))
    return (w, h, len(colours))


# ---------------- painting primitives ----------------

def rng_for(name):
    return random.Random(int(hashlib.sha1(name.encode()).hexdigest()[:8], 16))


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[x, y] = c


def border(img, x0, y0, x1, y1, c):
    for x in range(x0, x1):
        img[x, y0] = c
        img[x, y1 - 1] = c
    for y in range(y0, y1):
        img[x0, y] = c
        img[x1 - 1, y] = c


def noise(img, x0, y0, x1, y1, tones, rng, weights=None):
    """Fill a region with up to 4 tones (weights default to a base-heavy spread)."""
    tones = list(tones)[:4]
    if weights is None:
        weights = [6, 3, 2, 1][:len(tones)]
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[x, y] = rng.choices(tones, weights)[0]


def bevel(img, x0, y0, x1, y1, light, dark):
    """1-texel top-left light / bottom-right dark inside the rectangle (light from top-left)."""
    for x in range(x0, x1 - 1):
        img[x, y0] = light
    for y in range(y0, y1 - 1):
        img[x0, y] = light
    for x in range(x0 + 1, x1):
        img[x, y1 - 1] = dark
    for y in range(y0 + 1, y1):
        img[x1 - 1, y] = dark


def rivet(img, x, y):
    img[x, y] = G_LIGHT
    img[x + 1, y + 1] = G_DARK


def hazard(img, x0, y0, x1, y1, phase=0):
    """2-texel diagonal HAZ_Y / HAZ_K stripes."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[x, y] = HAZ_Y if ((x + y + phase) // 2) % 2 == 0 else HAZ_K


def line(img, x0, y0, x1, y1, c):
    """Bresenham line."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        img[x0, y0] = c
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def disc(img, cx, cy, r, c):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                img[x, y] = c


def outline_alpha(img, c=G_DARK):
    """Draw c on every opaque texel that touches a transparent (or off-image) neighbour."""
    edge = []
    for y in range(img.h):
        for x in range(img.w):
            if img[x, y][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= img.w or ny >= img.h or img[nx, ny][3] == 0:
                    edge.append((x, y))
                    break
    for x, y in edge:
        img[x, y] = c


def blit(dst, src, dx, dy):
    for y in range(src.h):
        for x in range(src.w):
            c = src[x, y]
            if c[3]:
                dst[dx + x, dy + y] = c


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def box_faces(u, v, w, h, d):
    """Standard Minecraft box UV layout for a w*h*d cube at texOffs (u, v).

    Returns {face: (x0, y0, x1, y1)} for up, down, east, north, west, south.
    """
    return {
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + 2 * w, v + d),
        "east": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def paint_box(img, u, v, w, h, d, painter, rng):
    """Call painter(img, face, x0, y0, x1, y1, rng) for every face, then outline each face."""
    for face, (x0, y0, x1, y1) in box_faces(u, v, w, h, d).items():
        painter(img, face, x0, y0, x1, y1, rng)
        if x1 - x0 >= 4 and y1 - y0 >= 4:
            border(img, x0, y0, x1, y1, G_DARK)
        elif x1 - x0 >= 4 and y1 - y0 >= 2:
            for x in range(x0, x1):
                img[x, y0] = G_DARK
        elif y1 - y0 >= 4 and x1 - x0 >= 2:
            for y in range(y0, y1):
                img[x0, y] = G_DARK


# ---------------- material painters ----------------

STEEL_MID = (108, 118, 130, 255)
STEEL_TONES = [STEEL, STEEL_MID, STEEL_DK, STEEL_LT]
GRAPHITE_TONES = [GRAPHITE, GRAPHITE_2, GRAPHITE_4, G_DARK]
SLAG_TONES = [SLAG, SLAG_LT, SLAG_DK, CRUST]
CRUST_TONES = [CRUST, CRUST_LT, SLAG, TRIM]


def plating(img, face, x0, y0, x1, y1, rng):
    noise(img, x0, y0, x1, y1, STEEL_TONES, rng, [12, 3, 1, 1])
    bevel(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, STEEL_LT, STEEL_DK)
    if face in ("up", "down"):
        for x in range(x0 + 1, x1 - 1):
            img[x, y0 + 1] = STEEL_LT if face == "up" else STEEL_DK


def slag_face(img, face, x0, y0, x1, y1, rng):
    noise(img, x0, y0, x1, y1, SLAG_TONES, rng, [7, 3, 3, 1])
    for _ in range(max(1, (x1 - x0) * (y1 - y0) // 14)):
        px, py = rng.randrange(x0 + 1, x1 - 1), rng.randrange(y0 + 1, y1 - 1)
        img[px, py] = SLAG_DK
        if rng.random() < 0.5:
            img[px + 1, py] = SLAG_DK


# ---------------- blocks ----------------

def block_full_border(img):
    border(img, 0, 0, 16, 16, G_DARK)


def tex_meteor_slag(name):
    img, rng = Img(16, 16), rng_for(name)
    slag_face(img, "north", 0, 0, 16, 16, rng)
    # ember pores (2 texels, the glow layer carries the light)
    img[4, 10] = EMBER
    img[11, 5] = EMBER
    block_full_border(img)
    return img


def tex_cooled_crust(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, CRUST_TONES, rng, [8, 3, 2, 1])
    # crack network in TRIM with SLAG_DK shadow
    for seg in (((2, 3), (6, 7)), ((6, 7), (9, 6)), ((9, 6), (13, 11)), ((6, 7), (5, 13)), ((10, 1), (9, 6))):
        line(img, seg[0][0], seg[0][1], seg[1][0], seg[1][1], TRIM)
    for seg in (((3, 4), (7, 8)), ((7, 8), (10, 7))):
        line(img, seg[0][0], seg[0][1], seg[1][0], seg[1][1], SLAG_DK)
    block_full_border(img)
    return img


def tex_embedded_meteor_ore(name):
    img, rng = Img(16, 16), rng_for(name)
    slag_face(img, "north", 0, 0, 16, 16, rng)
    ore_lt = (64, 196, 184, 255)
    cluster = [(5, 4), (6, 4), (5, 5), (10, 8), (11, 8), (11, 9), (6, 11), (7, 11), (12, 3)]
    for i, (x, y) in enumerate(cluster):
        img[x, y] = ore_lt if i % 3 == 0 else NERO_ALLOY
    for x, y in ((7, 5), (12, 9), (8, 12), (13, 4)):
        img[x, y] = G_DARK
    block_full_border(img)
    return img


def tex_crater_pocket_side(name):
    img, rng = Img(16, 16), rng_for(name)
    slag_face(img, "north", 0, 0, 16, 16, rng)
    # cracked lip band near the top
    for x in range(1, 15):
        if x % 3 != 1:
            img[x, 3] = SLAG_DK
    block_full_border(img)
    return img


def tex_crater_pocket_top(name):
    """Rim ring (2 texels) around a hot 12x12 floor; the floor glow lives in the animated glow strip."""
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, [CRUST, SLAG_LT, CRUST_LT, SLAG], rng, [6, 4, 2, 1])
    noise(img, 2, 2, 14, 14, [SLAG_DK, SLAG, (46, 36, 34, 255)], rng, [6, 2, 1])
    for x in range(2, 14):
        img[x, 13] = SLAG_LT
        img[x, 2] = (28, 22, 21, 255)
    for y in range(2, 14):
        img[13, y] = SLAG_LT
        img[2, y] = (28, 22, 21, 255)
    # cooled fissures on the floor
    line(img, 4, 4, 8, 8, SLAG)
    line(img, 8, 8, 11, 6, SLAG)
    line(img, 8, 8, 7, 12, SLAG)
    block_full_border(img)
    return img


def tex_crater_pocket_glow(name):
    """4-frame 16x64 strip: EMBER fissure pulse on the 12x12 pocket floor (interpolated)."""
    img = Img(16, 64)
    levels = [0.55, 0.8, 1.0, 0.8]
    for f, lv in enumerate(levels):
        oy = f * 16
        c = lerp((120, 40, 10, 255), EMBER, lv)
        hot = lerp(EMBER, MAGMA_HOT, (lv - 0.55) / 0.45)
        line(img, 4, oy + 4, 8, oy + 8, c)
        line(img, 8, oy + 8, 11, oy + 6, c)
        line(img, 8, oy + 8, 7, oy + 12, c)
        img[8, oy + 8] = hot
        img[7, oy + 8] = hot if lv >= 0.8 else c
        img[8, oy + 7] = hot if lv >= 1.0 else c
    return img


def tex_hull_plating(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, STEEL_TONES, rng, [12, 3, 1, 1])
    # two panels split by a vertical seam, rivets in the corners
    for y in range(1, 15):
        img[8, y] = STEEL_DK
    bevel(img, 1, 1, 8, 15, STEEL_LT, STEEL_DK)
    bevel(img, 9, 1, 15, 15, STEEL_LT, STEEL_DK)
    for x, y in ((2, 2), (5, 12), (10, 2), (12, 12)):
        rivet(img, x, y)
    block_full_border(img)
    return img


def tex_scorched_panel(name):
    img = tex_hull_plating(name)
    rng = rng_for(name + "/burn")
    cx, cy = 10.5, 10.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) + rng.random() * 1.6
            if d < 4.2:
                img[x, y] = SCORCH
            elif d < 6.0:
                img[x, y] = (40, 32, 30, 255) if rng.random() < 0.7 else RUST
            elif d < 7.2 and rng.random() < 0.35:
                img[x, y] = RUST
    block_full_border(img)
    return img


def tex_bent_strut(name):
    """Beam texture: TRIM body with a G_LIGHT edge and bolt marks; used on every strut element."""
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, [TRIM, (102, 114, 124, 255), STEEL_DK, GRAPHITE_4], rng, [8, 2, 2, 1])
    for y in range(16):
        img[0, y] = G_DARK
        img[15, y] = G_DARK
        img[1, y] = G_LIGHT if y % 5 else TRIM
        img[14, y] = STEEL_DK
    for y in (3, 8, 13):
        img[7, y] = G_DARK
        img[8, y] = G_LIGHT
    for x in range(16):
        img[x, 0] = G_DARK
        img[x, 15] = G_DARK
    return img


def tex_salvage_crate_side(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, STEEL_TONES, rng, [12, 3, 1, 1])
    bevel(img, 1, 1, 15, 15, STEEL_LT, STEEL_DK)
    # corner reinforcement plates
    for (x0, y0) in ((2, 2), (11, 2), (2, 11), (11, 11)):
        rect(img, x0, y0, x0 + 3, y0 + 3, STEEL_DK)
        rivet(img, x0 + 1, y0 + 1)
    # stencilled band
    for x in range(5, 11):
        img[x, 8] = GRAPHITE
    block_full_border(img)
    return img


def tex_salvage_crate_front(name):
    img = tex_salvage_crate_side(name)
    # latch plate in NERO_ALLOY at the top centre (the latch element covers it; keeps the particle/side coherent)
    rect(img, 6, 1, 10, 4, STEEL_DK)
    rect(img, 7, 1, 9, 3, NERO_ALLOY)
    block_full_border(img)
    return img


def tex_salvage_crate_lid(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, STEEL_TONES, rng, [12, 3, 1, 1])
    bevel(img, 1, 1, 15, 15, STEEL_LT, STEEL_DK)
    hazard(img, 2, 6, 14, 10)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(img, x, y)
    block_full_border(img)
    return img


def tex_salvage_crate_bottom(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, [STEEL_DK, TRIM, GRAPHITE_4, STEEL], rng, [8, 2, 2, 1])
    for y in (4, 11):
        for x in range(1, 15):
            img[x, y] = GRAPHITE
    block_full_border(img)
    return img


def tex_event_console_side(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [8, 3, 2, 1])
    bevel(img, 1, 1, 15, 15, GRAPHITE_4, G_DARK)
    # recessed vent slot
    rect(img, 4, 5, 12, 11, G_DARK)
    for y in (6, 8, 10):
        for x in range(5, 11):
            img[x, y] = GRAPHITE
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(img, x, y)
    block_full_border(img)
    return img


def tex_event_console_top(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [8, 3, 2, 1])
    bevel(img, 1, 1, 15, 15, GRAPHITE_4, G_DARK)
    # NERO_ALLOY ring (antenna coupling)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 3.2 <= d < 4.4:
                img[x, y] = NERO_ALLOY
            elif d < 3.2:
                img[x, y] = GRAPHITE_2 if (x + y) % 2 else GRAPHITE
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(img, x, y)
    block_full_border(img)
    return img


def tex_event_console_back(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [8, 3, 2, 1])
    bevel(img, 1, 1, 15, 15, GRAPHITE_4, G_DARK)
    for y in range(3, 13, 2):
        for x in range(3, 13):
            img[x, y] = G_DARK
    rect(img, 6, 13, 10, 15, G_DARK)
    img[7, 14] = NERO_ALLOY
    img[8, 14] = NERO_ALLOY
    block_full_border(img)
    return img


def tex_event_console_front(name):
    """Bezel frame: graphite ring with dark glass inside; the screen element sits on top of it."""
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [8, 3, 2, 1])
    bevel(img, 1, 1, 15, 15, GRAPHITE_4, G_DARK)
    rect(img, 2, 2, 14, 14, GLASS)
    border(img, 2, 2, 14, 14, G_DARK)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y)
    block_full_border(img)
    return img


def glyph_shape(slot):
    """8x8 glyph masks for event_glyphs.png (slot 0..7). 1 = ink.

    Ink occupies the inner 6x6 so the 1-px outline fits inside the 8x8 cell.
    0 INFO hollow circle + dot, 1 REWARD diamond, 2 HAZARD notched triangle, 3 COMBAT crossed blades,
    4 WAR opposing chevrons, 5 DONE hollow square, 6 vote tick (filled square, check cut out), 7 clock.
    """
    inner = {
        0: ["..##..", ".#..#.", "#.##.#", "#.##.#", ".#..#.", "..##.."],
        1: ["..##..", ".####.", "######", "######", ".####.", "..##.."],
        2: ["..##..", "..##..", ".####.", ".#.##.", "######", "######"],
        3: ["##..##", ".####.", "..##..", "..##..", ".####.", "##..##"],
        4: ["#....#", ".#..#.", ".#..#.", ".#..#.", "#....#", "......"],
        5: ["######", "#....#", "#....#", "#....#", "#....#", "######"],
        6: ["######", "#####.", "####.#", "#.#.##", "##.###", "######"],
        7: ["..##..", ".#..#.", "#..#.#", "#.##.#", ".#..#.", "..##.."],
    }[slot]
    rows = ["........"]
    for r in inner:
        rows.append("." + r + ".")
    rows.append("........")
    return rows


def tex_event_glyphs(name):
    """64x16: eight 8x8 white glyphs with a baked 1-px G_DARK outline, slots 0..7 left to right.

    The glyph fits an 8x8 cell *including* its outline, so ink is drawn at 6x6-ish and the outline
    grows outwards into the cell. Row 8..15 of the sheet holds the same glyphs in SUBTLE grey for
    disabled rows (the GUI may use either row).
    """
    img = Img(64, 16)
    for slot in range(8):
        mask = glyph_shape(slot)
        cell = Img(8, 8)
        for y in range(8):
            for x in range(8):
                if mask[y][x] == "#":
                    cell[x, y] = WHITE
        # 1-px dark outline: any clear texel next to ink becomes G_DARK (inside the 8x8 cell)
        out = Img(8, 8)
        for y in range(8):
            for x in range(8):
                if cell[x, y][3]:
                    out[x, y] = WHITE
                else:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if cell[x + dx, y + dy][3]:
                            out[x, y] = G_DARK
                            break
        blit(img, out, slot * 8, 0)
        grey = Img(8, 8)
        for y in range(8):
            for x in range(8):
                c = out[x, y]
                if c == WHITE:
                    grey[x, y] = (141, 160, 180, 255)
                elif c[3]:
                    grey[x, y] = c
        blit(img, grey, slot * 8, 8)
    return img


def glyph_into(img, slot, ox, oy, ink=WHITE, outline=None):
    """Draw an 8x8 glyph (shape only, no baked outline) at (ox, oy)."""
    mask = glyph_shape(slot)
    for y in range(8):
        for x in range(8):
            if mask[y][x] == "#":
                img[ox + x, oy + y] = ink
            elif outline is not None:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < 8 and 0 <= yy < 8 and mask[yy][xx] == "#":
                        img[ox + x, oy + y] = outline
                        break


def nburst(img, ox, oy, c, c2):
    """The magenta N-burst: an 8-point star mark, 6x6, used on the idle console screen."""
    for i in range(-2, 3):
        img[ox + i, oy] = c
        img[ox, oy + i] = c
    for i in (-1, 1):
        img[ox + i, oy + i] = c2
        img[ox + i, oy - i] = c2
    img[ox, oy] = EVENT_MAGENTA_BRIGHT if c == EVENT_MAGENTA else c2


def tex_event_console_screen(name):
    """8 idle frames, 16x128: GLASS with a PLASMA scanline moving down 2 px/frame and the magenta N-burst."""
    img = Img(16, 128)
    for f in range(8):
        oy = f * 16
        rect(img, 0, oy, 16, oy + 16, GLASS)
        # faint grid of the screen raster
        for y in range(oy + 1, oy + 15, 2):
            for x in range(1, 15, 4):
                img[x, y] = (16, 24, 34, 255)
        # scanline: 2 px per frame, wraps inside the 14 px glass area (y 1..14)
        sy = oy + 1 + (f * 2) % 14
        for x in range(1, 15):
            img[x, sy] = (48, 112, 128, 255)
            if x % 2 == 0:
                img[x, sy] = PLASMA
        nburst(img, 8, oy + 8, EVENT_MAGENTA, (120, 48, 100, 255))
        # corner status ticks
        img[2, oy + 2] = PLASMA if f % 4 < 2 else (24, 72, 84, 255)
        img[13, oy + 13] = (24, 72, 84, 255)
        border(img, 0, oy, 16, oy + 16, G_DARK)
    return img


def tex_event_console_screen_active(name):
    """8 active frames, 16x128, white-grey (tinted by severity in the model via tintindex 0).

    Centre: the severity glyph slot (drawn as a plain filled triangle placeholder cell? No - the
    glyph itself is swapped by the block-entity renderer from event_glyphs.png). This strip carries the
    blink box (4 on / 4 off) and the horizontal countdown bar that fills left->right across the frames.
    """
    img = Img(16, 128)
    for f in range(8):
        oy = f * 16
        rect(img, 0, oy, 16, oy + 16, GLASS)
        # countdown bar: 12 px wide at y 12..13, fills with the frame index (renderer picks the frame
        # by progress when it drives the strip manually; as a plain animation it sweeps)
        rect(img, 2, oy + 12, 14, oy + 14, (28, 36, 44, 255))
        fill = 2 + int(round(12 * (f + 1) / 8))
        rect(img, 2, oy + 12, fill, oy + 14, GREY_LT)
        # blink box 4 on / 4 off: a 6x6 hollow square behind the glyph area
        if f < 4:
            border(img, 5, oy + 3, 11, oy + 9, GREY_MD)
            rect(img, 6, oy + 4, 10, oy + 8, GREY_LT)
        else:
            border(img, 5, oy + 3, 11, oy + 9, (60, 66, 74, 255))
        # ticks at the bar ends
        img[1, oy + 12] = GREY_MD
        img[14, oy + 13] = GREY_MD
        border(img, 0, oy, 16, oy + 16, G_DARK)
    return img


def tex_event_console_strip(name):
    """Status strip under the bezel: EVENT_MAGENTA with bright centre texels (not emissive)."""
    img = Img(16, 16)
    rect(img, 0, 0, 16, 16, GRAPHITE)
    bevel(img, 1, 1, 15, 15, GRAPHITE_4, G_DARK)
    rect(img, 1, 6, 15, 10, EVENT_MAGENTA)
    for x in range(3, 13, 3):
        img[x, 7] = EVENT_MAGENTA_BRIGHT
    for x in range(1, 15):
        img[x, 9] = (150, 52, 118, 255)
    border(img, 0, 0, 16, 16, G_DARK)
    return img


def tex_event_console_keys(name):
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [8, 3, 2, 1])
    for y in range(2, 14, 3):
        for x in range(2, 14, 3):
            rect(img, x, y, x + 2, y + 2, GRAPHITE_4)
            img[x, y] = G_LIGHT
    block_full_border(img)
    return img


# ---------------- items ----------------

def tex_supply_beacon(name):
    img, rng = Img(16, 16), rng_for(name)
    # hand-held puck: STEEL body disc, NERO_ALLOY ring, lens cut-out (layer1 carries the lens), HAZ_Y pull-tab
    disc(img, 8, 9, 6.6, STEEL)
    for y in range(16):
        for x in range(16):
            if img[x, y] == STEEL and rng.random() < 0.2:
                img[x, y] = STEEL_LT if y < 9 else STEEL_MID
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 9)
            if 2.7 <= d < 3.8:
                img[x, y] = NERO_ALLOY
            elif d < 2.7:
                img[x, y] = GLASS
    # seam rivets on the body
    rivet(img, 3, 6)
    rivet(img, 12, 6)
    rivet(img, 7, 14)
    outline_alpha(img)
    # pull tab (drawn after the outline so it stays yellow)
    img[12, 3] = HAZ_Y
    img[13, 2] = HAZ_Y
    img[11, 4] = G_DARK
    return img


def tex_supply_beacon_lens(name):
    """layer1 (emissive): the lens; drawn white-grey and tinted REWARD gold by the item model."""
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 9)
            if d < 2.6:
                img[x, y] = GREY_LT if d < 1.6 else GREY_MD
    return img


def tex_reward_crate_token(name):
    img = Img(16, 16)
    # gold-rimmed chip, octagonal
    for y in range(2, 14):
        for x in range(2, 14):
            if abs(x - 7.5) + abs(y - 7.5) <= 8.5:
                img[x, y] = GOLD
    for y in range(4, 12):
        for x in range(4, 12):
            if abs(x - 7.5) + abs(y - 7.5) <= 6.5:
                img[x, y] = GLASS
    # highlights on the rim (top-left light)
    for x in range(5, 9):
        img[x, 3] = (240, 206, 112, 255)
    img[4, 4] = (240, 206, 112, 255)
    img[3, 5] = (240, 206, 112, 255)
    for x in range(8, 12):
        img[x, 12] = (170, 134, 52, 255)
    img[12, 10] = (170, 134, 52, 255)
    outline_alpha(img)
    return img


def tex_reward_crate_token_glyph(name):
    """layer1: the REWARD diamond, white-grey, tinted REWARD gold by the item definition."""
    img = Img(16, 16)
    for dy in range(-3, 4):
        w = 3 - abs(dy)
        for dx in range(-w, w + 1):
            img[8 + dx, 8 + dy] = GREY_LT
    img[8, 8] = WHITE
    img[7, 7] = WHITE
    for dy in range(-3, 4):
        w = 3 - abs(dy)
        img[8 + w, 8 + dy] = GREY_MD if dy > 0 else img[8 + w, 8 + dy]
    return img


def tex_salvage_plating(name):
    img, rng = Img(16, 16), rng_for(name)
    pts = [(2, 3), (12, 2), (14, 9), (9, 14), (3, 12)]
    # fill a rough polygon by scanline of its convex hull (hand-chosen torn outline)
    poly = [(2, 3), (12, 2), (14, 9), (9, 14), (3, 12)]
    for y in range(16):
        xs = []
        n = len(poly)
        for i in range(n):
            (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % n]
            if (y0 <= y < y1) or (y1 <= y < y0):
                xs.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            for x in range(int(math.ceil(xs[i])), int(xs[i + 1]) + 1):
                img[x, y] = rng.choices(STEEL_TONES, [8, 2, 2, 1])[0]
    # torn edge rust along the lower-right
    for x, y in ((13, 9), (12, 11), (11, 12), (10, 13), (9, 13), (14, 8)):
        img[x, y] = RUST
    rivet(img, 5, 5)
    outline_alpha(img)
    return img


def tex_salvage_conduit(name):
    img = Img(16, 16)
    hi = (102, 114, 124, 255)
    # a bent pipe: diagonal lower run, kink, then a horizontal upper run
    for i in range(6):
        rect(img, 2 + i, 11 - i, 5 + i, 14 - i, TRIM)
    rect(img, 8, 5, 15, 8, TRIM)
    for i in range(6):
        img[2 + i, 11 - i] = hi
    for x in range(8, 15):
        img[x, 5] = hi
    # kink collar
    rect(img, 7, 5, 9, 9, STEEL_DK)
    img[7, 5] = G_LIGHT
    # open ends: STEEL_DK rims with a dim core (the glow layer carries the light)
    rect(img, 2, 11, 4, 14, STEEL_DK)
    rect(img, 13, 5, 15, 8, STEEL_DK)
    img[3, 12] = (28, 72, 84, 255)
    img[14, 6] = (28, 72, 84, 255)
    outline_alpha(img)
    return img


def tex_salvage_conduit_glow(name):
    img = Img(16, 16)
    img[3, 12] = PLASMA
    img[14, 6] = PLASMA
    return img


def tex_salvage_core_fragment(name):
    img, rng = Img(16, 16), rng_for(name)
    poly = [(4, 2), (12, 4), (14, 10), (8, 14), (2, 9)]
    for y in range(16):
        xs = []
        n = len(poly)
        for i in range(n):
            (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % n]
            if (y0 <= y < y1) or (y1 <= y < y0):
                xs.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            for x in range(int(math.ceil(xs[i])), int(xs[i + 1]) + 1):
                img[x, y] = rng.choices(SLAG_TONES, [7, 3, 3, 1])[0]
    line(img, 5, 5, 9, 9, MAGMA)
    line(img, 9, 9, 12, 7, MAGMA)
    line(img, 9, 9, 8, 12, MAGMA)
    img[9, 9] = MAGMA_HOT
    img[8, 9] = MAGMA_HOT
    outline_alpha(img)
    return img


# ---------------- entities ----------------

def pod_painter(tier):
    band2 = tier >= 2
    gold = tier >= 3

    def paint(img, face, x0, y0, x1, y1, rng):
        plating(img, face, x0, y0, x1, y1, rng)
        w, h = x1 - x0, y1 - y0
        if face in ("north", "south", "east", "west") and h >= 12:
            # hull: horizontal TRIM seam + second NERO_ALLOY band on tier 2+, rivets at the corners
            for x in range(x0 + 1, x1 - 1):
                img[x, y0 + 4] = TRIM
                if band2:
                    img[x, y0 + h - 4] = NERO_ALLOY
            rivet(img, x0 + 2, y0 + 2)
            rivet(img, x1 - 4, y0 + 2)
            rivet(img, x0 + 2, y1 - 4)
            rivet(img, x1 - 4, y1 - 4)
            if gold:
                for x in range(x0 + 1, x1 - 1):
                    img[x, y0 + 1] = GOLD
        if face == "up" and w >= 12:
            # hull top: NERO_ALLOY ring where the dome seats
            for y in range(y0, y1):
                for x in range(x0, x1):
                    d = math.hypot(x + 0.5 - (x0 + w / 2), y + 0.5 - (y0 + h / 2))
                    if 4.8 <= d < 6.0:
                        img[x, y] = NERO_ALLOY
    return paint


def dome_painter(tier):
    def paint(img, face, x0, y0, x1, y1, rng):
        plating(img, face, x0, y0, x1, y1, rng)
        if face in ("north", "south", "east", "west"):
            for x in range(x0 + 1, x1 - 1):
                img[x, y1 - 2] = NERO_ALLOY
                if tier >= 3:
                    img[x, y0 + 1] = GOLD
        if face == "up":
            rect(img, x0 + 3, y0 + 3, x1 - 3, y1 - 3, GLASS)
    return paint


def hatch_painter(img, face, x0, y0, x1, y1, rng):
    plating(img, face, x0, y0, x1, y1, rng)
    if face == "north":
        hazard(img, x0 + 1, y0 + 1, x1 - 1, y0 + 3)
        hazard(img, x0 + 1, y1 - 3, x1 - 1, y1 - 1, phase=2)
        rect(img, x0 + 3, y0 + 4, x1 - 3, y1 - 4, GLASS)
        img[x0 + 3, y0 + 4] = PLASMA


def leg_painter(img, face, x0, y0, x1, y1, rng):
    noise(img, x0, y0, x1, y1, [STEEL_DK, TRIM, GRAPHITE_4, STEEL], rng, [8, 2, 1, 1])


def lens_painter(img, face, x0, y0, x1, y1, rng):
    rect(img, x0, y0, x1, y1, GLASS)
    if face == "up":
        rect(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, (28, 36, 44, 255))


def line_painter(img, face, x0, y0, x1, y1, rng):
    rect(img, x0, y0, x1, y1, CANOPY_DK)


def tex_supply_pod(tier):
    def build(name):
        img, rng = Img(64, 64), rng_for(name)
        paint_box(img, 0, 0, 14, 14, 14, pod_painter(tier), rng)      # hull
        paint_box(img, 0, 28, 10, 4, 10, dome_painter(tier), rng)      # dome
        paint_box(img, 40, 28, 8, 8, 1, hatch_painter, rng)            # hatch
        paint_box(img, 0, 42, 2, 4, 2, leg_painter, rng)               # leg (shared by all four)
        paint_box(img, 8, 42, 4, 2, 4, lens_painter, rng)              # beacon lens
        paint_box(img, 24, 42, 1, 14, 1, line_painter, rng)            # shroud line (shared by four)
        return img
    return build


def tex_supply_pod_glow(name):
    """Beacon lens only, white-grey; tinted by severity in the renderer."""
    img = Img(64, 64)
    for face, (x0, y0, x1, y1) in box_faces(8, 42, 4, 2, 4).items():
        rect(img, x0, y0, x1, y1, GREY_MD)
        if face == "up":
            rect(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, GREY_LT)
    return img


def tex_pod_beacon_glow(name):
    """4-frame 16x64 billboard sprite of the landed pod's beacon pulse (white-grey, severity-tinted)."""
    img = Img(16, 64)
    for f, lv in enumerate([0.4, 0.7, 1.0, 0.7]):
        oy = f * 16
        r_out = 2.5 + 4.0 * lv
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - (oy + 8) + oy)
                dd = math.hypot(x + 0.5 - 8, (y + 0.5) - 8)
                if dd < 2.0:
                    img[x, oy + y] = GREY_LT
                elif dd < r_out:
                    a = int(220 * (1 - (dd - 2.0) / (r_out - 2.0)) * lv)
                    img[x, oy + y] = (214, 220, 228, max(24, a))
    return img


def canopy_painter(tier):
    stripe = {1: None, 2: NERO_ALLOY, 3: GOLD}[tier]

    def paint(img, face, x0, y0, x1, y1, rng):
        noise(img, x0, y0, x1, y1, [CANOPY, CANOPY_SH, (232, 236, 242, 255), CANOPY_DK], rng, [9, 3, 2, 0.5])
        if face in ("up", "down"):
            # gore seams radiating from the centre corner (every gore's inner corner is the top-left)
            for i in range(0, x1 - x0, 4):
                img[x0 + i, y0 + i] = CANOPY_DK
            if stripe is not None:
                # 4-texel tier stripe band across the gore
                for i in range(6, 10):
                    for t in range(0, x1 - x0):
                        x, y = x0 + t, y0 + i + (t // 2) * 0
                        if y < y1:
                            img[x, y] = stripe
    return paint


def vent_painter(img, face, x0, y0, x1, y1, rng):
    rect(img, x0, y0, x1, y1, CANOPY_DK)
    if face in ("up", "down"):
        rect(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, (120, 128, 140, 255))


def tex_supply_parachute(tier):
    def build(name):
        img, rng = Img(64, 32), rng_for(name)
        paint_box(img, 0, 0, 16, 1, 16, canopy_painter(tier), rng)   # gore (shared by 4 gores)
        paint_box(img, 0, 17, 6, 1, 6, vent_painter, rng)            # vent (shared by 2)
        return img
    return build


def meteor_body(img, face, x0, y0, x1, y1, rng):
    noise(img, x0, y0, x1, y1, SLAG_TONES, rng, [7, 3, 3, 1])
    for _ in range(max(1, (x1 - x0) * (y1 - y0) // 10)):
        px, py = rng.randrange(x0 + 1, x1 - 1), rng.randrange(y0 + 1, y1 - 1)
        img[px, py] = CRUST if rng.random() < 0.5 else SLAG_DK


FISSURES = {
    # per box, per face: polylines in face-local coords; junction texels get MAGMA_HOT in a 2x2
    "core": {
        "north": [[(2, 3), (6, 7), (11, 5)], [(6, 7), (5, 11)]],
        "south": [[(1, 8), (7, 4), (12, 9)]],
        "east": [[(2, 2), (6, 6), (4, 10)]],
        "west": [[(3, 9), (7, 5), (11, 7)]],
        "up": [[(2, 10), (8, 6), (13, 9)], [(8, 6), (9, 2)]],
        "down": [[(4, 3), (9, 8)]],
    },
    "chunk_a": {"north": [[(1, 2), (5, 6)]], "up": [[(6, 1), (2, 5)]], "east": [[(2, 5), (6, 2)]]},
    "chunk_b": {"south": [[(2, 1), (4, 6)]], "west": [[(1, 6), (6, 2)]], "down": [[(1, 2), (6, 5)]]},
}


def draw_fissures(img, box_name, u, v, w, h, d, hot_at_junctions=True):
    faces = box_faces(u, v, w, h, d)
    for face, polys in FISSURES[box_name].items():
        x0, y0, x1, y1 = faces[face]
        for poly in polys:
            for (ax, ay), (bx, by) in zip(poly, poly[1:]):
                line(img, x0 + ax, y0 + ay, x0 + bx, y0 + by, MAGMA)
        if hot_at_junctions:
            for poly in polys:
                for (jx, jy) in poly[1:-1]:
                    for dx in (0, 1):
                        for dy in (0, 1):
                            if x0 + jx + dx < x1 - 1 and y0 + jy + dy < y1 - 1:
                                img[x0 + jx + dx, y0 + jy + dy] = MAGMA_HOT


def tex_meteor(name):
    img, rng = Img(64, 64), rng_for(name)
    paint_box(img, 0, 0, 14, 12, 16, meteor_body, rng)
    paint_box(img, 0, 28, 8, 8, 8, meteor_body, rng)
    paint_box(img, 32, 28, 8, 8, 8, meteor_body, rng)
    draw_fissures(img, "core", 0, 0, 14, 12, 16)
    draw_fissures(img, "chunk_a", 0, 28, 8, 8, 8)
    draw_fissures(img, "chunk_b", 32, 28, 8, 8, 8)
    return img


def tex_meteor_glow(name):
    img = Img(64, 64)
    draw_fissures(img, "core", 0, 0, 14, 12, 16)
    draw_fissures(img, "chunk_a", 0, 28, 8, 8, 8)
    draw_fissures(img, "chunk_b", 32, 28, 8, 8, 8)
    return img


def tex_meteor_heat(name):
    """16x64 vertical gradient MAGMA_HOT -> MAGMA -> transparent, UV-scrolled by the renderer."""
    img, rng = Img(16, 64), rng_for(name)
    for y in range(64):
        t = y / 63.0
        if t < 0.35:
            c = lerp(MAGMA_HOT, MAGMA, t / 0.35)
            a = 200
        else:
            c = MAGMA
            a = int(200 * (1 - (t - 0.35) / 0.65))
        for x in range(16):
            j = rng.random()
            aa = max(0, min(255, a + int((j - 0.5) * 40)))
            img[x, y] = (c[0], c[1], c[2], aa)
    # a few streaks of extra heat
    for x in (3, 9, 13):
        for y in range(0, 40):
            c = img[x, y]
            img[x, y] = (c[0], c[1], c[2], min(255, c[3] + 30))
    return img


def debris_plate(img, face, x0, y0, x1, y1, rng):
    plating(img, face, x0, y0, x1, y1, rng)
    w, h = x1 - x0, y1 - y0
    if face in ("up", "down") and w >= 10:
        # SCORCH burn gradient on the leading (left) edge, RUST on the trailing edge
        for y in range(y0, y1):
            for x in range(x0, x1):
                t = (x - x0) / max(1, w - 1)
                if t < 0.25 and rng.random() < 0.85:
                    img[x, y] = SCORCH
                elif t < 0.4 and rng.random() < 0.5:
                    img[x, y] = (40, 32, 30, 255)
                elif t > 0.85 and rng.random() < 0.5:
                    img[x, y] = RUST
        rivet(img, x1 - 3, y0 + 2)
        rivet(img, x1 - 3, y1 - 3)
    if face in ("north", "south"):
        for x in range(x0, x0 + max(1, w // 4)):
            img[x, y0] = SCORCH
            img[x, y1 - 1] = SCORCH


def debris_strut(img, face, x0, y0, x1, y1, rng):
    noise(img, x0, y0, x1, y1, [TRIM, (102, 114, 124, 255), STEEL_DK, RUST], rng, [8, 2, 2, 1])


def debris_pipe(img, face, x0, y0, x1, y1, rng):
    noise(img, x0, y0, x1, y1, [STEEL_DK, TRIM, GRAPHITE_4, RUST], rng, [8, 2, 1, 1])


def debris_conduit(img, face, x0, y0, x1, y1, rng):
    rect(img, x0, y0, x1, y1, STEEL_DK)
    if face in ("north", "south"):
        for x in range(x0, x1):
            img[x, y0] = (28, 72, 84, 255)


def tex_debris(name):
    img, rng = Img(64, 64), rng_for(name)
    paint_box(img, 0, 0, 16, 2, 10, debris_plate, rng)     # plate_a
    paint_box(img, 0, 12, 12, 2, 10, debris_plate, rng)    # plate_b
    paint_box(img, 0, 24, 2, 14, 2, debris_strut, rng)     # strut
    paint_box(img, 8, 24, 3, 3, 10, debris_pipe, rng)      # pipe
    paint_box(img, 34, 24, 2, 2, 4, debris_conduit, rng)   # conduit stub
    return img


def tex_debris_glow(name):
    img = Img(64, 64)
    for face, (x0, y0, x1, y1) in box_faces(34, 24, 2, 2, 4).items():
        if face in ("north", "south"):
            for x in range(x0, x1):
                img[x, y0] = PLASMA
    return img


# ---------------- particles ----------------

def tex_meteor_smoke(frame):
    def build(name):
        img, rng = Img(8, 8), rng_for(name)
        r = [3.6, 3.9, 3.4, 2.8][frame]
        c = lerp(G_LIGHT, TRIM, frame / 3.0)
        for y in range(8):
            for x in range(8):
                d = math.hypot(x + 0.5 - 4, y + 0.5 - 4) + rng.random() * 0.9
                if d < r:
                    a = int(230 * (1 - frame * 0.18) * (1 - d / (r + 0.5)) + 40)
                    img[x, y] = (c[0], c[1], c[2], max(0, min(255, a)))
        return img
    return build


def tex_meteor_ember(frame):
    def build(name):
        img = Img(8, 8)
        c = EMBER if frame == 0 else MAGMA
        rect(img, 2, 2, 6, 6, c)
        img[3, 3] = MAGMA_HOT
        img[4, 3] = MAGMA_HOT if frame == 0 else c
        img[3, 4] = MAGMA_HOT if frame == 0 else c
        img[2, 2] = (c[0], c[1], c[2], 120)
        img[5, 5] = (c[0], c[1], c[2], 120)
        img[5, 2] = (c[0], c[1], c[2], 160)
        img[2, 5] = (c[0], c[1], c[2], 160)
        return img
    return build


def tex_impact_flash(name):
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 3.0:
                img[x, y] = WHITE
            elif d < 5.5:
                img[x, y] = MAGMA_HOT
            elif d < 7.5:
                img[x, y] = (MAGMA_HOT[0], MAGMA_HOT[1], MAGMA_HOT[2], int(220 * (1 - (d - 5.5) / 2.0)))
    # eight rays
    for i in range(8):
        a = i * math.pi / 4
        for r in (6, 7):
            x, y = int(8 + math.cos(a) * r), int(8 + math.sin(a) * r)
            img[x, y] = MAGMA_HOT
    return img


def tex_beacon_ring(frame):
    def build(name):
        img = Img(8, 8)
        r_out, r_in = (3.9, 2.4) if frame == 0 else (3.3, 1.9)
        for y in range(8):
            for x in range(8):
                d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
                if r_in <= d < r_out:
                    img[x, y] = GREY_LT if frame == 0 else WHITE
        return img
    return build


def tex_reward_spark(name):
    img = Img(8, 8)
    for i in range(-2, 3):
        img[4 + i, 4] = GREY_LT
        img[4, 4 + i] = GREY_LT
    img[4, 4] = WHITE
    img[3, 3] = GREY_MD
    img[5, 5] = GREY_MD
    img[3, 5] = GREY_MD
    img[5, 3] = GREY_MD
    return img


def tex_storm_dust(frame):
    def build(name):
        img, rng = Img(8, 8), rng_for(name)
        for y in range(8):
            for x in range(8):
                d = math.hypot((x + 0.5 - 4) * 0.8, y + 0.5 - 4) + rng.random() * 1.2
                if d < 3.4 - frame * 0.25:
                    img[x, y] = (P["DUST"][0], P["DUST"][1], P["DUST"][2], 140 + rng.randrange(0, 90))
        return img
    return build


def tex_ion_spark(frame):
    def build(name):
        img = Img(8, 8)
        if frame == 0:
            line(img, 2, 1, 4, 4, WHITE)
            line(img, 4, 4, 3, 6, WHITE)
            img[5, 2] = GREY_LT
        else:
            line(img, 5, 1, 3, 4, WHITE)
            line(img, 3, 4, 5, 6, WHITE)
            img[2, 5] = GREY_LT
        return img
    return build


def tex_cryo_flake(frame):
    def build(name):
        img = Img(8, 8)
        for i in range(-2, 3):
            img[4 + i, 4] = WHITE
            img[4, 4 + i] = WHITE
        if frame % 2 == 0:
            img[2, 2] = GREY_LT
            img[6, 6] = GREY_LT
            img[2, 6] = GREY_LT
            img[6, 2] = GREY_LT
        else:
            img[3, 3] = GREY_LT
            img[5, 5] = GREY_LT
            img[3, 5] = GREY_LT
            img[5, 3] = GREY_LT
        if frame >= 2:
            img[4, 4] = GREY_MD
        return img
    return build


def tex_solar_flare(name):
    img = Img(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
            if d < 1.6:
                img[x, y] = WHITE
            elif d < 3.4:
                img[x, y] = (255, 255, 255, int(200 * (1 - (d - 1.6) / 1.8)))
    return img


# ---------------- war objective beacon (0.5.0, faction_war) ----------------
# The pylon is 2 blocks tall: a 12x14x12 chamfered plinth (block model), a 6x14x6 column and a
# 10x4x10 emitter head (both drawn by the block-entity renderer), a tintable faction band on the
# collar and a rotating 8-segment emitter ring. UV windows the models sample are noted per texture
# so the painting and the JSON stay in step (see tools/entity-geometry.md, "war_beacon BER").

def tex_war_beacon_base(name):
    """Plinth plate. Windows: sides (2,2)-(14,14), top (2,2)-(14,14), chamfer (3,0)-(13,2),
    feet (0,13)-(3,15), buttress (1,2)-(3,12), head underside (3,3)-(13,13)."""
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [10, 4, 2, 1])
    # chamfer strip (rows 0..1): lighter lip with a dark underside line
    rect(img, 0, 0, 16, 1, GRAPHITE_4)
    rect(img, 0, 1, 16, 2, G_DARK)
    # inner panel with top-left light bevel, seam cross in TRIM
    bevel(img, 2, 2, 14, 14, GRAPHITE_4, G_DARK)
    for y in range(3, 13):
        img[8, y] = TRIM
    for x in range(3, 13):
        img[x, 8] = TRIM
    img[8, 8] = G_DARK
    # four rivets in the quadrants
    for x, y in ((4, 4), (10, 4), (4, 10), (10, 10)):
        rivet(img, x, y)
    # a single NERO_ALLOY maker's tick low on the panel (Neroland-made, not emissive)
    img[7, 12] = NERO_ALLOY
    img[8, 12] = NERO_ALLOY
    # feet / skirt rows (13..15): darker, with a TRIM edge line
    rect(img, 0, 14, 16, 16, GRAPHITE)
    for x in range(0, 16):
        img[x, 13] = TRIM if x % 2 == 0 else GRAPHITE_2
    block_full_border(img)
    return img


def tex_war_beacon_column(name):
    """Column strip. Windows: column faces (5,0)-(11,16) sampled by y-range, cap faces (0,0)-(6,6)."""
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [10, 4, 2, 1])
    # flanks (used by the column's hidden cap faces and as filler): horizontal TRIM course + rivets
    for x in list(range(0, 5)) + list(range(11, 16)):
        img[x, 7] = TRIM
    rivet(img, 1, 2)
    rivet(img, 12, 11)
    # the 6-wide column face: dark edges, graphite body, a 2-wide NERO_ALLOY service groove
    for y in range(16):
        img[5, y] = G_DARK
        img[10, y] = G_DARK
        img[6, y] = GRAPHITE_4
        img[9, y] = GRAPHITE_2
        img[7, y] = NERO_ALLOY if 2 <= y < 14 else GRAPHITE_2
        img[8, y] = (30, 122, 114, 255) if 2 <= y < 14 else GRAPHITE_2
    # groove end caps and two TRIM collars across the column
    for y in (1, 14):
        img[7, y] = TRIM
        img[8, y] = TRIM
    for y in (5, 10):
        img[6, y] = TRIM
        img[9, y] = TRIM
    return img


def tex_war_beacon_head(name):
    """Emitter head. Windows: top (0,0)-(10,10) with the socket, sides (0,10)-(10,14); the underside
    samples war_beacon_base (3,3)-(13,13)."""
    img, rng = Img(16, 16), rng_for(name)
    noise(img, 0, 0, 16, 16, GRAPHITE_TONES, rng, [10, 4, 2, 1])
    # top face 10x10: border, bevel, TRIM ring, dark glass socket with a light rim
    border(img, 0, 0, 10, 10, G_DARK)
    bevel(img, 1, 1, 9, 9, GRAPHITE_4, G_DARK)
    for x in range(2, 8):
        img[x, 2] = TRIM
        img[x, 7] = TRIM
    for y in range(2, 8):
        img[2, y] = TRIM
        img[7, y] = TRIM
    disc(img, 5, 5, 2.6, G_LIGHT)
    disc(img, 5, 5, 1.9, GLASS)
    img[4, 4] = TRIM
    for x, y in ((1, 1), (8, 1), (1, 8), (8, 8)):
        img[x, y] = G_LIGHT
    # side strip 10x4 (rows 10..13): lit top edge, three vent ticks, dark bottom edge
    rect(img, 0, 10, 10, 11, GRAPHITE_4)
    rect(img, 0, 11, 10, 13, GRAPHITE_2)
    for x in (2, 5, 8):
        img[x, 11] = TRIM
        img[x, 12] = G_DARK
    rect(img, 0, 13, 10, 14, G_DARK)
    img[0, 10] = G_DARK
    img[9, 10] = G_DARK
    # filler (right column and bottom rows): a rivet and a TRIM course so no region is flat
    rivet(img, 12, 3)
    rivet(img, 13, 12)
    for y in range(0, 16):
        img[10, y] = G_DARK
    for x in range(0, 16):
        img[x, 15] = G_DARK
    return img


def tex_war_beacon_band(name):
    """White-grey tint mask for the faction band (tinted by the block colour handler; emissive).
    Window: collar sides (4,7)-(12,9). Every row pair carries the same motif so any 2-row window
    works: a WHITE core row with GREY_MD segment ticks over a GREY_LT shadow row."""
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            if y % 2 == 0:
                img[x, y] = GREY_MD if x % 4 == 0 else WHITE
            else:
                img[x, y] = (190, 198, 208, 255) if x % 4 == 0 else GREY_LT
    return img


def tex_war_beacon_ring(name):
    """6-frame 16x96 strip (frametime 2, interpolated), white: one emitter-ring segment plate.
    A horizontal bar (rows 6..9) whose bright core breathes 10->14 texels wide, with a scan dot
    travelling left to right so the renderer's yaw rotation reads as motion even up close."""
    img = Img(16, 96)
    core_w = [10, 12, 14, 14, 12, 10]
    dot_x = [2, 4, 7, 9, 12, 14]
    for f in range(6):
        oy = f * 16
        half = core_w[f] // 2
        x0, x1 = 8 - half, 8 + half
        # halo rows (5 and 10): faint
        for x in range(x0 - 1, x1 + 1):
            img[x, oy + 5] = (255, 255, 255, 70)
            img[x, oy + 10] = (255, 255, 255, 70)
        # body rows 6 and 9: mid, tapering at the ends
        for x in range(x0 - 1, x1 + 1):
            a = 150 if x0 <= x < x1 else 90
            img[x, oy + 6] = (255, 255, 255, a)
            img[x, oy + 9] = (255, 255, 255, a)
        # core rows 7 and 8: solid, with soft ends
        for x in range(x0 - 1, x1 + 1):
            edge = x < x0 or x >= x1
            img[x, oy + 7] = (255, 255, 255, 170 if edge else 255)
            img[x, oy + 8] = (255, 255, 255, 170 if edge else 240)
        # scan dot (2x2 brighter-than-white = full alpha over the core; a 1-texel glint above)
        d = dot_x[f]
        img[d, oy + 7] = WHITE
        img[d + 1, oy + 7] = WHITE
        img[d, oy + 8] = WHITE
        img[d + 1, oy + 8] = WHITE
        img[d, oy + 4] = (255, 255, 255, 110)
        img[d + 1, oy + 4] = (255, 255, 255, 110)
        # segment end studs (always-on anchor texels so the 8 segments read as separate plates)
        img[0, oy + 7] = (214, 220, 228, 200)
        img[0, oy + 8] = (214, 220, 228, 200)
        img[15, oy + 7] = (214, 220, 228, 200)
        img[15, oy + 8] = (214, 220, 228, 200)
    return img


# ---------------- manifest ----------------

# name -> (relative path under textures/, painter, expected (w, h), mcmeta or None)
MANIFEST = {}


def add(path, painter, size, mcmeta=None):
    MANIFEST[path] = (painter, size, mcmeta)


# blocks
add("block/meteor_slag.png", tex_meteor_slag, (16, 16))
add("block/cooled_crust.png", tex_cooled_crust, (16, 16))
add("block/embedded_meteor_ore.png", tex_embedded_meteor_ore, (16, 16))
add("block/crater_pocket_side.png", tex_crater_pocket_side, (16, 16))
add("block/crater_pocket_top.png", tex_crater_pocket_top, (16, 16))
add("block/crater_pocket_glow.png", tex_crater_pocket_glow, (16, 64),
    {"animation": {"frametime": 4, "interpolate": True}})
add("block/hull_plating.png", tex_hull_plating, (16, 16))
add("block/scorched_panel.png", tex_scorched_panel, (16, 16))
add("block/bent_strut.png", tex_bent_strut, (16, 16))
add("block/salvage_crate_side.png", tex_salvage_crate_side, (16, 16))
add("block/salvage_crate_front.png", tex_salvage_crate_front, (16, 16))
add("block/salvage_crate_lid.png", tex_salvage_crate_lid, (16, 16))
add("block/salvage_crate_bottom.png", tex_salvage_crate_bottom, (16, 16))
add("block/event_console_side.png", tex_event_console_side, (16, 16))
add("block/event_console_top.png", tex_event_console_top, (16, 16))
add("block/event_console_back.png", tex_event_console_back, (16, 16))
add("block/event_console_front.png", tex_event_console_front, (16, 16))
add("block/event_console_keys.png", tex_event_console_keys, (16, 16))
add("block/event_console_strip.png", tex_event_console_strip, (16, 16))
add("block/event_console_screen.png", tex_event_console_screen, (16, 128), {"animation": {"frametime": 3}})
add("block/event_console_screen_active.png", tex_event_console_screen_active, (16, 128),
    {"animation": {"frametime": 2}})
# gui
add("gui/event_glyphs.png", tex_event_glyphs, (64, 16))
# items
add("item/supply_beacon.png", tex_supply_beacon, (16, 16))
add("item/supply_beacon_lens.png", tex_supply_beacon_lens, (16, 16))
add("item/reward_crate_token.png", tex_reward_crate_token, (16, 16))
add("item/reward_crate_token_glyph.png", tex_reward_crate_token_glyph, (16, 16))
add("item/salvage_plating.png", tex_salvage_plating, (16, 16))
add("item/salvage_conduit.png", tex_salvage_conduit, (16, 16))
add("item/salvage_conduit_glow.png", tex_salvage_conduit_glow, (16, 16))
add("item/salvage_core_fragment.png", tex_salvage_core_fragment, (16, 16))
# entities
for t in (1, 2, 3):
    add("entity/supply_pod_t%d.png" % t, tex_supply_pod(t), (64, 64))
    add("entity/supply_parachute_t%d.png" % t, tex_supply_parachute(t), (64, 32))
add("entity/supply_pod_glow.png", tex_supply_pod_glow, (64, 64))
# 4-frame strip; entity textures are not atlas sprites, so SupplyPodRenderer picks the frame itself (no .mcmeta).
add("entity/pod_beacon_glow.png", tex_pod_beacon_glow, (16, 64))
add("entity/meteor.png", tex_meteor, (64, 64))
add("entity/meteor_glow.png", tex_meteor_glow, (64, 64))
add("entity/meteor_heat.png", tex_meteor_heat, (16, 64))
add("entity/debris.png", tex_debris, (64, 64))
add("entity/debris_glow.png", tex_debris_glow, (64, 64))
# particles (one file per frame: that is what particles/<id>.json lists)
for f in range(4):
    add("particle/meteor_smoke_%d.png" % f, tex_meteor_smoke(f), (8, 8))
for f in range(2):
    add("particle/meteor_ember_%d.png" % f, tex_meteor_ember(f), (8, 8))
add("particle/impact_flash.png", tex_impact_flash, (16, 16))
for f in range(2):
    add("particle/beacon_ring_%d.png" % f, tex_beacon_ring(f), (8, 8))
add("particle/reward_spark.png", tex_reward_spark, (8, 8))
for f in range(4):
    add("particle/storm_dust_%d.png" % f, tex_storm_dust(f), (8, 8))
for f in range(2):
    add("particle/ion_spark_%d.png" % f, tex_ion_spark(f), (8, 8))
for f in range(4):
    add("particle/cryo_flake_%d.png" % f, tex_cryo_flake(f), (8, 8))
add("particle/solar_flare.png", tex_solar_flare, (8, 8))
# war objective beacon (0.5.0): block-atlas textures; the BER samples the column, head and ring
# sprites from the block atlas (they are stitched because the models below reference them)
add("block/war_beacon_base.png", tex_war_beacon_base, (16, 16))
add("block/war_beacon_column.png", tex_war_beacon_column, (16, 16))
add("block/war_beacon_head.png", tex_war_beacon_head, (16, 16))
add("block/war_beacon_band.png", tex_war_beacon_band, (16, 16))
add("block/war_beacon_ring.png", tex_war_beacon_ring, (16, 96), {"animation": {"frametime": 2, "interpolate": True}})

# Anti-placeholder floors. Particle frames, glow layers and white-grey tint masks are legitimately
# small and sparse; every other texture must clear both floors. A 16x16 RGBA PNG compresses to
# roughly 120-300 bytes, so the byte floor is per size class, and the colour count is the real test.
MIN_COLOURS = 5
SPARSE_OK = ("_glow", "_lens", "_glyph", "particle/", "war_beacon_band", "war_beacon_ring")


def min_bytes(size):
    w, h = size
    return 100 if w * h <= 256 else 250


def paint(force=False, only=None):
    written, skipped = [], []
    for rel, (painter, size, mcmeta) in MANIFEST.items():
        if only and rel not in only and os.path.basename(rel) not in only:
            continue
        path = os.path.join(TEX_ROOT, rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if os.path.exists(path) and not force:
            skipped.append(rel)
            continue
        img = painter(rel)
        assert (img.w, img.h) == size, "%s painted %dx%d, expected %dx%d" % (rel, img.w, img.h, size[0], size[1])
        img.save(path)
        if mcmeta is not None:
            with open(path + ".mcmeta", "w", newline="\n") as f:
                json.dump(mcmeta, f, indent=2)
                f.write("\n")
        written.append(rel)
    return written, skipped


def check():
    bad = 0
    for rel, (painter, size, mcmeta) in MANIFEST.items():
        path = os.path.join(TEX_ROOT, rel.replace("/", os.sep))
        if not os.path.exists(path):
            print("MISSING  %s" % rel)
            bad += 1
            continue
        info = png_info(path)
        if info is None or (info[0], info[1]) != size:
            print("SIZE     %s: %s, expected %s" % (rel, info, size))
            bad += 1
        sparse = any(tag in rel for tag in SPARSE_OK)
        if info[2] < (2 if sparse else MIN_COLOURS):
            print("FLAT     %s: %d colours" % (rel, info[2]))
            bad += 1
        nbytes = os.path.getsize(path)
        if not sparse and nbytes < min_bytes(size):
            print("SMALL    %s: %d bytes" % (rel, nbytes))
            bad += 1
        if mcmeta is not None and not os.path.exists(path + ".mcmeta"):
            print("MCMETA   %s has no .mcmeta" % rel)
            bad += 1
    print("checked %d textures, %d problems" % (len(MANIFEST), bad))
    return bad


def main(argv):
    if "--list" in argv:
        for rel in MANIFEST:
            print(rel)
        return 0
    if "--check" in argv:
        return 1 if check() else 0
    only = None
    if "--only" in argv:
        only = set(argv[argv.index("--only") + 1].split(","))
    written, skipped = paint(force="--force" in argv, only=only)
    for rel in written:
        print("wrote    %s" % rel)
    for rel in skipped:
        print("kept     %s" % rel)
    print("%d written, %d kept (existing; use --force to replace)" % (len(written), len(skipped)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
