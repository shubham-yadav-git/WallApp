# 🚀 Deploy to Play Store - Quick Guide

**Date:** March 20, 2026  
**Status:** Ready to deploy from master branch

---

## 📋 What You Need (Required Secrets)

Before deploying, you need these GitHub Secrets configured:

### For Signing the AAB:
- ✅ `KEYSTORE_BASE64` - Your keystore file (base64 encoded)
- ✅ `KEYSTORE_PASSWORD` - Keystore password
- ✅ `KEY_ALIAS` - Key alias  
- ✅ `KEY_PASSWORD` - Key password

### For Play Store Upload:
- ✅ `PLAY_STORE_SERVICE_ACCOUNT_JSON` - Service account JSON (base64 encoded)

---

## 🎯 Option 1: Deploy via GitHub Actions (Recommended)

### Step 1: Go to GitHub Actions
1. Open: https://github.com/shubham-yadav-git/WallApp/actions
2. Click **"Deploy to Google Play"** workflow (left sidebar)
3. Click **"Run workflow"** button (top right)

### Step 2: Configure Deployment
- **Branch:** Select `master` (default)
- **Release track:** Choose one:
  - `internal` - Internal testing (recommended for first deploy)
  - `alpha` - Alpha testing
  - `beta` - Beta testing  
  - `production` - Production release

### Step 3: Run!
- Click **"Run workflow"** (green button)
- Wait for the build to complete (~5-10 minutes)
- Check the Actions tab for progress

### What Happens:
1. ✅ Builds signed AAB with your keystore
2. ✅ Uploads to Play Store on the selected track
3. ✅ Saves artifacts for 90 days
4. ✅ Creates mapping.txt for debugging

---

## 🎯 Option 2: Local Build + Manual Upload

### Step 1: Build Locally
```bash
cd /Users/shubhamy/Developer/hobby_projects/WallApp

# Build release AAB
./gradlew bundleRelease

# Find the AAB file
ls -lh app/build/outputs/bundle/release/
```

### Step 2: Upload to Play Console
1. Go to [Google Play Console](https://play.google.com/console)
2. Select your WallApp
3. Go to **Release** → **Production** (or Testing)
4. Click **"Create new release"**
5. Upload the AAB file
6. Fill in release notes
7. Review and rollout

---

## 📝 Before First Deploy - Setup Required

If this is your **first time** deploying via automation, you need:

### 1. Configure GitHub Secrets

Go to: https://github.com/shubham-yadav-git/WallApp/settings/secrets/actions

Add these secrets:

#### Keystore Secrets (for signing):
```bash
# Get your keystore base64
base64 -i /path/to/your/keystore.jks | pbcopy

# Then paste into GitHub Secrets as KEYSTORE_BASE64
```

**Required secrets:**
- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

#### Play Store Secret (for upload):
```bash
# Get service account JSON base64
base64 -i /path/to/service-account.json | pbcopy

# Then paste into GitHub Secrets as PLAY_STORE_SERVICE_ACCOUNT_JSON
```

**Guide:** See `PLAY_STORE_SERVICE_ACCOUNT_SETUP.md` for how to create service account

### 2. First Upload Must Be Manual

⚠️ **Important:** Google Play requires the **first version** of your app to be uploaded manually through Play Console. After that, you can use automated deployments.

**If this is your first release:**
1. Build locally (see Option 2 above)
2. Upload manually to Play Console
3. Complete the Play Store listing
4. After first version is live, use automation for updates

---

## 🎮 Using the Deployment Helper

```bash
./deployment-helper.sh

# Then choose:
# 3 - Create a release tag (triggers release-build.yml)
# This builds APK/AAB and creates GitHub Release
# Then manually trigger deploy-playstore.yml for Play Store
```

---

## 🔍 Check Secrets Status

### Quick Check:
Go to: https://github.com/shubham-yadav-git/WallApp/settings/secrets/actions

You should see:
- ✅ KEYSTORE_BASE64
- ✅ KEYSTORE_PASSWORD
- ✅ KEY_ALIAS
- ✅ KEY_PASSWORD
- ✅ PLAY_STORE_SERVICE_ACCOUNT_JSON (if deploying to Play Store)

### Test Keystore Works:
You can test signing locally first:
```bash
./gradlew bundleRelease
# If it builds successfully, your keystore is configured correctly
```

---

## 📊 Deployment Tracks Explained

| Track | Purpose | Visibility |
|-------|---------|------------|
| **Internal** | Quick testing with internal testers | Up to 100 testers |
| **Alpha** | Early testing with wider audience | Closed testing |
| **Beta** | Pre-release testing | Open or closed testing |
| **Production** | Public release | All users |

**Recommendation:** Start with `internal` track for testing!

---

## ⚠️ Common Issues

### Issue 1: "No service account configured"
**Solution:** Set up `PLAY_STORE_SERVICE_ACCOUNT_JSON` secret
**Guide:** See `PLAY_STORE_SERVICE_ACCOUNT_SETUP.md`

### Issue 2: "Keystore not found"
**Solution:** Configure all 4 keystore secrets (KEYSTORE_BASE64, etc.)

### Issue 3: "App not found on Play Console"
**Solution:** First version must be uploaded manually through Play Console

### Issue 4: "Version code already exists"
**Solution:** Increment `versionCode` in `app/build.gradle`

---

## 🎯 Quick Deploy Checklist

Before deploying, verify:

- [ ] ✅ All GitHub secrets are configured
- [ ] ✅ `versionCode` is incremented in `app/build.gradle`
- [ ] ✅ `versionName` is updated (e.g., "1.2.1")
- [ ] ✅ App is already on Play Console (or this is manual first upload)
- [ ] ✅ Release notes prepared (what's new)
- [ ] ✅ Tested locally first

---

## 🚀 Quick Start Commands

### For GitHub Actions Deploy:
```bash
# 1. Make sure you're on master
git checkout master

# 2. Pull latest
git pull origin master

# 3. Go to GitHub Actions
# https://github.com/shubham-yadav-git/WallApp/actions
# Click "Deploy to Google Play" → "Run workflow"
```

### For Local Build:
```bash
# Build signed AAB
./gradlew bundleRelease

# Output location
open app/build/outputs/bundle/release/
```

---

## 📖 Additional Resources

- **Service Account Setup:** `PLAY_STORE_SERVICE_ACCOUNT_SETUP.md`
- **Play Console Navigation:** `PLAY_CONSOLE_NAVIGATION.md`
- **Deployment Guide:** `DEPLOYMENT_GUIDE.md`
- **Quick Reference:** `QUICK_REFERENCE.md`

---

## 🎉 Ready to Deploy!

**Current Status:**
- ✅ Master branch has fixed workflows
- ✅ deploy-playstore.yml is ready
- ✅ Keystore auto-conversion implemented
- ✅ All validation errors resolved

**Next Step:** Configure GitHub Secrets, then trigger the workflow! 🚀

---

**Date:** March 20, 2026  
**Branch:** master  
**Commit:** d60217a  
**Status:** Ready for deployment

