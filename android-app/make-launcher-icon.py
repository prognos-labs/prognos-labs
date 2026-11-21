#!/usr/bin/env python3
"""Regenerate the Prognos Labs launcher icon from the real logo v3.

Choice (Flint, 20 Nov voice call): lime #C8F542 background with the
black P mark. The old icon was a white background with a black logo,
which Flint called boring. Lime is the app's own accent colour, so the
icon stands out on a home screen and matches the app; the black P is
the real logo v3, unchanged, not redrawn or AI-made.

The logo file is black on a white square with no transparency, so the
mark is recovered from luminance (white -> transparent, black ->
opaque), which keeps the antialiased edges smooth on any background.

Outputs (proper adaptive icon + legacy fallbacks):
  mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png
      108dp canvas, mark centred in the 66dp safe zone (60dp here).
  mipmap-*/ic_launcher.png
      48dp legacy icon: lime square, black P centred (~70% of canvas).
  mipmap-*/ic_launcher_round.png
      48dp round icon: lime circle on transparent, black P centred.
The background layer stays in res/drawable/ic_launcher_background.xml
(solid #C8F542); the anydpi-v26 XMLs already point at it.
"""
import os
from PIL import Image

SRC = "/home/hatch/workspace/prognos-labs/prognos-labs-logo-v3.png"
RES = "/home/hatch/workspace/prognos-labs/android-app/app/src/main/res"
LIME = (200, 245, 66, 255)   # #C8F542
INK = (17, 17, 17, 255)      # #111111

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

im = Image.open(SRC).convert("RGB")
gray = im.convert("L")
# White becomes transparent, black opaque; grey edge pixels keep a
# partial alpha, so the mark stays smooth on the lime background.
alpha = gray.point(lambda v: 255 - v)
black_layer = Image.new("RGBA", im.size, INK)
mark_full = Image.composite(black_layer, Image.new("RGBA", im.size, (0, 0, 0, 0)), alpha)
bbox = alpha.getbbox()
assert bbox, "logo has no dark pixels"
# Small breathing room around the mark, in source pixels.
pad = int(max(bbox[2] - bbox[0], bbox[3] - bbox[1]) * 0.03)
bbox = (max(0, bbox[0] - pad), max(0, bbox[1] - pad),
        min(im.width, bbox[2] + pad), min(im.height, bbox[3] + pad))
mark = mark_full.crop(bbox)
print("mark bbox:", bbox, "cropped size:", mark.size)


def paste_centred(canvas, mark_img, target_max_px):
    """Scale the mark so its longest side is target_max_px, centre it."""
    mw, mh = mark_img.size
    scale = target_max_px / max(mw, mh)
    nw, nh = max(1, round(mw * scale)), max(1, round(mh * scale))
    resized = mark_img.resize((nw, nh), Image.LANCZOS)
    canvas.alpha_composite(resized, ((canvas.width - nw) // 2, (canvas.height - nh) // 2))
    return canvas


for name, scale in DENSITIES.items():
    # --- adaptive foreground: 108dp, mark inside the 66dp safe zone ---
    fg_px = round(108 * scale)
    fg = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
    paste_centred(fg, mark, round(60 * scale))
    fg.save(os.path.join(RES, f"mipmap-{name}", "ic_launcher_foreground.png"))

    # --- legacy square icon: 48dp, lime square, mark ~70% of canvas ---
    lg_px = round(48 * scale)
    sq = Image.new("RGBA", (lg_px, lg_px), LIME)
    paste_centred(sq, mark, round(34 * scale))
    sq.save(os.path.join(RES, f"mipmap-{name}", "ic_launcher.png"))

    # --- legacy round icon: 48dp, lime circle, mark inside it ---
    circ = Image.new("RGBA", (lg_px, lg_px), (0, 0, 0, 0))
    mask = Image.new("L", (lg_px, lg_px), 0)
    from PIL import ImageDraw
    ImageDraw.Draw(mask).ellipse([0, 0, lg_px - 1, lg_px - 1], fill=255)
    lime_disc = Image.new("RGBA", (lg_px, lg_px), LIME)
    circ.paste(lime_disc, (0, 0), mask)
    paste_centred(circ, mark, round(31 * scale))
    circ.save(os.path.join(RES, f"mipmap-{name}", "ic_launcher_round.png"))

    print(f"{name}: foreground {fg_px}px, legacy {lg_px}px")

# --- preview: adaptive (square + round mask) and legacy, at xxxhdpi ---
prev = Image.new("RGBA", (432 * 3 + 40, 432 + 20), (255, 255, 255, 255))
fg_big = Image.new("RGBA", (432, 432), LIME)
fg_big.alpha_composite(Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png")))
prev.alpha_composite(fg_big, (10, 10))
round_prev = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_round.png")).resize((432, 432), Image.LANCZOS)
prev.alpha_composite(round_prev, (452, 10))
sq_prev = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher.png")).resize((432, 432), Image.LANCZOS)
prev.alpha_composite(sq_prev, (894, 10))
prev.convert("RGB").save("/tmp/prognos-icon-preview.png")
print("preview: /tmp/prognos-icon-preview.png")
