package com.sky.wallapp

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.GestureDetector
import android.os.Environment
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
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
import androidx.core.content.edit
import kotlin.math.abs

class ImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageBinding
    private var titlev: String? = null
    private var imageUrl: String? = null
    private var currentIndex: Int = -1
    private var swipeItems: List<Model> = emptyList()
    private var swipeSource: String = "detail"
    private var swipeCount: Int = 0
    private var boundaryHitCount: Int = 0
    private var sessionMetricsLogged = false
    private var swipeHintAnimator: AnimatorSet? = null
    private lateinit var gestureDetector: GestureDetector
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var reviewManager: ReviewManager

    companion object {
        const val WRITE_EXTERNAL_STORAGE_CODE = 1
        const val EXTRA_SWIPE_SESSION_ID = "swipe_session_id"
        const val EXTRA_SWIPE_INDEX = "swipe_index"

        private const val STATE_CURRENT_INDEX = "state_current_index"
        private const val STATE_TITLE = "state_title"
        private const val STATE_IMAGE_URL = "state_image_url"
        private const val STATE_SWIPE_COUNT = "state_swipe_count"
        private const val PREFS_SWIPE_HINT = "swipe_hint_prefs"
        private const val KEY_SWIPE_HINT_SHOWN = "swipe_hint_shown"
        private const val PREFS_REVIEW = "review_prompt_prefs"
        private const val KEY_POSITIVE_ACTION_COUNT = "positive_action_count"
        private const val KEY_LAST_REVIEW_REQUEST_TIME = "last_review_request_time"
        private const val REVIEW_ACTION_THRESHOLD = 3
        private const val REVIEW_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val SWIPE_DISTANCE_THRESHOLD = 120
        private const val SWIPE_VELOCITY_THRESHOLD = 120
        private const val ENABLE_CIRCULAR_SWIPE = true
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

        hydrateSwipeState(savedInstanceState)
        setupSwipeGesture()
        setupSwipeHint()
        analyticsTracker.logEvent("wallpaper_detail_open", mapOf("title" to titlev))
        renderCurrentWallpaper()

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

    private fun hydrateSwipeState(savedInstanceState: Bundle?) {
        val sessionId = intent.getStringExtra(EXTRA_SWIPE_SESSION_ID)
        val session = WallpaperSwipeSession.getSession(sessionId)
        if (session != null && session.items.isNotEmpty()) {
            swipeItems = session.items
            swipeSource = session.source
            currentIndex = intent.getIntExtra(EXTRA_SWIPE_INDEX, 0).coerceIn(0, swipeItems.lastIndex)
            applyModel(swipeItems[currentIndex])
        } else {
            titlev = intent.getStringExtra("title")
            imageUrl = intent.getStringExtra("image")
        }

        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt(STATE_CURRENT_INDEX, currentIndex)
            titlev = savedInstanceState.getString(STATE_TITLE, titlev)
            imageUrl = savedInstanceState.getString(STATE_IMAGE_URL, imageUrl)
            swipeCount = savedInstanceState.getInt(STATE_SWIPE_COUNT, swipeCount)
            if (swipeItems.isNotEmpty()) {
                currentIndex = currentIndex.coerceIn(0, swipeItems.lastIndex)
                applyModel(swipeItems[currentIndex])
            }
        }
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val distanceX = e2.x - start.x
                val distanceY = e2.y - start.y

                val isHorizontalSwipe = abs(distanceX) > abs(distanceY) &&
                    abs(distanceX) > SWIPE_DISTANCE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD

                if (!isHorizontalSwipe) return false

                return if (distanceX < 0) {
                    showNextWallpaper()
                } else {
                    showPreviousWallpaper()
                }
            }
        })

        binding.imageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun setupSwipeHint() {
        if (swipeItems.size <= 1) {
            binding.swipeHintCard.visibility = View.GONE
            return
        }

        val prefs = getSharedPreferences(PREFS_SWIPE_HINT, MODE_PRIVATE)
        val hasSeenHint = prefs.getBoolean(KEY_SWIPE_HINT_SHOWN, false)
        val shouldShowHint = !hasSeenHint
        binding.swipeHintCard.visibility = if (shouldShowHint) View.VISIBLE else View.GONE
        if (shouldShowHint) startSwipeHintPulse() else stopSwipeHintPulse()

        binding.swipeHintDismiss.setOnClickListener {
            markSwipeHintSeen()
        }
    }

    private fun markSwipeHintSeen() {
        getSharedPreferences(PREFS_SWIPE_HINT, MODE_PRIVATE).edit {
            putBoolean(KEY_SWIPE_HINT_SHOWN, true)
        }
        stopSwipeHintPulse()
        binding.swipeHintCard.visibility = View.GONE
    }

    private fun startSwipeHintPulse() {
        if (swipeHintAnimator?.isRunning == true) return

        val nudge = ObjectAnimator.ofFloat(
            binding.swipeHintCard,
            View.TRANSLATION_X,
            0f,
            -16f,
            16f,
            0f
        ).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        val fade = ObjectAnimator.ofFloat(binding.swipeHintCard, View.ALPHA, 1f, 0.88f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        swipeHintAnimator = AnimatorSet().apply {
            playTogether(nudge, fade)
            start()
        }
    }

    private fun stopSwipeHintPulse() {
        swipeHintAnimator?.cancel()
        swipeHintAnimator = null
        binding.swipeHintCard.translationX = 0f
        binding.swipeHintCard.alpha = 1f
    }

    private fun showNextWallpaper(): Boolean {
        if (swipeItems.isEmpty()) return false
        if (currentIndex >= swipeItems.lastIndex) {
            if (ENABLE_CIRCULAR_SWIPE) {
                currentIndex = 0
                swipeCount += 1
                markSwipeHintSeen()
                applyModel(swipeItems[currentIndex])
                renderCurrentWallpaper()
                analyticsTracker.logEvent(
                    "wallpaper_swipe_next",
                    mapOf(
                        "source" to swipeSource,
                        "index" to currentIndex.toString(),
                        "title" to titlev,
                        "wrapped" to "true"
                    )
                )
                return true
            }

            boundaryHitCount += 1
            analyticsTracker.logEvent("wallpaper_swipe_boundary", mapOf("edge" to "last", "source" to swipeSource))
            Toast.makeText(this, getString(R.string.no_next_wallpaper), Toast.LENGTH_SHORT).show()
            return true
        }

        currentIndex += 1
        swipeCount += 1
        markSwipeHintSeen()
        applyModel(swipeItems[currentIndex])
        renderCurrentWallpaper()
        analyticsTracker.logEvent(
            "wallpaper_swipe_next",
            mapOf("source" to swipeSource, "index" to currentIndex.toString(), "title" to titlev)
        )
        return true
    }

    private fun showPreviousWallpaper(): Boolean {
        if (swipeItems.isEmpty()) return false
        if (currentIndex <= 0) {
            if (ENABLE_CIRCULAR_SWIPE) {
                currentIndex = swipeItems.lastIndex
                swipeCount += 1
                markSwipeHintSeen()
                applyModel(swipeItems[currentIndex])
                renderCurrentWallpaper()
                analyticsTracker.logEvent(
                    "wallpaper_swipe_previous",
                    mapOf(
                        "source" to swipeSource,
                        "index" to currentIndex.toString(),
                        "title" to titlev,
                        "wrapped" to "true"
                    )
                )
                return true
            }

            boundaryHitCount += 1
            analyticsTracker.logEvent("wallpaper_swipe_boundary", mapOf("edge" to "first", "source" to swipeSource))
            Toast.makeText(this, getString(R.string.no_previous_wallpaper), Toast.LENGTH_SHORT).show()
            return true
        }

        currentIndex -= 1
        swipeCount += 1
        markSwipeHintSeen()
        applyModel(swipeItems[currentIndex])
        renderCurrentWallpaper()
        analyticsTracker.logEvent(
            "wallpaper_swipe_previous",
            mapOf("source" to swipeSource, "index" to currentIndex.toString(), "title" to titlev)
        )
        return true
    }

    private fun applyModel(model: Model) {
        titlev = model.title
        imageUrl = model.image
    }

    private fun renderCurrentWallpaper() {
        Glide.with(this)
            .load(imageUrl)
            .into(binding.imageView)

        val animFadeIn = AnimationUtils.loadAnimation(applicationContext, R.anim.fade_in)
        binding.imageView.startAnimation(animFadeIn)
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
                        prefs.edit { putLong(KEY_LAST_REVIEW_REQUEST_TIME, now) }
                        analyticsTracker.logEvent("in_app_review_requested")
                    }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_INDEX, currentIndex)
        outState.putString(STATE_TITLE, titlev)
        outState.putString(STATE_IMAGE_URL, imageUrl)
        outState.putInt(STATE_SWIPE_COUNT, swipeCount)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        if (sessionMetricsLogged) return

        analyticsTracker.logEvent(
            "wallpaper_swipe_session",
            mapOf(
                "source" to swipeSource,
                "swipes_total" to swipeCount.toString(),
                "boundary_hits" to boundaryHitCount.toString(),
                "has_swipe" to (swipeCount > 0).toString()
            )
        )
        sessionMetricsLogged = true
    }

    override fun onDestroy() {
        stopSwipeHintPulse()
        super.onDestroy()
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
