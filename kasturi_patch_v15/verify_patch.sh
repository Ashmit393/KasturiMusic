#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT="${1:-$PWD}"
fail=0
check(){ if [ -f "$PROJECT/$1" ]; then echo "[OK] $1"; else echo "[MISSING] $1"; fail=1; fi; }
check app/src/main/java/com/ashmit/kasturimusic/FloatingPlayerService.kt
check app/src/main/java/com/ashmit/kasturimusic/MainActivity.kt
check app/src/main/java/com/ashmit/kasturimusic/SettingsActivity.kt
check app/src/main/AndroidManifest.xml
check app/build.gradle.kts
if ! grep -q 'versionName = "1.5.0"' "$PROJECT/app/build.gradle.kts"; then echo '[FAIL] Version is not 1.5.0'; fail=1; else echo '[OK] Version 1.5.0'; fi
if ! grep -q 'SYSTEM_ALERT_WINDOW' "$PROJECT/app/src/main/AndroidManifest.xml"; then echo '[FAIL] Overlay permission declaration missing'; fail=1; else echo '[OK] Overlay permission declaration'; fi
if [ "$fail" -ne 0 ]; then echo 'VERIFY FAILED'; exit 1; fi
echo 'VERIFY PASSED (file-level checks only; this does not replace Android compilation/testing).'
