# ShiftAlarm — комплект иконок и логотипа

Знак: циферблат будильника, обод которого — лента смен 2/2
(оранжевые пары «работа», стальные — «отдых»). Стрелки на 7:00.

## Палитра

| Роль | HEX |
|---|---|
| Фон (ночной графит) | `#18222E` |
| Работа (сигнальный оранжевый) | `#FF8A1E` |
| Отдых (сталь) | `#46586C` |
| Стрелки/белый | `#F5F7FA` |
| Вторичный текст | `#9FB0C2` |

## Куда что идёт

| Файл | Назначение |
|---|---|
| `icon_512.png` | Иконка карточки: Google Play (512×512, ≤1 МБ) **и** RuStore |
| `icon_1024.png` | Мастер-растр про запас (пресса, сайт) |
| `feature_graphic_1024x500.png` | Google Play → Store listing → Feature graphic |
| `logo.svg` | Векторный мастер-логотип (полный, с колокольчиками) |
| `adaptive/ic_launcher_foreground.svg` | Слой переднего плана adaptive-иконки (в проект) |
| `adaptive/ic_launcher_foreground.png` | То же в PNG 432×432 (если не хочется SVG) |
| `adaptive/ic_launcher_monochrome.png` | Монохромный слой для тем Android 13+ |
| `gen_branding.py` | Генератор: правь цвета/пропорции и перегенерируй всё |

## Adaptive-иконка в проекте (minSdk 26 — mipmap-плотности не нужны)

Способ 1, через студию: **File → New → Image Asset → Launcher Icons (Adaptive
and Legacy)** → Foreground: `ic_launcher_foreground.svg` → Background: Color
`#18222E` → Finish. Студия сгенерирует всё сама.

Способ 2, вручную — `res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
```

`colors.xml`: `<color name="ic_launcher_background">#18222E</color>`.
Слой `monochrome` даёт красивую тонированную иконку в «Тематических значках»
Android 13+ — конкуренты этим редко заморачиваются.

## Перегенерация

`python3 gen_branding.py` — весь комплект пересобирается из одного файла.
Хочешь другие цвета, время на стрелках или ритм сегментов — меняются константы
в шапке скрипта.
