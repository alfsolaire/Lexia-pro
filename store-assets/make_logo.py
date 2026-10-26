"""Génère le logo Alf TV Pro : icône, bannière Fire TV et visuels de la fiche Appstore."""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1]
SS = 4  # suréchantillonnage pour l'anticrénelage
FONT_BLACK = "/usr/share/fonts/opentype/inter/Inter-Black.otf"
FONT_BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
FONT_SEMI = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"

BG_TOP = (10, 14, 32)
BG_BOTTOM = (22, 34, 74)
C1 = (46, 155, 255)   # bleu
C2 = (139, 92, 246)   # violet
C3 = (236, 72, 153)   # rose


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size, colors, diagonal=True):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    stops = len(colors) - 1
    for y in range(h):
        for x in range(w):
            t = ((x / max(w - 1, 1)) + (y / max(h - 1, 1))) / 2 if diagonal else y / max(h - 1, 1)
            seg = min(int(t * stops), stops - 1)
            lt = t * stops - seg
            px[x, y] = lerp(colors[seg], colors[seg + 1], lt)
    return img


def fast_gradient(size, colors, diagonal=True):
    # Calcul sur une petite image puis agrandissement (rapide et lisse).
    small = gradient((64, 64), colors, diagonal)
    return small.resize(size, Image.BICUBIC)


def background(size):
    w, h = size
    bg = fast_gradient(size, [BG_TOP, BG_BOTTOM], diagonal=False)
    # Halo lumineux derrière l'emblème
    glow = Image.new("L", size, 0)
    d = ImageDraw.Draw(glow)
    r = int(min(w, h) * 0.55)
    cx, cy = int(w * 0.5), int(h * 0.42)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=110)
    glow = glow.filter(ImageFilter.GaussianBlur(r * 0.45))
    tint = Image.new("RGB", size, C2)
    return Image.composite(tint, bg, glow)


def paste_gradient(base, mask, colors):
    grad = fast_gradient(base.size, colors)
    base.paste(grad, (0, 0), mask)


