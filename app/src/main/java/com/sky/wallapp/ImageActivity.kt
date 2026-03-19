package com.sky.wallapp

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sky.wallapp.databinding.ActivityImageBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageBinding
    private var titlev: String? = null
    private var imageUrl: String? = null
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var reviewManager: ReviewManager

    companion object {
        const val WRITE_EXTERNAL_STORAGE_CODE = 1
        private const val PREFS_REVIEW = "review_prompt_prefs"
        private const val KEY_POSITIVE_ACTION_COUNT = "positive_action_count"
        private const val KEY_LAST_REVIEW_REQUEST_TIME = "last_review_request_time"
        private const val REVIEW_ACTION_THRESHOLD = 3
        private const val REVIEW_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        analyticsTracker = AnalyticsTracker(FirebaseAnalytics.getInstance(this))
        reviewManager = ReviewManagerFactory.create(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        titlev = intent.getStringExtra("title")
        imageUrl = intent.getStringExtra("image")
        analyticsTracker.logEvent("wallpaper_detail_open", mapOf("title" to titlev))

        Glide.with(this)
            .load(imageUrl)
            .into(binding.imageView)

        val animFadeIn = AnimationUtils.loadAnimation(applicationContext, R.anim.fade_in)
        binding.imageView.startAnimation(animFadeIn)

        binding.btnSave.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), WRITE_EXTERNAL_STORAGE_CODE)
                } else {
                    saveImage()
                }
            } else {
                saveImage()
            }
        }

        binding.btnShare.setOnClickListener {
            shareImage()
        }

        binding.btnWall.setOnClickListener {
            openWallDialog()
        }
    }

    private fun openWallDialog() {
        val mDrawable = binding.imageView.drawable
        if (mDrawable !is BitmapDrawable) {
            Toast.makeText(this, "Image not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val mBitmap = mDrawable.bitmap
        val wallpaperManager = WallpaperManager.getInstance(applicationContext)

        val wallOptions = arrayOf("Home Screen", "Lock Screen", "Both")
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Wallpaper")
            .setItems(wallOptions) { _, i ->
                Toast.makeText(this@ImageActivity, "Setting wallpaper...", Toast.LENGTH_SHORT).show()
                try {
                    when (i) {
                        0 -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                wallpaperManager.setBitmap(mBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                            } else {
                                wallpaperManager.setBitmap(mBitmap)
                            }
                            Toast.makeText(this, "Home Screen set successfully", Toast.LENGTH_SHORT).show()
                            onPositiveAction("set_wallpaper_home")
                        }
                        1 -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                wallpaperManager.setBitmap(mBitmap, null, true, WallpaperManager.FLAG_LOCK)
                            } else {
                                wallpaperManager.setBitmap(mBitmap)
                            }
                            Toast.makeText(this, "Lock Screen set successfully", Toast.LENGTH_SHORT).show()
                            onPositiveAction("set_wallpaper_lock")
                        }
                        else -> {
                            wallpaperManager.setBitmap(mBitmap)
                            Toast.makeText(this, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
                            onPositiveAction("set_wallpaper_both")
                        }
                    }
                } catch (e: Exception) {
                    analyticsTracker.logEvent("set_wallpaper_failed", mapOf("error" to e.message))
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * Shares the wallpaper as an actual image (not just a URL) using FileProvider.
     * Falls back to sharing the URL if the bitmap is not yet loaded.
     */
    private fun shareImage() {
        val drawable = binding.imageView.drawable
        if (drawable !is BitmapDrawable) {
            // Fallback: share URL as plain text
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, imageUrl)
            }
            analyticsTracker.logEvent("share_wallpaper_link", mapOf("title" to titlev))
            startActivity(Intent.createChooser(fallback, "Share via"))
            return
        }

        try {
            val bitmap = drawable.bitmap

            // Write bitmap to app cache so FileProvider can serve it
            val cacheDir = File(cacheDir, "shared_images").also { it.mkdirs() }
            val shareFile = File(cacheDir, "wallpaper.jpg")
            FileOutputStream(shareFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                shareFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, titlev)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            analyticsTracker.logEvent("share_wallpaper_image", mapOf("title" to titlev))
            startActivity(Intent.createChooser(intent, "Share wallpaper"))
        } catch (e: Exception) {
            analyticsTracker.logEvent("share_wallpaper_failed", mapOf("error" to e.message))
            Toast.makeText(this, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImage() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())

        val mDrawable = binding.imageView.drawable
        if (mDrawable !is BitmapDrawable) {
            Toast.makeText(this, "Image not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = mDrawable.bitmap

        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(path, "WallApp")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val fileName = "${titlev?.replace(" ", "_")}_$timeStamp.jpg"
        val file = File(dir, fileName)

        try {
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.flush()
            }

            // Notify the media scanner so the image appears in the gallery immediately
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null
            )

            analyticsTracker.logEvent("save_wallpaper", mapOf("title" to titlev))
            onPositiveAction("save_wallpaper")
            Toast.makeText(this, "Saved to Gallery / WallApp", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            analyticsTracker.logEvent("save_wallpaper_failed", mapOf("error" to e.message))
            Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun onPositiveAction(action: String) {
        analyticsTracker.logEvent(action, mapOf("title" to titlev))

        val prefs = getSharedPreferences(PREFS_REVIEW, MODE_PRIVATE)
        val updatedCount = prefs.getInt(KEY_POSITIVE_ACTION_COUNT, 0) + 1
        prefs.edit().putInt(KEY_POSITIVE_ACTION_COUNT, updatedCount).apply()

        maybeRequestInAppReview(updatedCount)
    }

    private fun maybeRequestInAppReview(actionCount: Int) {
        if (actionCount < REVIEW_ACTION_THRESHOLD) return

        val prefs = getSharedPreferences(PREFS_REVIEW, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastRequestedAt = prefs.getLong(KEY_LAST_REVIEW_REQUEST_TIME, 0L)
        if (now - lastRequestedAt < REVIEW_COOLDOWN_MS) return

        reviewManager.requestReviewFlow()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val reviewInfo = task.result
                reviewManager.launchReviewFlow(this, reviewInfo)
                    .addOnCompleteListener {
                        prefs.edit().putLong(KEY_LAST_REVIEW_REQUEST_TIME, now).apply()
                        analyticsTracker.logEvent("in_app_review_requested")
                    }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_EXTERNAL_STORAGE_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImage()
            } else {
                Toast.makeText(this, "Permission required to save", Toast.LENGTH_LONG).show()
            }
        }
    }
}
