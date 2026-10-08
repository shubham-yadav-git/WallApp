package com.sky.wallapp

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.sky.wallapp.databinding.ActivityImageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Wallpaper close-up (website PinCloseup): contained image over a blurred copy, swipe through the
 * list it was opened from, Share / Open full size / Download / Save ▾ / Set as wallpaper, and
 * "More like this". Opening a related wallpaper pushes a step that Back returns from; the trail in
 * the top bar jumps back several steps (or closes) at once, and pulling the image down closes.
 */
class ImageActivity : AppCompatActivity(), FeedListener {

    private data class Viewer(val list: List<Wallpaper>, val index: Int, val source: String) {
        val image: Wallpaper get() = list[index]
    }

    private lateinit var binding: ActivityImageBinding
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var reviewManager: ReviewManager
    private lateinit var relatedAdapter: FeedAdapter
    private lateinit var gestureDetector: GestureDetector

    private var viewer: Viewer? = null
    private val history = ArrayDeque<Viewer>()
    private var relatedFor: String? = null
    private var related: List<Wallpaper> = emptyList()
    private var largeLoadedKey: String? = null
    private var swipeCount = 0
    private var pendingDownload: Wallpaper? = null

    // Pull-down-to-close drag on the image
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private var velocityTracker: VelocityTracker? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dismissDragging = false
    private var pastDismissPoint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        analyticsTracker = AnalyticsTracker(FirebaseAnalytics.getInstance(this))
        reviewManager = ReviewManagerFactory.create(this)
        SavedSync.init(this)
        WallpaperRepository.start(this)

