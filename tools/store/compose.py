"""Store screenshots (1080x1920, captioned) + 1024x500 feature graphic for Postmory."""
import math, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT, SHOTS, SEED, OUT = sys.argv[1:5]
PAPER, WHITE, INK, RED, COBALT, KRAFT = "#F6F0E6", "#FFFEFB", "#18223F", "#D7362B", "#2848C8", "#E3CFAE"
fr = lambda s: ImageFont.truetype(f"{FONT}/fraunces_semibold_italic.ttf", s)
cav = lambda s: ImageFont.truetype(f"{FONT}/caveat_semibold.ttf", s)
jak = lambda s: ImageFont.truetype(f"{FONT}/plus_jakarta_sans_bold.ttf", s)


def stripe(img, y, h, pitch=28):
    d = ImageDraw.Draw(img)
    cols = [RED, WHITE, COBALT, WHITE]
    W = img.width
    for i in range(-h // pitch - 2, W // pitch + 4):
        x = i * pitch
        d.polygon([(x, y + h), (x + pitch, y + h), (x + pitch + h, y), (x + h, y)], fill=cols[i % 4])


def grain(img, amount=10):
    n = Image.effect_noise(img.size, 40).convert("L").point(lambda v: 128 + (v - 128) * amount // 40)
    layer = Image.merge("RGB", (n, n, n))
    return Image.blend(img.convert("RGB"), Image.composite(img.convert("RGB"), layer, Image.new("L", img.size, 235)), .5)


def shadowed(base, im, xy, radius=36, blur=40, tint=(70, 45, 15), alpha=70, off=(0, 24)):
    sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
    mask = im.split()[3] if im.mode == "RGBA" else Image.new("L", im.size, 255)
    a = mask.point(lambda v: v * alpha // 255)
    sh.paste((*tint, 255), (xy[0] + off[0], xy[1] + off[1]), a)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(sh)
    base.alpha_composite(im, xy)


def phone(path, height):
    s = Image.open(path).convert("RGBA")
    w = round(s.width * height / s.height)
    s = s.resize((w, height), Image.LANCZOS)
    r = 44
    m = Image.new("L", (w * 3, height * 3), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * 3 - 1, height * 3 - 1], r * 3, fill=255)
    s.putalpha(m.resize((w, height), Image.LANCZOS))
    # thin device bezel
    b = 14
    dev = Image.new("RGBA", (w + 2 * b, height + 2 * b), (0, 0, 0, 0))
    m2 = Image.new("L", (dev.width * 3, dev.height * 3), 0)
    ImageDraw.Draw(m2).rounded_rectangle([0, 0, dev.width * 3 - 1, dev.height * 3 - 1], (r + b) * 3, fill=255)
    dev.paste(INK, (0, 0), m2.resize(dev.size, Image.LANCZOS))
    dev.alpha_composite(s, (b, b))
    return dev


def wrap_center(d, text, font, y, W, fill, gap=8):
    for line in text.split("\n"):
        tw = d.textlength(line, font=font)
        d.text(((W - tw) / 2, y), line, font=font, fill=fill)
        y += font.size + gap
    return y


SHOTS_SPEC = [
    ("raw-home", "Turn every day\ninto a stamp", "one photo, one stamp, one day", PAPER),
    ("raw-import", "Frame it like\na real stamp", "portrait · landscape · square · round", INK),
    ("raw-stamped", "Postmarked with\nwhere and when", "your city and the date, automatically", COBALT),
    ("raw-albumpage", "Albums that feel\nlike paper", "kraft pages, washi tape, handwritten dates", PAPER),
    ("raw-calendar-oct", "Keep your\nstreak going", "a calendar that fills up with stamps", KRAFT),
    ("raw-map", "Every place\nyou've stamped", "a world map of your trips, offline", PAPER),
    ("raw-share", "Send it\nby airmail", "as an envelope, postcard or card", COBALT),
    ("raw-collections", "Trips, pets\nand everyday", "free: your photos, full quality, no forced ads", PAPER),
]

for i, (name, title, sub, bg) in enumerate(SHOTS_SPEC, 1):
    W, H = 1080, 1920
    img = Image.new("RGBA", (W, H), bg)
    if bg in (COBALT, KRAFT):
        img = grain(img).convert("RGBA")
    stripe(img, 0, 22)
    dark = bg in (COBALT, INK)
    fg = WHITE if dark else INK
    d = ImageDraw.Draw(img)
    y = wrap_center(d, title, fr(92), 96, W, fg, gap=4)
    subc = (255, 254, 251, 210) if dark else (24, 34, 63, 190)
    wrap_center(d, sub, cav(50), y + 14, W, subc)
    ph = phone(f"{SHOTS}/{name}.png", 1380)
    tint = (10, 20, 70) if dark else (70, 45, 15)
    shadowed(img, ph, ((W - ph.width) // 2, 470), tint=tint, alpha=110 if dark else 80)
    img.convert("RGB").save(f"{OUT}/screenshot-{i}-{name[4:]}.png")

# ---------- feature graphic 1024x500 ----------
W, H = 1024, 500
f = grain(Image.new("RGB", (W, H), COBALT), 14).convert("RGBA")
stripe(f, 0, 16, 24)
stripe(f, H - 16, 16, 24)
d = ImageDraw.Draw(f)
d.text((64, 150), "Postmory", font=fr(112), fill=WHITE)
d.text((70, 290), "Turn today into a stamp", font=jak(30), fill=(255, 254, 251, 220))
d.text((72, 340), "postmarked with where and when", font=cav(36), fill=(255, 254, 251, 190))


def postmark(city, date, size=190):
    S = 3
    pm = Image.new("RGBA", (size * 2 * S, size * S), (0, 0, 0, 0))
    p = ImageDraw.Draw(pm)
    c = (215, 54, 43, 225)
    R = size * S // 2 - 6 * S
    cx, cy = size * S // 2, size * S // 2
    p.ellipse([cx - R, cy - R, cx + R, cy + R], outline=c, width=5 * S)
    r2 = R - 13 * S
    p.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], outline=c, width=3 * S)
    ft = jak(int(size * .17) * S)
    tw = p.textlength(city, font=ft)
    p.text((cx - tw / 2, cy - ft.size * .95), city, font=ft, fill=c)
    fd = jak(int(size * .1) * S)
    tw = p.textlength(date, font=fd)
    p.text((cx - tw / 2, cy + fd.size * .4), date, font=fd, fill=c)
    for k in range(4):
        yy = cy - R * .45 + k * R * .3
        pts = [(cx + R * .9 + t * S, yy + math.sin(t / 14) * 7 * S) for t in range(0, int(size * 1.0))]
        p.line(pts, fill=c, width=4 * S)
    return pm.resize((size * 2, size), Image.LANCZOS)


def stamp(sid, h, rot):
    s = Image.open(f"{SEED}/out_{sid}.png").convert("RGBA")
    s = s.resize((round(s.width * h / s.height), h), Image.LANCZOS)
    return s.rotate(rot, Image.BICUBIC, expand=True)


navy = (8, 18, 70)
for sid, h, rot, xy in [
    ("119a72d1-74c9-4f6a-8c01-1cdd9474031b", 180, 7, (545, 52)),
    ("6513270e-269e-4d37-b2a7-4de452e6b438", 255, -6, (770, 120)),
    ("c6f87718-6d76-407e-881e-d162ae2eb154", 255, 3, (600, 168)),
]:
    shadowed(f, stamp(sid, h, rot), xy, blur=14, tint=navy, alpha=120, off=(6, 12))
pm = postmark("KYOTO", "03 APR 26", 140).rotate(-12, Image.BICUBIC, expand=True)
f.alpha_composite(pm, (800, 255))
f.convert("RGB").save(f"{OUT}/feature-graphic.png")
print("ok")
