import os
from PIL import Image
import numpy as np

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FG_IMAGE = os.path.join(PROJECT_ROOT, "app", "src", "main", "res", "drawable", "ic_launcher_foreground_dark.png")
OUTPUT_XML = os.path.join(PROJECT_ROOT, "app", "src", "main", "res", "drawable", "ic_launcher_monochrome.xml")

def generate_monochrome_vector():
    img = Image.open(FG_IMAGE).convert("RGBA")
    alpha = np.array(img.resize((108, 108), Image.Resampling.LANCZOS))[:, :, 3].astype(float)
    threshold = 120.0
    H, W = alpha.shape
    segments = []
    for y in range(H - 1):
        for x in range(W - 1):
            c = 0
            if alpha[y, x] > threshold: c |= 1
            if alpha[y, x+1] > threshold: c |= 2
            if alpha[y+1, x+1] > threshold: c |= 4
            if alpha[y+1, x] > threshold: c |= 8
            if c == 0 or c == 15: continue
            def interp(val1, val2, p1, p2):
                if abs(val2 - val1) < 1e-5: return p1
                t = (threshold - val1) / (val2 - val1)
                return (p1[0] + t * (p2[0] - p1[0]), p1[1] + t * (p2[1] - p1[1]))
            top = interp(alpha[y, x], alpha[y, x+1], (x, y), (x+1, y))
            right = interp(alpha[y, x+1], alpha[y+1, x+1], (x+1, y), (x+1, y+1))
            bottom = interp(alpha[y+1, x], alpha[y+1, x+1], (x, y+1), (x+1, y+1))
            left = interp(alpha[y, x], alpha[y+1, x], (x, y), (x, y+1))
            if c in (1, 14): segments.append((left, top))
            elif c in (2, 13): segments.append((top, right))
            elif c in (3, 12): segments.append((left, right))
            elif c in (4, 11): segments.append((right, bottom))
            elif c in (5,): segments.append((left, top)); segments.append((right, bottom))
            elif c in (6, 9): segments.append((top, bottom))
            elif c in (7, 8): segments.append((left, bottom))
            elif c in (10,): segments.append((top, left)); segments.append((bottom, right))

    loops = []
    used = [False] * len(segments)
    def pt_dist(p1, p2): return (p1[0]-p2[0])**2 + (p1[1]-p2[1])**2

    for i in range(len(segments)):
        if used[i]: continue
        loop = [segments[i][0], segments[i][1]]
        used[i] = True
        while True:
            curr = loop[-1]
            best_j = -1
            best_rev = False
            min_d = 0.5
            for j in range(len(segments)):
                if used[j]: continue
                p1, p2 = segments[j]
                d1 = pt_dist(curr, p1)
                d2 = pt_dist(curr, p2)
                if d1 < min_d: min_d = d1; best_j = j; best_rev = False
                elif d2 < min_d: min_d = d2; best_j = j; best_rev = True
            if best_j != -1:
                used[best_j] = True
                loop.append(segments[best_j][0] if best_rev else segments[best_j][1])
            else: break
        if len(loop) > 10: loops.append(loop)

    path_strs = []
    for loop in loops:
        pts = loop[::2]
        cmds = [f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"]
        for pt in pts[1:]:
            cmds.append(f"L{pt[0]:.2f},{pt[1]:.2f}")
        cmds.append("Z")
        path_strs.append(" ".join(cmds))

    full_path_data = " ".join(path_strs)

    xml_content = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- DC Ribbon Silhouette for Android 13+ Themed Icons -->
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{full_path_data}" />
</vector>
"""
    with open(OUTPUT_XML, "w", encoding="utf-8") as f:
        f.write(xml_content)
    print("Generated ic_launcher_monochrome.xml successfully!")

if __name__ == "__main__":
    generate_monochrome_vector()
