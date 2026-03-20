# ⚡ Quick Reference - WallApp Deployment

## 🚀 Common Commands

### Create a Release
```bash
# 1. Update version in app/build.gradle
versionCode 15
versionName "1.2.2"

# 2. Commit changes
git add app/build.gradle
git commit -m "Bump version to 1.2.1"
git push

# 3. Create and push tag
git tag v1.2.3
git push origin v1.2.3

# ✅ Done! Check GitHub Actions for automated build
```

### Test Build Locally
```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned)
./gradlew bundleRelease

# Run helper script
./deployment-helper.sh
```

### Manual Workflow Trigger
```
GitHub → Actions tab → Select workflow → Run workflow
```

---

## 🔑 GitHub Secrets Setup

**Location**: GitHub repo → Settings → Secrets and variables → Actions

### Required for Signed Releases
- `KEYSTORE_BASE64` - Base64 encoded keystore
- `KEYSTORE_PASSWORD` - Keystore password
- `KEY_ALIAS` - Key alias
- `KEY_PASSWORD` - Key password

### Required for Play Store Deploy
- `PLAY_STORE_SERVICE_ACCOUNT_JSON` - Base64 encoded service account JSON

### Quick Commands
```bash
# Encode keystore
base64 -i /path/to/keystore.jks | pbcopy

# Encode service account
base64 -i /path/to/service-account.json | pbcopy
```

---

## 📁 Important Files

| File | Purpose |
|------|---------|
| `.github/workflows/build-check.yml` | Auto-build on push |
| `.github/workflows/release-build.yml` | Create releases |
| `.github/workflows/deploy-playstore.yml` | Deploy to Play Store |
| `DEPLOYMENT_GUIDE.md` | Full documentation |
| `deployment-helper.sh` | Interactive assistant |
| `app/build.gradle` | Signing configuration |

---

## 🎯 Workflow Triggers

| Workflow | Trigger | Output |
|----------|---------|--------|
| **Build Check** | Push to main/develop | Debug APK (7 days) |
| **Release Build** | Git tag `v*.*.*` | Signed APK/AAB + GitHub Release |
| **Play Store Deploy** | Git tag `v*.*.*` | App on Play Store |

---

## ⚠️ Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails | Check Actions logs; likely same as local build error |
| Keystore error | Verify secrets are correct; re-encode if needed |
| Play Store upload fails | Increment versionCode; check service account permissions |
| Unsigned build | Add keystore secrets or build without signing |

---

## 📊 Release Tracks

| Track | Purpose | Command |
|-------|---------|---------|
| **Internal** | Team testing | Default for automated deploys |
| **Alpha** | Closed alpha testers | Select in manual trigger |
| **Beta** | Public beta testing | Select in manual trigger |
| **Production** | Live to all users | Select in manual trigger |

---

## 🔗 Quick Links

- [GitHub Repo](https://github.com/shubham-yadav-git/WallApp)
- [GitHub Actions](https://github.com/shubham-yadav-git/WallApp/actions)
- [GitHub Releases](https://github.com/shubham-yadav-git/WallApp/releases)
- [Play Console](https://play.google.com/console)
- [App on Play Store](https://play.google.com/store/apps/details?id=com.sky.wallapp)

---

## 💡 Pro Tips

1. **Always increment versionCode** before creating a tag
2. **Use semantic versioning** (v1.2.3 = major.minor.patch)
3. **Test on internal track first** before going to production
4. **Keep keystore backed up** - you can't recover it if lost!
5. **Monitor GitHub Actions** - you get email notifications on failures

---

## 📞 Need Help?

1. Read `DEPLOYMENT_GUIDE.md` for detailed instructions
2. Check `ARCHITECTURE.md` for visual overview
3. Run `./deployment-helper.sh` for interactive help
4. Check GitHub Actions logs for error details

---

**Version**: 1.0
**Updated**: March 20, 2026

