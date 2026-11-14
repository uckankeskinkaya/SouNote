#!/usr/bin/env python3
"""SouNote 4.0 logosu: köşesi kıvrık beyaz kâğıt üstünde kalemle yazılmış "S" (her tema kendi vurgu renginde).

Üretilenler (app/src/main/res/drawable):
  logo_v_<tema>.xml   uygulama içi kare logo (AppLogo, widget)
  ic_fg_<tema>.xml    uygulama simgesi ön planı (kâğıt + S), ic_bg_<tema>.xml zemin
  ic_launcher_monochrome.xml  temalı simge (Android 13+)
Önizleme ve site için SVG: --svg <klasör>
Kullanım: python3 tools/make_logo4.py [--svg out_dir]
"""
import os, sys

THEMES = {  # tema → vurgu (açık temadaki birincil renk)
    "electric": "#3346D3", "sapphire": "#2A52A0", "silver": "#4A5F86",
    "mono": "#1F1F1F", "emerald": "#256E48", "rose": "#C2356F",
}
PAGE = "M37,26 H64 L76,38 V77 A5,5 0 0 1 71,82 H37 A5,5 0 0 1 32,77 V31 A5,5 0 0 1 37,26 Z"
FOLD = "M64,26 V35 A3,3 0 0 0 67,38 H76 Z"
S = "M64.5,46 C61.5,40.8 48.5,40.6 47.2,48 C45.9,55.2 63.6,54.6 61.4,64 C59.4,72.2 44.8,72.6 40.6,65.2 M40.6,65.2 C40,64.2 39.8,63.2 40.4,62.4"

def mix(hex_a, hex_b, t):
    a = [int(hex_a[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(hex_b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#%02X%02X%02X" % tuple(round(x + (y - x) * t) for x, y in zip(a, b))

def page_paths(accent, indent="        "):
    fold = mix(accent, "#FFFFFF", 0.72)
    return (
        f'{indent}<path android:fillColor="#FFFFFF" android:pathData="{PAGE}" />\n'
        f'{indent}<path android:fillColor="{fold}" android:pathData="{FOLD}" />\n'
        f'{indent}<path android:strokeColor="{accent}" android:strokeWidth="4.6" android:strokeLineCap="round"\n'
        f'{indent}    android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="{S}" />\n'
    )

def vector(body, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} (tools/make_logo4.py ile üretildi; elle düzenleme) -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            + body + '</vector>\n')

def main():
    res = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")
    for name, accent in THEMES.items():
        tile = (f'    <path android:fillColor="{accent}" android:pathData="M24,0 H84 A24,24 0 0 1 108,24 V84 A24,24 0 0 1 84,108 H24 A24,24 0 0 1 0,84 V24 A24,24 0 0 1 24,0 Z" />\n'
                '    <group android:pivotX="54" android:pivotY="54" android:scaleX="1.32" android:scaleY="1.32">\n'
                + page_paths(accent) + '    </group>\n')
        open(os.path.join(res, f"logo_v_{name}.xml"), "w").write(vector(tile, f"SouNote logosu, {name} teması"))
        fg = '    <group android:pivotX="54" android:pivotY="54" android:scaleX="1.08" android:scaleY="1.08">\n' + page_paths(accent) + '    </group>\n'
        open(os.path.join(res, f"ic_fg_{name}.xml"), "w").write(vector(fg, f"Uygulama simgesi ön planı, {name}"))
        bg = f'    <path android:fillColor="{accent}" android:pathData="M0,0 H108 V108 H0 Z" />\n'
        open(os.path.join(res, f"ic_bg_{name}.xml"), "w").write(vector(bg, f"Uygulama simgesi zemini, {name}"))
    mono = ('    <group android:pivotX="54" android:pivotY="54" android:scaleX="1.08" android:scaleY="1.08">\n'
            f'        <path android:fillColor="#FFFFFFFF" android:pathData="{PAGE}" />\n'
            f'        <path android:strokeColor="#FF000000" android:strokeWidth="4.6" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="{S}" />\n'
            '    </group>\n')
    open(os.path.join(res, "ic_launcher_monochrome.xml"), "w").write(vector(mono, "Temalı uygulama simgesi (tek renk)"))

    if "--svg" in sys.argv:
        out = sys.argv[sys.argv.index("--svg") + 1]
        os.makedirs(out, exist_ok=True)
        for name, accent in THEMES.items():
            fold = mix(accent, "#FFFFFF", 0.72)
            svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">'
                   f'<rect width="108" height="108" rx="24" fill="{accent}"/>'
                   f'<g transform="translate(54 54) scale(1.32) translate(-54 -54)">'
                   f'<path fill="#fff" d="{PAGE}"/><path fill="{fold}" d="{FOLD}"/>'
                   f'<path fill="none" stroke="{accent}" stroke-width="4.6" stroke-linecap="round" stroke-linejoin="round" d="{S}"/></g></svg>')
            open(os.path.join(out, f"logo-{name}.svg"), "w").write(svg)

if __name__ == "__main__":
    main()
