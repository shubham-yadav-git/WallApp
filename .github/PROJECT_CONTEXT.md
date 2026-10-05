# WallApp Project Context

> For AI agents: the canonical, up-to-date instructions are in `/AGENTS.md`.

## Quick Facts
- **App Name**: WallApp
- **Package**: com.sky.wallapp
- **Current Version**: see `versionCode` / `versionName` in `app/build.gradle`
- **Platform**: Android (Kotlin)
- **Min SDK**: 23 | Target SDK: 36
- **Java**: 21
- **Build Tool**: Gradle

## Current State
✅ CI/CD workflows configured and working
✅ **Automatic deployment to Play Store internal track on master push**
✅ Keystore setup complete (JKS with auto-conversion)
✅ Firebase integrated (Analytics, Crashlytics, AdMob)
✅ Play Store service account configured
✅ Automated deployments via GitHub Actions
✅ In-app updates fully implemented

## Next Version Numbers
Increment `versionCode` by 1 from the current value in `app/build.gradle`; bump `versionName` semantically.

## GitHub Repository
- URL: https://github.com/shubham-yadav-git/WallApp
- Workflows: .github/workflows/
- Actions: https://github.com/shubham-yadav-git/WallApp/actions

## Critical Secrets (GitHub)
- KEYSTORE_BASE64
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD
- PLAY_STORE_SERVICE_ACCOUNT_JSON

## Common Tasks

### Deploy New Version (Automatic - Recommended)
```bash
# 1. Update version in app/build.gradle
# 2. Commit and push to master
git add app/build.gradle
git commit -m "Bump version to X.X.X"
git push origin master

# ✅ Automatically deploys to Play Store internal track!
```

### Deploy New Version (Manual - For Production)
```bash
# 1. Update version in app/build.gradle
# 2. Commit changes
git add app/build.gradle
git commit -m "Bump version to X.X.X"
git push

# 3. Create and push tag
git tag vX.X.X
git push origin vX.X.X

# 4. Go to GitHub Actions → Deploy to Google Play
#    → Run workflow → Select production track
```

### Test Build Locally
```bash
./gradlew assembleDebug
./gradlew bundleRelease
```

### Check Workflow Status
Visit: https://github.com/shubham-yadav-git/WallApp/actions

## Documentation Files
- README.md - Main docs
- ARCHITECTURE.md - App architecture
- DEPLOYMENT_GUIDE.md - Deployment instructions
- PLAY_STORE_SERVICE_ACCOUNT_SETUP.md - Play Console setup
- QUICK_REFERENCE.md - Quick commands

