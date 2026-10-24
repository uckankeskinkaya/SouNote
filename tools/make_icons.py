#!/usr/bin/env python3
"""ui/Icons.kt üretici (Material Symbols Rounded 400, Apache 2.0).

Kullanım:
  npm pack @material-symbols/svg-400 && tar xzf material-symbols-svg-400-*.tgz
  python3 tools/make_icons.py package/rounded
Yeni simge: NAMES'e (dolu sürüm de gerekiyorsa FILLED'e) dosya adını ekle, yeniden üret.
"""
import re, sys

NAMES = list(dict.fromkeys("""home folder library_books school calendar_month edit_note settings search add close check check_circle star keep history schedule timer local_fire_department menu_book description picture_as_pdf draw stylus ink_pen ink_highlighter ink_eraser gesture_select text_fields image mic category approval undo redo more_vert arrow_back chevron_right keyboard_arrow_down keyboard_arrow_up share download upload cloud_upload lock palette tune dark_mode light_mode notifications help info bug_report person star_shine style bookmark delete settings_backup_restore sort grid_view view_list filter_list fullscreen fullscreen_exit translate auto_stories headphones record_voice_over extension spellcheck layers checklist assignment task_alt flag bar_chart calculate backup sync link visibility visibility_off print content_copy drag_indicator keyboard touch_app pan_tool format_bold format_italic format_list_bulleted calendar_today alarm trophy lightbulb rocket_launch play_arrow pause stop edit bolt quiz psychology swap_vert open_in_new folder_open note_add create_new_folder article contrast format_size text_snippet event done_all radio_button_unchecked circle trending_up sticky_note_2 timer_play label account_circle widgets dashboard_customize view_agenda view_week view_timeline graphic_eq highlight bookmarks fit_screen zoom_in north_east chat send stop_circle refresh remove arrow_forward co_present slideshow shapes interests wand_stars restore_from_trash chevron_left arrow_drop_down menu rectangle change_history north_east diagonal_line stylus_laser_pointer ink_marker pen_size_2 straighten architecture mood""".split()))
FILLED = """home folder school calendar_month edit_note settings star keep bookmark check_circle library_books description""".split()

HEADER = 'package com.sounote.app.ui\n\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.SolidColor\nimport androidx.compose.ui.graphics.vector.ImageVector\nimport androidx.compose.ui.graphics.vector.addPathNodes\nimport androidx.compose.ui.graphics.vector.group\nimport androidx.compose.ui.unit.dp\n\n/*\n * Uygulamanın simge seti: Material Symbols Rounded (400, Apache 2.0), emoji yerine her yerde bunlar.\n * Dosya tools/make_icons.py ile üretilir; elle düzenleme, betiğe ad ekleyip yeniden üret.\n */\n\nprivate fun icon(name: String, d: String): ImageVector =\n    ImageVector.Builder(name, 24.dp, 24.dp, 960f, 960f).apply {\n        group(translationY = 960f) { addPath(addPathNodes(d), fill = SolidColor(Color.Black)) }\n    }.build()\n\nobject SouIcons {'

def path(root, f):
    s = open(f"{root}/{f}.svg").read()
    ps = re.findall(r'<path d="([^"]+)"', s)
    assert len(ps) == 1, f
    return ps[0]

def camel(n):
    return "".join(p.capitalize() for p in n.split("_"))

def main(root):
    out = [HEADER]
    for n in NAMES:
        out.append(f'    val {camel(n)} by lazy {{ icon("{n}", "{path(root, n)}") }}')
    for n in FILLED:
        out.append(f'    val {camel(n)}Filled by lazy {{ icon("{n}_fill", "{path(root, n + "-fill")}") }}')
    out.append("}\n")
    open("app/src/main/java/com/sounote/app/ui/Icons.kt", "w").write("\n".join(out))

if __name__ == "__main__":
    main(sys.argv[1])
