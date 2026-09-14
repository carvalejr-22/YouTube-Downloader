from pathlib import Path
from PIL import Image, ImageDraw

SIZE = 512
V = 108.0
S = SIZE / V


def pt(x, y):
    return (x * S, y * S)


def cubic(p0, p1, p2, p3, steps=24):
    out = []
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0]
        y = u**3 * p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]
        out.append((x, y))
    return out


def rgba(hex_color, alpha=255):
    h = hex_color.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size, start, end, stops):
    w, h = size
    img = Image.new('RGBA', size)
    px = img.load()
    sx, sy = start
    ex, ey = end
    dx, dy = ex - sx, ey - sy
    denom = dx*dx + dy*dy or 1
    rgb_stops = [(p, rgba(c)[:3]) for p, c in stops]
    for y in range(h):
        for x in range(w):
            t = ((x - sx) * dx + (y - sy) * dy) / denom
            t = max(0.0, min(1.0, t))
            left, right = rgb_stops[0], rgb_stops[-1]
            for i in range(len(rgb_stops) - 1):
                if rgb_stops[i][0] <= t <= rgb_stops[i + 1][0]:
                    left, right = rgb_stops[i], rgb_stops[i + 1]
                    break
            span = right[0] - left[0] or 1
            local = (t - left[0]) / span
            r, g, b = mix(left[1], right[1], local)
            px[x, y] = (r, g, b, 255)
    return img


def apply_shape(canvas, points, fill=None, grad=None):
    mask = Image.new('L', canvas.size, 0)
    ImageDraw.Draw(mask).polygon([(int(x*S), int(y*S)) for x, y in points], fill=255)
    if grad is not None:
        layer = grad
    else:
        layer = Image.new('RGBA', canvas.size, fill)
    canvas.alpha_composite(Image.composite(layer, Image.new('RGBA', canvas.size, (0, 0, 0, 0)), mask))


img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

# Fundo: mesma geometria e mesma paleta do vetor Android aprovado.
bg_mask = Image.new('L', img.size, 0)
ImageDraw.Draw(bg_mask).rounded_rectangle(
    (int(2*S), int(2*S), int(106*S), int(106*S)),
    radius=int(12*S),
    fill=255,
)
bg = gradient(
    img.size,
    pt(8, 8),
    pt(102, 102),
    [(0.0, '#06265A'), (0.52, '#071A46'), (1.0, '#17105B')],
)
img.alpha_composite(Image.composite(bg, Image.new('RGBA', img.size, (0, 0, 0, 0)), bg_mask))

# Ondas inferiores.
p = [(2, 78)]
p += [(x/S, y/S) for x, y in cubic(pt(2, 78), pt(20, 91), pt(39, 96), pt(58, 96))]
p += [(x/S, y/S) for x, y in cubic(pt(58, 96), pt(76, 96), pt(91, 90), pt(106, 79))]
p += [(106, 94), (106, 106), (2, 106)]
apply_shape(img, p, fill=rgba('#117CFF', 209))

p = [(2, 87)]
p += [(x/S, y/S) for x, y in cubic(pt(2, 87), pt(21, 100), pt(43, 102), pt(61, 100))]
p += [(x/S, y/S) for x, y in cubic(pt(61, 100), pt(79, 99), pt(94, 94), pt(106, 86))]
p += [(106, 106), (2, 106)]
apply_shape(img, p, fill=rgba('#38E8E2', 184))

p = [(57, 98)]
p += [(x/S, y/S) for x, y in cubic(pt(57, 98), pt(75, 97), pt(92, 91), pt(106, 81))]
p += [(106, 106), (57, 106)]
apply_shape(img, p, fill=rgba('#7C46FF', 194))

