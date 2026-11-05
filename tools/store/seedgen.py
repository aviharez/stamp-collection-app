"""Builds a Postmory backup ZIP of sample stamps (store screenshots). Mimics StampRenderer.drawStamp."""
import json, math, sys, uuid, zipfile, random
from datetime import datetime, date, timedelta, timezone
from PIL import Image, ImageDraw

SEED = sys.argv[1]  # dir with full/<id>.jpg
OUT = sys.argv[2]
random.seed(7)
SS = 3  # supersampling for the paper mask
FRAME_SHORT_DP = 240.0
R_DP, PITCH_DP = 3.5, 12.0
MARGIN_DP = R_DP * 2 + 2
CORNER_DP = 1.5
ASPECT = {"Portrait": 3 / 4, "Landscape": 4 / 3, "Square": 1.0, "Circle": 1.0}
TZ = timezone(timedelta(hours=7))


def centres(length, pitch):
    n = max(1, round(length / pitch))
    step = length / n
    return [step * (i + .5) for i in range(n)]


def paper_mask(w, h, shape, r, pitch, corner):
    W, H = w * SS, h * SS
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    r, pitch, corner = r * SS, pitch * SS, corner * SS
    if shape == "Circle":
        R = min(W, H) / 2
        cx, cy = W / 2, H / 2
        d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=255)
        n = max(1, round(2 * math.pi * R / pitch))
        for i in range(n):
            a = 2 * math.pi * (i + .5) / n
            x, y = cx + R * math.cos(a), cy + R * math.sin(a)
            d.ellipse([x - r, y - r, x + r, y + r], fill=0)
    else:
        d.rounded_rectangle([0, 0, W - 1, H - 1], radius=min(corner, r), fill=255)
        for x in centres(W, pitch):
            for y in (0, H):
                d.ellipse([x - r, y - r, x + r, y + r], fill=0)
        for y in centres(H, pitch):
            for x in (0, W):
                d.ellipse([x - r, y - r, x + r, y + r], fill=0)
    return m.resize((w, h), Image.LANCZOS)


def render(photo_path, shape, out_png, bias=(.5, .5)):
    photo = Image.open(photo_path).convert("RGB")
    a = ASPECT[shape]
    long_px = 2000
    W, H = (long_px, round(long_px / a)) if a >= 1 else (round(long_px * a), long_px)
    ppd = min(W, H) / FRAME_SHORT_DP
    margin = MARGIN_DP * ppd
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    paper = Image.new("RGBA", (W, H), (0xFF, 0xFE, 0xFB, 255))
    out.paste(paper, (0, 0), paper_mask(W, H, shape, R_DP * ppd, PITCH_DP * ppd, CORNER_DP * ppd))
    iw, ih = round(W - 2 * margin), round(H - 2 * margin)
    # crop window of the photo at the inner aspect, biased toward the subject
    pw, ph = photo.size
    ia = iw / ih
    if pw / ph > ia:
        cw, ch = ph * ia, ph
    else:
        cw, ch = pw, pw / ia
    left = (pw - cw) * bias[0]
    top = (ph - ch) * bias[1]
    window = (left / pw, top / ph, (left + cw) / pw, (top + ch) / ph)
    crop = photo.crop((round(left), round(top), round(left + cw), round(top + ch))).resize((iw, ih), Image.LANCZOS)
    ox, oy = round(margin), round(margin)
    if shape == "Circle":
        cm = Image.new("L", (iw * SS, ih * SS), 0)
        ImageDraw.Draw(cm).ellipse([0, 0, iw * SS - 1, ih * SS - 1], fill=255)
        out.paste(crop, (ox, oy), cm.resize((iw, ih), Image.LANCZOS))
    else:
        out.paste(crop, (ox, oy))
    out.save(out_png, optimize=False)
    spec = ",".join(str(round(v, 5)) for v in window) + f",0,0,{FRAME_SHORT_DP}"
    return spec


