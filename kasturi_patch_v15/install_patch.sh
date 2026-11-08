#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PATCH_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT="${1:-$PWD}"
if [ ! -f "$PROJECT/settings.gradle.kts" ] || [ ! -d "$PROJECT/app/src/main" ]; then
  echo "ERROR: Android project folder not found. Usage: bash install_patch.sh /path/to/KasturiMusic"
  exit 2
fi
if ! grep -q 'com.ashmit.kasturimusic' "$PROJECT/app/build.gradle.kts"; then
  echo "ERROR: This does not look like Kasturi Music project. No files changed."
  exit 3
fi
if [ -f "$PROJECT/app/src/main/java/com/ashmit/kasturimusic/FloatingPlayerService.kt" ]; then
  echo "INFO: FloatingPlayerService already exists; making a timestamped backup."
fi
STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP="$PROJECT/.kasturi_patch_backup_$STAMP"
mkdir -p "$BACKUP"
for f in app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/ashmit/kasturimusic/MainActivity.kt app/src/main/java/com/ashmit/kasturimusic/SettingsActivity.kt app/src/main/java/com/ashmit/kasturimusic/SettingsManager.kt; do
  if [ -f "$PROJECT/$f" ]; then mkdir -p "$BACKUP/$(dirname "$f")"; cp -p "$PROJECT/$f" "$BACKUP/$f"; fi
done
mkdir -p "$PROJECT/app/src/main/java/com/ashmit/kasturimusic"
cp -f "$PATCH_DIR/app/build.gradle.kts" "$PROJECT/app/build.gradle.kts"
cp -f "$PATCH_DIR/app/src/main/AndroidManifest.xml" "$PROJECT/app/src/main/AndroidManifest.xml"
for f in MainActivity.kt SettingsActivity.kt SettingsManager.kt FloatingPlayerService.kt; do
  cp -f "$PATCH_DIR/app/src/main/java/com/ashmit/kasturimusic/$f" "$PROJECT/app/src/main/java/com/ashmit/kasturimusic/$f"
done
echo "PATCH APPLIED: Kasturi Music V1.5.0"
echo "Backup saved at: $BACKUP"
echo "Next: bash verify_patch.sh \"$PROJECT\""
