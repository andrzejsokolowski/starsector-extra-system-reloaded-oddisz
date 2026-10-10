"""Draws the mod's icons: one per augment, the hull mod badge and the LunaLib settings icon.

Run from the project root with Pillow installed:  python tools/make_icons.py
Everything is drawn from scratch, so the mod ships no borrowed art.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

SS = 4  # supersampling factor


def rgba(hex_color, alpha=255):
    hex_color = hex_color.lstrip('#')
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def polygon(cx, cy, r, sides, rotation=0.0):
    return [(cx + r * math.cos(rotation + 2 * math.pi * i / sides),
             cy + r * math.sin(rotation + 2 * math.pi * i / sides)) for i in range(sides)]


def casing(size, accent):
    """A bevelled octagonal device plate with a glowing core."""
    s = size * SS
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    c = s / 2
    # drop shadow
    shadow = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).polygon(polygon(c + s * 0.02, c + s * 0.03, s * 0.44, 8, math.pi / 8), fill=(0, 0, 0, 150))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(s * 0.025)))
    d = ImageDraw.Draw(img)
    # outer rim and body
    d.polygon(polygon(c, c, s * 0.44, 8, math.pi / 8), fill=rgba('#3a4048'))
    d.polygon(polygon(c, c, s * 0.40, 8, math.pi / 8), fill=rgba('#20252b'))
    # bevel highlight on the upper left
    hl = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.polygon(polygon(c, c, s * 0.44, 8, math.pi / 8), outline=rgba('#9aa6b2', 200), width=int(s * 0.012))
    mask = Image.new('L', (s, s), 0)
    ImageDraw.Draw(mask).polygon([(0, 0), (s, 0), (0, s)], fill=255)
    img.paste(hl, (0, 0), Image.composite(hl, Image.new('RGBA', (s, s)), mask).split()[3])
    # core glow
    glow = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([c - s * 0.26, c - s * 0.26, c + s * 0.26, c + s * 0.26], fill=rgba(accent, 120))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(s * 0.07)))
    d = ImageDraw.Draw(img)
    d.ellipse([c - s * 0.30, c - s * 0.30, c + s * 0.30, c + s * 0.30], outline=rgba('#5a6470'), width=int(s * 0.012))
    # rivets
    for i in range(8):
        a = math.pi / 8 + 2 * math.pi * i / 8
        x, y = c + s * 0.36 * math.cos(a), c + s * 0.36 * math.sin(a)
        r = s * 0.012
        d.ellipse([x - r, y - r, x + r, y + r], fill=rgba('#7d8894'))
    return img


def glowing(img, draw_fn, color, blur):
    """Draws a glyph twice: a blurred halo, then the crisp shape on top."""
    s = img.size[0]
    halo = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(halo), rgba(color, 255), s)
    img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(s * blur)))
    crisp = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(crisp), rgba('#ffffff', 235), s)
    tint = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(tint), rgba(color, 150), s)
    return Image.alpha_composite(Image.alpha_composite(img, crisp), tint)


def w(s, f):
    return max(1, int(s * f))


# ------------------------------------------------------------------ glyphs

def g_overload(d, col, s):
    c = s / 2
    d.arc([c - s * .2, c - s * .2, c + s * .2, c + s * .2], 200, 340, fill=col, width=w(s, .035))
    d.polygon([(c + s * .03, c - s * .17), (c - s * .08, c + s * .02), (c, c + s * .02),
               (c - s * .04, c + s * .18), (c + s * .09, c - s * .03), (c + s * .01, c - s * .03)], fill=col)


def g_nanolattice(d, col, s):
    c = s / 2
    r = s * .065
    for dx, dy in [(0, 0), (1.73, 0), (-1.73, 0), (.87, 1.5), (-.87, 1.5), (.87, -1.5), (-.87, -1.5)]:
        d.polygon(polygon(c + dx * r, c + dy * r, r * .95, 6, math.pi / 6), outline=col, width=w(s, .014))


def g_cryo(d, col, s):
    c = s / 2
    for i in range(6):
        a = math.pi / 6 * 2 * i
        x, y = c + s * .19 * math.cos(a), c + s * .19 * math.sin(a)
        d.line([(c, c), (x, y)], fill=col, width=w(s, .028))
        for side in (-1, 1):
            b = a + side * math.pi / 4
            mx, my = c + s * .11 * math.cos(a), c + s * .11 * math.sin(a)
            d.line([(mx, my), (mx + s * .06 * math.cos(b), my + s * .06 * math.sin(b))], fill=col, width=w(s, .02))


def g_temporal(d, col, s):
    c = s / 2
    d.polygon([(c - s * .12, c - s * .17), (c + s * .12, c - s * .17), (c, c)], outline=col, width=w(s, .022))
    d.polygon([(c - s * .12, c + s * .17), (c + s * .12, c + s * .17), (c, c)], fill=col)
    d.line([(c - s * .15, c - s * .17), (c + s * .15, c - s * .17)], fill=col, width=w(s, .03))
    d.line([(c - s * .15, c + s * .17), (c + s * .15, c + s * .17)], fill=col, width=w(s, .03))


def g_phase(d, col, s):
    c = s / 2
    for i, r in enumerate((.19, .13, .07)):
        d.arc([c - s * r, c - s * r, c + s * r, c + s * r], 30 + i * 60, 330 + i * 60, fill=col, width=w(s, .022))


def g_pd(d, col, s):
    c = s / 2
    d.ellipse([c - s * .15, c - s * .15, c + s * .15, c + s * .15], outline=col, width=w(s, .025))
    for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
        d.line([(c + dx * s * .07, c + dy * s * .07), (c + dx * s * .21, c + dy * s * .21)], fill=col, width=w(s, .025))
    d.ellipse([c - s * .025, c - s * .025, c + s * .025, c + s * .025], fill=col)


def g_hangar(d, col, s):
    """Three delta-wing fighters in a V formation."""
    c = s / 2

    def fighter(x, y, r):
        d.polygon([(x, y - r), (x + r * .8, y + r * .7), (x, y + r * .35), (x - r * .8, y + r * .7)], fill=col)

    fighter(c, c - s * .07, s * .09)
    fighter(c - s * .13, c + s * .08, s * .065)
    fighter(c + s * .13, c + s * .08, s * .065)


def g_missile(d, col, s):
    c = s / 2
    body = [(c + s * .19, c - s * .19), (c + s * .1, c - s * .19), (c - s * .12, c + s * .03),
            (c - s * .03, c + s * .12), (c + s * .19, c - s * .1)]
    d.polygon(body, fill=col)
    d.polygon([(c - s * .12, c + s * .03), (c - s * .2, c + s * .04), (c - s * .1, c + s * .1)], fill=col)
    d.polygon([(c - s * .03, c + s * .12), (c - s * .04, c + s * .2), (c - s * .1, c + s * .1)], fill=col)


def g_vent(d, col, s):
    c = s / 2
    for k, off in enumerate((-.09, 0, .09)):
        y = c + s * off
        d.line([(c - s * .18, y), (c + s * .1, y)], fill=col, width=w(s, .022))
    d.polygon([(c + s * .1, c - s * .14), (c + s * .21, c), (c + s * .1, c + s * .14)], fill=col)


def g_range(d, col, s):
    c = s / 2
    d.ellipse([c - s * .04, c - s * .04, c + s * .04, c + s * .04], fill=col)
    for r in (.11, .18):
        d.arc([c - s * r, c - s * r, c + s * r, c + s * r], -60, 60, fill=col, width=w(s, .022))
        d.arc([c - s * r, c - s * r, c + s * r, c + s * r], 120, 240, fill=col, width=w(s, .022))


AUGMENTS = [
    ('esro_overload_damper', '#58c8ff', g_overload),
    ('esro_nanolattice', '#5aff8c', g_nanolattice),
    ('esro_cryo_exchanger', '#a8e8ff', g_cryo),
    ('esro_temporal_capacitor', '#ffc94a', g_temporal),
    ('esro_phase_coil', '#c27cff', g_phase),
    ('esro_pd_integrator', '#ff6a4a', g_pd),
    ('esro_hangar_forge', '#ffa63a', g_hangar),
    ('esro_missile_autoforge', '#ff8c3a', g_missile),
    ('esro_vent_thrusters', '#7ab4ff', g_vent),
    ('esro_range_equalizer', '#4ae0d0', g_range),
]


def emblem(d, col, s):
    """The mod's own mark: three stacked upgrade chevrons over a ring."""
    c = s / 2
    d.arc([c - s * .2, c - s * .2, c + s * .2, c + s * .2], 0, 360, fill=col, width=w(s, .03))
    for k in range(3):
        y = c + s * (.08 - k * .08)
        d.line([(c - s * .11, y + s * .05), (c, y - s * .03), (c + s * .11, y + s * .05)], fill=col, width=w(s, .035))


def render(size, accent, glyph, out):
    img = casing(size, accent)
    img = glowing(img, glyph, accent, 0.02)
    img = img.resize((size, size), Image.LANCZOS)
    if os.path.dirname(out):
        os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)


def main():
    for item_id, color, glyph in AUGMENTS:
        render(80, color, glyph, 'graphics/icons/cargo/%s.png' % item_id)
    render(128, '#58c8ff', emblem, 'graphics/icons/esr_oddisz_icon.png')
    render(40, '#58c8ff', emblem, 'graphics/hullmods/esro_extra_systems.png')
    # For the forum post; not part of the mod download.
    render(256, '#58c8ff', emblem, 'ExtraSystemReloadedIconBig.png')


if __name__ == '__main__':
    main()
