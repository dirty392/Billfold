# Billfold roadmap

## Next
- Monthly spending limits per category
- App lock with a PIN or fingerprint
- Charts of income, bills, wealth and debt over 6–12 months

## Before submitting to F-Droid
- Convert the build to a standard Gradle Android project (F-Droid builds from source and won't use the prebuilt tools that `fetch_tools.sh` downloads)
- Add F-Droid metadata (`fastlane/metadata/android/en-US/`: title, short and full description, screenshots, changelogs)
- Write a short privacy statement (no network access, no tracking, data stays on the device)

## Ideas, not scheduled
- Net worth on a widget; mark bills paid from the widget
- Debt payoff strategies (snowball / avalanche)
- Receipt photos per payment
- Reminders for income and wealth contributions

## Release notes

### v0.5-alpha (versionCode 8)
First public alpha. Same features as internal build 1.6, renumbered for release, licensed GPL-3.0-or-later.

### Internal builds before the alpha
- 1.0 — calendar, bills, check off paid, bill list, summary
- 1.1 — income, colored categories, filters, custom repeats
- 1.2 — themes, settings, CSV export, backup/restore, bills by category, savings section, reminders
- 1.3 — fixed/range/varying amounts, per-payment amount/move/skip, debt section
- 1.4 — home-screen widgets, "Nest egg" renamed Wealth
- 1.5 — assets, net worth, large account boxes
- 1.6 — Calculations settings and display options
