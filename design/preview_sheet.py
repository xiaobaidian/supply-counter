"""把去水印后的 6 张候选拼成一张预览图（含圆形裁切效果）。"""
import glob, os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "icons_clean")

files = sorted(glob.glob(os.path.join(SRC, "*.png")))
CELL = 190
PAD = 18
cols, rows = 3, 2
W = cols * (CELL + PAD) + PAD
H = rows * (CELL + PAD + 26) + PAD
canvas = Image.new("RGB", (W, H), (240, 242, 245))
d = ImageDraw.Draw(canvas)

for i, p in enumerate(files):
    name = os.path.basename(p)[:-4]
    im = Image.open(p).convert("RGB")

    # 圆形裁切预览（模拟启动器）
    sq = im.resize((CELL, CELL), Image.LANCZOS)
    mask = Image.new("L", (CELL * 4, CELL * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, CELL * 4 - 1, CELL * 4 - 1), fill=255)
    mask = mask.resize((CELL, CELL), Image.LANCZOS)

    x = PAD + (i % cols) * (CELL + PAD)
    y = PAD + (i // cols) * (CELL + PAD + 26)
    canvas.paste(sq, (x, y), mask)
    d.text((x + 4, y + CELL + 6), name, fill=(30, 40, 50))

out = os.path.join(HERE, "_preview_sheet.png")
canvas.save(out)
print(out, canvas.size)
