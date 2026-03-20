# WallApp Project Context

## Quick Facts
- **App Name**: WallApp
- **Package**: [Check AndroidManifest.xml]
- **Current Version**: 1.2.3 (Code: 16)
- **Platform**: Android (Java)
- **Min SDK**: 21 | Target SDK: 34
- **Java**: 21
- **Build Tool**: Gradle

## Current State
✅ CI/CD workflows configured and working
✅ Keystore setup complete (JKS with auto-conversion)
✅ Firebase integrated (Analytics, Crashlytics, AdMob)
✅ Play Store service account configured
✅ Automated deployments via GitHub Actions

## Next Version Numbers
- Next patch: 1.2.4 (Code: 17)
- Next minor: 1.3.0 (Code: 18)
- Next major: 2.0.0 (Code: 19)

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

### Deploy New Version
```bash
# 1. Update version in app/build.gradle
# 2. Commit changes
git add app/build.gradle
git commit -m "Bump version to X.X.X"
git push

# 3. Create and push tag
git tag vX.X.X
git push origin vX.X.X
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

