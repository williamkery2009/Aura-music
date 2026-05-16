#!/usr/bin/env bash
# ╔══════════════════════════════════════════════════════════════╗
# ║  Aura v25 — One-Shot Android APK Builder                    ║
# ║  Requirements: Node.js 18+, Java 17+, Android SDK (API 34) ║
# ║  Run: chmod +x build-apk.sh && ./build-apk.sh              ║
# ╚══════════════════════════════════════════════════════════════╝
set -e

echo ""
echo "🎵 Aura v25 — Android Build"
echo "═══════════════════════════════"

# ── 0. Prerequisites check ──────────────────────────────────────
command -v node  >/dev/null 2>&1 || { echo "❌ Node.js not found"; exit 1; }
command -v java  >/dev/null 2>&1 || { echo "❌ Java not found"; exit 1; }
echo "✓ Node $(node --version)"
echo "✓ Java $(java -version 2>&1 | head -1)"

# ── 1. Android SDK ───────────────────────────────────────────────
# Try common SDK locations
if [ -z "$ANDROID_HOME" ]; then
  for DIR in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk"; do
    if [ -d "$DIR" ]; then
      export ANDROID_HOME="$DIR"
      export PATH="$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$PATH"
      break
    fi
  done
fi

if [ -z "$ANDROID_HOME" ]; then
  echo "❌ ANDROID_HOME not set. Install Android Studio or set ANDROID_HOME."
  exit 1
fi
echo "✓ ANDROID_HOME=$ANDROID_HOME"

# ── 2. Install npm dependencies ─────────────────────────────────
echo ""
echo "📦 Installing dependencies..."
npm install --prefer-offline

# ── 3. Build web layer (inject native bridge) ───────────────────
echo ""
echo "🔨 Building web layer..."
node scripts/build.js

# ── 4. Capacitor sync ────────────────────────────────────────────
echo ""
echo "⚡ Syncing Capacitor..."
npx cap sync android

# ── 5. Gradle wrapper setup ──────────────────────────────────────
cd android
if [ ! -f "./gradlew" ]; then
  echo "⬇  Downloading Gradle wrapper..."
  gradle wrapper --gradle-version 8.6 2>/dev/null || {
    # Fallback: download manually
    curl -fsSL "https://services.gradle.org/distributions/gradle-8.6-bin.zip" -o /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d /tmp/gradle-bin
    export PATH="/tmp/gradle-bin/gradle-8.6/bin:$PATH"
    gradle wrapper
  }
fi
chmod +x ./gradlew

# ── 6. Build APK ─────────────────────────────────────────────────
echo ""
echo "🏗  Building debug APK..."
./gradlew assembleDebug --parallel --build-cache -q

APK_PATH=$(find . -name "*.apk" -path "*/debug/*.apk" | head -1)
if [ -z "$APK_PATH" ]; then
  echo "❌ APK not found — check Gradle output above"
  exit 1
fi

cd ..
echo ""
echo "═══════════════════════════════"
echo "✅ Build successful!"
echo "   APK: android/$APK_PATH"
echo ""
echo "Install on connected device:"
echo "   adb install -r android/$APK_PATH"
echo ""
echo "Or copy the APK to your Android device and open it."
