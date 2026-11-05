import math, subprocess, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
FONT = "/home/nyxelyn/Documents/Playgrounds/app-project/stamp-app/app/src/main/res/font/fraunces_semibold_italic.ttf"
PAPER="#F6F0E6"; WHITE="#FFFEFB"; INK="#18223F"; RED="#D7362B"; COBALT="#2848C8"
KRAFT="#E3CFAE"; SAGE="#6E8F6A"; EMBER="#FF6A4D"

def perforated(x, y, w, h, r, n_w, n_h):
    """Outline of a stamp: rectangle with half-circle bites along each edge (clockwise)."""
    pw, ph = w / n_w, h / n_h
    d = [f"M{x:.2f},{y:.2f}"]
    for i in range(n_w):  # top, left→right
        cx = x + pw * (i + .5); d += [f"L{cx-r:.2f},{y:.2f}", f"A{r},{r} 0 0 0 {cx+r:.2f},{y:.2f}"]
    d.append(f"L{x+w:.2f},{y:.2f}")
    for i in range(n_h):  # right, top→bottom
        cy = y + ph * (i + .5); d += [f"L{x+w:.2f},{cy-r:.2f}", f"A{r},{r} 0 0 0 {x+w:.2f},{cy+r:.2f}"]
    d.append(f"L{x+w:.2f},{y+h:.2f}")
    for i in range(n_w):  # bottom, right→left
        cx = x + w - pw * (i + .5); d += [f"L{cx+r:.2f},{y+h:.2f}", f"A{r},{r} 0 0 0 {cx-r:.2f},{y+h:.2f}"]
    d.append(f"L{x:.2f},{y+h:.2f}")
    for i in range(n_h):  # left, bottom→top
        cy = y + h - ph * (i + .5); d += [f"L{x:.2f},{cy+r:.2f}", f"A{r},{r} 0 0 0 {x:.2f},{cy-r:.2f}"]
    return " ".join(d) + "Z"

def scene(x, y, w, h):
    """The 'photo' in the stamp: sky, sun, two hills (all inside the rect)."""
    out = [f'<path fill="{COBALT}" d="M{x},{y}h{w}v{h}h{-w}z"/>']
    out.append(f'<circle fill="{EMBER}" cx="{x+w*.68}" cy="{y+h*.33}" r="{w*.16}"/>')
    out.append(f'<path fill="{SAGE}" d="M{x},{y+h*.70} Q{x+w*.30},{y+h*.48} {x+w*.62},{y+h*.68} L{x+w},{y+h*.60} V{y+h} H{x} Z"/>')
    out.append(f'<path fill="{KRAFT}" d="M{x},{y+h*.86} Q{x+w*.55},{y+h*.66} {x+w},{y+h*.82} V{y+h} H{x} Z"/>')
    return "".join(out)

def stamp(cx, cy, w, h, tilt, border, r, nw, nh, scene_fn=scene):
    x, y = cx - w/2, cy - h/2
    s = f'<g transform="rotate({tilt} {cx} {cy})">'
    s += f'<path fill="{WHITE}" d="{perforated(x, y, w, h, r, nw, nh)}"/>'
    s += scene_fn(x+border, y+border, w-2*border, h-2*border)
    return s + "</g>"

def postmark(cx, cy, r, color, stroke, waves=True, opacity=1):
    s = f'<g opacity="{opacity}" fill="none" stroke="{color}" stroke-width="{stroke}" stroke-linecap="round">'
    s += f'<circle cx="{cx}" cy="{cy}" r="{r}"/><circle cx="{cx}" cy="{cy}" r="{r*.68}" stroke-width="{stroke*.7}"/>'
    if waves:
        for k in (-.45, 0, .45):
            yy = cy + r*k; x0 = cx + r + 1.5; L = r*1.1; a = r*.13
            s += f'<path d="M{x0},{yy} q{L/8},{-a} {L/4},0 t{L/4},0 t{L/4},0 t{L/4},0"/>'
    return s + "</g>"

def glyph(ch, cx, cy, size, color):
    f = TTFont(FONT); gs = f.getGlyphSet(); name = f.getBestCmap()[ord(ch)]
    upm = f["head"].unitsPerEm; k = size / upm; g = gs[name]
    pen = SVGPathPen(gs)
    # bounds to centre it
    from fontTools.pens.boundsPen import BoundsPen
    bp = BoundsPen(gs); g.draw(bp); x0, y0, x1, y1 = bp.bounds
    tx = cx - (x0 + x1) / 2 * k; ty = cy + (y0 + y1) / 2 * k
    g.draw(TransformPen(pen, (k, 0, 0, -k, tx, ty)))
    return f'<path fill="{color}" d="{pen.getCommands()}"/>'

def svg(bg, fg):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108"><g id="bg">{bg}</g><g id="fg">{fg}</g></svg>'

full = lambda c: f'<path fill="{c}" d="M0,0h108v108h-108z"/>'
concepts = {
  "A_red": svg(full(RED), stamp(48, 50, 35, 43, -6, 3.2, 1.9, 6, 7) + postmark(64, 68, 9, INK, 2.1, opacity=.9)),
  "B_cobalt": svg(full(COBALT), stamp(48, 50, 35, 43, -6, 3.2, 1.9, 6, 7, scene_fn=lambda x,y,w,h: scene(x,y,w,h).replace(COBALT, "#7FA0E8")) + postmark(64, 68, 9, RED, 2.1)),
  "C_ink": svg(full(INK), postmark(46, 54, 20, RED, 3, waves=False) + glyph("P", 46, 54, 25, WHITE) +
      "".join(f'<path fill="none" stroke="{RED}" stroke-width="2.4" stroke-linecap="round" d="M{69},{54+k} q2.25,-2 4.5,0 t4.5,0 t4.5,0"/>' for k in (-6, 0, 6))),
}
for name, s in concepts.items():
    open(f"{name}.svg", "w").write(s)
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{name}.svg", "-o", f"{name}.png"], check=True)
print("ok")
