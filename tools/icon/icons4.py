import subprocess
exec(open("icons2.py").read().split("concepts = {")[0])

def postmark_left(cx, cy, r, color, stroke, opacity=.88):
    s = f'<g opacity="{opacity}" fill="none" stroke="{color}" stroke-width="{stroke}" stroke-linecap="round">'
    s += f'<circle cx="{cx}" cy="{cy}" r="{r}"/><circle cx="{cx}" cy="{cy}" r="{r*.66}" stroke-width="{stroke*.7}"/>'
    for k in (-.42, 0, .42):
        yy = cy + r*k; x0 = cx - r - 1.5; L = r*1.6; a = r*.16
        s += f'<path d="M{x0},{yy} q{-L/8},{-a} {-L/4},0 t{-L/4},0 t{-L/4},0 t{-L/4},0"/>'
    return s + "</g>"

def envelope_a(shadow_col, lines_col=INK):
    x, y, w, h, b = 24, 38, 60, 38, 3.4
    s = f'<g transform="translate(54 55) scale(.9) translate(-54 -55) rotate(-6 54 56)"><path fill="{shadow_col}" fill-opacity=".4" transform="translate(1.3 2.2)" d="M{x},{y}h{w}v{h}h{-w}z"/>'
    s += f'<defs><clipPath id="env"><path d="M{x},{y}h{w}v{h}h{-w}z"/></clipPath></defs><g clip-path="url(#env)">{stripes_band(y, h, pitch=5)}</g>'
    s += f'<path fill="{WHITE}" d="M{x+b},{y+b}h{w-2*b}v{h-2*b}h{-(w-2*b)}z"/>'
    s += "".join(f'<path fill="none" stroke="{lines_col}" stroke-opacity=".28" stroke-width="2" stroke-linecap="round" d="M{x+9},{y+h-13+k*5.5}h{20-k*6}"/>' for k in range(2))
    # A's stamp, bigger than F's, standing up off the envelope's top-right
    s += stamp2(x+w-15, y+11, 23, 28, 7, 2.4, 1.3, 4, 5, scene, shadow=("#462D0F", .28))
    s += postmark_left(x+w-27, y+20, 7.2, INK, 1.7)
    return s + "</g>"

concepts = {
  "L_cobalt": svg(full(COBALT), envelope_a("#14277A")),
  "M_red": svg(full(RED), envelope_a("#5E140F")),
}
for name, s in concepts.items():
    open(f"{name}.svg", "w").write(s)
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{name}.svg", "-o", f"{name}.png"], check=True)
print("ok")

def mono():
    K = "#000000"
    x, y, w, h, b = 24, 38, 60, 38, 3.4
    s = '<g transform="translate(54 55) scale(.9) translate(-54 -55) rotate(-6 54 56)">'
    s += f'<path fill="none" stroke="{K}" stroke-width="{b}" stroke-linejoin="round" d="M{x+b/2},{y+b/2}h{w-b}v{h-b}h{-(w-b)}z"/>'
    s += "".join(f'<path fill="none" stroke="{K}" stroke-width="2" stroke-linecap="round" d="M{x+9},{y+h-10+k*4.5}h{20-k*6}"/>' for k in range(2))
    cx, cy, sw, sh = x+w-15, y+11, 23, 28
    s += f'<g transform="rotate(7 {cx} {cy})"><path fill="{K}" d="{perforated(cx-sw/2, cy-sh/2, sw, sh, 1.3, 4, 5)}"/></g>'
    pm = postmark_left(x+w-34, y+17, 6.2, K, 1.8, opacity=1)
    s += pm
    return s + "</g>"
open("mono.svg", "w").write(svg("", mono()))
subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", "mono.svg", "-o", "mono.png"], check=True)
