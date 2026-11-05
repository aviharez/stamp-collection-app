import subprocess
exec(open("icons.py").read().split("full = lambda")[0])  # reuse helpers + colours
full = lambda c: f'<path fill="{c}" d="M0,0h108v108h-108z"/>'

def stamp2(cx, cy, w, h, tilt, border, r, nw, nh, inner, shadow=None, paper=WHITE):
    x, y = cx - w/2, cy - h/2
    p = perforated(x, y, w, h, r, nw, nh)
    s = f'<g transform="rotate({tilt} {cx} {cy})">'
    if shadow: s += f'<path fill="{shadow[0]}" fill-opacity="{shadow[1]}" transform="translate(1.2 2)" d="{p}"/>'
    s += f'<path fill="{paper}" d="{p}"/>' + inner(x+border, y+border, w-2*border, h-2*border)
    return s + "</g>"

def lens(x, y, w, h):
    cx, cy = x + w/2, y + h/2; R = w*.36
    return (f'<path fill="{INK}" d="M{x},{y}h{w}v{h}h{-w}z"/>'
            f'<circle fill="{COBALT}" cx="{cx}" cy="{cy}" r="{R}"/>'
            f'<circle fill="none" stroke="{WHITE}" stroke-opacity=".9" stroke-width="{w*.035}" cx="{cx}" cy="{cy}" r="{R}"/>'
            f'<circle fill="{INK}" cx="{cx}" cy="{cy}" r="{R*.52}"/>'
            f'<circle fill="{WHITE}" cx="{cx-R*.32}" cy="{cy-R*.34}" r="{R*.16}"/>')

def solid(c, extra=""):
    return lambda x, y, w, h: f'<path fill="{c}" d="M{x},{y}h{w}v{h}h{-w}z"/>' + (extra(x, y, w, h) if extra else "")

def monogram(x, y, w, h):
    return f'<path fill="{RED}" d="M{x},{y}h{w}v{h}h{-w}z"/>' + glyph("P", x + w/2, y + h/2, h*.62, WHITE)

def stripes_band(y0, hgt, pitch=7):
    cols = [RED, WHITE, COBALT, WHITE]; s = ""
    for i, x in enumerate(range(-30, 140, pitch)):
        c = cols[i % 4]
        s += f'<path fill="{c}" d="M{x},{y0+hgt} L{x+hgt},{y0} h{pitch} L{x+pitch},{y0+hgt} Z"/>'
    return s

def envelope():
    x, y, w, h, b = 27, 37, 54, 36, 3.4
    s = f'<g transform="rotate(-6 54 55)"><path fill="#14277A" fill-opacity=".45" transform="translate(1.2 2)" d="M{x},{y}h{w}v{h}h{-w}z"/>'
    s += f'<defs><clipPath id="env"><path d="M{x},{y}h{w}v{h}h{-w}z"/></clipPath></defs><g clip-path="url(#env)">' + stripes_band(y, h, pitch=5).replace("M", "M").replace(str(y+h), str(y+h)) + '</g>'
    s += f'<path fill="{WHITE}" d="M{x+b},{y+b}h{w-2*b}v{h-2*b}h{-(w-2*b)}z"/>'
    s += stamp2(x+w-13, y+14, 14, 17, 4, 1.7, 1.0, 4, 5, solid(RED), shadow=("#462D0F", .25))
    s += postmark(x+w-24, y+21, 6, INK, 1.4, waves=False, opacity=.85)
    s += "".join(f'<path fill="none" stroke="{INK}" stroke-opacity=".35" stroke-width="2" stroke-linecap="round" d="M{x+11},{y+h-12+k*5}h{18 - k*5}"/>' for k in range(2))
    return s + "</g>"

concepts = {
  # D: camera-first: the photo window is a lens
  "D_lens": svg(full(RED), stamp2(54, 54, 40, 47, -5, 3.8, 2.1, 5, 6, lens, shadow=("#5E140F", .35))),
  # E: a collection, two fanned stamps
  "E_pair": svg(full(KRAFT),
      stamp2(60, 50, 30, 36, 9, 3.3, 1.9, 5, 6, solid(COBALT), shadow=("#462D0F", .18)) +
      stamp2(48, 58, 30, 36, -7, 3.3, 1.9, 5, 6, solid(RED, lambda x,y,w,h: postmark(x+w*.5, y+h*.5, w*.27, WHITE, 1.8, waves=False, opacity=.95)), shadow=("#462D0F", .22))),
  # F: airmail envelope corner with a stamp
  "F_envelope": svg(full(COBALT), envelope()),
  # G: bold monogram stamp
  "G_monogram": svg(full(INK), stamp2(54, 54, 39, 46, -6, 3.6, 2.1, 5, 6, monogram, shadow=("#000814", .5))),
}
for name, s in concepts.items():
    open(f"{name}.svg", "w").write(s)
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{name}.svg", "-o", f"{name}.png"], check=True)
print("ok")
