#!/bin/sh
# Builds Billfold.apk without Gradle: aapt2 -> ecj (Java compiler) -> d8 -> zip -> align -> apksigner
set -e
cd "$(dirname "$0")"
T=tools; B=build; rm -rf $B; mkdir -p $B/classes $B/dex
$T/aapt2 compile --dir res -o $B/res.zip
$T/aapt2 link -o $B/base.apk -I $T/android.jar --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 8 --version-name 0.5-alpha \
  --java $B/gen $B/res.zip
java -jar $T/ecj-3.45.0.jar -8 -nowarn -bootclasspath $T/android.jar -d $B/classes \
  $(find src $B/gen -name '*.java')
java -cp $T/d8.jar com.android.tools.r8.D8 --release --min-api 26 --lib $T/android.jar \
  --output $B/dex $(find $B/classes -name '*.class')
python3 - "$B" <<'PY'
import sys, zipfile
b = sys.argv[1]
src = zipfile.ZipFile(f"{b}/base.apk")
out = open(f"{b}/aligned.apk", "wb")
# Write a zip where every STORED entry's data starts on a 4-byte boundary (what zipalign does).
with zipfile.ZipFile(out, "w") as z:
    items = [(i, src.read(i.filename)) for i in src.infolist()]
    items.append((zipfile.ZipInfo("classes.dex", (2026, 1, 1, 0, 0, 0)), open(f"{b}/dex/classes.dex", "rb").read()))
    for info, data in items:
        zi = zipfile.ZipInfo(info.filename, info.date_time)
        stored = info.filename == "resources.arsc" or info.compress_type == zipfile.ZIP_STORED
        zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        if stored:
            hdr = 30 + len(zi.filename.encode())
            pad = (-(out.tell() + hdr)) % 4
            zi.extra = b"\x00" * pad  # zero padding in the extra field, as zipalign does
        z.writestr(zi, data)
PY
if [ ! -f release.keystore ]; then
  echo "release.keystore is missing. It is not in git on purpose." >&2
  echo "Restore it from the base64 copy:  grep -v '^#' release.keystore.b64.txt | base64 -d > release.keystore" >&2
  echo "(Making a new key would stop updates from installing over the existing app.)" >&2
  exit 1
fi
java -jar $T/apksigner.jar sign --ks release.keystore --ks-pass pass:billfold --ks-key-alias billfold \
  --out Billfold.apk $B/aligned.apk
java -jar $T/apksigner.jar verify -v Billfold.apk
