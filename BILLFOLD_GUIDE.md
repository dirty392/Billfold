# Billfold — Project Guide (reference for Claude)

This is the working reference for building and changing Billfold. Read it before making any change. It describes what the app does, how it is built, where everything lives, the data model, the core math, the Android side, conventions, and known pitfalls. Keep it up to date: after each release, update **Version history**, **Roadmap**, and any section the change touched.

## 0. Starting a new session

- **Code:** public GitHub repo **dirty392/Billfold** (branch `main`, tags `vX.Y-alpha`). Attach it with `add_repo` (push access) and clone it.
- **Signing key:** not in git. The owner keeps `release.keystore.b64.txt` in the Claude project files. Decode it into the repo root: `grep -v '^#' release.keystore.b64.txt | base64 -d > release.keystore`. Never commit either file (both are in `.gitignore`).
- Read this guide and `CLAUDE.md`. Confirm the current version from `VERSION` in `assets/index.html` and `--version-code/--version-name` in `build.sh`.
- `sh ./fetch_tools.sh` (tools aren't in git), make the change, test (§2), build, check the signer SHA-256 matches `444660367f35669286da07425c6ff4adf623721ce59c4ef31721584166921039`.
- Send the owner the APK, then commit (with attribution lines), push `main`, tag `vX.Y-alpha`, push the tag. Update this guide and `ROADMAP.md` in the same commit.
- The owner is not a developer. Explain in plain words; don't use terms like keystore, versionCode or commit without explaining them.

---

## 1. What Billfold is

An Android app (APK, sideloaded) for tracking **bills, income, wealth (savings/investments/assets) and debt**, organized around a Google-Calendar-style month view. Bills appear on their due dates and are checked off when paid. All data stays on the phone.

**Owner / user:** a single person (GitHub `dirty392`) building this as their own app, planning to publish it on **F-Droid**. They install each new APK over the old one. They like: fast iteration, plain-language UI, big clear numbers, "automatic and intuitive" behavior, and options in Settings to tweak how things are calculated. They review on their phone.

**Current version:** **0.5-alpha** (versionCode 8). Package `com.billfold.app`. minSdk 26 (Android 8.0), targetSdk/compileSdk 34. License **GPL-3.0-or-later** (`LICENSE`); fonts SIL OFL 1.1 (`assets/fonts/licenses/`).
Version names follow `0.X-alpha` until the first stable release. Internal builds 1.0–1.6 came before the alpha renumbering, which is why versionCode is already 8.

### Product principles (keep these)
- **Phone-first.** Designed at ~390px wide. Everything must fit without horizontal scroll.
- **Local only.** No accounts, no network. Data lives in WebView `localStorage`. Backups are JSON files the user saves.
- **Automatic.** Checked-off payments flow into debt balances, asset values, averages, and totals without extra steps.
- **Never lose data.** Every schema change ships with a migration. Updates install over the old app (same signing key, higher versionCode).
- **Plain words.** "Left to spend", "Paid off by", "Adds to". No jargon. Errors say what to do.
- **Separate money in vs. money out** wherever a calculation choice exists (user explicitly asked for this).

---

## 2. Build, sign, test

### Toolchain (no Android Studio / Gradle)
`fetch_tools.sh` downloads into `./tools/` via npm:
| Tool | From npm package | Purpose |
|---|---|---|
| `aapt2` (linux/darwin binary) | `aaptjs3@2.0.2` | compile/link resources + manifest |
| `android.jar` (API 34) | `@drxiaozhi/minapk@0.4.0` | compile-time Android API |
| `ecj-3.45.0.jar` | same | **Java compiler** (use ecj, not javac — see pitfalls) |
| `d8.jar` | same | .class → classes.dex |
| `apksigner.jar` | same | v2/v3 signing |

### `build.sh` pipeline
1. `aapt2 compile --dir res` → `build/res.zip`
2. `aapt2 link` with manifest, `-A assets`, `--min-sdk-version 26 --target-sdk-version 34 --version-code N --version-name X`, `--java build/gen` (generates `R.java`)
3. `java -jar ecj -8 -bootclasspath android.jar` on `src/**` + `build/gen/**`
4. `d8 --release --min-api 26`
5. Python step: rebuild zip, add `classes.dex`, **4-byte align stored entries** (acts as zipalign)
6. `apksigner sign` with `release.keystore` (alias `billfold`, pass `billfold`), then `apksigner verify`. If the key is missing, `build.sh` stops with instructions (it never makes a new key).
Output: `Billfold.apk` in the project root.

### Releasing a new version
- Bump **both** `--version-code` (integer, +1) and `--version-name` (e.g. `0.6-alpha`) in `build.sh`.
- Bump `VERSION` constant in `assets/index.html` (shown in Settings footer, e.g. `"0.6 alpha"`).
- Append a line to `ROADMAP.md` ("Shipped in X.Y: …") and to **Version history** below.
- **Always sign with the same `release.keystore`.** A different key = Android refuses the update. Verify: `java -jar tools/apksigner.jar verify --print-certs Billfold.apk` → SHA-256 `444660367f35669286da07425c6ff4adf623721ce59c4ef31721584166921039`.
- Commit, push `main`, create and push tag `vX.Y-alpha`. (Attaching the APK to a GitHub Release is done by the owner on the website; this environment has no `gh` CLI.)

### Testing approach (used every release)
- Syntax check: extract the `<script>` block and run `node --check`.
- Headless Chromium via Python Playwright at `viewport 390×844, deviceScaleFactor 2`, light and dark (`color_scheme`).
- Logic tests through `window.__test` (exposes `occurrences, repText, csv, debtState, assetState, migrate, planned, autoCheck, leftOf, widgetData, monthEntries, S`).
- A fake native bridge can be injected with `page.add_init_script("window.Android={saveFile(){},setBars(){},setReminders(){},notifyStatus(){return 'granted'},requestNotify(){},setWidgetData(j){window.__got=j}}")` to inspect widget/reminder payloads.
- Check sheets for overflow: `document.querySelector('#xScrim .sheet')` → `getBoundingClientRect().width === scrollWidth`.
- Keep a copy of the previous `index.html` to test **migrations** (load old file, add example data, then load new file on the same `file://` origin — they share localStorage).
- Native-only features (widgets drawing, notifications, file save/open dialogs) cannot be tested here; tell the user so and ask for screenshots if something looks off.
- **Month indexes are 0-based** in JS (`m=8` is September). Several test "failures" in past sessions were test typos from this.

---

## 3. Architecture

A thin native Android shell hosts a single-file web app.

```
billfold-android/
├── AndroidManifest.xml        activity (singleTop), ReminderReceiver, 3 widget receivers, permissions
├── build.sh / fetch_tools.sh
├── release.keystore           SIGNING KEY — not in git; recreate from the owner's base64 copy (§0)
├── LICENSE                    GPL-3.0-or-later
├── README.md / ROADMAP.md / CLAUDE.md / BILLFOLD_GUIDE.md
├── assets/
│   ├── index.html             ENTIRE app: HTML + CSS + JS (~2,200 lines)
│   └── fonts/*.woff2          Sora 500/600/700, IBM Plex Sans 400/500/600, IBM Plex Mono 500 (+ licenses/ OFL texts)
├── res/
│   ├── values/ & values-night/ styles.xml (AppTheme, status/nav bar colors), widget_colors.xml
│   ├── values/strings.xml      app_name + widget descriptions
│   ├── drawable/               launcher fg/bg, ic_notify, widget_bg, w_dot, w_bar (progress)
│   ├── mipmap-anydpi-v26/      adaptive launcher icon
│   ├── layout/                 widget_upcoming.xml (5 fixed rows), widget_month.xml, widget_debt.xml
│   └── xml/                    appwidget-provider info for each widget
└── src/com/billfold/app/
    ├── MainActivity.java       WebView host, Bridge (JS interface "Android"), file save/open, permissions, deep links
    ├── ReminderReceiver.java   schedules/posts bill reminder notifications; reschedules on boot/update
    ├── Widgets.java            draws all 3 widgets from the saved JSON snapshot
    └── WidgetUpcoming/WidgetMonth/WidgetDebt.java   AppWidgetProvider stubs → Widgets.update
```

- WebView loads `file:///android_asset/index.html` with JS, DOM storage, and file access enabled; text zoom fixed at 100.
- The page works in a normal desktop browser too (bridge absent → features degrade: downloads via `<a download>`, reminders show "work in the Android app").

---

## 4. Data model (localStorage)

| Key | Contents |
|---|---|
| `billfold.items.v2` | array of **items** (bills, income, wealth contributions) |
| `billfold.cats.v1` | array of **categories** `{id, label, color (0–12), kind}` |
| `billfold.debts.v1` | array of **debts** |
| `billfold.assets.v1` | array of **assets** (presence of this key = asset migration done) |
| `billfold.settings.v1` | **settings** object |
| `billfold.view.v1` | `{kindFilter, hidden[], billSort}` view prefs |
| `billfold.bills.v1` | legacy 1.0 bills (migrated into items.v2 on first load; left in place) |

### Item (a repeating series)
```js
{ id, kind: "bill"|"income"|"save", name, amount, start: "YYYY-MM-DD", category,
  rep: { unit: "none"|"day"|"week"|"month"|"year"|"semimonth", every: N, days: [d1,d2] /*semimonth*/ },
  end: { type: "never"|"count"|"until", count, until },
  amtType?: "range"|"variable"   // absent = fixed
  max?: number                   // top of range (amount = low end)
  autopay: bool, note, example?: bool,
  debtId?: string   // bills only: pays toward a debt
  assetId?: string  // save only: adds to an asset
  paid: { "YYYY-MM-DD(orig)": true },          // done/paid/received/added, keyed by ORIGINAL scheduled date
  occ:  { "YYYY-MM-DD(orig)": { amt?, to?, skip? } }  // per-payment overrides: actual amount, moved date, skipped
}
```
- `kind: "save"` is shown to the user as **Wealth** (renamed from "Nest egg" in 1.4). Internal ids stay `save` and tab id `nest`.
- Legacy fields `balance`/`goal` on save items were moved to assets in 1.5 (`migrateAssets`).

### Debt
`{ id, name, category (debt cat), balance, asOf, apr (0 = none), note, example? }`

### Asset
`{ id, name, category (asset cat), value, asOf, rate (growth %/yr, 0 = none), goal?, note, example? }`

### Category kinds & defaults
- `bill`: housing, utilities, phone, insurance, subs, loans, transport, **other** (fallback)
- `income`: paycheck, side, **other-income**
- `save` (contribution types): emergency, retirement, invest, goals, **other-save**
- `asset`: a-savings, a-checking, a-retire, a-invest, a-metals, a-realestate, a-vehicle, a-crypto, a-cash, **other-asset**
- `debt`: card, auto, student, mortgage, personal, medical, **other-debt**
- Fallbacks (`FALLBACK`) can't be deleted; deleting a category reassigns its items to the fallback. `loadCats()` adds any missing kind's defaults and always ensures fallbacks exist (so new kinds appear for existing users).

### Settings (`mergeSettings` deep-merges defaults for `rem`, `calc`, `disp`, `defs`)
```js
{ mode: "system"|"light"|"dark", theme: "evergreen"|"ocean"|"plum"|"ember"|"rose"|"graphite",
  weekStart: 0|1, currency: "USD"|…,
  rem:  { on, days (0,1,2,3,5,7), time "HH:MM", skipAuto },
  calc: { rangeOut: "up"|"avg"|"down", rangeIn: same, varyOut: "avg"|"last3"|"recent"|"high"|"low", varyIn: same,
          leftIncome: "all"|"received", leftWealth: bool, soonDays: 0..7, autoPaid: bool },
  disp: { startTab: "cal"|"list"|"nest"|"debt"|"sum", chip: "name"|"amount", showPaid, stripCents },
  defs: { bill, income, save }   // PRESETS keys for new items' repeat
  autoLast: "YYYY-MM-DD"|null     // next date autopay auto-check starts from
}
```
Defaults: rangeOut **up**, rangeIn **down**, vary avg/avg, leftIncome all, leftWealth true, soonDays 3, autoPaid false.

### Backup file
`{ app: "billfold", version: 5, exported, items, cats, debts, assets, settings }`. Restore validates `app === "billfold"` and `items` array; if `assets` missing (old backup) runs `migrateAssets(true)`.

---

## 5. Core logic (in `index.html`)

### Recurrence — `occurrences(it, y, m)` → dates in month
- day/week: step = every×(1|7) days from `start`; month/year: same day-of-month (clamped to month length, so 31st → Feb 28); semimonth: two chosen days each month; none: just `start`.
- `end.count` counts occurrence index from start; `end.until` inclusive date.
- `repText(rep, start, end)` → human sentence ("Every 2 weeks on Friday, starting Sep 4, 6 times").
- `PRESETS`: none, daily, weekly, biweekly, semimonth, monthly, quarterly, semiannual, yearly (+ custom).

### Entries pipeline
- `rawEntries(y, m, items?)` → per-payment entries `{it, orig, date, done, skipped, moved, custom, amt}` after applying `occ` moves (a payment moved into this month from another month is included; moved out is excluded). Sorted by date, then income → bill → save, then name.
- `monthEntries(y, m, withSkipped)` → rawEntries filtered for display, plus **debt trimming**: linked unpaid payments after a debt's projected payoff are dropped, and the final one is reduced to exactly what's left (`e.final = true`).
- `status(e)`: skipped / got / expected / added / planned / paid / overdue (past, unpaid) / soon (within `calc.soonDays`) / upcoming.
- `totals(list)` → sums per kind (`bill, income, save`) and done sums, counts `n`, done counts `nd`. Skipped excluded.
- `leftOf(t)` = (income or incomeDone per `calc.leftIncome`) − bills − (wealth if `calc.leftWealth`).

### Amounts
- `amtType(it)`: fixed | range | variable.
- `planned(it)` = what an unpaid payment counts as:
  - range: `calc.rangeOut` for bill/save, `calc.rangeIn` for income → high (up), low (down), or midpoint (avg).
  - variable: `history(it)` (paid amounts, oldest→newest) → avg / avg of last 3 / most recent / highest / lowest (per `varyOut`/`varyIn`); no history → estimate (`amount`).
  - fixed: `amount`.
- `amountFor(it, orig)` = occ amt override if present, else planned.
- Checking off a range/variable item opens the payment sheet to enter the actual amount; fixed toggles immediately. Saving the sheet stores `occ.amt` when paid or when it differs from planned.

### Debt — `debtState(d)`
- Uses linked bills (`it.debtId === d.id`) and only entries on/after `asOf`, not skipped.
- Pass 1 (asOf month → current month): add monthly interest `bal × apr/1200` for each month after the first; subtract **done** payments (actual amounts). Also subtracts done payments up to 36 months ahead (paid early).
- `left` = max(0, bal). `paidOff` if ≤ 0.005.
- Pass 2 projection (up to 600 months): monthly interest, subtract unpaid scheduled payments (≥ today) → `payoff` date, `finalAmt`, `finalItem/finalOrig`, `nLeft`, `next`. `stuck` if never pays off (payments don't cover interest). Stops if no payments for 24 months.
- Verified example: $26,000 at $800/mo, 0% → 33 payments; Jan–Sep checked → $18,800 left, payoff Sep 2028, final $400.

### Asset — `assetState(a)`
- Linked contributions (`it.assetId === a.id`), entries on/after `asOf`.
- value = asOf value + monthly growth (`rate/1200`, compounding) + done contributions (+ early ones up to 36 months ahead). `added`, `growth`.
- Projection → `next` contribution and `goalBy` date if a goal exists.
- Updating an asset's value (metals price, statement) = edit value + set as-of to today; older contributions then stop counting.
- `totalAssets()`, `totalDebt()`; **net worth** = assets − debts.

### Autopay auto-check — `autoCheck()`
Runs on startup and when the app becomes visible. If `calc.autoPaid`, marks autopay bills/contributions (not income) with dates from `autoLast` through today as paid (range/variable store their planned amount), then sets `autoLast` = tomorrow. Turning the option on sets `autoLast = today`, so no backfill, and anything the user unchecks stays unchecked.

### Migrations (run on load; must stay forever)
1. `billfold.bills.v1` (1.0) → `items.v2` (`loadItems`, freq → rep).
2. Missing category kinds added by `loadCats`.
3. `migrateAssets(false)`: if `assets.v1` key absent, every save item becomes an asset (value = old `balance`, goal, asOf = item start) and is linked; `balance/goal` removed. Preserves totals exactly (verified $13,250 → $13,250).
4. `mergeSettings` fills new settings keys.

---

## 6. UI map

### Header
Two rows: brand "BILLFOLD" + **gear** (Settings) · month title, Today, ◀ ▶. Settings view hides month nav, strip and FAB. Sticky with safe-area top.

### Totals strip (all tabs except Settings)
Income · Bills · Wealth · Left (whole dollars unless `disp.stripCents`; bills rounded up, left rounded down).

### Tabs (bottom bar, `data-tab`)
- **cal — Calendar:** filter chips (All/Bills/Income/Wealth + per-category show/hide + "Edit colors"), month grid (week start setting), chips: bill = soft color, income = solid with "+", wealth = outlined, paid = grey strikethrough, overdue = red outline; up to 2 per day + "+N more"; legend; selected-day agenda rows.
- **list — Bills:** "This month" (Income group, then bills by **Category** or by **Date** (Overdue/Coming up/Paid), then Skipped) · "Everything" (series definitions; tap to edit).
- **nest — Wealth:** large **asset boxes** first (sorted by value), then Total assets / net worth / put-away-this-month hero, contributions this month, regular contributions list (+ Add). FAB = Add asset.
- **debt — Debt:** large **debt boxes** first (sorted by balance left), then Total debt left / debt-free date hero, payments this month. FAB = Add debt.
- **sum — Summary:** **Net worth** hero (assets − debts, split bar, links), Left to spend hero (breakdown, estimate note with "Change" → Calculations, meters for bills paid / income received / wealth added), Overdue, Next up, by-category breakdowns.
- **set — Settings** (gear): Appearance (mode + 6 themes) · **Calculations** (ranges in/out, varies in/out, left-to-spend rules, due soon, autopay auto-check, reset) · Calendar & display · New items (default repeats) · Reminders · Export (payments CSV for a month range; list CSV incl. debts, assets, net worth) · Backup/Restore · Data (examples, erase) · Coming later · version.

### Sheets (bottom sheets; `.scrim` > `.sheet`)
- `#scrim` series form: kind segmented (Bill/Income/Wealth), name, amount type (Fixed/Range/Varies) with From/Up to, date, category (+Edit categories), **Pays toward a debt** (bills), **Adds to an asset** (wealth), repeat box (presets/custom/semimonth days/ends), autopay/auto-transfer, notes, delete (tap twice).
- `#occScrim` one payment: status (Not paid/Paid wording per kind), actual amount, date (move), hint text, Skip/Don't skip, Edit whole series.
- `#debtScrim` debt: name, type, amount owed, as-of, APR, linked payments (link/unlink), add a payment to the calendar (amount, first date, repeat), notes, delete.
- `#assetScrim` asset: name, type, value, as-of, growth %/yr, goal, linked contributions (link/unlink), add a regular contribution, notes, delete.
- `#catScrim` (z-index above others) categories & colors for all 5 kinds: rename, 13-color palette, add, delete.

### Event delegation (one document click handler)
`data-toggle` (id|orig check-off) · `data-occ` (open payment) · `data-edit` (series) · `data-debt` · `data-asset` · `data-day` · `data-tab` · `data-mode` (list month/all) · `data-sort` · `data-kind` · `data-hide` (category filter) · `data-mode-set` / `data-theme-set` · `data-arm` (two-tap confirm: erase, restore) · `data-act`: add, add-income, add-save, add-debt, add-asset, examples, clearex, cats, export-tx, export-list, backup, restore, restore-cancel, perm, calc, calc-reset. Sheet-local: `data-unlink`, `data-aunlink`, `data-swatch`, `data-pick`, `data-catdel`, `data-catadd`.
Settings inputs are handled by one `change` listener on `#view-set` with `MAP`/`BOOL` tables (id → settings path).

### Android back button
`window.__back()`: closes cat → occ → debt → asset → series sheet, then Settings → last tab, then any tab → Calendar, else exits.

### Toasts
`toast(msg, undo?)` — undo shown for deletes/skips (5 s).

---

## 7. Native bridge & Android features

### JS → Android (`window.Android`, class `MainActivity.Bridge`)
| Method | Does |
|---|---|
| `setBars(bgHex, navHex, lightBool)` | status/nav bar colors + light/dark icons (called by `applyTheme`) |
| `saveFile(name, mime, text)` | ACTION_CREATE_DOCUMENT; writes text; calls back `window.__fileSaved(ok, name)` |
| `setReminders(json)` | saves `[{t: epochMs, title, text}]` to prefs, `ReminderReceiver.schedule()` |
| `setWidgetData(json)` | saves snapshot to prefs, `Widgets.updateAll()` |
| `notifyStatus()` | "granted"/"denied" |
| `requestNotify()` | runtime POST_NOTIFICATIONS (API 33+) or opens app notification settings |

### Android → JS
`window.__back()` (back button, returns truthy if handled) · `window.__openTab(tab)` (widget taps; closes sheets, jumps to current month) · `window.__fileSaved(ok, name)` · `window.__notifyStatus()` (after permission result / onResume).
File picking for restore uses `WebChromeClient.onShowFileChooser` → ACTION_OPEN_DOCUMENT; the page reads it with FileReader.

### Reminders
JS `pushReminders()` (debounced via `syncReminders()` on every save): next 3 months of unpaid bills (optionally skipping autopay), fire time = due date − `rem.days` at `rem.time`, max 100. Java uses `AlarmManager.setAndAllowWhileIdle` (inexact, no exact-alarm permission), PendingIntent request codes 1000+n, notification channel "bills", tap opens app. `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` reschedule.

### Home-screen widgets
- Snapshot built by `widgetData()` and pushed with reminders on every change, on theme change, and when the app becomes visible:
  `{ v, cur, netWorth, assets, accentL, accentD, upcoming:[{d, n, a, est, auto, cl, cd, id}], months:[{ym, name, income, bills, save, left, billsPaid, nb, np}], debt:{count, open, left, paid, freeBy, unknown} }`
  - `upcoming`: unpaid bills from ~45 days ago through the next 3 months (max 80); `cl/cd` = category color light/dark hex (`PAL_HEX`).
  - `months`: current + next 2 months (widget picks the current `ym`, so it survives month rollover for 2 months without opening the app).
- `Widgets.java` renders at update time using today's date, so "Due today"/"Overdue" labels stay right; `updatePeriodMillis` = 1 h.
  - **Upcoming bills** (4×3): header "DUE NEXT" + "$X due this week", 5 rows (dot tinted via `setColorFilter`, name, when label, amount with "~" for estimates). Tap → Bills tab.
  - **This month** (3×2): month name, Left to spend, Income/Bills/Wealth, bills-paid progress. Tap → Summary.
  - **Debt left** (3×2): total left, paid progress, debt-free date, paid · accounts. Tap → Debt.
- Widget colors come from `res/values(-night)/widget_colors.xml`; title color uses the theme accent from the snapshot.
- `netWorth`/`assets` are in the snapshot but not yet shown on any widget (easy follow-up).

---

## 8. Design system

- **Fonts:** Sora (display: titles, brand, big card names), IBM Plex Sans (body), IBM Plex Mono 500 (all money; class `.num`, tabular numbers). Bundled as woff2 in `assets/fonts`.
- **Tokens** on `:root` with dark overrides under `prefers-color-scheme` (guarded by `:not([data-theme="light"])`) and `[data-theme="dark"]`: `--bg --surface --surface-2 --line --fg --fg-2 --fg-3 --accent --accent-soft --on-accent --income --save --debt --danger(-soft) --warn(-soft) --paid`, palette `--p0…--p12` with soft backgrounds `--p0b…--p12b`.
- **Themes** (`THEMES`): each has `L` and `D` arrays `[accent, accentSoft, onAccent, bg, surface, surface2, line]`; `applyTheme()` writes them as inline CSS vars on `<html>` and calls `setBars`.
- **Semantic money colors:** income green (`--income`), wealth blue (`--save`), debt rust (`--debt`), overdue/negative red (`--danger`).
- **Category colors:** `catVars(id)` → `--cfg`/`--cbg` from palette index.
- Key components: `.strip`, `.hero`, `.meter/.bar`, `.rows/.row` (check button, `.rmain`, `.ramt` with `<small>` note), `.pill` (overdue/soon/got/auto/info/debt), `.bigcard` (bc-type chip, bc-name, bc-amt, facts grid), `.split` (net worth bar), `.seg` (segmented), `.fchip` filters, `.sheet` forms (`.field`, `.two`, `.repbox`, `.inline`, `.toggle`, `.linked`), `.card` (settings), `.toast`.
- Layout: max-width 560px centered, 16px side padding, fixed 5-tab bar (64px + safe area), FAB above it.

---

## 9. Conventions & pitfalls

- **Use ecj, not javac.** javac 21 output crashes this d8 build (NPE on anonymous classes); lambdas fail too (missing LambdaMetafactory). Write Java with anonymous classes (`new Runnable(){…}`), no lambdas.
- **RemoteViews limits:** only simple layouts (LinearLayout, TextView, ImageView, ProgressBar). No custom views. Row counts are fixed in XML; IDs are listed in arrays in `Widgets.java`.
- **aapt2 `--java build/gen`** generates `R`; resource IDs are referenced as `R.id.x`, `R.layout.x`.
- `JavascriptInterface` methods run on a background thread; UI work goes through `runOnUiThread`.
- Activity is `singleTop`; widget taps arrive via `onNewIntent`. `uiMode` is not in `configChanges`, so the activity recreates on dark-mode switches (WebView state restored).
- The `file://` WebView origin keeps localStorage across updates. Never change the asset path or the origin changes and data looks lost.
- Everything keyed by **original** scheduled date (`orig`) in `paid`/`occ`; the displayed `date` may differ after a move.
- `render()` recomputes `S.dstate` and `S.astate` every time; keep `debtState`/`assetState` cheap (they only scan linked items).
- Adding a new setting: add a default in `defaultSettings`/`defaultCalc`, a control in `renderSettings` (use `sel()`/`chk()`), an entry in the `MAP`/`BOOL` change tables, and read it where needed. `mergeSettings` handles old saved settings.
- Adding a new entity kind: new localStorage key, save function (call `syncReminders()` so widgets refresh), include in backup/restore/erase/examples/clearExamples/CSV, categories kind + fallback, migration if it replaces old fields.
- CSV: `csvCell` leaves plain numbers unquoted and prefixes text starting with `= + - @` with `'` (formula-injection guard). Files start with a UTF-8 BOM.
- No `alert/confirm/prompt`: confirmations are two-tap buttons (`data-arm`, delete buttons).
- Keep copy plain: short labels, amounts in mono, sentences without jargon.

---

## 10. Version history

- **0.5-alpha** (versionCode 8) — first public alpha; same features as internal 1.6. Added GPL-3.0-or-later license, font licenses, README, CLAUDE.md; key removed from the repo; `build.sh` refuses to create a new key.

Internal builds before the alpha:

- **1.0** — Calendar with bills on due dates, check off paid, bill list, summary. Native WebView APK.
- **1.1** — Income; editable colored categories; calendar filters; custom repeats (every N days/weeks/months/years, twice a month, end after N / on date).
- **1.2** — Themes (6) + light/dark; Settings tab; CSV export; backup/restore; bills grouped by category; "Nest egg" savings section with goals; bill reminders (notifications).
- **1.3** — Fixed/Range/Varies amounts; per-payment actual amount, move, skip; Debt section with linked payments, interest, payoff date, auto-stop after payoff; Settings moved to header gear.
- **1.4** — Home-screen widgets (Upcoming bills, This month, Debt left); "Nest egg" renamed **Wealth**.
- **1.5** — Assets in Wealth (types, value, as-of, growth, goal, linked contributions); net worth on Summary; large account boxes at the top of Debt and Wealth; migration of old wealth items to assets.
- **1.6** — Settings → Calculations: ranges and varying amounts calculated separately for money in/out (round up / average / round down; average/last 3/recent/high/low); left-to-spend rules; due-soon window; autopay auto-check; display options (start tab, chip names/amounts, hide paid, cents); default repeats for new items. Incoming ranges now default to round down.

---

## 11. Roadmap (pinned by the user)

**F-Droid submission prerequisites:** convert to a standard Gradle project (F-Droid builds from source and rejects prebuilt binaries such as the npm-sourced aapt2/d8/ecj); fastlane metadata (`fastlane/metadata/android/en-US/` title, short/full description, screenshots, changelogs/<versionCode>.txt); privacy note (no network, no tracking). F-Droid signs its builds with its own key, so F-Droid installs and sideloaded installs can't update each other.

- Monthly spending limits per category (budgets) with over/under indicators.
- App lock with PIN or fingerprint (BiometricPrompt via the bridge).
- Charts of income, bills, wealth and debt over 6–12 months (trend view; likely in Summary).
- Ideas offered but not requested yet: net worth on a widget, mark-paid from the widget, debt payoff strategies (snowball/avalanche), receipt photos per payment, cloud/Drive backup, recurring reminders for income/wealth.

---

## 12. Checklist for any change

1. Read this guide and the relevant code in `assets/index.html` (functions listed in §5–6).
2. Keep a copy of the current `index.html` before editing (for migration tests).
3. Make the change; add a migration if stored shapes change.
4. `node --check` the script; run Playwright screenshots of affected screens in light and dark at 390px; run logic tests via `window.__test`; check sheets for overflow.
5. Bump versionCode/versionName in `build.sh` and `VERSION` in `index.html`; run `./build.sh`; confirm "Verified using v2 scheme: true".
6. Update `ROADMAP.md` and this guide (§4 data model, §6 UI, §10 history, §11 roadmap).
7. Tell the user what changed in plain words, anything that changed default behavior, and what couldn't be tested off-device.
