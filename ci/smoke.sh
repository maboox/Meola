#!/usr/bin/env bash
# Emulator smoke test: install, click through setup, open the main screens, take screenshots,
# and fail if the app crashed at any point.
set -u
PKG=ir.nama.launcher
mkdir -p shots
APK=$(ls apk/*.apk | head -1)
echo "Installing $APK"
adb install -r "$APK" || exit 1
adb logcat -c

W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)
echo "Screen ${W}x${H}"

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }

# Taps the first on-screen element whose text contains $1 (uses the accessibility tree).
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
needle = sys.argv[1]
xml = open('/tmp/ui.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if needle in m.group(1):
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped '$1' at $xy"; return 0; fi
  echo "text not found: $1"; return 1
}

crashed() {
  adb logcat -d | grep -q "FATAL EXCEPTION" && return 0
  return 1
}

# --- Setup -----------------------------------------------------------------------------------
adb shell am start -n $PKG/.ui.EntryActivity
shot 01_setup_welcome 6
tap_text "ادامه"; shot 02_setup_style
tap_text "ادامه"; tap_text "شعر و ادبیات"; tap_text "اقتصاد و بازار"; tap_text "تکنولوژی"; shot 03_setup_interests 1
tap_text "ادامه"; tap_text "کارمند"; shot 04_setup_persona 1
tap_text "ادامه"; shot 05_setup_places
tap_text "فعلاً فقط امتحانش کنم"
shot 06_home_first 10

# Make Nama the home app on the emulator so the home button returns to it.
adb shell cmd role add-role-holder android.app.role.HOME $PKG 2>/dev/null || adb shell cmd package set-home-activity $PKG/.ui.MainActivity
adb shell input keyevent KEYCODE_HOME
shot 07_home_after_home_key 4

# --- Drawer, search, news, menus ---------------------------------------------------------------
adb shell input swipe $((W/2)) $((H*85/100)) $((W/2)) $((H*20/100)) 250
shot 08_drawer 3
adb shell input keyevent KEYCODE_BACK
tap_text "جستجو یا فرمان"; sleep 2
adb shell input text "1405/7/17"; shot 09_search_date 3
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK
tap_text "جستجو یا فرمان"; sleep 2
adb shell input text "250000"; shot 10_search_words 3
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK
adb shell input swipe $((W*15/100)) $((H/2)) $((W*85/100)) $((H/2)) 300
shot 11_swipe_right 4
adb shell input swipe $((W*85/100)) $((H/2)) $((W*15/100)) $((H/2)) 300
shot 12_swipe_left 3
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input swipe $((W*85/100)) $((H/2)) $((W*15/100)) $((H/2)) 300
shot 13_page2 3
adb shell input keyevent KEYCODE_HOME
# Long press on the space chip area opens the space switcher; long press low on the page opens the home menu.
tap_text "بدون فضا" || tap_text "●"
shot 14_space_switcher 2
adb shell input keyevent KEYCODE_BACK

# --- Styles: switch through all four from settings --------------------------------------------
for style in "شیشه‌ای مدرن" "مینیمال آرام" "داشبوردی" "ایرانیِ مدرن"; do
  adb shell am start -n $PKG/.ui.EntryActivity; sleep 3
  tap_text "ظاهر"; sleep 1
  tap_text "سبک پایه"; sleep 1
  tap_text "$style"; sleep 1
  adb shell input keyevent KEYCODE_HOME
  name=$(echo "$style" | md5sum | cut -c1-6)
  shot "20_style_$name" 4
done

# --- Settings screens --------------------------------------------------------------------------
adb shell am start -n $PKG/.ui.EntryActivity; shot 30_settings 3
tap_text "فضاها"; shot 31_spaces 2
adb shell input keyevent KEYCODE_BACK
tap_text "دسترسی‌ها"; shot 32_permissions 2
adb shell input keyevent KEYCODE_HOME
sleep 3

echo "=== crash check ==="
if crashed; then
  adb logcat -d | grep -A 60 "FATAL EXCEPTION" > shots/crash.txt
  cat shots/crash.txt
  echo "App crashed during the smoke test."
  exit 1
fi
adb logcat -d | grep -iE "nama|AndroidRuntime" | tail -200 > shots/log.txt
echo "No crashes."
