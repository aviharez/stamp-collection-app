"""Builds app/src/main/assets/world_50m.bin from Natural Earth 1:50m admin-0 countries (public domain).

    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson
    python3 tools/world_map.py ne_50m_admin_0_countries.geojson app/src/main/assets/world_50m.bin

Format (big-endian, read by data/map/WorldOutline.kt): int country count; per country: 2 ASCII bytes
ISO 3166 alpha-2 ("--" when none), short ring count; per ring: int point count, then (short lon, short lat)
in hundredths of a degree. Antarctica is left out; rings are simplified (Douglas-Peucker, 0.02 deg).
"""
import json, struct, sys

TOL = 2  # hundredths of a degree


def simplify(pts):
    if len(pts) < 5:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = pts[a]; bx, by = pts[b]
        dx, dy = bx - ax, by - ay
        L = (dx * dx + dy * dy) ** 0.5
        best, idx = -1.0, -1
        for i in range(a + 1, b):
            px, py = pts[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / L if L else ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            if d > best:
                best, idx = d, i
        if idx >= 0 and best > TOL:
            keep[idx] = True
            stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(pts, keep) if k]


def main(src, dst):
    features = json.load(open(src))['features']
    out = []
    for f in features:
        cc = f['properties']['ISO_A2_EH']
        if cc == 'AQ':
            continue
        if len(cc) != 2:
            cc = '--'
        g = f['geometry']
        polys = g['coordinates'] if g['type'] == 'MultiPolygon' else [g['coordinates']]
        rings = []
        for poly in polys:
            for ring in poly:
                pts, last = [], None
                for lon, lat in ring:
                    q = (round(lon * 100), round(lat * 100))
                    if q != last:
                        pts.append(q); last = q
                pts = simplify(pts)
                if len(pts) >= 4:
                    rings.append(pts)
        if rings:
            out.append((cc, rings))
    with open(dst, 'wb') as fh:
        fh.write(struct.pack('>i', len(out)))
        for cc, rings in out:
            fh.write(cc.encode('ascii'))
            fh.write(struct.pack('>h', len(rings)))
            for r in rings:
                fh.write(struct.pack('>i', len(r)))
                for x, y in r:
                    fh.write(struct.pack('>hh', x, y))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
