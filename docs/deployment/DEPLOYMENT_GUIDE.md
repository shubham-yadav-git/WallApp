# 🚀 Automated Deployment Setup Guide

This guide explains how to set up automated builds and deployments for WallApp using GitHub Actions.

## 📋 Table of Contents
1. [Workflows Overview](#workflows-overview)
2. [Quick Start](#quick-start)
3. [Setting Up Secrets](#setting-up-secrets)
4. [Usage Instructions](#usage-instructions)
5. [Troubleshooting](#troubleshooting)

---

## 🔄 Workflows Overview

### 1. **Build Check** (`build-check.yml`)
- **Triggers**: Every push to main/master/develop branches
- **Purpose**: Ensures the app builds successfully
- **Output**: Debug APK (available for 7 days)
- **No setup required** - works immediately!

### 2. **Release Build** (`release-build.yml`)
- **Triggers**: 
  - When you create a git tag (e.g., `v1.2.1`)
  - Manual trigger from GitHub Actions tab
- **Purpose**: Creates signed release builds
- **Output**: 
  - Release AAB and APK
  - GitHub Release with downloadable files
- **Requires**: Keystore secrets (optional, can build unsigned)

### 3. **Deploy to Play Store** (`deploy-playstore.yml`)
- **Triggers**: 
  - When you create a git tag (e.g., `v1.2.1`)
  - Manual trigger with track selection
- **Purpose**: Automatically uploads to Google Play Store
- **Output**: Published app on Play Store
- **Requires**: Keystore + Play Console API credentials

---

## 🚀 Quick Start

### Option A: Start Simple (No Setup Needed!)
The **Build Check** workflow works immediately. Just push code:
```bash
git add .
git commit -m "Update app"
git push
```
✅ Check the "Actions" tab on GitHub to see the build

---

### Option B: Create Releases (Requires Keystore)

1. **Add your secrets** (see below)
2. **Create a release tag**:
```bash
git tag v1.2.1
git push origin v1.2.1
```
3. **Check GitHub** → Releases for your AAB/APK files

---

### Option C: Auto-Deploy to Play Store (Full Automation)

1. **Add all secrets** (keystore + Play Console API)
2. **Create a release tag**:
```bash
git tag v1.3.0
git push origin v1.3.0
```
3. **App automatically uploads** to Play Store!

---

## 🔐 Setting Up Secrets

Go to your GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

### For Signed Builds (Workflows 2 & 3)

#### 1. **KEYSTORE_BASE64**
Convert your keystore to base64:
```bash
base64 -i /path/to/your/keystore.jks | pbcopy
```
Paste the output as the secret value.

#### 2. **KEYSTORE_PASSWORD**
Your keystore password (the one you use when creating the keystore)

#### 3. **KEY_ALIAS**
Your key alias (usually something like "wallapp" or "release")

#### 4. **KEY_PASSWORD**
Your key password (might be the same as keystore password)

---

### For Play Store Deployment (Workflow 3)

#### 5. **PLAY_STORE_SERVICE_ACCOUNT_JSON**

You need a Google Play Console service account.

**📖 See detailed guide**: `PLAY_STORE_SERVICE_ACCOUNT_SETUP.md` for complete step-by-step instructions with screenshots references and troubleshooting.

**Quick version:**

1. Go to [Google Play Console](https://play.google.com/console)
2. Select your app → **Setup** → **API access**
3. Click **Create new service account**
4. Follow the link to Google Cloud Console
5. Create a service account with these permissions:
   - Service Account User
6. Create a JSON key for this service account
7. Download the JSON file
8. Grant permissions in Play Console (Manage releases)
9. Convert it to base64:
```bash
base64 -i /path/to/service-account.json | pbcopy
```
9. Paste as secret value

10. Back in Play Console, grant access:
    - **Releases** → Can manage production releases
    - **App access** → View app information

---

## 📝 Usage Instructions

### How to Create a Release

1. **Update version** in `app/build.gradle`:
```gradle
versionCode 16
versionName "1.2.3"
```

2. **Commit the changes**:
```bash
git add app/build.gradle
git commit -m "Bump version to 1.2.1"
git push
```

3. **Create and push a tag**:
```bash
git tag v1.2.1
git push origin v1.2.1
```

4. **Watch the magic happen**:
   - Go to GitHub → **Actions** tab
   - See workflows running
   - Check **Releases** tab for downloadable files
   - Check Play Console for the upload (if configured)

---

### Manual Trigger

You can also trigger workflows manually:

1. Go to GitHub → **Actions** tab
2. Select the workflow (e.g., "Deploy to Google Play")
3. Click **Run workflow**
4. Choose options (like release track: internal/alpha/beta/production)
5. Click **Run workflow**

---

## 🛠️ Troubleshooting

### Build fails with "SDK not found"
✅ This is handled automatically - GitHub Actions has Android SDK pre-installed

### "Keystore was tampered with, or password was incorrect"
❌ Check your secrets - password or key alias might be wrong

### Play Store upload fails
❌ Common issues:
- Service account doesn't have permissions
- Version code not incremented
- AAB not properly signed

### No signing config found
❌ Your `app/build.gradle` needs a signing config. See below.

---

## 📦 Configuring Signing in build.gradle

Update your `app/build.gradle` to support CI/CD signing:

```gradle
android {
    // ... existing config ...
    
    signingConfigs {
        release {
            // For CI/CD (GitHub Actions)
            if (System.getenv("CI")) {
                storeFile file(System.getenv("KEYSTORE_FILE") ?: "keystore.jks")
                storePassword System.getenv("KEYSTORE_PASSWORD")
                keyAlias System.getenv("KEY_ALIAS")
                keyPassword System.getenv("KEY_PASSWORD")
            }
            // For local builds (optional - use your own keystore path)
            else if (project.hasProperty('RELEASE_STORE_FILE')) {
                storeFile file(RELEASE_STORE_FILE)
                storePassword RELEASE_STORE_PASSWORD
                keyAlias RELEASE_KEY_ALIAS
                keyPassword RELEASE_KEY_PASSWORD
            }
        }
    }
    
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

---

## 🎯 Next Steps

1. ✅ **Start with Build Check** - Already working!
2. ⚙️ **Add keystore secrets** - Enable signed releases
3. 🚀 **Set up Play Console API** - Full automation!
4. 📱 **Create your first automated release**

---

## 📚 Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Google Play Console API](https://developers.google.com/android-publisher)
- [Android App Signing](https://developer.android.com/studio/publish/app-signing)

---

**Questions?** Check the GitHub Actions logs for detailed error messages!