def emblem(base, cx, cy, s):
    """Écran arrondi dégradé avec un bouton lecture, centré sur (cx, cy), largeur s."""
    w, h = base.size
    sw, sh = s, int(s * 0.74)
    x0, y0 = cx - sw // 2, cy - sh // 2
    radius = int(s * 0.17)
    stroke = int(s * 0.075)

    # Ombre portée
    shadow = Image.new("L", base.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle([x0, y0 + s * 0.05, x0 + sw, y0 + sh + s * 0.05], radius, fill=140)
    shadow = shadow.filter(ImageFilter.GaussianBlur(s * 0.06))
    base.paste(Image.new("RGB", base.size, (3, 5, 15)), (0, 0), shadow)

    # Cadre de l'écran (anneau dégradé)
    ring = Image.new("L", base.size, 0)
    d = ImageDraw.Draw(ring)
    d.rounded_rectangle([x0, y0, x0 + sw, y0 + sh], radius, fill=255)
    d.rounded_rectangle([x0 + stroke, y0 + stroke, x0 + sw - stroke, y0 + sh - stroke], radius - stroke, fill=0)
    paste_gradient(base, ring, [C1, C2, C3])

    # Intérieur de l'écran légèrement teinté
    inner = Image.new("L", base.size, 0)
    ImageDraw.Draw(inner).rounded_rectangle(
        [x0 + stroke, y0 + stroke, x0 + sw - stroke, y0 + sh - stroke], radius - stroke, fill=255)
    base.paste(Image.new("RGB", base.size, (16, 22, 52)), (0, 0), inner)

    # Bouton lecture (triangle arrondi)
    tri = Image.new("L", base.size, 0)
    td = ImageDraw.Draw(tri)
    th = sh * 0.46
    tx = cx - th * 0.36
    pts = [(tx, cy - th / 2), (tx, cy + th / 2), (tx + th * 0.9, cy)]
    td.polygon(pts, fill=255)
    tri = tri.filter(ImageFilter.GaussianBlur(s * 0.012)).point(lambda v: 255 if v > 110 else 0)
    paste_gradient(base, tri, [(120, 200, 255), C1, C2])

    # Pied de l'écran
    foot = Image.new("L", base.size, 0)
    fd = ImageDraw.Draw(foot)
    fw, fh = sw * 0.34, s * 0.05
    fd.rounded_rectangle([cx - fw / 2, y0 + sh + s * 0.07, cx + fw / 2, y0 + sh + s * 0.07 + fh], fh / 2, fill=255)
    paste_gradient(base, foot, [C1, C2, C3])


def text_width(draw, text, font):
    l, t, r, b = draw.textbbox((0, 0), text, font=font)
    return r - l


def pro_badge(base, x, y, height, text="PRO"):
    d = ImageDraw.Draw(base)
    font = ImageFont.truetype(FONT_BLACK, int(height * 0.62))
    tw = text_width(d, text, font)
    pad = height * 0.42
    w = tw + pad * 2
    mask = Image.new("L", base.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([x, y, x + w, y + height], height * 0.28, fill=255)
    paste_gradient(base, mask, [C1, C2, C3])
    l, t, r, b = d.textbbox((0, 0), text, font=font)
    d.text((x + pad - l, y + (height - (b - t)) / 2 - t), text, font=font, fill=(255, 255, 255))
    return w


def finish(img, size, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize(size, Image.LANCZOS).save(path, optimize=True)
    print("ok", path, size)


def make_icon(size, path, rounded=False):
    S = size * SS
    img = background((S, S))
    emblem(img, S // 2, int(S * 0.40), int(S * 0.62))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT_BLACK, int(S * 0.15))
    word = "ALF TV"
    gap = S * 0.025
    badge_h = S * 0.105
    ww = text_width(d, word, font)
    badge_font = ImageFont.truetype(FONT_BLACK, int(badge_h * 0.62))
    bw = text_width(d, "PRO", badge_font) + badge_h * 0.84
    total = ww + gap + bw
    x = (S - total) / 2
    ty = S * 0.73
    l, t, r, b = d.textbbox((0, 0), word, font=font)
    d.text((x - l, ty - t), word, font=font, fill=(255, 255, 255))
    pro_badge(img, x + ww + gap, ty + ((b - t) - badge_h) / 2, badge_h)
    if rounded:
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S, S], int(S * 0.22), fill=255)
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        img = out
    finish(img, (size, size), path)


def make_banner(w, h, path, subtitle=True):
    W, H = w * SS, h * SS
    img = background((W, H))
    d = ImageDraw.Draw(img)
    eh = int(H * 0.52)            # largeur de l'emblème
    gap_e = W * 0.045             # espace emblème / texte
    word = "ALF TV"
    # Taille de police choisie pour que l'ensemble tienne dans 86 % de la largeur.
    size = int(H * 0.28)
    while True:
        font = ImageFont.truetype(FONT_BLACK, size)
        l, t, r, b = d.textbbox((0, 0), word, font=font)
        badge_h = (b - t) * 0.62
        badge_font = ImageFont.truetype(FONT_BLACK, int(badge_h * 0.62))
        bw = text_width(d, "PRO", badge_font) + badge_h * 0.84
        gap_b = W * 0.02
        total = eh + gap_e + (r - l) + gap_b + bw
        if total <= W * 0.86 or size < 10:
            break
        size = int(size * 0.95)
    x0 = (W - total) / 2
    text_h = (b - t)
    sub_h = H * 0.085 if subtitle else 0
    block_h = text_h + (H * 0.07 + sub_h if subtitle else 0)
    ty = (H - block_h) / 2
    emblem(img, int(x0 + eh / 2), int(H * 0.47), eh)
    d = ImageDraw.Draw(img)
    x = x0 + eh + gap_e
    d.text((x - l, ty - t), word, font=font, fill=(255, 255, 255))
    pro_badge(img, x + (r - l) + gap_b, ty + (text_h - badge_h) / 2, badge_h)
    if subtitle:
        sfont = ImageFont.truetype(FONT_SEMI, int(sub_h))
        sub = "I P T V   P L A Y E R"
        sl, st, sr, sb = d.textbbox((0, 0), sub, font=sfont)
        d.text((x - sl, ty + text_h + H * 0.07 - st), sub, font=sfont, fill=(160, 175, 210))
    finish(img, (w, h), path)


res = os.path.join(OUT, "app/src/main/res")
store = os.path.join(OUT, "store-assets")
make_icon(192, os.path.join(res, "mipmap-xxxhdpi/ic_launcher.png"), rounded=True)
make_icon(144, os.path.join(res, "mipmap-xxhdpi/ic_launcher.png"), rounded=True)
make_icon(96, os.path.join(res, "mipmap-xhdpi/ic_launcher.png"), rounded=True)
make_icon(72, os.path.join(res, "mipmap-hdpi/ic_launcher.png"), rounded=True)
make_icon(48, os.path.join(res, "mipmap-mdpi/ic_launcher.png"), rounded=True)
make_banner(640, 360, os.path.join(res, "drawable-xhdpi/banner.png"))
make_icon(512, os.path.join(store, "icon-512.png"))
make_icon(114, os.path.join(store, "icon-114.png"))
make_banner(1280, 720, os.path.join(store, "firetv-banner-1280x720.png"))
make_banner(1920, 1080, os.path.join(store, "firetv-background-1920x1080.png"))
