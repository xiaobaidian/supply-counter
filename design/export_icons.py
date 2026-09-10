"""去掉 AI 水印并导出 Android 各密度图标。

用法: python export_icons.py [选中的候选名，默认 A1]
"""
import os, sys, shutil
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "icons")
CLEAN = os.path.join(HERE, "icons_clean")
RES = os.path.abspath(os.path.join(HERE, "..", "app", "src", "main", "res"))

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def clean_watermark(path):
    """水印在右下角，用其上方一行的像素向下复制填充（该区域是纯色背景）。"""
    im = Image.open(path).convert("RGB")
    w, h = im.size
    name = os.path.basename(path)[:-4]

    # A2 的水印压在角色上，改用裁剪（右侧 12% / 底部 8% 去掉后仍是正方形）
    if name == "A2":
        return im.crop((0, 0, int(w * 0.88), int(h * 0.92))).resize((w, h), Image.LANCZOS)

    a = np.asarray(im).copy()
    x0, y0 = int(w * 0.70), int(h * 0.82)
    row = a[y0 - 4, :, :]
    a[y0:, x0:] = row[x0:][None, :, :]
    return Image.fromarray(a)


def compose(im, canvas_px, ratio):
    """把图缩到画布的 ratio 比例后居中，四周补该图的角部背景色。"""
    bg = tuple(int(v) for v in np.asarray(im)[3, 3])
    inner = int(canvas_px * ratio)
    out = Image.new("RGB", (canvas_px, canvas_px), bg)
    off = (canvas_px - inner) // 2
    out.paste(im.resize((inner, inner), Image.LANCZOS), (off, off))
    return out


def main():
    pick = (sys.argv[1] if len(sys.argv) > 1 else "A1").upper()
    if os.path.isdir(CLEAN):
        shutil.rmtree(CLEAN)
    os.makedirs(CLEAN)

    cleaned = {}
    for f in sorted(os.listdir(SRC)):
        if not f.lower().endswith(".png"):
            continue
        img = clean_watermark(os.path.join(SRC, f))
        img.save(os.path.join(CLEAN, f), "PNG", optimize=True)
        cleaned[os.path.basename(f)[:-4]] = img

    src = cleaned[pick]
    bg = tuple(int(v) for v in np.asarray(src)[3, 3])
    print(f"选中 {pick}，背景色 = #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")

    # 传统图标（各密度）
    for d, px in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{d}")
        os.makedirs(folder, exist_ok=True)
        icon = compose(src, px, 0.86)
        icon.save(os.path.join(folder, "ic_launcher.png"), "PNG", optimize=True)
        icon.save(os.path.join(folder, "ic_launcher_round.png"), "PNG", optimize=True)

    # 自适应图标前景（108dp 画布，内容收在中心安全区内）
    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    compose(src, 432, 0.66).save(os.path.join(nodpi, "ic_launcher_foreground.png"), "PNG", optimize=True)

    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/ic_launcher_background"/>\n'
           '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
           '</adaptive-icon>\n')
    for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(anydpi, n), "w", encoding="utf-8").write(xml)

    print("已导出:", RES)


if __name__ == "__main__":
    main()
