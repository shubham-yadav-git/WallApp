# Firebase Cloud Messaging

WallApp uses Firebase Cloud Messaging for optional wallpaper update notifications.
The user enables or disables the `wallpaper_updates` topic from **You → Wallpaper
updates**. On Android 13 and newer, notification permission is requested only
after the user opts in. The app does not log or upload the FCM registration token.

## Sending a message

Send to the `wallpaper_updates` topic using Firebase Console or a trusted
server-side Firebase Admin SDK integration. Do not embed server credentials or
FCM send credentials in the Android app.

For a general update, provide a notification title and body. For a wallpaper
notification that should open a specific image, include these data fields:

```json
{
  "wallpaper_key": "nature/-ExamplePushKey123456",
  "title": "New wallpaper",
  "body": "Tap to view this wallpaper."
}
```

`wallpaper_key` is the app's `{category}/{id}` key. Alternatively, data may
include both `category` and `wallpaper_id`. The app validates the key before
using it. Notification title/body can be sent as an FCM notification payload or
as data fields. Foreground messages are rendered by the app; background
notification payloads are displayed by Firebase Android SDK using the declared
notification icon, color, and channel.

## Verification

1. Install a debug build on a device with Google Play services.
2. Open **You → Wallpaper updates**, enable the switch, and grant notification
   permission when Android asks.
3. In Firebase Console, send a test notification to the `wallpaper_updates`
   topic, first while WallApp is open and then while it is backgrounded.
4. Tap each notification and confirm WallApp opens. For a payload containing a
   valid wallpaper key, confirm its detail screen opens; for a generic message,
   confirm the app opens normally.
5. Disable the switch, send another topic message, and confirm the device no
   longer receives it.
6. On Android 13+, repeat with notification permission denied and confirm the
   app keeps the topic switch off without repeatedly prompting.

FCM delivery and OS notification display require a real Firebase project/device
test; JVM tests and builds cannot validate those external services.
