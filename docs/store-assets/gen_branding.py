#!/usr/bin/env python3
"""ShiftAlarm branding generator: icons, adaptive layers, feature graphic, SVG."""
import math, os, zipfile
from PIL import Image, ImageDraw, ImageFont

BG = (24, 34, 46, 255)        # #18222E deep night slate
ORANGE = (255, 138, 30, 255)  # #FF8A1E safety orange (work)
STEEL = (70, 88, 108, 255)    # #46586C muted steel (rest)
WHITE = (245, 247, 250, 255)  # #F5F7FA hands
SUB = (159, 176, 194, 255)    # #9FB0C2 secondary text

SS = 4  # supersampling
OUT = "/home/claude/branding"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

GAP = 7.0            # deg between ring segments
SEG_COLORS = [ORANGE, ORANGE, STEEL, STEEL, ORANGE, ORANGE, STEEL, STEEL]


def rline(d, p1, p2, w, fill):
    d.line([p1, p2], fill=fill, width=int(round(w)))
    r = w / 2
    for p in (p1, p2):
        d.ellipse([p[0]-r, p[1]-r, p[0]+r, p[1]+r], fill=fill)


def draw_mark(d, cx, cy, R, bells=True, mono=False):
    """R = outer ring radius, px (already supersampled)."""
    t = 0.235 * R
    col_work = WHITE if mono else ORANGE
    col_rest = (255, 255, 255, 115) if mono else STEEL
    col_hand = WHITE
    bbox = [cx-R, cy-R, cx+R, cy+R]
    for i in range(8):
        a0 = -90 + i*45 + GAP/2
        a1 = -90 + (i+1)*45 - GAP/2
        col = col_work if SEG_COLORS[i] == ORANGE else col_rest
        d.arc(bbox, a0, a1, fill=col, width=int(round(t)))
    if bells:
        rb = 0.235 * R
        dist = 1.30 * R
        for ang in (-90-27, -90+27):
            bx = cx + dist*math.cos(math.radians(ang))
            by = cy + dist*math.sin(math.radians(ang))
            d.ellipse([bx-rb, by-rb, bx+rb, by+rb], fill=col_work)
        sr = 0.075 * R
        sx, sy = cx, cy - 1.44*R
        d.ellipse([sx-sr, sy-sr, sx+sr, sy+sr], fill=col_hand)
    # hands: 7:00 (minute up, hour to 7)
    rline(d, (cx, cy), (cx, cy - 0.62*R), 0.13*R, col_hand)
    ha = math.radians(210 - 90)
    rline(d, (cx, cy), (cx + 0.44*R*math.cos(ha), cy + 0.44*R*math.sin(ha)),
          0.15*R, col_hand)
    hub = 0.155 * R
    d.ellipse([cx-hub, cy-hub, cx+hub, cy+hub], fill=col_hand)
    core = 0.075 * R
    d.ellipse([cx-core, cy-core, cx+core, cy+core],
              fill=(255, 255, 255, 0) if mono else ORANGE)


def render_icon(size, bells, transparent, R_factor, cy_factor=0.5, mono=False):
    W = size * SS
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0) if transparent else BG)
    d = ImageDraw.Draw(img)
    draw_mark(d, W/2, W*cy_factor, W*R_factor, bells=bells, mono=mono)
    return img.resize((size, size), Image.LANCZOS)


def fit_font(d, text, path, start, max_w):
    size = start
    while size > 10:
        f = ImageFont.truetype(path, size)
        if d.textlength(text, font=f) <= max_w:
            return f
        size -= 2
    return ImageFont.truetype(path, 10)


def render_feature():
    W, H = 1024*SS, 500*SS
    img = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(img)
    draw_mark(d, 235*SS, 268*SS, 152*SS, bells=True)
    x = 455*SS
    max_w = (1024-455-42)*SS
    f1 = ImageFont.truetype(FONT_BOLD, 92*SS)
    d.text((x, 118*SS), "Будильник", font=f1, fill=WHITE)
    d.text((x, 218*SS), "работяги", font=f1, fill=WHITE)
    d.rectangle([x, 342*SS, x+128*SS, 348*SS], fill=ORANGE)
    sub = "Смены 2/2, сутки/трое, ночные — не проспишь"
    f2 = fit_font(d, sub, FONT_REG, 30*SS, max_w)
    d.text((x, 372*SS), sub, font=f2, fill=SUB)
    return img.resize((1024, 500), Image.LANCZOS)


