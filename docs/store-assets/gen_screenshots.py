# -*- coding: utf-8 -*-
"""
Генератор витринных скриншотов для сторов (RuStore / Google Play).

Берёт сырой скриншот с устройства (1440×3200), обрезает системный статусбар и
жест-полоску, кладёт на графитовый фон с крупным заголовком-оверлеем и оранжевым
акцентом — в фирменных цветах иконки (Fable). Выход: 1080×2400 PNG без альфы.

Запуск: python docs/store-assets/gen_screenshots.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "screenshots", "raw")
OUT = os.path.join(HERE, "screenshots")

# --- Фирменная палитра (совпадает с иконкой) ---
GRAPHITE = (0x18, 0x22, 0x2E)
ORANGE = (0xFF, 0x8A, 0x1E)
WHITE = (0xF5, 0xF7, 0xFA)
STEEL = (0x8A, 0x9B, 0xAE)

# --- Размеры холста витрины ---
CANVAS_W, CANVAS_H = 1080, 2400
CAPTION_TOP = 96          # отступ заголовка сверху
SIDE_MARGIN = 70          # поля скриншота по бокам
IMG_BOTTOM_MARGIN = 70    # нижний отступ скриншота
CORNER = 30               # скругление углов скриншота

# --- Обрезка сырого кадра (устройство 1440×3200) ---
CROP_TOP = 96             # системный статусбар
CROP_BOTTOM = 3200 - 48   # жест-полоска снизу

FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_SEMI = "C:/Windows/Fonts/segoeui.ttf"


def _font(path, size):
    return ImageFont.truetype(path, size)


def _text_w(draw, text, font):
    b = draw.textbbox((0, 0), text, font=font)
    return b[2] - b[0]


def make_shot(raw_name, caption_lines, out_name, accent_line=None, kicker=None):
    """caption_lines — список строк заголовка; accent_line — индекс строки оранжевым (или None)."""
    raw = Image.open(os.path.join(RAW, raw_name)).convert("RGB")
    # Обрезаем статусбар и жест-полоску.
    content = raw.crop((0, CROP_TOP, raw.width, CROP_BOTTOM))

    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), GRAPHITE)
    draw = ImageDraw.Draw(canvas)

    y = CAPTION_TOP

    # Кикер (маленькая надпись над заголовком) — опционально.
    if kicker:
        kf = _font(FONT_BOLD, 34)
        kw = _text_w(draw, kicker, kf)
        draw.text(((CANVAS_W - kw) // 2, y), kicker, font=kf, fill=ORANGE)
        y += 54

    # Заголовок (1–2 строки), по центру.
    cf = _font(FONT_BOLD, 66)
    line_h = 84
    for i, line in enumerate(caption_lines):
        col = ORANGE if accent_line == i else WHITE
        lw = _text_w(draw, line, cf)
        draw.text(((CANVAS_W - lw) // 2, y), line, font=cf, fill=col)
        y += line_h
    y += 18

    # Оранжевый акцент-подчёркивание — только если ни одна строка не выделена цветом
    # (иначе оно дублирует оранжевую строку и читается как подчёркивание одного слова).
    if accent_line is None:
        bar_w, bar_h = 132, 9
        bx = (CANVAS_W - bar_w) // 2
        draw.rounded_rectangle((bx, y, bx + bar_w, y + bar_h), radius=bar_h // 2, fill=ORANGE)
        y += bar_h
    y += 46

    # Область под скриншот.
    area_top = y
    area_h = CANVAS_H - area_top - IMG_BOTTOM_MARGIN
    area_w = CANVAS_W - 2 * SIDE_MARGIN
    scale = min(area_w / content.width, area_h / content.height)
    sw, sh = int(content.width * scale), int(content.height * scale)
    shot = content.resize((sw, sh), Image.LANCZOS)

    # Скруглённые углы через маску.
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw, sh), radius=CORNER, fill=255)
    sx = (CANVAS_W - sw) // 2
    sy = area_top + (area_h - sh) // 2
    canvas.paste(shot, (sx, sy), mask)

    # Тонкая стальная рамка вокруг скриншота.
    ImageDraw.Draw(canvas).rounded_rectangle(
        (sx, sy, sx + sw - 1, sy + sh - 1), radius=CORNER, outline=STEEL, width=2
    )

    out_path = os.path.join(OUT, out_name)
    canvas.save(out_path, "PNG")
    print(f"  {out_name}: {CANVAS_W}x{CANVAS_H}  (скриншот {sw}x{sh})")
    return out_path


SHOTS = [
    dict(
        raw_name="01_list.png",
        kicker="БУДИЛЬНИК РАБОТЯГИ",
        caption_lines=["Знает твой график.", "Будит только в смену"],
        accent_line=1,
        out_name="01_list_framed.png",
    ),
    dict(
        raw_name="02_calendar.png",
        kicker="ГРАФИК СМЕН",
        caption_lines=["Весь месяц", "как на ладони"],
        accent_line=1,
        out_name="02_calendar_framed.png",
    ),
    dict(
        raw_name="03_cycle.png",
        kicker="ЛЮБОЙ ГРАФИК",
        caption_lines=["2/2, сутки-трое", "или свой цикл"],
        accent_line=1,
        out_name="03_cycle_framed.png",
    ),
    dict(
        raw_name="04_ring.png",
        kicker="НЕ ПРОСПИШЬ",
        caption_lines=["Разбудит", "наверняка"],
        accent_line=1,
        out_name="04_ring_framed.png",
    ),
    dict(
        raw_name="05_periods.png",
        kicker="ПЕРЕРЫВЫ",
        caption_lines=["В отпуск —", "без будильника"],
        accent_line=1,
        out_name="05_periods_framed.png",
    ),
    dict(
        raw_name="06_study.png",
        kicker="УЧЁБА",
        caption_lines=["Школьникам", "и студентам"],
        accent_line=1,
        out_name="06_study_framed.png",
    ),
    dict(
        raw_name="07_reliability.png",
        kicker="ЧЕСТНЫЙ БУДИЛЬНИК",
        caption_lines=["Оффлайн, без", "рекламы и слежки"],
        accent_line=1,
        out_name="07_reliability_framed.png",
    ),
]


if __name__ == "__main__":
    print("Генерация витринных кадров:")
    for s in SHOTS:
        make_shot(**s)
    print("Готово. Папка:", OUT)
