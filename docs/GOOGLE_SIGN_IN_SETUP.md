# Fixing Google sign-in

## Why it fails

`app/google-services.json` has no SHA fingerprints registered for the Android app
`com.sky.wallapp`. Google sign-in only works for app builds whose signing certificate's
SHA is registered for that app, so right now it fails for every build. The "web client"
entry the code uses (`default_web_client_id`) is already there, so no code changes are needed.

Each build is signed with a different key, so register three sets of fingerprints.

## Step 1 — Get the SHA fingerprints

**a) Debug key** (builds run from Android Studio), from `~/.android/debug.keystore`:

```
SHA-1:   5D:36:58:38:0E:5E:5A:6F:A0:D4:32:74:51:C4:B1:60:0C:1D:5B:73
SHA-256: 4F:CA:65:5B:98:14:77:18:92:8A:E9:7D:14:8F:C3:3C:18:CF:45:6F:1C:60:67:E9:80:92:3C:76:BC:4D:D9:84
```

These are only valid for that one Mac's debug keystore. On another machine, get them with:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -storepass android | grep -E "SHA1|SHA256"
```

**b) Play App Signing key and upload key** (Play Store builds). This is the important one,
because Google re-signs the app before users download it.

1. Open [Play Console](https://play.google.com/console) and select **WallApp**.
2. Go to **Test and release → App integrity** (older consoles: **Setup → App integrity**)
   and open the **App signing** tab.
3. Copy the SHA-1 and SHA-256 under **App signing key certificate**.
4. Copy the SHA-1 and SHA-256 under **Upload key certificate** too.

## Step 2 — Add them in Firebase

1. Open the [Firebase console](https://console.firebase.google.com) and select project **wallapp-611ae**.
2. Click the ⚙️ gear icon, then **Project settings**, then the **General** tab.
3. Under **Your apps**, select the Android app **com.sky.wallapp**.
4. Click **Add fingerprint** and paste each of the 6 values (3 SHA-1 and 3 SHA-256), one at a time.

## Step 3 — Check that Google sign-in is turned on

1. In Firebase, go to **Build → Authentication → Sign-in method**.
2. Make sure **Google** is **Enabled** and a **support email** is set. Save.

## Step 4 — Download the new config file

1. Go back to **Project settings → General → com.sky.wallapp**.
2. Click **google-services.json** to download it.
3. Replace `app/google-services.json` with the downloaded file.
4. Check it: the new file should include an entry for `com.sky.wallapp` with
   `"client_type": 1` and your certificate hashes.

## Step 5 — Test the debug build

1. Run `./gradlew assembleDebug` and install it on a phone that has a Google account.
2. Go to **You → Continue with Google**.
3. If it fails, run `adb logcat -s SavedSync` and look for the line that starts with `Sign-in failed`.

## Step 6 — Ship it to the Play Store build

1. Bump `versionCode` in `app/build.gradle`.
2. Commit, then push to `master`. That push automatically uploads a release to the Play Store
   internal track, so only do it when you're ready.
3. Install the build from the internal track and test sign-in again. Fingerprint changes can
   take a few minutes to take effect.

## If it still fails

- **"Developer console is not set up correctly" / error 10:** a SHA is missing or wrong.
  The Play App Signing SHA is the one people most often forget.
- **Testing on an emulator:** use one with Google Play and a signed-in Google account.
- **Sign-in only fails for some Google accounts:** go to Google Cloud Console →
  **APIs & Services → OAuth consent screen** and check that the app's publishing status is
  **In production**, not "Testing".
