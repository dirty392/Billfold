#!/bin/sh
# Downloads the Android build tools Billfold needs into ./tools (about 50 MB), using npm.
# No Android Studio, Gradle or SDK install is required.
set -e
cd "$(dirname "$0")"
mkdir -p tools
TMP=$(mktemp -d)
( cd "$TMP" && npm pack aaptjs3@2.0.2 @drxiaozhi/minapk@0.4.0 >/dev/null )
mkdir -p "$TMP/a" "$TMP/m"
tar xzf "$TMP"/aaptjs3-2.0.2.tgz -C "$TMP/a"
tar xzf "$TMP"/drxiaozhi-minapk-0.4.0.tgz -C "$TMP/m"
case "$(uname -s)" in
  Darwin) cp "$TMP/a/package/bin/x64/darwin/aapt2" tools/aapt2 ;;
  *)      cp "$TMP/a/package/bin/x64/linux/aapt2" tools/aapt2 ;;
esac
chmod +x tools/aapt2
for f in android.jar d8.jar apksigner.jar ecj-3.45.0.jar; do cp "$TMP/m/package/tools/$f" tools/; done
rm -rf "$TMP"
echo "Tools ready in ./tools"
