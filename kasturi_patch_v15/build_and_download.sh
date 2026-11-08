#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT="${1:-$PWD}"
cd "$PROJECT"
if [ -x ./gradlew ]; then ./gradlew --no-daemon assembleDebug
elif command -v gradle >/dev/null 2>&1; then gradle --no-daemon assembleDebug
else echo 'ERROR: Gradle not found. Install/configure Gradle and Android SDK/NDK first.'; exit 2; fi
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo 'ERROR: APK was not produced.'; exit 3; }
mkdir -p "$HOME/storage/downloads"
cp -f "$APK" "$HOME/storage/downloads/KasturiMusic-v1.5.0-debug.apk"
echo "APK saved: $HOME/storage/downloads/KasturiMusic-v1.5.0-debug.apk"
