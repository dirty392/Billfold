# Billfold — notes for Claude

Read `BILLFOLD_GUIDE.md` before changing anything. It covers the data model, the math, the Android bridge, the build, testing, and pitfalls.

The owner is not a developer: explain things in plain words, don't assume they know Android or git terms.

## Signing key
`release.keystore` is NOT in git (the repo is public). The owner keeps a base64 text copy, `release.keystore.b64.txt`, in the Claude project files.
Before building, recreate it: `grep -v '^#' release.keystore.b64.txt | base64 -d > release.keystore` (alias `billfold`, password `billfold`).
Check the certificate SHA-256 after signing: `444660367f35669286da07425c6ff4adf623721ce59c4ef31721584166921039`.
Never generate a new key and never commit the key or its base64 copy.

## Rules
- Build: `sh ./fetch_tools.sh` (once per machine), then `sh ./build.sh`. No Gradle yet (see ROADMAP for the F-Droid plan).
- Each release: increase `--version-code` by 1 and set `--version-name` in `build.sh`; set `VERSION` in `assets/index.html`.
  versionCode must always go up or the update won't install.
- Any change to stored data needs a migration that keeps existing data and totals.
- Java: compile with ecj (already in `build.sh`); use anonymous classes, not lambdas.
- After each release: update `BILLFOLD_GUIDE.md` and `ROADMAP.md`, commit, push to `main`, and tag `vX.Y-alpha`.
