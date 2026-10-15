# CI/CD Workflows Reference

## Available Workflows

### 1. Build Check
**File**: `.github/workflows/build-check.yml`
**Trigger**: Push to main/master/develop
**Purpose**: Validate builds on every push
**Output**: Debug APK
**Secrets**: None required

### 2. Release Build
**File**: `.github/workflows/release-build.yml`
**Trigger**: Git tags (v*.*.*)
**Purpose**: Create signed release builds
**Output**: AAB + APK + GitHub Release
**Secrets Required**:
- KEYSTORE_BASE64
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD

### 3. Deploy to Play Store
**File**: `.github/workflows/deploy-playstore.yml`
**Trigger**: Git tags OR manual
**Purpose**: Upload to Google Play Console
**Output**: App published to Play Store
**Secrets Required**:
- KEYSTORE_BASE64
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD
- PLAY_STORE_SERVICE_ACCOUNT_JSON

## Workflow Triggers

### Automatic Triggers
```bash
# Trigger build-check.yml
git push origin main

# Trigger release-build.yml + deploy-playstore.yml
git tag v1.2.3
git push origin v1.2.3
```

### Manual Triggers
1. Go to: https://github.com/shubham-yadav-git/WallApp/actions
2. Select workflow
3. Click "Run workflow"
4. Choose options
5. Execute

## Keystore Handling
- Keystore is decoded from base64 during workflow
- Auto-converts JKS → BKS for Android < 9
- Signing happens in workflow, not committed to repo
- Original keystore: `upload-keystore.jks` (tracked in git)

## Play Store Tracks
- **internal**: Internal testing (fast, limited users)
- **alpha**: Alpha testing
- **beta**: Beta testing
- **production**: Public release

## Troubleshooting Workflows

### Build Failures
1. Check workflow logs
2. Verify secrets are set correctly
3. Ensure version code is incremented
4. Check Java/Gradle compatibility

### Signing Failures
1. Verify keystore secrets
2. Check KEY_ALIAS matches keystore
3. Ensure passwords are correct

### Play Store Upload Failures
1. Version code must be higher than current
2. Service account needs permissions
3. AAB must be signed correctly
4. Check Play Console API status

## Version Requirements
- **versionCode**: Must increment for each upload
- **versionName**: Semantic versioning (X.Y.Z)
- Never reuse version codes
- Play Store rejects duplicate version codes

## Artifact Locations
- **GitHub Actions Artifacts**: Available for 7 days
- **GitHub Releases**: Permanent storage
- **Play Store**: Published releases

## Best Practices
1. Always increment versionCode before tagging
2. Test locally before creating release tag
3. Use semantic versioning for versionName
4. Tag format: vX.Y.Z (e.g., v1.2.3)
5. Check workflow logs after deployment
6. Monitor Play Console for upload status

