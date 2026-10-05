# GitHub Copilot Instructions for WallApp

The canonical agent instructions are in [`AGENTS.md`](../AGENTS.md) at the repo root.
Read it before suggesting changes. Key points:

- Kotlin-only Android app (`com.sky.wallapp`), XML layouts + ViewBinding, minSdk 24, target/compileSdk 36, JDK 21, AGP 9.
- Version lives in `app/build.gradle` (`versionCode` / `versionName`); always bump `versionCode` for a release.
- **Every push to `master` deploys to the Play Store internal track.** Prefer branches + PRs.
- Firebase RTDB data classes `Model` / `Category` are reflection-mapped and kept by ProGuard — don't rename their fields.
- Load `cloudinaryUrl` when non-blank, else `image`; use `image` as the wallpaper's identity.
- Never commit keystores, `local.properties`, or service-account JSON.
- Verify with `./gradlew assembleDebug`.
