"""Converts the small SVG subset used by icons*.py to Android VectorDrawable XML."""
import re, sys, xml.etree.ElementTree as ET
NS = "{http://www.w3.org/2000/svg}"
STYLE = ("fill", "fill-opacity", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-opacity", "opacity")

def argb(color, alpha):
    a = max(0, min(255, round(alpha * 255)))
    return f"#{a:02X}{color[1:].upper()}" if a < 255 else color.upper()

def circle_d(cx, cy, r):
    return f"M{cx-r:.3f},{cy:.3f} a{r:.3f},{r:.3f} 0 1,0 {2*r:.3f},0 a{r:.3f},{r:.3f} 0 1,0 {-2*r:.3f},0 Z"

def fmt(v): return f"{float(v):.3f}".rstrip("0").rstrip(".")

def transforms(t):
    out = []
    for name, args in re.findall(r"(\w+)\(([^)]*)\)", t or ""):
        a = [float(x) for x in re.split(r"[ ,]+", args.strip())]
        out.append((name, a))
    return out

def open_groups(t, ind):
    xml, n = "", 0
    base = ind
    for name, a in transforms(t):
        if name == "translate":
            xml += f'{base}<group android:translateX="{fmt(a[0])}" android:translateY="{fmt(a[1] if len(a) > 1 else 0)}">\n'
        elif name == "scale":
            xml += f'{base}<group android:scaleX="{fmt(a[0])}" android:scaleY="{fmt(a[1] if len(a) > 1 else a[0])}">\n'
        elif name == "rotate":
            px, py = (a[1], a[2]) if len(a) == 3 else (0, 0)
            xml += f'{base}<group android:rotation="{fmt(a[0])}" android:pivotX="{fmt(px)}" android:pivotY="{fmt(py)}">\n'
        else:
            raise ValueError(name)
        n += 1
        base += "    "
    return xml, n

def emit(el, style, clips, ind="    "):
    style = dict(style)
    for k in STYLE:
        if el.get(k) is not None:
            style[k] = float(el.get(k)) * style.get(k, 1) if k == "opacity" else el.get(k)
    tag = el.tag.replace(NS, "")
    if tag in ("defs", "clipPath"):
        return ""
    gx, n = open_groups(el.get("transform"), ind)
    inner = ind + "    " * n
    clip = ""
    if el.get("clip-path"):
        cid = re.search(r"#([\w-]+)", el.get("clip-path")).group(1)
        gx += f"{inner}<group>\n"; n += 1; inner += "    "
        gx += f'{inner}<clip-path android:pathData="{clips[cid]}"/>\n'
    body = ""
    if tag in ("g", "svg"):
        for c in el: body += emit(c, style, clips, inner)
    else:
        d = el.get("d") if tag == "path" else circle_d(float(el.get("cx")), float(el.get("cy")), float(el.get("r")))
        op = style.get("opacity", 1)
        attrs = [f'android:pathData="{d}"']
        fill = style.get("fill", "#000000")
        if fill != "none":
            attrs.append(f'android:fillColor="{argb(fill, float(style.get("fill-opacity", 1)) * op)}"')
        stroke = style.get("stroke")
        if stroke and stroke != "none":
            attrs.append(f'android:strokeColor="{argb(stroke, float(style.get("stroke-opacity", 1)) * op)}"')
            attrs.append(f'android:strokeWidth="{fmt(style.get("stroke-width", 1))}"')
            if style.get("stroke-linecap"): attrs.append(f'android:strokeLineCap="{style["stroke-linecap"]}"')
            if style.get("stroke-linejoin"): attrs.append(f'android:strokeLineJoin="{style["stroke-linejoin"]}"')
        body = f"{inner}<path\n{inner}    " + f"\n{inner}    ".join(attrs) + " />\n"
    close = "".join(f"{ind}{'    ' * i}</group>\n" for i in reversed(range(n)))
    return gx + body + close

def convert(svg_path, group_id, mono=None):
    root = ET.parse(svg_path).getroot()
    clips = {}
    for cp in root.iter(NS + "clipPath"):
        p = cp.find(NS + "path"); clips[cp.get("id")] = p.get("d")
    g = next(e for e in root.iter(NS + "g") if e.get("id") == group_id)
    body = emit(g, {"opacity": 1}, clips)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n' + body + "</vector>\n")

if __name__ == "__main__":
    print(convert(sys.argv[1], sys.argv[2]))