def svg_arc(cx, cy, r, a0, a1, w, color):
    x0 = cx + r*math.cos(math.radians(a0)); y0 = cy + r*math.sin(math.radians(a0))
    x1 = cx + r*math.cos(math.radians(a1)); y1 = cy + r*math.sin(math.radians(a1))
    return (f'<path d="M {x0:.1f} {y0:.1f} A {r:.1f} {r:.1f} 0 0 1 '
            f'{x1:.1f} {y1:.1f}" fill="none" stroke="{color}" '
            f'stroke-width="{w:.1f}"/>')


def hexc(c):
    return "#%02X%02X%02X" % c[:3]


def gen_svg(path, size, bells, transparent, R_factor, cy_factor=0.5):
    W = size
    cx, cy, R = W/2, W*cy_factor, W*R_factor
    t = 0.235*R
    rmid = R - t/2
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {W}">']
    if not transparent:
        parts.append(f'<rect width="{W}" height="{W}" fill="{hexc(BG)}"/>')
    for i in range(8):
        a0 = -90 + i*45 + GAP/2
        a1 = -90 + (i+1)*45 - GAP/2
        parts.append(svg_arc(cx, cy, rmid, a0, a1, t, hexc(SEG_COLORS[i])))
    if bells:
        rb, dist = 0.235*R, 1.30*R
        for ang in (-117, -63):
            bx = cx + dist*math.cos(math.radians(ang))
            by = cy + dist*math.sin(math.radians(ang))
            parts.append(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="{rb:.1f}" '
                         f'fill="{hexc(ORANGE)}"/>')
        parts.append(f'<circle cx="{cx:.1f}" cy="{cy-1.44*R:.1f}" '
                     f'r="{0.075*R:.1f}" fill="{hexc(WHITE)}"/>')
    ha = math.radians(120)
    hx, hy = cx + 0.44*R*math.cos(ha), cy + 0.44*R*math.sin(ha)
    parts.append(f'<g stroke="{hexc(WHITE)}" stroke-linecap="round">'
                 f'<line x1="{cx}" y1="{cy}" x2="{cx}" y2="{cy-0.62*R:.1f}" '
                 f'stroke-width="{0.13*R:.1f}"/>'
                 f'<line x1="{cx}" y1="{cy}" x2="{hx:.1f}" y2="{hy:.1f}" '
                 f'stroke-width="{0.15*R:.1f}"/></g>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="{0.155*R:.1f}" '
                 f'fill="{hexc(WHITE)}"/>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="{0.075*R:.1f}" '
                 f'fill="{hexc(ORANGE)}"/>')
    parts.append('</svg>')
    with open(path, "w") as f:
        f.write("\n".join(parts))


def main():
    os.makedirs(f"{OUT}/adaptive", exist_ok=True)
    render_icon(512, True, False, 0.315, 0.545).convert("RGB").save(
        f"{OUT}/icon_512.png")
    render_icon(1024, True, False, 0.315, 0.545).convert("RGB").save(
        f"{OUT}/icon_1024.png")
    render_icon(432, False, True, 0.285).save(
        f"{OUT}/adaptive/ic_launcher_foreground.png")
    render_icon(432, False, True, 0.285, mono=True).save(
        f"{OUT}/adaptive/ic_launcher_monochrome.png")
    render_feature().convert("RGB").save(f"{OUT}/feature_graphic_1024x500.png")
    gen_svg(f"{OUT}/logo.svg", 1024, True, False, 0.315, 0.545)
    gen_svg(f"{OUT}/adaptive/ic_launcher_foreground.svg", 432, False, True, 0.285)
    print("done:", os.listdir(OUT), os.listdir(f"{OUT}/adaptive"))


if __name__ == "__main__":
    main()
