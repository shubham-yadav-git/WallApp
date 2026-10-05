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
- **SDK:** minSdk 23, target/compileSdk 36
- **Backend:** Firebase Realtime Database (read-only from the app), no own server
- **Other SDKs:** Firebase Analytics + Messaging, AdMob banner, Play In-App Update, Play In-App Review, Glide 5, WorkManager

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
  production ad unit IDs in debug code paths, and never click real ads while testing.

## Source map (`app/src/main/java/com/sky/wallapp/`)

| File | Role |
|---|---|
| `WallAppApplication.kt` | App init: Firebase offline persistence, WorkManager config, re-schedules auto-wallpaper |
| `Splashscreen` (`splashscreen.kt`) | Legacy splash Activity — **not** in the manifest; `MainActivity` is the launcher and uses the SplashScreen API |
| `MainActivity.kt` | Home: nav drawer of categories, Trending / category / Favorites feeds, search, AdMob banner, in-app update, auto-wallpaper menu |
| `ImageActivity.kt` | Detail screen: full preview, swipe between items, favorite (button / double-tap), set wallpaper, save, share, in-app review prompt |
| `CategoryAdapter.kt` | Drawer category grid; lazily fetches each category's first image as its thumbnail |
| `ViewHolder.kt` | Wallpaper grid cell (`res/layout/row.xml`) |
| `Model.kt` / `Category.kt` | Firebase data classes (see schema below) |
| `FavoritesStore.kt` | Local favorites as JSON in SharedPreferences, keyed by `image` URL |
| `WallpaperSwipeSession.kt` | In-memory feed handoff from list → detail (max 6 sessions, lost on process death) |
| `WallpaperApplier.kt` | Resizes/composes a bitmap to screen size (FIXED or SCROLLABLE) and calls `WallpaperManager` |
| `AutoWallpaperManager.kt` / `DailyWallpaperWorker.kt` | Opt-in daily rotation through favorites via WorkManager |
| `AnalyticsTracker.kt` | Thin wrapper over Firebase Analytics; params are truncated to 100 chars |
| `WallAppGlideModule.kt` | Glide config (500 MB disk cache) |

## Firebase Realtime Database schema

The app only reads. Data is maintained outside this repo.

```
/categories/{key}: { name, path, icon }        -> Category
/{category.path}/{key}: { title, image, search, thumbs, cloudinaryUrl }  -> Model
```

- `Model` and `Category` are deserialized by reflection and are `-keep`-ed in
  `app/proguard-rules.pro`. **Do not rename their properties or make them
  non-nullable / remove the default constructor** — release builds will silently
  get nulls or crash.
- **Image URL rule:** display/download `cloudinaryUrl` when non-blank, otherwise
  `image` (the primary host returns HTTP 402 when over quota). Every new image load
  must follow this rule.
- **Identity rule:** `image` is the stable ID for a wallpaper (favorites, last
  applied auto-wallpaper). Don't key on `cloudinaryUrl` or `title`.
- "Trending" = first 20 items from every category path, merged and shuffled
  client-side (`MainActivity.loadTrending`). It costs one RTDB read per category.

## Conventions

- Match the existing style: plain Activities + ViewBinding, Kotlin `object`
  singletons for stores/managers, SharedPreferences via `androidx.core.content.edit {}`.
  Each feature uses its own prefs file name constant — don't share keys across files.
- User-facing strings go in `res/values/strings.xml` (some legacy code still
  hardcodes strings; don't add more). Colors have light + `values-night` variants.
- Log analytics through `AnalyticsTracker.logEvent(snake_case_name, mapOf(...))`.
  Don't send PII; titles/queries are OK.
- Always remove Firebase listeners / stop `FirebaseRecyclerAdapter`s you start
  (see `onDestroy` in `MainActivity`).
- Load images with Glide using `applicationContext` or a live Activity context
  (see the destroyed-Activity guard in `CategoryAdapter`).
- Wallpaper setting and image download must stay off the main thread
  (WorkManager / coroutines / Glide `submit()` on IO).
- Keep minSdk 23 compatibility; gate newer APIs with `Build.VERSION.SDK_INT`.
- Dependencies are declared inline in `app/build.gradle` (no version catalog).

## Known issues / tech debt (verify before relying on these)

- `AndroidManifest.xml` hardcodes the **production** AdMob app ID instead of
  `${admobAppId}`, so the `manifestPlaceholders` switch in `app/build.gradle` has no effect.
- `WallAppApplication` enables Firebase `Logger.Level.DEBUG` in all builds, including release.
- `FavoritesStore` doesn't save `cloudinaryUrl`, so favorites and the daily worker
  always load from the primary `image` host (the 402 fallback never applies to them).
- `ImageActivity.saveImage()` writes directly to public `Pictures/WallApp` with
  `File` APIs; this likely fails on Android 10 (API 29) without `MediaStore`.
- `MainActivity.loadTrending()` never finishes if a category read is cancelled
  as the last callback — the completion check only runs in `onDataChange`.
- `Splashscreen` Activity, the `navigation-*` dependencies, and `firebase-messaging`
  (no messaging service is registered) are unused.
- `app/release/app-release.aab` is a tracked build artifact.
- Unit/UI tests are placeholders only.

## Roadmap

Product roadmap and phases: `docs/PRODUCT_DEVELOPMENT_PLAN.md`. Feature work
should line up with it (featured section, categories, search, daily wallpaper, …).

## Other docs (human-oriented, partially outdated)

- `ARCHITECTURE.md` — CI/CD pipeline diagram
- `DEPLOYMENT_GUIDE.md`, `QUICK_REFERENCE.md`, `PLAY_STORE_SERVICE_ACCOUNT_SETUP.md` — release process
- `.github/IN_APP_UPDATE_FEATURE.md` — in-app update design
- `docs/index.html` — GitHub Pages site (privacy policy / app-ads.txt host)

If these disagree with this file or the code, trust the code, then this file.