# Camada principal ciano/azul.
p = [(34, 17)]
p += [(x/S, y/S) for x, y in cubic(pt(34,17), pt(28,16), pt(23,21), pt(23,28))]
p += [(23, 78)]
p += [(x/S, y/S) for x, y in cubic(pt(23,78), pt(23,86), pt(29,91), pt(37,88))]
p += [(84,61)]
p += [(x/S, y/S) for x, y in cubic(pt(84,61), pt(91,57), pt(91,51), pt(84,47))]
p += [(38,20)]
p += [(x/S, y/S) for x, y in cubic(pt(38,20), pt(36,18.5), pt(35,17.5), pt(34,17))]
main_grad = gradient(img.size, pt(25,19), pt(84,83), [(0.0,'#48F3E7'), (0.52,'#0D98FF'), (1.0,'#2860FF')])
apply_shape(img, p, grad=main_grad)

# Camada violeta/magenta posterior.
p = [(58,31)]
p += [(x/S, y/S) for x, y in cubic(pt(58,31), pt(70,35), pt(82,42), pt(90,48))]
p += [(x/S, y/S) for x, y in cubic(pt(90,48), pt(96,52), pt(96,58), pt(90,62))]
p += [(51,84)]
p += [(x/S, y/S) for x, y in cubic(pt(51,84), pt(45,88), pt(38,86), pt(34,81))]
p += [(x/S, y/S) for x, y in cubic(pt(34,81), pt(49,77), pt(64,69), pt(75,58))]
p += [(x/S, y/S) for x, y in cubic(pt(75,58), pt(83,50), pt(77,40), pt(58,31))]
magenta = gradient(img.size, pt(54,34), pt(91,69), [(0.0,'#506BFF'), (0.52,'#8C48FF'), (1.0,'#F05CFF')])
apply_shape(img, p, grad=magenta)

# Nota musical branca: coordenadas do vetor Android.
p = [(45,35)]
p += [(x/S, y/S) for x, y in cubic(pt(45,35), pt(45,31.5), pt(47,29.5), pt(50,29))]
p += [(72,25)]
p += [(x/S, y/S) for x, y in cubic(pt(72,25), pt(76,24.2), pt(79,27), pt(79,30.5))]
p += [(79,49), (72,49), (72,36), (53,39.5), (53,69)]
p += [(x/S, y/S) for x, y in cubic(pt(53,69), pt(53,79.5), pt(46,87), pt(37,87))]
p += [(x/S, y/S) for x, y in cubic(pt(37,87), pt(28.5,87), pt(22,81.2), pt(22,73.5))]
p += [(x/S, y/S) for x, y in cubic(pt(22,73.5), pt(22,65.5), pt(28.2,59.5), pt(36.5,59.5))]
p += [(x/S, y/S) for x, y in cubic(pt(36.5,59.5), pt(39.5,59.5), pt(42.5,60.4), pt(45,62))]
apply_shape(img, p, fill=(255,255,255,255))

# Seta vertical reta e centralizada.
p = [(69,48.5)]
p += [(x/S, y/S) for x, y in cubic(pt(69,48.5), pt(69,46.5), pt(70.5,45), pt(72.5,45))]
p += [(76.5,45)]
p += [(x/S, y/S) for x, y in cubic(pt(76.5,45), pt(78.5,45), pt(80,46.5), pt(80,48.5))]
p += [(80,65), (86,65)]
p += [(x/S, y/S) for x, y in cubic(pt(86,65), pt(88.2,65), pt(89.2,67.4), pt(87.6,69))]
p += [(77,80)]
p += [(x/S, y/S) for x, y in cubic(pt(77,80), pt(75.6,81.5), pt(73.4,81.5), pt(72,80))]
p += [(61.2,69)]
p += [(x/S, y/S) for x, y in cubic(pt(61.2,69), pt(59.6,67.4), pt(60.7,65), pt(62.8,65))]
p += [(69,65)]
apply_shape(img, p, fill=(255,255,255,255))

# Suavização final preservando a geometria original.
img = img.resize((512, 512), Image.Resampling.LANCZOS)
out = Path(__file__).resolve().parent / 'assets'
out.mkdir(parents=True, exist_ok=True)
img.save(out / 'getmuvi.png')
img.save(out / 'getmuvi.ico', format='ICO', sizes=[(256,256),(128,128),(64,64),(48,48),(32,32),(16,16)])
print(out / 'getmuvi.ico')