# album key, name, category, cover, created
ALBUMS = [
    (1, "Kyoto in spring", "Trips", "Red", "2026-04-01"),
    (2, "Paris weekend", "Trips", "Cobalt", "2026-06-12"),
    (3, "Pets", "Pets", "Sage", "2026-06-20"),
    (4, "Lisbon trip", "Trips", "Kraft", "2026-08-06"),
    (5, "Norway road trip", "Trips", "Cobalt", "2026-09-10"),
    (6, "Everyday", "Everyday", "Ink", "2026-03-30"),
]
PLACES = {
    "Kyoto": ("Japan", "JP", 35.0116, 135.7681),
    "Paris": ("France", "FR", 48.8566, 2.3522),
    "Lisbon": ("Portugal", "PT", 38.7223, -9.1393),
    "Sintra": ("Portugal", "PT", 38.8029, -9.3817),
    "Bergen": ("Norway", "NO", 60.3913, 5.3221),
    "Geiranger": ("Norway", "NO", 62.1008, 7.2059),
    "Ålesund": ("Norway", "NO", 62.4722, 6.1495),
    "Jakarta": ("Indonesia", "ID", -6.2088, 106.8456),
    "Bandung": ("Indonesia", "ID", -6.9175, 107.6191),
}
# photo id, album, shape, day, hh:mm, city, title, caption, crop bias
S = [
    ("704", 1, "Portrait", "2026-04-03", "09:12", "Kyoto", "Arashiyama", "the bamboo creaks in the wind", (.5, .5)),
    ("844", 1, "Square", "2026-04-03", "10:40", "Kyoto", None, None, (.5, .5)),
    ("783", 1, "Circle", "2026-04-04", "14:05", "Kyoto", "Snow monkey", "he stared first", (.5, .4)),
    ("460", 1, "Portrait", "2026-04-05", "19:30", "Kyoto", None, "ramen at 7:30, worth the queue", (.5, .5)),
    ("318", 2, "Portrait", "2026-06-13", "08:15", "Paris", None, None, (.5, .5)),
    ("234", 2, "Landscape", "2026-06-13", "17:50", "Paris", "Rive gauche", None, (.5, .5)),
    ("431", 2, "Square", "2026-06-14", "10:02", "Paris", None, "café crème, slow morning", (.5, .5)),
    ("1062", 3, "Square", "2026-07-02", "21:10", "Jakarta", "Mochi", "blanket burrito", (.5, .4)),
    ("837", 3, "Portrait", "2026-07-19", "16:45", "Jakarta", None, None, (.5, .5)),
    ("670", 4, "Portrait", "2026-08-08", "11:20", "Lisbon", "Tram 28", "up the hill to Alfama", (.5, .5)),
    ("419", 4, "Landscape", "2026-08-08", "18:05", "Lisbon", None, None, (.5, .5)),
    ("322", 4, "Portrait", "2026-08-09", "10:30", "Lisbon", None, "lost on purpose", (.5, .5)),
    ("1065", 4, "Portrait", "2026-08-09", "16:12", "Lisbon", None, None, (.5, .5)),
    ("437", 4, "Square", "2026-08-10", "13:40", "Sintra", None, None, (.5, .5)),
    ("369", 4, "Landscape", "2026-08-11", "09:55", "Lisbon", None, "last pastel de nata", (.5, .5)),
    ("1025", 3, "Circle", "2026-08-24", "08:00", "Bandung", None, None, (.5, .5)),
    ("1015", 5, "Landscape", "2026-09-12", "15:30", "Bergen", "Fjord light", None, (.5, .5)),
    ("1011", 5, "Portrait", "2026-09-13", "07:45", "Bergen", None, "glass-still water", (.5, .5)),
    ("1018", 5, "Landscape", "2026-09-14", "12:20", "Geiranger", None, None, (.5, .5)),
    ("873", 5, "Square", "2026-09-15", "18:10", "Geiranger", None, None, (.5, .5)),
    ("1036", 5, "Landscape", "2026-09-16", "11:05", "Ålesund", None, "first snow on the peaks", (.5, .5)),
    ("507", 5, "Portrait", "2026-09-17", "16:40", "Ålesund", "Trolltunga-ish", None, (.5, .5)),
    ("237", 3, "Square", "2026-09-28", "19:20", "Bandung", "Kopi", "the neighbour's dog", (.5, .5)),
    ("659", 3, "Portrait", "2026-10-11", "17:00", "Jakarta", None, None, (.5, .5)),
]
EVERY = [  # 12-day streak Oct 24 .. Nov 4
    ("1060", "Portrait", "08:20", "Jakarta", None, "the good coffee place"),
    ("429", "Circle", "16:10", "Bandung", None, None),
    ("493", "Square", "07:35", "Jakarta", "Breakfast", None),
    ("835", "Square", "15:50", "Bandung", None, "baked these, ate most"),
    ("696", "Portrait", "10:15", "Bandung", None, None),
    ("106", "Portrait", "17:30", "Bandung", None, None),
    ("292", "Landscape", "19:05", "Jakarta", None, "sambal night"),
    ("30", "Portrait", "08:05", "Bandung", None, None),
    ("63", "Square", "13:25", "Jakarta", None, None),
    ("1080", "Square", "11:40", "Jakarta", None, "market strawberries"),
    ("360", "Circle", "16:55", "Bandung", None, None),
    ("425", "Square", "09:30", "Jakarta", "Roast day", None),
]
d0 = date(2026, 10, 24)
for i, (pid, shape, t, city, title, cap) in enumerate(EVERY):
    S.append((pid, 6, shape, (d0 + timedelta(days=i)).isoformat(), t, city, title, cap, (.5, .5)))
