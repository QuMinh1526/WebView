#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PROPERTIES_FILE="$ROOT_DIR/keystore.properties"
if [[ ! -f "$PROPERTIES_FILE" ]]; then
    echo "Missing keystore.properties; create it from the documented release settings." >&2
    exit 1
fi

property() {
    local key="$1"
    sed -n "s/^${key}=//p" "$PROPERTIES_FILE" | tail -n 1
}

STORE_FILE="$(property storeFile)"
STORE_PASSWORD="$(property storePassword)"
KEY_ALIAS="$(property keyAlias)"
KEY_PASSWORD="$(property keyPassword)"
if [[ -z "$STORE_FILE" || -z "$STORE_PASSWORD" || -z "$KEY_ALIAS" || -z "$KEY_PASSWORD" ]]; then
    echo "keystore.properties is missing a required signing property." >&2
    exit 1
fi

KEYSTORE_PATH="$ROOT_DIR/$STORE_FILE"
if [[ ! -f "$KEYSTORE_PATH" ]]; then
    echo "Signing keystore not found: $STORE_FILE" >&2
    exit 1
fi

SDK_ROOT="${ANDROID_HOME:-}"
if [[ -z "$SDK_ROOT" && -f "$ROOT_DIR/local.properties" ]]; then
    SDK_ROOT="$(sed -n 's/^sdk.dir=//p' "$ROOT_DIR/local.properties" | tail -n 1)"
fi
if [[ -z "$SDK_ROOT" ]]; then
    echo "Set ANDROID_HOME or configure sdk.dir in local.properties." >&2
    exit 1
fi

BUILD_TOOLS_DIR="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d -print | sort -V | tail -n 1)"
if [[ -z "$BUILD_TOOLS_DIR" ]]; then
    echo "No Android build-tools installation found in $SDK_ROOT/build-tools." >&2
    exit 1
fi

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
if [[ -z "$VERSION_NAME" ]]; then
    echo "Could not read versionName from app/build.gradle.kts." >&2
    exit 1
fi

./gradlew clean assembleRelease

UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
ALIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-aligned.apk"
OUTPUT_DIR="$ROOT_DIR/dist"
SIGNED_APK="$OUTPUT_DIR/WebView-v${VERSION_NAME}.apk"
mkdir -p "$OUTPUT_DIR"
export WEBVIEW_STORE_PASSWORD="$STORE_PASSWORD"
export WEBVIEW_KEY_PASSWORD="$KEY_PASSWORD"

"$BUILD_TOOLS_DIR/zipalign" -v -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$BUILD_TOOLS_DIR/apksigner" sign \
    --ks "$KEYSTORE_PATH" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:WEBVIEW_STORE_PASSWORD \
    --key-pass env:WEBVIEW_KEY_PASSWORD \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"
"$BUILD_TOOLS_DIR/zipalign" -c -v 4 "$SIGNED_APK"
"$BUILD_TOOLS_DIR/apksigner" verify --verbose --print-certs "$SIGNED_APK"
sha256sum "$SIGNED_APK" | tee "$SIGNED_APK.sha256"

echo "Signed release: $SIGNED_APK"
