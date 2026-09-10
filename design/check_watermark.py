"""把 6 张候选图的右下角（水印所在区域）拼成一张对照图。"""
import glob, os
from PIL import Image, ImageDraw

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icons")
files = sorted(glob.glob(os.path.join(D, "*.png")))

tiles = []
for p in files:
    im = Image.open(p).convert("RGB")
    w, h = im.size
    tiles.append((os.path.basename(p)[:-4], im.crop((int(w * 0.68), int(h * 0.78), w, h))))

tw, th = tiles[0][1].size
cols, rows = 2, 3
canvas = Image.new("RGB", (cols * tw, rows * th), (255, 255, 255))
d = ImageDraw.Draw(canvas)
for i, (name, t) in enumerate(tiles):
    x, y = (i % cols) * tw, (i // cols) * th
    canvas.paste(t, (x, y))
    d.rectangle([x + 6, y + 6, x + 80, y + 34], fill=(255, 255, 255))
    d.text((x + 14, y + 14), name, fill=(200, 0, 0))

out = os.path.join(os.path.dirname(D), "_watermark_check.png")
canvas.save(out)
print(out, canvas.size)
