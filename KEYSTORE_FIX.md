# Keystore Format Fix - "Tag number over 30 is not supported"

## Problem

The error `Tag number over 30 is not supported` occurs when your keystore is in **PKCS12 format** (modern format) but Android build tools expect **JKS format** (older format).

## What Was Fixed

### 1. Updated `app/build.gradle`
- Added explicit `storeType` configuration to signing configs
- Supports both JKS and PKCS12 formats
- Reads `KEYSTORE_TYPE` environment variable
- Added v1 and v2 signing for maximum compatibility

### 2. Updated GitHub Actions Workflows
Both `release-build.yml` and `deploy-playstore.yml` now:
- Automatically detect keystore format using `file` command
- Convert PKCS12 to JKS if needed using `keytool`
- Set `KEYSTORE_TYPE` environment variable for Gradle

## How the Fix Works

### Workflow Process:
```bash
1. Decode base64 keystore → keystore-original
2. Detect format with `file` command
3. If JKS → Use directly
4. If PKCS12 → Convert to JKS using keytool
5. Pass KEYSTORE_TYPE to Gradle build
```

### Conversion Command:
```bash
keytool -importkeystore \
  -srckeystore keystore-original \
  -srcstoretype PKCS12 \
  -srcstorepass "PASSWORD" \
  -destkeystore keystore.jks \
  -deststoretype JKS \
  -deststorepass "PASSWORD" \
  -noprompt
```

## For Local Builds

If you're building locally and have a PKCS12 keystore, you can either:

### Option 1: Convert to JKS
```bash
keytool -importkeystore \
  -srckeystore your-keystore.p12 \
  -srcstoretype PKCS12 \
  -srcstorepass YOUR_PASSWORD \
  -destkeystore your-keystore.jks \
  -deststoretype JKS \
  -deststorepass YOUR_PASSWORD \
  -noprompt
```

### Option 2: Use PKCS12 Directly
In `local.properties` or `gradle.properties`:
```properties
RELEASE_STORE_FILE=/path/to/your-keystore.p12
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_key_password
RELEASE_STORE_TYPE=PKCS12
```

## GitHub Secrets Required

For CI/CD to work, ensure these secrets are set in your GitHub repository:

1. `KEYSTORE_BASE64` - Base64 encoded keystore file
2. `KEYSTORE_PASSWORD` - Keystore password
3. `KEY_ALIAS` - Key alias
4. `KEY_PASSWORD` - Key password

## How to Generate Base64 Keystore

### From JKS:
```bash
base64 -i your-keystore.jks | pbcopy
# Then paste into GitHub Secrets
```

### From PKCS12:
```bash
# Option 1: Convert first (recommended)
keytool -importkeystore \
  -srckeystore your-keystore.p12 \
  -srcstoretype PKCS12 \
  -srcstorepass PASSWORD \
  -destkeystore your-keystore.jks \
  -deststoretype JKS \
  -deststorepass PASSWORD \
  -noprompt

base64 -i your-keystore.jks | pbcopy

# Option 2: Use PKCS12 directly (workflow will auto-convert)
base64 -i your-keystore.p12 | pbcopy
```

## Testing the Fix

### Test locally:
```bash
./gradlew clean bundleRelease
```

### Test in CI:
1. Push changes to GitHub
2. Create and push a tag: `git tag v1.0.0 && git push origin v1.0.0`
3. Check GitHub Actions for successful build

## Common Keystore Types

| Format | Extension | Description |
|--------|-----------|-------------|
| JKS | `.jks`, `.keystore` | Older Java KeyStore format |
| PKCS12 | `.p12`, `.pfx` | Modern standard format |

## Additional Notes

- **Java 9+** defaults to PKCS12 for new keystores
- **Android Studio** can generate either format
- The fix maintains **backward compatibility** with both formats
- CI/CD workflows automatically handle conversion now

## Troubleshooting

### If build still fails:

1. **Check keystore password**: Ensure secrets are set correctly
2. **Verify key alias**: Use `keytool -list -v -keystore your-keystore.jks` to see aliases
3. **Check keystore validity**: Ensure keystore is not corrupted
4. **Review build logs**: Look for specific keytool errors

### Verify keystore format:
```bash
# Check format
file your-keystore.jks
# Output: "Java KeyStore" → JKS format
# Output: "data" or "PKCS12" → PKCS12 format

# List keystore contents
keytool -list -v -keystore your-keystore.jks -storetype JKS
keytool -list -v -keystore your-keystore.p12 -storetype PKCS12
```

## References

- [Android App Signing Guide](https://developer.android.com/studio/publish/app-signing)
- [Oracle Keytool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
- [PKCS12 vs JKS](https://stackoverflow.com/questions/11536848/jks-vs-pkcs12)

