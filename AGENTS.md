# AGENTS.md — WallApp

Instructions for AI coding agents (Claude Code, Codex, Copilot, Cursor, etc.).
This file is the single source of truth for agents. `CLAUDE.md` and
`.github/copilot-instructions.md` point here. Keep it in sync with the code.

## What this is

WallApp HD Wallpapers — a single-module Android app (`com.sky.wallapp`,
[Play Store](https://play.google.com/store/apps/details?id=com.sky.wallapp)).
Users browse wallpapers by category, favorite them, set/save/share them, and
can enable a daily auto-wallpaper rotation from favorites.

- **Language:** Kotlin only (the two `Example*Test.java` files are template leftovers)
- **UI:** XML layouts + ViewBinding, Activities (no Fragments, no Compose)
- **Build:** Gradle (Groovy DSL), AGP 9.1 with built-in Kotlin, JDK 21
- **SDK:** minSdk 24, target/compileSdk 36
- **Backend:** Firebase Realtime Database (read-only from the app), no own server
- **Other SDKs:** Firebase Analytics + Messaging + Auth, Credential Manager (Google sign-in), AdMob native ads (in-feed), Play In-App Update, Play In-App Review, Glide 5, WorkManager

## Commands

```bash
./gradlew assembleDebug          # debug APK (uses AdMob test IDs)
./gradlew bundleRelease          # release AAB (minified; needs signing config, see below)
./gradlew lint                   # Android lint
./gradlew test                   # JVM unit tests (only a placeholder today)
./gradlew connectedAndroidTest   # instrumented tests (needs device/emulator)
```

Always run `./gradlew assembleDebug` after changing code or resources — it is the
only automated check that catches most mistakes. There is no meaningful test suite yet.

## ⚠️ Release & deployment rules (read before any git operation)

- **Every push to `master` builds a signed AAB and uploads it to the Play Store
  internal track** (`.github/workflows/deploy-playstore.yml`). Do not push to
  `master` unless the user explicitly asks. Work on a branch and open a PR.
- A deploy fails if `versionCode` was not bumped. Version lives in
  `app/build.gradle` → `defaultConfig { versionCode / versionName }`. Bump
  `versionCode` by 1 for every release; only bump when the user asks for a release.
- `build-check.yml` runs on PRs (and pushes to `main`/`develop`, not `master`).
  `release-build.yml` is manual (`workflow_dispatch`) only.
- Never commit keystores, `local.properties`, service-account JSON, or secrets.
  CI signing comes from GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_STORE_SERVICE_ACCOUNT_JSON`). Local release
  signing reads `RELEASE_STORE_*` properties (see `local.properties.template`).
- Release builds swap in **production AdMob IDs** via `manifestPlaceholders` /
  `resValue` in `app/build.gradle`. Debug builds use Google's test IDs. Never put
  production ad unit IDs in debug code paths, never ship Google's test IDs in release,
  and never click real ads while testing.
- Ads are **native in-feed "sponsored pins"** (no banner). `FeedAds` is disabled
  if `native_ad_unit_id` is blank.

## Website parity

The app mirrors the website [wallapp.shubhamy.in](https://wallapp.shubhamy.in)
(repo `shubham-yadav-git/wallapp-react`, private) and shares its Firebase backend.
Several files are **ports of website files and must stay in sync** with them:

| App | Website source of truth |
|---|---|
| `ImageUrls.kt` (+ `ImageUrlsTest`) | `src/lib/imageUrls.js` (+ tests) |
| `FeedSnapshot.kt` | `scripts/build-feed.mjs`, `src/lib/feedSnapshot.js` (URL prefixes!) |
| `WallpaperRepository.kt` | `src/features/public/usePublicRecords.js`, `src/hooks/useCategories.js` |
| `SavedStore.kt` (+ `SavedStoreTest`) | `src/lib/savedStore.js` (+ tests) — storage JSON shape is shared |
| `SavedSync.kt` | `src/features/public/savedSync.js` |
| `PopularityStats.kt` | `src/features/public/stats.js` |
| `PinUtils.kt` (+ `PinUtilsTest`) | `src/features/public/pinUtils.js` (FNV-1a hash, share URL) |

## Source map (`app/src/main/java/com/sky/wallapp/`)

| File | Role |
|---|---|
| `WallAppApplication.kt` | App init: Glide config (500 MB disk cache, 8 source threads), Firebase persistence, `SavedRepository.init`, `WallpaperRepository.prewarm`, re-schedules auto-wallpaper |
| `MainActivity.kt` | Tabs **Home** (website header: brand, search, account; chips All / 🔥 Popular / categories; recently viewed strip; masonry feed paged 30 at a time; native ads every 15), **Saved** (sync banner; Favourites / Collections / Recently viewed), **You** (account + settings). Handles App Links `?pin=` / `?c=` |
| `ImageActivity.kt` | Close-up: contained image over blurred copy, counter, swipe through the opening list, Share (web link) / Open full size / Download (DownloadManager) / Save ▾ / Set as wallpaper, category chip, "More like this" (60, seeded by the image); related opens push a Back step |
| `CollectionActivity.kt` | One collection: rename, delete (Undo shown on Saved), long-press to remove a wallpaper (Undo) |
| `SaveToSheet.kt` | "Save to…" bottom sheet: Favourites, collections, inline New collection |
| `FeedAdapter.kt` | One `ListAdapter` for every masonry screen; `FeedRow` types (pin, ad, heading, empty, recent strip, sync banner, saved tabs, collection card); layout manager factory (2/3/4 columns) |
| `ViewHolder.kt` | Pin tile: ratio from `RatioCache`, placeholder colour from the key, fallback image sources, saved badge |
| `RatioCache.kt` | Tile height/width: known from URL/fields or measured before placement (2.5 s cap → fallback 1.4), so tiles never change shape |
| `Wallpaper.kt` | App-wide wallpaper model; `key` = `"{category}/{id}"`; defensive parse from a `DataSnapshot` |
| `WallpaperRepository.kt` | Loads everything: cached feed.json → fresh feed.json → live `orderByKey().startAfter(last)` per category (full live read only without a snapshot). Never reads the DB root |
| `SavedRepository.kt` | Device copy of saved data (prefs key `wallapp:saved:v1`) + actions with analytics; migrates old URL-based favourites once |
| `SavedSync.kt` | Google sign-in (Credential Manager → Firebase Auth) and `/users/{uid}/saved` sync |
| `PopularityStats.kt` | `/stats/{path}/{id}` downloads (+1 once per device) and favorites (±1) |
| `FeedAds.kt` | Up to 5 AdMob native ads rendered as pins; keep the "Ad" badge + "Sponsored" line (policy) |
| `WallpaperSwipeSession.kt` | In-memory list handoff to the detail screen (falls back to lookup by key) |
| `WallpaperApplier.kt` | Resizes/composes a bitmap to screen size (FIXED or SCROLLABLE) and calls `WallpaperManager` |
| `AutoWallpaperManager.kt` / `DailyWallpaperWorker.kt` | Opt-in daily rotation through favourites (by key) via WorkManager |
| `AnalyticsTracker.kt` | Firebase Analytics wrapper; website event names: `select_content`, `search`, `file_download`, `share`, `add_to_wishlist`, `remove_from_wishlist`, `create_collection`, `login` |

## Firebase Realtime Database

```
/categories/{pushId}    { name, path, icon?, order? }   sorted by order (missing last), then push key
/{path}/{pushId}        { title, search, image, cloudinaryUrl?, thumbs?, width?, height? }
/stats/{path}/{pushId}  { downloads, favorites }
/users/{uid}/saved      { json: "<SavedStore JSON>", updatedAt }
```

- **Rules (deployed from the website repo):** reads are public except the **root**
  and `/users`. The app may only write `/stats/**` counters (+1 / ±1 via
  `ServerValue.increment`) and the signed-in user's own `/users/{uid}/saved`.
  Never read `/` or write anything else; never ship secrets (Cloudinary secret,
  service accounts) in the APK.
- Records are parsed by hand (`Wallpaper.fromSnapshot`), not by reflection, so
  unexpected field types can't crash the app.
- **Image URLs:** use `ImageUrls` (tile 474/236 px, large 1080/1400 px, original,
  download). Never load originals in lists. Main URL = `cloudinaryUrl` else `image`.
- **Identity:** `Wallpaper.key` (`"{path}/{pushId}"`) everywhere — favourites,
  collections, recent, stats, links. Push keys sort by time (newest = descending).
- feed.json (`https://wallapp.shubhamy.in/feed.json`, regenerated ~30 min) is cached
  in `filesDir/feed.json` and parsed with Gson's streaming reader.

## UI conventions

- Website design tokens: accent `#E6553A` (`@color/primary`), ink `#111111`, muted
  `#767676`, soft `#EFEFEF`; 16dp tile corners, 8dp gaps, 36dp chips, 48dp pill
  buttons (`Widget.App.Pill.*`), black rounded snackbar. Dark theme swaps neutrals.
- Every masonry list goes through `FeedAdapter` + `FeedRow`; measure ratios with
  `RatioCache.measure` before revealing pins.
- Inside a `NestedScrollView`, use `FeedAdapter.newLayoutManager(ctx, insideScrollView = true)`
  (the `GAP_HANDLING_NONE` variant measures to 0 height there).
- All activities are edge-to-edge (`enableEdgeToEdge()`); handle insets explicitly.
- Feedback via snackbars with View / Undo, matching the website's wording.

## Conventions

- Match the existing style: plain Activities + ViewBinding, Kotlin `object`
  singletons for stores/managers, StateFlow + `repeatOnLifecycle` for observing,
  SharedPreferences via `androidx.core.content.edit {}`.
- User-facing strings go in `res/values/strings.xml`. Colors have light + `values-night` variants.
- Wallpaper setting, downloads and feed parsing stay off the main thread.
- Keep minSdk 24 compatibility; gate newer APIs with `Build.VERSION.SDK_INT`.
- Dependencies are declared inline in `app/build.gradle` (no version catalog).
- Run `./gradlew testDebugUnitTest` after touching any ported file.

## Setup still required (outside the code)

- **Google sign-in:** add the SHA-1/SHA-256 of the debug keystore, the upload key and
  the Play App Signing key to the `com.sky.wallapp` app in Firebase, then replace
  `app/google-services.json`. Until then sign-in fails.
- **App Links:** publish `docs/assetlinks.json` (with the real release SHA-256) at
  `https://wallapp.shubhamy.in/.well-known/assetlinks.json` (website `public/.well-known/`).

## Known issues / tech debt (verify before relying on these)

- `WallAppApplication` enables Firebase `Logger.Level.DEBUG` in all builds, including release.
- First launch downloads the 2.3 MB feed before showing tiles; later launches use the cache.
- `Splashscreen` Activity, the `navigation-*` dependencies and `firebase-messaging`
  (no messaging service is registered) are unused.
- `app/release/app-release.aab` is a tracked build artifact.

## Roadmap

Product roadmap and phases: `docs/PRODUCT_DEVELOPMENT_PLAN.md`. Feature work
should line up with it (featured section, categories, search, daily wallpaper, …).

## Other docs (human-oriented, partially outdated)

- `ARCHITECTURE.md` — CI/CD pipeline diagram
- `DEPLOYMENT_GUIDE.md`, `QUICK_REFERENCE.md`, `PLAY_STORE_SERVICE_ACCOUNT_SETUP.md` — release process
- `.github/IN_APP_UPDATE_FEATURE.md` — in-app update design
- `docs/index.html` — GitHub Pages site (privacy policy / app-ads.txt host)

If these disagree with this file or the code, trust the code, then this file.
