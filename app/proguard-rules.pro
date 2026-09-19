# 1. App Data Models
# We keep these because they are used for Firebase reflection
-keep class com.sky.wallapp.Model { *; }
-keep class com.sky.wallapp.Category { *; }

# 2. General Attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# 3. Firebase UI
# Removed the broad keep. FirebaseUI includes its own consumer ProGuard rules.
# We only need to keep what's strictly necessary if not already covered.
-keep class com.firebase.ui.database.FirebaseRecyclerAdapter { *; }
-keep class com.firebase.ui.database.FirebaseRecyclerOptions { *; }

# 4. Google Play Services
# REMOVED: -keep class com.google.android.gms.** { *; }
# Broad keeps on GMS are the main cause of low shrinking rates.
# The SDKs (Ads, Analytics) provide their own necessary keep rules.

# 5. Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# 6. View Binding
# Keep ViewBinding classes to avoid issues with reflection if used
-keep class **.databinding.** { *; }
