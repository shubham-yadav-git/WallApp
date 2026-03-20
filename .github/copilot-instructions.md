# GitHub Copilot Instructions for WallApp

## Project Overview
WallApp is an Android wallpaper application built with:
- **Language**: Java
- **Build System**: Gradle
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Java Version**: 21

## Project Structure
```
WallApp/
├── app/
│   ├── src/main/
│   │   ├── java/           # Java source code
│   │   ├── res/            # Resources (layouts, drawables, etc.)
│   │   └── AndroidManifest.xml
│   ├── build.gradle        # App-level Gradle config
│   └── google-services.json # Firebase config
├── gradle/                 # Gradle wrapper
├── build.gradle            # Project-level Gradle config
└── docs/                   # Documentation and assets
```

## Key Configuration Files

### Version Management
- **Location**: `app/build.gradle`
- **Current Version**: 1.2.3 (versionCode 16)
- **Update Process**: 
  1. Increment versionCode (always!)
  2. Update versionName (semantic versioning)
  3. Commit changes
  4. Create and push tag (e.g., v1.2.3)

### Keystore & Signing
- **Keystore Location**: Root directory (upload-keystore.jks)
- **Type**: JKS format (auto-converted to BKS for older Android)
- **Signing Config**: Configured in app/build.gradle
- **GitHub Secrets**: KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, KEYSTORE_BASE64

### Firebase
- **Config**: google-services.json (in both root and app/)
- **Services**: Analytics, Crashlytics, AdMob
- **App ID**: Available in debug.txt and release.txt

## CI/CD Workflows

### Build Check (`build-check.yml`)
- Triggers on push to main/master/develop
- Builds debug APK
- No secrets required

### Release Build (`release-build.yml`)
- Triggers on git tags (v*.*.*)
- Creates signed AAB and APK
- Creates GitHub Release
- Requires: Keystore secrets

### Deploy to Play Store (`deploy-playstore.yml`)
- **Triggers on push to master** (automatic deployment to internal track) 🆕
- Triggers on git tags or manual workflow dispatch
- Uploads to Google Play Console
- Requires: Keystore + PLAY_STORE_SERVICE_ACCOUNT_JSON
- **Default track: internal** (automatic)
- Manual track selection: internal / alpha / beta / production

## Development Guidelines

### Code Style
- Follow Android best practices
- Use proper naming conventions (camelCase for methods, PascalCase for classes)
- Add comments for complex logic
- Keep activities/fragments modular

### Dependencies
- Use stable versions
- Update through Gradle
- Test after major updates
- Check for CVEs regularly

### Version Bumping
**ALWAYS increment versionCode before deployment!**
```gradle
defaultConfig {
    versionCode 16  // Increment this
    versionName "1.2.3"  // Semantic versioning
}
```

### Building
```bash
# Debug build
./gradlew assembleDebug

# Release build (signed)
./gradlew bundleRelease

# Clean build
./gradlew clean build
```

### Deployment
**Automatic Deployment (Recommended)**:
1. Update version in app/build.gradle
2. Commit: `git commit -m "Bump version to X.X.X"`
3. Push: `git push origin master`
4. Automatically deploys to Play Store internal track!

**Manual Production Deployment**:
1. Update version in app/build.gradle
2. Commit: `git commit -m "Bump version to X.X.X"`
3. Tag: `git tag vX.X.X`
4. Push: `git push origin vX.X.X`
5. Go to GitHub Actions → Deploy to Google Play → Select production track

## Common Issues & Solutions

### Keystore Errors
- Ensure KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD are correct
- JKS auto-converts to BKS for Android < 9
- Check deployment-helper.sh for conversion logic

### Play Store Upload Failures
- Version code must be incremented
- Service account needs "Manage releases" permission
- AAB must be properly signed
- Check workflow logs for specific errors

### Build Failures
- Run `./gradlew clean` first
- Check Java version (requires Java 21)
- Verify google-services.json is present
- Check for dependency conflicts

## Important Files to Reference

### Documentation
- `README.md` - Main project documentation
- `ARCHITECTURE.md` - Application architecture
- `DEPLOYMENT_GUIDE.md` - Complete deployment guide
- `PLAY_STORE_SERVICE_ACCOUNT_SETUP.md` - Play Console setup
- `QUICK_REFERENCE.md` - Quick command reference

### Scripts
- `deployment-helper.sh` - Deployment automation script
- `debug_firebase.sh` - Firebase debugging script

### Configuration
- `app/build.gradle` - App configuration
- `build.gradle` - Project configuration
- `google-services.json` - Firebase configuration
- `gradle.properties` - Gradle properties
- `proguard-rules.pro` - ProGuard rules

## GitHub Actions Context
When suggesting workflow changes:
- Use ubuntu-latest for runners
- Android SDK is pre-installed
- Use actions/checkout@v4 for code checkout
- Cache Gradle dependencies for speed
- Store artifacts with actions/upload-artifact@v4
- Use r0adkll/sign-android-release@v1 for signing

## AdMob Integration
- app-ads.txt file is present in root and docs/
- Configured through Firebase/AdMob console
- Test ads available in debug builds
- Production ads in release builds

## Pro Tips for AI Assistance
1. Always check current versionCode before suggesting updates
2. Reference existing workflow files for CI/CD changes
3. Test commands locally before committing
4. Keep documentation updated with code changes
5. Use semantic versioning for releases
6. Never commit keystore files to git (except upload-keystore.jks which is intentionally tracked)

