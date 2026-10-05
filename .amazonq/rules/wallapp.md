# WallApp rules for Amazon Q

The canonical agent instructions are in `AGENTS.md` at the repo root. Read it before making changes. Key points:

- Kotlin-only Android app (`com.sky.wallapp`), XML layouts + ViewBinding, minSdk 23, target/compileSdk 36, JDK 21, AGP 9.
- **Every push to `master` deploys to the Play Store internal track.** Don't push to `master` unless asked.
- Bump `versionCode` in `app/build.gradle` only for a release.
- Firebase RTDB data classes `Model` / `Category` are reflection-mapped and kept by ProGuard — don't rename their fields.
- Load `cloudinaryUrl` when non-blank, else `image`; use `image` as the wallpaper's identity.
- Never commit keystores, `local.properties`, or service-account JSON.
- Verify with `./gradlew assembleDebug`.