# a few earlier everyday stamps so the album has pages & the calendar has history
EXTRA = [("312","2026-10-02","Square"),("102","2026-10-03","Portrait"),("824","2026-10-04","Portrait"),("488","2026-10-06","Square"),
 ("627","2026-10-07","Landscape"),("755","2026-10-09","Square"),("999","2026-10-10","Square"),("1069","2026-10-12","Portrait"),
 ("674","2026-10-13","Circle"),("306","2026-10-15","Square"),("152","2026-10-16","Circle"),("882","2026-10-18","Portrait"),
 ("625","2026-10-19","Landscape"),("1068","2026-10-21","Square"),("82","2026-10-22","Square")]
cities = ["Jakarta","Bandung","Jakarta","Jakarta","Bandung"]
for i, (pid, day, shape) in enumerate(EXTRA):
    S.append((pid, 6, shape, day, f"{8 + i % 10:02d}:{(i * 17) % 60:02d}", cities[i % 5], None, None, (.5, .5)))

entries = []
stamps = []
for pid, album, shape, day, t, city, title, cap, bias in S:
    sid = str(uuid.UUID(int=random.getrandbits(128), version=4))
    png = f"{SEED}/out_{sid}.png"
    spec = render(f"{SEED}/full/{pid}.jpg", shape, png, bias)
    country, cc, lat, lon = PLACES[city]
    lat += random.uniform(-.01, .01); lon += random.uniform(-.01, .01)
    hh, mm = map(int, t.split(":"))
    y, mo, dd = map(int, day.split("-"))
    taken = int(datetime(y, mo, dd, hh, mm, tzinfo=TZ).timestamp() * 1000)
    entries += [(png, f"rendered/{sid}.png"), (f"{SEED}/full/{pid}.jpg", f"originals/{sid}.jpg")]
    stamps.append(dict(id=sid, albumKey=album, shape=shape, perforation="Classic", image=f"rendered/{sid}.png",
                       original=f"originals/{sid}.jpg", takenAt=taken, day=day, createdDay=day, city=city,
                       country=country, countryCode=cc, latitude=round(lat, 4), longitude=round(lon, 4),
                       title=title, caption=cap, source="Camera", filter=None, renderSpec=spec))


def ms(d):
    y, m, dd = map(int, d.split("-"))
    return int(datetime(y, m, dd, 8, 0, tzinfo=TZ).timestamp() * 1000)


manifest = dict(format=1, createdAt=int(datetime.now(TZ).timestamp() * 1000),
                albums=[dict(key=k, name=n, category=c, cover=cv, createdAt=ms(cr)) for k, n, c, cv, cr in ALBUMS],
                stamps=stamps, settings=dict(mirrorSelfie=None, bonusAlbums=0), scrapPages=[])
z = zipfile.ZipFile(OUT, "w", zipfile.ZIP_STORED)
z.writestr("backup.json", json.dumps(manifest, ensure_ascii=False))
for a, b in entries: z.write(a, b)
z.close()
print(len(stamps), "stamps")
