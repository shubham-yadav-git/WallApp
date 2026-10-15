# AGENTS.md — WallApp

Instructions for AI coding agents (Claude Code, Codex, Copilot, Cursor, etc.).
This file is the single source of truth for agents. `CLAUDE.md`, `GEMINI.md`,
and `.github/copilot-instructions.md` point here. Keep it in sync with the code.

## Agent Skills & Entry Points

As an AI assistant, I (and other agents) maintain core competencies for this project:

- **Android Expert:** Proficient in Activity lifecycle, ViewBinding, and XML layouts (no Fragments/Compose).
- **Firebase Integrator:** Expert in Firebase RTDB (best-effort writes, offline persistence), Auth (Credential Manager), and Analytics.
- **Parity Guard:** Ensuring logic remains in sync between the Android app and the React website.
- **Deployment Aware:** Managing the GitHub Actions CI/CD pipeline and Play Store internal track rules.
- **Resourceful:** Skilled in optimizing Glide, WorkManager, and AdMob native integrations.

### Agent Entry Points:
- `CLAUDE.md`: Claude-specific environment instructions.
- `GEMINI.md`: Gemini-specific environment instructions.
- `DEVELOPER_ASSISTANT.md`: General identity and skill summary.

### Specialized Agent Roles:
- [Parity Guardian](PARITY_GUARDIAN.md): Ensures logic sync between Android and React Web.
- [Release Specialist](RELEASE_SPECIALIST.md): Manages safe deployments, versioning, and release notes.
- [UI/UX Stylist](UI_UX_STYLIST.md): Enforces design system and accessibility.
- [Data Architect](DATA_ARCHITECT.md): Optimizes performance and Firebase usage.

## What this is

WallApp HD Wallpapers — a single-module Android app (`com.sky.wallapp`,
[Play Store](https://play.google.com/store/apps/details?id=com.sky.wallapp)).
Users browse wallpapers by category, favorite them, set/save/share them, and
can enable a daily auto-wallpaper rotation from favorites.

- **Language:** Kotlin only
- **UI:** XML layouts + ViewBinding, Activities (no Fragments, no Compose)
- **Build:** Gradle (Groovy DSL), AGP 9.1, JDK 21, minSdk 24, target/compileSdk 36
- **Backend:** Firebase Realtime Database, no own server
- **Other SDKs:** Firebase Analytics/Messaging/Auth, Credential Manager, AdMob, WorkManager, Glide 5

## Commands

```bash
./gradlew assembleDebug          # debug APK (uses AdMob test IDs)
./gradlew bundleRelease          # release AAB (minified; needs signing)
./gradlew lint                   # Android lint
./gradlew test                   # JVM unit tests
```

## ⚠️ Release & deployment rules

- **Every push to `master` deploys to Play Store.** Do not push to `master` without explicit request.
- Always bump `versionCode` in `app/build.gradle` for every release.
- **Always update release notes** in `distribution/whatsnew/whatsnew-en-US` before pushing to `master`.
- Work on branches and open PRs; `build-check.yml` runs on PRs.
- Never commit keystores, secrets, or service-account JSON.

## Website parity

The app mirrors [wallapp.shubhamy.in](https://wallapp.shubhamy.in). Keep logic in sync for:
`ImageUrls`, `SavedStore`, `SavedSync`, `PinUtils`, `PinPaths`, `Members`, etc. (See [Parity Guardian](PARITY_GUARDIAN.md)).

## Source map (`app/src/main/java/com/sky/wallapp/`)

- `MainActivity.kt`: Home/Saved/You tabs, App Links, search, filtering.
- `ImageActivity.kt`: Wallpaper detail/glass view, downloads, sharing.
- `WallpaperRepository.kt`: Feed loading (feed.json cache + live updates).
- `SavedRepository.kt`: Local copy of favorites/collections.
- `SavedSync.kt`: Cloud sync via Firebase and Google Sign-in.
- `AutoWallpaperManager.kt`: WorkManager rotation logic.

(For full details, see the code or the [Data Architect](DATA_ARCHITECT.md) guide.)
