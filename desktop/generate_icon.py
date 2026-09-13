from pathlib import Path
from PIL import Image, ImageDraw

SIZE = 512
img = Image.new("RGBA", (SIZE, SIZE), (7, 20, 45, 255))
d = ImageDraw.Draw(img, "RGBA")

# Fundo arredondado e camadas que reproduzem a identidade aprovada do GetMuvi.
d.rounded_rectangle((8, 8, 504, 504), radius=78, fill=(7, 24, 62, 255))
for y, color, alpha in [
    (365, (18, 124, 255), 205),
    (410, (56, 232, 226), 180),
    (445, (124, 70, 255), 190),
]:
    d.rounded_rectangle((12, y, 500, 500), radius=70, fill=(*color, alpha))

# Play/camada principal.
d.polygon([(118, 90), (118, 390), (408, 240)], fill=(37, 194, 255, 255))
d.polygon([(240, 136), (420, 240), (250, 350)], fill=(124, 70, 255, 215))

# Nota musical.
d.rounded_rectangle((188, 152, 224, 338), radius=16, fill=(255, 255, 255, 255))
d.rounded_rectangle((210, 142, 330, 176), radius=16, fill=(255, 255, 255, 255))
d.ellipse((134, 304, 226, 396), fill=(255, 255, 255, 255))

# Seta de download central e reta.
d.rounded_rectangle((320, 220, 354, 324), radius=14, fill=(255, 255, 255, 255))
d.polygon([(287, 310), (387, 310), (337, 370)], fill=(255, 255, 255, 255))

out = Path(__file__).resolve().parent / "assets"
out.mkdir(parents=True, exist_ok=True)
img.save(out / "getmuvi.png")
img.save(out / "getmuvi.ico", format="ICO", sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
print(out / "getmuvi.ico")
