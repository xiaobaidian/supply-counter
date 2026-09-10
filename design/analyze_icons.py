"""检查 AI 生成图标右下角水印区是否能安全用背景色盖掉。"""
import glob, os
import numpy as np
from PIL import Image

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icons")

for p in sorted(glob.glob(os.path.join(D, "*.png"))):
    im = Image.open(p).convert("RGB")
    w, h = im.size
    a = np.asarray(im)

    # 水印所在的右下角区域
    reg = a[int(h * 0.84):, int(w * 0.76):]
    colors, counts = np.unique(reg.reshape(-1, 3), axis=0, return_counts=True)
    idx = counts.argmax()
    main = colors[idx]
    pct = counts[idx] / counts.sum() * 100

    # 参考：右上角（应该也是纯背景）
    ref = a[int(h * 0.05):int(h * 0.15), int(w * 0.80):int(w * 0.95)]
    rc, rn = np.unique(ref.reshape(-1, 3), axis=0, return_counts=True)
    rmain = rc[rn.argmax()]
    rpct = rn.max() / rn.sum() * 100

    print(f"{os.path.basename(p):8s} {w}x{h}  右下主色={tuple(int(x) for x in main)} {pct:5.1f}%   "
          f"右上主色={tuple(int(x) for x in rmain)} {rpct:5.1f}%   "
          f"{'可填充' if pct > 88 else '★角色占位，需另处理'}")
