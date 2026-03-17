# Firebase and Data Models
-keep class com.sky.wallapp.Model { *; }
-keep class com.sky.wallapp.Category { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Firebase UI
-keep class com.firebase.ui.database.** { *; }

# Google Play Services (AD_ID and others)
-keep class com.google.android.gms.** { *; }

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