        binding.media.updateLayoutParams {
            height = (resources.displayMetrics.heightPixels * MEDIA_HEIGHT_FRACTION).toInt()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.backdrop.setRenderEffect(RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP))
        }
        relatedAdapter = FeedAdapter(this)
        binding.moreRecycler.layoutManager = FeedAdapter.newLayoutManager(this, insideScrollView = true)
        binding.moreRecycler.adapter = relatedAdapter
        binding.moreRecycler.itemAnimator = null

        applySystemBarInsets()
        setupGestures()
        setupActions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        val session = WallpaperSwipeSession.getSession(intent.getStringExtra(EXTRA_SWIPE_SESSION_ID))
        val restoredKey = savedInstanceState?.getString(STATE_KEY)
        if (session != null && session.items.isNotEmpty()) {
            val startIndex = restoredKey?.let { key -> session.items.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
                ?: intent.getIntExtra(EXTRA_SWIPE_INDEX, 0).coerceIn(0, session.items.lastIndex)
            show(Viewer(session.items, startIndex, session.source))
        } else {
            loadByKey(restoredKey ?: intent.getStringExtra(EXTRA_KEY))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { SavedRepository.state.collect { renderSaveState(); renderRelated() } }
                launch { WallpaperRepository.state.collect { if (related.isEmpty()) loadRelated() } }
            }
        }
    }

    /** Opened without a list (process restored or link): find the wallpaper first. */
    private fun loadByKey(key: String?) {
        if (key == null) {
            finish()
            return
        }
        binding.loading.isVisible = true
        lifecycleScope.launch {
            val image = WallpaperRepository.lookup(this@ImageActivity, key)
            binding.loading.isVisible = false
            if (image == null) {
                Toast.makeText(this@ImageActivity, R.string.wallpaper_not_found, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                show(Viewer(listOf(image), 0, "link"))
            }
        }
    }

    // ── Showing a wallpaper ─────────────────────────────────────────────────────

    private fun show(next: Viewer) {
        viewer = next
        val image = next.image
        SavedRepository.addRecent(image.key)

        binding.counter.isVisible = next.list.size > 1
        binding.counter.text = getString(R.string.counter_format, next.index + 1, next.list.size)
        binding.hintText.isVisible = next.list.size > 1
        binding.categoryChip.text = image.categoryName
        binding.titleText.text = image.displayTitle

        loadImages(image)
        renderSaveState()
        loadRelated()
        renderTrail()
        binding.detailScroll.scrollTo(0, 0)
    }

    /**
     * The grid image (already cached) shows sharp immediately; the large version fades in over
     * it when ready, then the preview is hidden — never left on top of the sharp image.
     */
    private fun loadImages(image: Wallpaper) {
        val tile = ImageUrls.tileUrl(image, ViewHolder.gridWidth(binding.root))
        largeLoadedKey = null
        binding.imageView.animate().cancel()
        binding.preview.animate().cancel()
        binding.imageView.alpha = 0f
        binding.preview.alpha = 1f
        binding.imageView.contentDescription = image.displayTitle

        ViewHolder.loadWithFallbacks(binding.preview, image, tile)
        Glide.with(this).load(tile).override(BACKDROP_SIZE).centerCrop().into(binding.backdrop)

        val key = image.key
        Glide.with(this)
            .load(ImageUrls.largeUrl(image))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean) =
                    false // keep the preview

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (viewer?.image?.key == key) {
                        largeLoadedKey = key
                        binding.imageView.animate().alpha(1f).setDuration(200).withEndAction {
                            if (largeLoadedKey == key) binding.preview.alpha = 0f
                        }.start()
                    }
                    return false
                }
            })
            .into(binding.imageView)
    }

    private fun navigate(direction: Int) {
        val current = viewer ?: return
        if (current.list.size < 2) return
        swipeCount += 1
        show(current.copy(index = (current.index + direction + current.list.size) % current.list.size))
    }

    private fun goBack() {
        val previous = history.removeLastOrNull()
        if (previous != null) show(previous) else finish()
    }

    // ── Trail (several "More like this" steps deep) ─────────────────────────────

    /** Home, the first wallpaper, "…" and the latest steps; the current one is on screen, not here. */
    private fun renderTrail() {
        val trail = binding.trail
        trail.removeAllViews()
        trail.isVisible = history.size >= TRAIL_MIN_STEPS
        if (!trail.isVisible) return

        trail.addView(trailItem(overlap = false).apply {
            setImageResource(R.drawable.ic_home_black_24dp)
            setColorFilter(ContextCompat.getColor(this@ImageActivity, R.color.white))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = getString(R.string.trail_home)
            setOnClickListener { closeFromTrail() }
        })
        val steps = if (history.size <= TRAIL_MAX_THUMBS) history.indices.toList()
        else listOf(0) + (history.size - TRAIL_MAX_THUMBS + 1 until history.size)
        steps.forEachIndexed { n, step ->
            if (n == 1 && step != 1) {
                trail.addView(TextView(this).apply {
                    setText(R.string.trail_more)
                    setTextColor(ContextCompat.getColor(this@ImageActivity, R.color.white))
                    setPadding(dp(6), 0, dp(2), 0)
                })
            }
            val image = history[step].image
            trail.addView(trailItem(overlap = n > 0 && !(n == 1 && step != 1)).apply {
                setBackgroundResource(R.drawable.trail_ring)
                setPadding(dp(2), dp(2), dp(2), dp(2))
                contentDescription = getString(R.string.trail_step, image.displayTitle)
                Glide.with(this@ImageActivity).load(ImageUrls.tileUrl(image, ImageUrls.GRID_WIDTH_SMALL))
                    .override(dp(TRAIL_THUMB_DP)).circleCrop().into(this)
                setOnClickListener { jumpBack(step) }
            })
        }
    }

    private fun trailItem(overlap: Boolean) = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(TRAIL_THUMB_DP), dp(TRAIL_THUMB_DP)).apply {
            marginStart = if (overlap) -dp(8) else dp(2)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    /** Back to the wallpaper at [step] in the history, dropping everything opened after it. */
    private fun jumpBack(step: Int) {
        val target = history.getOrNull(step) ?: return
        val skipped = history.size - step
        while (history.size > step) history.removeLast()
        performActionHaptic()
        analyticsTracker.logEvent("detail_trail", mapOf("action" to "step", "steps" to skipped.toString()))
        show(target)
    }

    private fun closeFromTrail() {
        performActionHaptic()
        analyticsTracker.logEvent("detail_trail", mapOf("action" to "home", "steps" to (history.size + 1).toString()))
        finish()
    }

    // ── More like this ──────────────────────────────────────────────────────────

    /** Up to 60 from the same category, in a stable pseudo-random order seeded by this image. */
    private fun loadRelated() {
        val image = viewer?.image ?: return
        val records = WallpaperRepository.state.value.records
        val list = records
            .filter { it.category == image.category && it.key != image.key }
            .sortedBy { PinUtils.hash(image.key + it.id) }
            .take(RELATED_MAX)
        if (relatedFor == image.key && list.size == related.size) return
        relatedFor = image.key
        related = list
        relatedAdapter.submitList(emptyList())
        binding.moreTitle.isVisible = list.isNotEmpty()
        binding.moreRecycler.isVisible = list.isNotEmpty()
        if (list.isEmpty()) return
        lifecycleScope.launch {
            RatioCache.measure(this@ImageActivity, list)
            if (relatedFor == image.key) renderRelated()
        }
    }

    private fun renderRelated() {
        if (related.isEmpty() || relatedFor != viewer?.image?.key) return
        val favorites = SavedRepository.state.value.favorites.mapTo(HashSet()) { it.key }
        relatedAdapter.submitList(related.mapIndexed { i, image -> FeedRow.Pin(image, i, image.key in favorites) })
    }

    override fun onPinClick(row: FeedRow.Pin) {
        viewer?.let { history.addLast(it) }
        analyticsTracker.logEvent(
            "select_content",
            mapOf("content_type" to "wallpaper", "item_id" to row.image.key, "item_name" to row.image.displayTitle,
                "category" to row.image.categoryName)
        )
        show(Viewer(related, row.index, "related"))
    }

    override fun onPinLongClick(row: FeedRow.Pin) {
        binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        toggleSave(row.image)
    }

    // ── Actions ─────────────────────────────────────────────────────────────────

    private fun setupActions() {
        binding.btnBack.setOnClickListener { goBack() }
        binding.btnShare.setOnClickListener { share() }
        binding.btnOpen.setOnClickListener {
            val url = viewer?.image?.let(ImageUrls::originalUrl) ?: return@setOnClickListener
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        }
        binding.btnSave.setOnClickListener { viewer?.image?.let(::requestDownload) }
        binding.btnFavorite.setOnClickListener { viewer?.image?.let(::toggleSave) }
        binding.btnSaveMore.setOnClickListener {
            val image = viewer?.image ?: return@setOnClickListener
            SaveToSheet.show(this, image, onToggleFavorite = { toggleSave(image) }, notify = { snackbar(it) })
        }
        binding.btnWall.setOnClickListener { viewer?.image?.let(::openWallDialog) }
        binding.categoryChip.setOnClickListener {
            val category = viewer?.image?.category ?: return@setOnClickListener
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECT_CATEGORY, category))
            finish()
        }
    }

    private fun renderSaveState() {
        val image = viewer?.image ?: return
        val saved = SavedRepository.isFavorite(image.key)
        val background = ContextCompat.getColor(this, if (saved) R.color.onSurface else R.color.primary)
        val foreground = ContextCompat.getColor(this, if (saved) R.color.surface else R.color.white)
        binding.saveSplit.setCardBackgroundColor(background)
        binding.btnFavorite.setText(if (saved) R.string.action_saved else R.string.action_save)
        binding.btnFavorite.setTextColor(foreground)
        binding.btnSaveMore.setColorFilter(foreground)
        binding.btnFavorite.contentDescription = getString(if (saved) R.string.favorited else R.string.favorite)
    }

    private fun toggleSave(image: Wallpaper, fromDoubleTap: Boolean = false) {
        val added = SavedRepository.toggleFavorite(image)
        performActionHaptic()
        if (fromDoubleTap) showDoubleTapHeart(added)
        if (added) {
            snackbar(getString(R.string.saved_to_favourites), getString(R.string.view)) {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_OPEN_SAVED, true))
                finish()
            }
        } else {
            snackbar(getString(R.string.removed_from_favourites), getString(R.string.undo)) {
                SavedRepository.toggleFavorite(image)
            }
        }
    }

    private fun share() {
        val image = viewer?.image ?: return
        analyticsTracker.logEvent("share", mapOf("content_type" to "wallpaper", "item_id" to image.key))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, image.displayTitle)
            putExtra(Intent.EXTRA_TEXT, PinUtils.webUrl(image))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun requestDownload(image: Wallpaper) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = image
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), WRITE_EXTERNAL_STORAGE_CODE)
        } else {
            download(image)
        }
    }

    /** Downloads the full-size file with the system DownloadManager into Pictures/WallApp. */
    private fun download(image: Wallpaper) {
        val url = ImageUrls.downloadUrl(image) ?: return
        try {
            val request = DownloadManager.Request(url.toUri())
                .setTitle(image.displayTitle)
                .setMimeType("image/jpeg")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "WallApp/wallapp-${image.id}.jpg")
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) request.allowScanningByMediaScanner()
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)

            analyticsTracker.logEvent("file_download", mapOf("item_id" to image.key))
            PopularityStats.recordDownload(this, image)
            snackbar(getString(R.string.downloading))
            onPositiveAction()
        } catch (e: Exception) {
            snackbar(getString(R.string.download_failed))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != WRITE_EXTERNAL_STORAGE_CODE) return
        val image = pendingDownload
        pendingDownload = null
        if (image != null && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            download(image)
        } else {
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    // ── Set as wallpaper ────────────────────────────────────────────────────────

    private fun openWallDialog(image: Wallpaper) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_wallpaper, null)
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).create()

        val fixedModeRadio = dialogView.findViewById<RadioButton>(R.id.radio_wallpaper_fixed)
        val scrollableModeRadio = dialogView.findViewById<RadioButton>(R.id.radio_wallpaper_scrollable)
        when (WallpaperApplier.getSavedDisplayMode(this)) {
            WallpaperApplier.DisplayMode.FIXED -> fixedModeRadio.isChecked = true
            WallpaperApplier.DisplayMode.SCROLLABLE -> scrollableModeRadio.isChecked = true
        }
        fun mode() = if (scrollableModeRadio.isChecked) WallpaperApplier.DisplayMode.SCROLLABLE else WallpaperApplier.DisplayMode.FIXED

        fun bind(buttonId: Int, flag: Int?, event: String, success: Int) {
            dialogView.findViewById<MaterialButton>(buttonId).setOnClickListener {
                applyWallpaper(image, flag, event, getString(success), mode())
                dialog.dismiss()
            }
        }
        bind(R.id.btn_wall_home, android.app.WallpaperManager.FLAG_SYSTEM, "set_wallpaper_home", R.string.wallpaper_home_success)
        bind(R.id.btn_wall_lock, android.app.WallpaperManager.FLAG_LOCK, "set_wallpaper_lock", R.string.wallpaper_lock_success)
        bind(R.id.btn_wall_both, null, "set_wallpaper_both", R.string.wallpaper_both_success)
        dialogView.findViewById<MaterialButton>(R.id.btn_wall_cancel).setOnClickListener {
            performActionHaptic()
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialogView.alpha = 0f
            dialogView.translationY = 24f
            dialogView.animate().alpha(1f).translationY(0f).setDuration(180)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        dialog.show()
    }

    /** Loads a screen-sized copy of the original off the main thread, then applies it. */
    private fun applyWallpaper(
        image: Wallpaper,
        targetFlag: Int?,
        analyticsEvent: String,
        successMessage: String,
        displayMode: WallpaperApplier.DisplayMode
    ) {
        val url = ImageUrls.originalUrl(image) ?: return
        Toast.makeText(this, getString(R.string.wallpaper_setting), Toast.LENGTH_SHORT).show()
        val metrics = resources.displayMetrics
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap: Bitmap = Glide.with(applicationContext).asBitmap().load(url)
                        .downsample(DownsampleStrategy.CENTER_OUTSIDE)
                        .submit((metrics.widthPixels * 1.5f).toInt(), metrics.heightPixels)
                        .get()
                    WallpaperApplier.saveDisplayMode(applicationContext, displayMode)
                    WallpaperApplier.applyBitmap(applicationContext, bitmap, targetFlag, displayMode)
                }
            }
            if (result.isSuccess) {
                performOutcomeHaptic(isSuccess = true)
                Toast.makeText(this@ImageActivity, successMessage, Toast.LENGTH_SHORT).show()
                analyticsTracker.logEvent(analyticsEvent, mapOf("item_id" to image.key))
                onPositiveAction()
            } else {
                performOutcomeHaptic(isSuccess = false)
                analyticsTracker.logEvent("set_wallpaper_failed", mapOf("error" to result.exceptionOrNull()?.message))
                Toast.makeText(this@ImageActivity, R.string.set_wallpaper_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Gestures ────────────────────────────────────────────────────────────────

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                viewer?.image?.let { toggleSave(it, fromDoubleTap = true) }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                val horizontal = abs(dx) > SWIPE_DISTANCE_THRESHOLD && abs(dx) > abs(dy) * 1.5f &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                if (!horizontal) return false
                navigate(if (dx < 0) 1 else -1)
                return true
            }
        })
        binding.media.setOnTouchListener { view, event ->
            if (trackDismissDrag(view, event)) return@setOnTouchListener true
            if (event.action == MotionEvent.ACTION_UP) view.performClick()
            gestureDetector.onTouchEvent(event)
        }
    }

    /**
     * Pull the image down (with the page scrolled to the top) to close the whole close-up, however
     * many "More like this" steps deep. Returns true while it owns the gesture.
     */
    private fun trackDismissDrag(view: View, event: MotionEvent): Boolean {
        // Raw coordinates: the image itself moves while dragging
        val raw = MotionEvent.obtain(event).apply { setLocation(event.rawX, event.rawY) }
        velocityTracker?.addMovement(raw)
        val dx = event.rawX - dragStartX
        val dy = event.rawY - dragStartY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                dismissDragging = false
                pastDismissPoint = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(raw) }
                // Stop the page from taking the pull; handed back below if it's a scroll up
                if (binding.detailScroll.scrollY == 0) view.parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dismissDragging && binding.detailScroll.scrollY == 0 && dy > touchSlop && dy > abs(dx) * 1.5f) {
                    dismissDragging = true
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    gestureDetector.onTouchEvent(cancel) // no double tap / fling from this gesture
                    cancel.recycle()
                } else if (!dismissDragging && dy < -touchSlop && abs(dy) > abs(dx)) {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                }
                if (dismissDragging) dragDismiss(dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dismissDragging) {
                dismissDragging = false
                val velocityY = velocityTracker?.run { computeCurrentVelocity(1000); yVelocity } ?: 0f
                val close = event.actionMasked == MotionEvent.ACTION_UP &&
                    (dy > dp(DISMISS_DISTANCE_DP) || (velocityY > DISMISS_VELOCITY && dy > dp(DISMISS_DISTANCE_DP) / 3))
                if (close) dismissByDrag() else settleDismissDrag()
                raw.recycle()
                return true
            }
        }
        raw.recycle()
        return dismissDragging
    }

    /** Image follows the finger and shrinks a little; everything around it fades. */
    private fun dragDismiss(dy: Float) {
        val distance = dy.coerceAtLeast(0f)
        val progress = (distance / dp(DISMISS_DISTANCE_DP * 2)).coerceAtMost(1f)
        binding.mediaCard.translationY = distance
        binding.mediaCard.scaleX = 1f - progress * 0.2f
        binding.mediaCard.scaleY = 1f - progress * 0.2f
        dismissSurroundings().forEach { it.alpha = 1f - progress }

        val past = distance > dp(DISMISS_DISTANCE_DP)
        if (past != pastDismissPoint) {
            pastDismissPoint = past
            if (past) binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun settleDismissDrag() {
        binding.mediaCard.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
        dismissSurroundings().forEach { it.animate().alpha(1f).setDuration(200).start() }
    }

    private fun dismissByDrag() {
        analyticsTracker.logEvent("detail_pull_close", mapOf("steps" to (history.size + 1).toString()))
        binding.mediaCard.animate().translationY(binding.root.height.toFloat()).alpha(0f).setDuration(180)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                finish()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, android.R.anim.fade_out)
                }
            }
            .start()
    }

    /** The top bar and everything on the page except the image. */
    private fun dismissSurroundings(): List<View> =
        (binding.mediaCard.parent as ViewGroup).children.filter { it !== binding.mediaCard }.toList() + binding.topBar

    private fun showDoubleTapHeart(added: Boolean) {
        val heart = binding.doubleTapHeart
        heart.setImageResource(if (added) R.drawable.ic_favorite_24 else R.drawable.ic_favorite_border_24)
        heart.animate().cancel()
        heart.scaleX = 0.6f
        heart.scaleY = 0.6f
        heart.alpha = 0f
        heart.visibility = View.VISIBLE
        heart.animate().scaleX(1.18f).scaleY(1.18f).alpha(0.95f).setDuration(170)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                heart.animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(180)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { heart.visibility = View.GONE }
                    .start()
            }
            .start()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun applySystemBarInsets() {
        val topPadding = binding.topBar.paddingTop
        val bottomPadding = binding.detailScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.updatePadding(top = topPadding + systemBars.top)
            binding.detailScroll.updatePadding(bottom = bottomPadding + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun snackbar(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        Snackbar.make(binding.root, message, if (action != null) 6000 else Snackbar.LENGTH_SHORT).apply {
            if (action != null && onAction != null) setAction(action) { onAction() }
            show()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun performActionHaptic() {
        binding.root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun performOutcomeHaptic(isSuccess: Boolean) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (isSuccess) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        binding.root.performHapticFeedback(constant)
    }

    /** Counts downloads / wallpaper sets and occasionally asks for a Play review. */
    private fun onPositiveAction() {
        val prefs = getSharedPreferences(PREFS_REVIEW, MODE_PRIVATE)
        val count = prefs.getInt(KEY_POSITIVE_ACTION_COUNT, 0) + 1
        prefs.edit { putInt(KEY_POSITIVE_ACTION_COUNT, count) }
        if (count < REVIEW_ACTION_THRESHOLD) return

        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_REVIEW_REQUEST_TIME, 0L) < REVIEW_COOLDOWN_MS) return
        reviewManager.requestReviewFlow().addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            reviewManager.launchReviewFlow(this, task.result).addOnCompleteListener {
                prefs.edit { putLong(KEY_LAST_REVIEW_REQUEST_TIME, now) }
                analyticsTracker.logEvent("in_app_review_requested")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewer?.image?.key?.let { outState.putString(STATE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing && swipeCount > 0) {
            analyticsTracker.logEvent(
                "wallpaper_swipe_session",
                mapOf("source" to viewer?.source, "swipes_total" to swipeCount.toString())
            )
        }
    }

    companion object {
        const val EXTRA_SWIPE_SESSION_ID = "swipe_session_id"
        const val EXTRA_SWIPE_INDEX = "swipe_index"
        const val EXTRA_KEY = "wallpaper_key"
        const val EXTRA_SELECT_CATEGORY = "select_category"
        const val EXTRA_OPEN_SAVED = "open_saved"

        private const val WRITE_EXTERNAL_STORAGE_CODE = 1
        private const val STATE_KEY = "state_key"
        private const val PREFS_REVIEW = "review_prompt_prefs"
        private const val KEY_POSITIVE_ACTION_COUNT = "positive_action_count"
        private const val KEY_LAST_REVIEW_REQUEST_TIME = "last_review_request_time"
        private const val REVIEW_ACTION_THRESHOLD = 3
        private const val REVIEW_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val SWIPE_DISTANCE_THRESHOLD = 120
        private const val SWIPE_VELOCITY_THRESHOLD = 120
        private const val MEDIA_HEIGHT_FRACTION = 0.72f
        private const val BACKDROP_SIZE = 64
        private const val RELATED_MAX = 60
        private const val TRAIL_MIN_STEPS = 2
        private const val TRAIL_MAX_THUMBS = 4
        private const val TRAIL_THUMB_DP = 32
        private const val DISMISS_DISTANCE_DP = 120
        private const val DISMISS_VELOCITY = 1500f

        /** Opens [list] at [index]; swiping moves through the same list. */
        fun intent(context: Context, list: List<Wallpaper>, index: Int, source: String): Intent =
            Intent(context, ImageActivity::class.java).apply {
                putExtra(EXTRA_SWIPE_SESSION_ID, WallpaperSwipeSession.createSession(list, source))
                putExtra(EXTRA_SWIPE_INDEX, index)
                list.getOrNull(index)?.let { putExtra(EXTRA_KEY, it.key) }
            }
    }
}
