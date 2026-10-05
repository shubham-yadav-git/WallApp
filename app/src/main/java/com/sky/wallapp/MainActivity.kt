package com.sky.wallapp

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.ads.MobileAds
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sky.wallapp.databinding.ActivityMainBinding

/**
 * Pinterest-style home: masonry feed with category pills (Home), search over the loaded
 * wallpaper pool with category tiles (Search), favorites (Saved), and app settings (You).
 */
class MainActivity : AppCompatActivity() {

    private enum class Tab(val menuId: Int) {
        HOME(R.id.nav_home),
        SEARCH(R.id.nav_search),
        SAVED(R.id.nav_saved),
        YOU(R.id.nav_you)
    }

    private enum class FavoritesSortMode {
        RECENT,
        ALPHABETICAL
    }

    private data class FeedContext(
        val items: List<Model>,
        val currentIndex: Int,
        val source: String
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var appUpdateManager: AppUpdateManager

    private lateinit var feedAds: FeedAds

    private var currentTab = Tab.HOME
    private val categoriesList = mutableListOf<Category>()
    private var categoryAdapter: CategoryAdapter? = null

    /** Selected home category; null means "All" (the trending mix). */
    private var selectedCategory: Category? = null
    private var suppressChipEvents = false

    /** First [TRENDING_ITEMS_PER_CATEGORY] items of every category, shuffled. Also the search pool. */
    private val trendingItems = mutableListOf<Model>()
    private var trendingLoaded = false
    private var trendingRequestId = 0

    private var searchQuery = ""
    private var favoritesSortMode = FavoritesSortMode.RECENT

    /** Home feed scroll position, restored when coming back from another tab. */
    private var pendingHomeScrollState: Parcelable? = null

    // Firebase state management to prevent leaks
    private var categoriesRef: DatabaseReference? = null
    private var categoriesListener: ValueEventListener? = null

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                analyticsTracker.logEvent("in_app_update_downloaded")
                showUpdateDownloadedSnackbar()
            }
            InstallStatus.DOWNLOADING -> analyticsTracker.logEvent("in_app_update_downloading")
            InstallStatus.INSTALLING -> analyticsTracker.logEvent("in_app_update_installing")
            InstallStatus.FAILED -> analyticsTracker.logEvent("in_app_update_failed")
            else -> {}
        }
    }

    private val updateFlowLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            analyticsTracker.logEvent("in_app_update_flow_accepted")
        } else {
            analyticsTracker.logEvent("in_app_update_flow_dismissed")
        }
    }

    private val imageDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val favoritesChanged = result.data?.getBooleanExtra(
            ImageActivity.EXTRA_FAVORITES_CHANGED,
            false
        ) == true
        if (!favoritesChanged) return@registerForActivityResult

        if (currentTab == Tab.SAVED) {
            loadFavorites()
        } else {
            binding.feedRecycler.adapter?.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseDatabase = FirebaseDatabase.getInstance()
        analyticsTracker = AnalyticsTracker(FirebaseAnalytics.getInstance(this))
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        favoritesSortMode = readFavoritesSortMode()
        analyticsTracker.logEvent("app_open")
        checkForAppUpdates()

        // Sponsored pins in the Home and Search feeds (native ads styled like wallpapers)
        MobileAds.initialize(this) {}
        feedAds = FeedAds(this, getString(R.string.native_ad_unit_id))
        feedAds.onAdsChanged = { (binding.feedRecycler.adapter as? PinAdapter)?.refreshAds() }
        feedAds.loadIfNeeded()

        setupInsets()
        setupLists()
        setupSearch()
        setupProfile()
        setupBottomNav()
        setupBackHandling()
        renderCategoryChips()
        loadCategories()

        val restoredTab = savedInstanceState?.getString(STATE_TAB)
            ?.let { name -> Tab.entries.firstOrNull { it.name == name } }
            ?: Tab.HOME
        if (restoredTab == Tab.HOME) {
            selectTab(Tab.HOME)
        } else {
            binding.bottomNav.selectedItemId = restoredTab.menuId
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, currentTab.name)
        super.onSaveInstanceState(outState)
    }

    // ── Setup ───────────────────────────────────────────────────────────────────

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            binding.statusBarScrim.updateLayoutParams { height = systemBars.top }
            insets
        }

        // Keep the last row of every scrolling list clear of the bottom navigation.
        binding.bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val bottomSpace = bottom - top + resources.getDimensionPixelSize(R.dimen.pin_gutter)
            listOf(binding.feedRecycler, binding.categoryGrid, binding.profileScroll).forEach {
                if (it.paddingBottom != bottomSpace) it.updatePadding(bottom = bottomSpace)
            }
        }
    }

    private fun setupLists() {
        binding.feedRecycler.layoutManager = PinAdapter.newLayoutManager(this)

        binding.categoryGrid.layoutManager = GridLayoutManager(this, PinAdapter.spanCount(this))
        categoryAdapter = CategoryAdapter(categoriesList, firebaseDatabase) { category ->
            analyticsTracker.logEvent(
                "search_category_tile",
                mapOf("category_name" to category.name, "category_path" to category.path)
            )
            categoryAdapter?.clearSelection()
            openCategoryOnHome(category)
        }
        binding.categoryGrid.adapter = categoryAdapter
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val tab = Tab.entries.firstOrNull { it.menuId == item.itemId } ?: return@setOnItemSelectedListener false
            selectTab(tab)
            true
        }
        binding.bottomNav.setOnItemReselectedListener {
            // Tapping the active tab again scrolls back to the top, like most feed apps.
            binding.appBar.setExpanded(true)
            binding.feedRecycler.smoothScrollToPosition(0)
            binding.categoryGrid.smoothScrollToPosition(0)
            binding.profileScroll.smoothScrollTo(0, 0)
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentTab == Tab.SEARCH && searchQuery.isNotEmpty() -> binding.searchInput.setText("")
                    currentTab != Tab.HOME -> binding.bottomNav.selectedItemId = R.id.nav_home
                    selectedCategory != null -> checkChipFor(null, notify = true)
                    else -> showExitDialog()
                }
            }
        })
    }

    // ── Tabs ────────────────────────────────────────────────────────────────────

    private fun selectTab(tab: Tab) {
        if (currentTab == Tab.HOME && tab != Tab.HOME) {
            pendingHomeScrollState = binding.feedRecycler.layoutManager?.onSaveInstanceState()
        }
        currentTab = tab
        binding.appBar.setExpanded(true, false)
        if (tab != Tab.SEARCH) hideKeyboard()

        binding.categoryScroll.isVisible = tab == Tab.HOME
        binding.titleRow.isVisible = tab == Tab.SAVED || tab == Tab.YOU
        binding.sortButton.isVisible = tab == Tab.SAVED
        binding.searchBar.isVisible = tab == Tab.SEARCH
        binding.searchSectionTitle.isVisible = tab == Tab.SEARCH && searchQuery.isEmpty()
        binding.screenTitle.setText(if (tab == Tab.YOU) R.string.tab_you else R.string.tab_saved)

        when (tab) {
            Tab.HOME -> renderHome()
            Tab.SEARCH -> renderSearch()
            Tab.SAVED -> loadFavorites()
            Tab.YOU -> renderProfile()
        }
        analyticsTracker.logEvent("tab_open", mapOf("tab" to tab.name.lowercase()))
    }

    // ── Home ────────────────────────────────────────────────────────────────────

    private fun renderHome() {
        val category = selectedCategory
        if (category == null) {
            if (trendingLoaded) {
                showStaticFeed(trendingItems.toList(), "trending", getString(R.string.empty_wallpapers), withAds = true)
            } else {
                showLoading()
            }
        } else {
            showCategoryFeed(category)
        }
    }

    private fun loadCategories() {
        categoriesRef = firebaseDatabase.getReference("categories")
        Log.d(TAG, "Loading categories from: $categoriesRef")

        categoriesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoriesList.clear()
                snapshot.children.forEach { postSnapshot ->
                    postSnapshot.getValue(Category::class.java)?.let { categoriesList.add(it) }
                }
                Log.d(TAG, "Categories loaded: ${categoriesList.size}")

                // Drop a selection whose category no longer exists.
                if (selectedCategory != null && categoriesList.none { it.path == selectedCategory?.path }) {
                    selectedCategory = null
                }
                renderCategoryChips()
                categoryAdapter?.notifyDataSetChanged()
                if (currentTab == Tab.SEARCH && searchQuery.isEmpty()) renderSearch()
                loadTrending()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load categories: ${error.message} (code ${error.code})")
                trendingLoaded = true
                if (currentTab == Tab.HOME && selectedCategory == null) {
                    showStaticFeed(emptyList(), "trending", getString(R.string.empty_wallpapers))
                }
            }
        }

        categoriesRef?.addValueEventListener(categoriesListener!!)
    }

    private fun loadTrending() {
        val requestId = ++trendingRequestId
        val paths = categoriesList.mapNotNull { it.path?.takeIf(String::isNotBlank) }
        if (paths.isEmpty()) {
            onTrendingLoaded(emptyList())
            return
        }

        val collected = mutableListOf<Model>()
        var remaining = paths.size

        // Count failures too, so one failing category can't leave the feed loading forever.
        fun onCategoryDone() {
            remaining -= 1
            if (remaining == 0 && requestId == trendingRequestId) {
                onTrendingLoaded(collected.shuffled())
            }
        }

        paths.forEach { path ->
            firebaseDatabase.getReference(path)
                .limitToFirst(TRENDING_ITEMS_PER_CATEGORY)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.children.mapNotNullTo(collected) { it.getValue(Model::class.java) }
                        onCategoryDone()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error loading $path: ${error.message}")
                        onCategoryDone()
                    }
                })
        }
    }

    private fun onTrendingLoaded(items: List<Model>) {
        trendingItems.clear()
        trendingItems.addAll(items)
        trendingLoaded = true
        analyticsTracker.logEvent("trending_loaded", mapOf("item_count" to items.size.toString()))

        when {
            currentTab == Tab.HOME && selectedCategory == null -> renderHome()
            currentTab == Tab.SEARCH && searchQuery.isNotEmpty() -> renderSearch()
        }
    }

    private fun renderCategoryChips() {
        val group = binding.categoryChips
        suppressChipEvents = true
        group.removeAllViews()
        group.addView(createCategoryChip(getString(R.string.category_all), null))
        categoriesList.forEach { group.addView(createCategoryChip(it.name.orEmpty(), it)) }
        checkChipFor(selectedCategory, notify = false)
        suppressChipEvents = false

        group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
            if (suppressChipEvents) return@setOnCheckedStateChangeListener
            val chip = checkedIds.firstOrNull()?.let { chipGroup.findViewById<Chip>(it) }
                ?: return@setOnCheckedStateChangeListener
            onCategoryChipSelected(chip.tag as? Category)
            scrollChipIntoView(chip)
        }
    }

    private fun createCategoryChip(label: String, category: Category?): Chip {
        return (layoutInflater.inflate(R.layout.item_category_chip, binding.categoryChips, false) as Chip).apply {
            id = View.generateViewId()
            text = label
            tag = category
        }
    }

    /** Checks the chip for [category] (null = "All"). With [notify] the feed reloads as if tapped. */
    private fun checkChipFor(category: Category?, notify: Boolean) {
        val group = binding.categoryChips
        val chip = (0 until group.childCount)
            .map { group.getChildAt(it) as Chip }
            .firstOrNull { (it.tag as? Category)?.path == category?.path }
            ?: return

        if (chip.isChecked) {
            if (notify) onCategoryChipSelected(category)
        } else {
            val previous = suppressChipEvents
            suppressChipEvents = !notify
            chip.isChecked = true
            suppressChipEvents = previous
        }
        scrollChipIntoView(chip)
    }

    private fun scrollChipIntoView(chip: Chip) {
        binding.categoryScroll.post {
            val target = chip.left - (binding.categoryScroll.width - chip.width) / 2
            binding.categoryScroll.smoothScrollTo(target.coerceAtLeast(0), 0)
        }
    }

    private fun onCategoryChipSelected(category: Category?) {
        selectedCategory = category
        pendingHomeScrollState = null
        binding.appBar.setExpanded(true)
        if (currentTab == Tab.HOME) renderHome()

        if (category == null) {
            analyticsTracker.logEvent("open_trending")
        } else {
            analyticsTracker.logEvent(
                "category_select",
                mapOf("category_name" to category.name, "category_path" to category.path)
            )
        }
    }

    private fun openCategoryOnHome(category: Category) {
        selectedCategory = category
        pendingHomeScrollState = null
        checkChipFor(category, notify = false)
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    /** Loads a category once (served from the offline cache when available) into the masonry feed. */
    private fun showCategoryFeed(category: Category) {
        val path = category.path?.takeIf { it.isNotBlank() } ?: run {
            showFeed(0, getString(R.string.empty_wallpapers))
            return
        }
        showLoading()

        firebaseDatabase.getReference(path).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isShowingCategory(path)) return
                val items = snapshot.children.mapNotNull { it.getValue(Model::class.java) }
                showStaticFeed(items, "category", getString(R.string.empty_wallpapers), withAds = true)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error loading category $path: ${error.message}")
                if (isShowingCategory(path)) showFeed(0, getString(R.string.empty_wallpapers))
            }
        })
    }

    /** Guards async category loads against the user having moved on. */
    private fun isShowingCategory(path: String): Boolean =
        !isDestroyed && currentTab == Tab.HOME && selectedCategory?.path == path

    private fun restoreHomeScrollIfPending() {
        if (currentTab != Tab.HOME) return
        pendingHomeScrollState?.let { binding.feedRecycler.layoutManager?.onRestoreInstanceState(it) }
        pendingHomeScrollState = null
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            binding.searchClear.isVisible = !text.isNullOrEmpty()
            if (query == searchQuery) return@doAfterTextChanged
            searchQuery = query
            if (currentTab == Tab.SEARCH) renderSearch()
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            hideKeyboard()
            analyticsTracker.logEvent("search_submit", mapOf("query" to searchQuery))
            true
        }

        binding.searchClear.setOnClickListener { binding.searchInput.setText("") }
    }

    private fun renderSearch() {
        binding.searchSectionTitle.isVisible = searchQuery.isEmpty()

        if (searchQuery.isEmpty()) {
            if (categoriesList.isEmpty()) showLoading() else showOnly(binding.categoryGrid)
            return
        }

        if (!trendingLoaded) {
            showLoading()
            return
        }

        val tokens = searchQuery.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val results = searchPool().filter { model ->
            val haystack = "${model.title.orEmpty()} ${model.search.orEmpty()}".lowercase()
            tokens.all { haystack.contains(it) }
        }
        showStaticFeed(results, "search", getString(R.string.empty_search_results), withAds = true)
        analyticsTracker.logEvent(
            "search_results",
            mapOf("query" to searchQuery, "result_count" to results.size.toString())
        )
    }

    /** Everything already on the device: the trending mix plus saved favorites. */
    private fun searchPool(): List<Model> =
        (trendingItems + FavoritesStore.getFavorites(this)).distinctBy { it.image }

    private fun hideKeyboard() {
        binding.searchInput.clearFocus()
        WindowCompat.getInsetsController(window, binding.searchInput).hide(WindowInsetsCompat.Type.ime())
    }

    // ── Saved ───────────────────────────────────────────────────────────────────

    private fun loadFavorites() {
        val favorites = sortFavorites(FavoritesStore.getFavorites(this))
        showStaticFeed(favorites, "favorites", getString(R.string.empty_favorites))
        analyticsTracker.logEvent("open_favorites", mapOf("item_count" to favorites.size.toString()))
    }

    private fun sortFavorites(items: List<Model>): List<Model> {
        return when (favoritesSortMode) {
            FavoritesSortMode.RECENT -> items
            FavoritesSortMode.ALPHABETICAL -> items.sortedBy { it.title?.lowercase() ?: "" }
        }
    }

    private fun showFavoritesSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_recent),
            getString(R.string.sort_alphabetical)
        )
        val checked = if (favoritesSortMode == FavoritesSortMode.RECENT) 0 else 1

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_favorites_title)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                favoritesSortMode = if (which == 0) FavoritesSortMode.RECENT else FavoritesSortMode.ALPHABETICAL
                saveFavoritesSortMode(favoritesSortMode)
                analyticsTracker.logEvent(
                    "favorites_sort_changed",
                    mapOf("mode" to favoritesSortMode.name.lowercase())
                )
                loadFavorites()
                dialog.dismiss()
            }
            .show()
    }

    private fun readFavoritesSortMode(): FavoritesSortMode {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_FAVORITES_SORT_MODE, FavoritesSortMode.RECENT.name)
        return runCatching { FavoritesSortMode.valueOf(raw ?: FavoritesSortMode.RECENT.name) }
            .getOrDefault(FavoritesSortMode.RECENT)
    }

    private fun saveFavoritesSortMode(mode: FavoritesSortMode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putString(KEY_FAVORITES_SORT_MODE, mode.name)
        }
    }

    // ── You (profile + settings) ────────────────────────────────────────────────

    private fun setupProfile() {
        binding.sortButton.setOnClickListener { showFavoritesSortDialog() }

        val profile = binding.profile
        profile.rowAutoWallpaper.setOnClickListener { handleAutoWallpaperAction() }
        profile.rowRunNow.setOnClickListener { runAutoWallpaperNow() }
        profile.rowCheckUpdate.setOnClickListener {
            analyticsTracker.logEvent("check_updates_manual")
            checkForAppUpdates(force = true)
        }
        profile.rowRate.setOnClickListener {
            analyticsTracker.logEvent("nav_rate_app")
            startActivity(Intent(Intent.ACTION_VIEW, playStoreUrl().toUri()))
        }
        profile.rowShare.setOnClickListener {
            analyticsTracker.logEvent("nav_share_app")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, playStoreUrl())
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        }
        profile.rowFeedback.setOnClickListener {
            analyticsTracker.logEvent("nav_contact")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
            }
            startActivity(Intent.createChooser(intent, getString(R.string.choose_email_client)))
        }
        profile.rowPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        profile.versionText.text = getString(R.string.version_format, appVersionName())
    }

    private fun renderProfile() {
        showOnly(binding.profileScroll)

        val profile = binding.profile
        val autoEnabled = AutoWallpaperManager.isEnabled(this)
        profile.switchAutoWallpaper.isChecked = autoEnabled
        profile.rowRunNow.isVisible = autoEnabled

        val savedCount = FavoritesStore.getFavorites(this).size
        profile.profileStats.text = resources.getQuantityString(R.plurals.saved_count, savedCount, savedCount)
    }

    private fun handleAutoWallpaperAction() {
        if (AutoWallpaperManager.isEnabled(this)) {
            AutoWallpaperManager.disable(this)
            analyticsTracker.logEvent("auto_wallpaper_disabled")
            renderProfile()
            Toast.makeText(this, getString(R.string.auto_wallpaper_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        if (!AutoWallpaperManager.hasEligibleFavorites(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auto_wallpaper_title)
                .setMessage(R.string.auto_wallpaper_requires_favorites)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_wallpaper_title)
            .setMessage(R.string.auto_wallpaper_message)
            .setPositiveButton(R.string.auto_wallpaper_enable) { _, _ ->
                AutoWallpaperManager.enable(this)
                analyticsTracker.logEvent("auto_wallpaper_enabled")
                renderProfile()
                Toast.makeText(this, getString(R.string.auto_wallpaper_enabled), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runAutoWallpaperNow() {
        if (!AutoWallpaperManager.hasEligibleFavorites(this)) {
            Toast.makeText(this, getString(R.string.auto_wallpaper_requires_favorites), Toast.LENGTH_SHORT).show()
            return
        }

        AutoWallpaperManager.runNow(this)
        analyticsTracker.logEvent("auto_wallpaper_run_now_requested")
        Toast.makeText(this, getString(R.string.auto_wallpaper_run_now_queued), Toast.LENGTH_SHORT).show()
    }

    private fun playStoreUrl() = "https://play.google.com/store/apps/details?id=$packageName"

    @Suppress("DEPRECATION")
    private fun appVersionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

    // ── Feed rendering ──────────────────────────────────────────────────────────

    private fun showStaticFeed(items: List<Model>, source: String, emptyMessage: String, withAds: Boolean = false) {
        binding.feedRecycler.adapter = PinAdapter(
            items,
            onPinClick = { position, model ->
                analyticsTracker.logEvent("wallpaper_open", mapOf("source" to source, "title" to model.title))
                openImageDetail(model, FeedContext(items = items, currentIndex = position, source = source))
            },
            onFavoriteToggled = { model, nowFavorite -> onPinFavoriteToggled(model, nowFavorite, source) },
            feedAds = feedAds.takeIf { withAds }
        )
        showFeed(items.size, emptyMessage)
        restoreHomeScrollIfPending()
    }

    private fun onPinFavoriteToggled(model: Model, nowFavorite: Boolean, source: String) {
        analyticsTracker.logEvent(
            if (nowFavorite) "favorite_added" else "favorite_removed",
            mapOf("title" to model.title, "source" to source)
        )
        if (currentTab == Tab.SAVED && !nowFavorite) loadFavorites()
    }

    private fun openImageDetail(model: Model, feedContext: FeedContext?) {
        val intent = Intent(this, ImageActivity::class.java).apply {
            putExtra("image", model.image)
            putExtra("title", model.title)
            if (!model.cloudinaryUrl.isNullOrBlank()) {
                putExtra(ImageActivity.EXTRA_CLOUDINARY_URL, model.cloudinaryUrl)
            }
        }

        if (feedContext != null) {
            val sessionId = WallpaperSwipeSession.createSession(feedContext.items, feedContext.source)
            intent.putExtra(ImageActivity.EXTRA_SWIPE_SESSION_ID, sessionId)
            intent.putExtra(ImageActivity.EXTRA_SWIPE_INDEX, feedContext.currentIndex)
        }

        imageDetailLauncher.launch(intent)
    }

    private fun showLoading() {
        showOnly(binding.loadingProgress)
    }

    private fun showFeed(itemCount: Int, emptyMessage: String) {
        if (itemCount > 0) {
            showOnly(binding.feedRecycler)
        } else {
            binding.emptyStateText.text = emptyMessage
            showOnly(binding.emptyStateText)
        }
    }

    /** Shows exactly one of the content-area views. */
    private fun showOnly(view: View) {
        listOf(
            binding.feedRecycler,
            binding.categoryGrid,
            binding.profileScroll,
            binding.loadingProgress,
            binding.emptyStateText
        ).forEach { it.isVisible = it == view }
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    // ── In-app updates ──────────────────────────────────────────────────────────

    private fun checkForAppUpdates(force: Boolean = false) {
        if (!force && !shouldCheckForUpdatesNow()) return

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { updateInfo ->
                markUpdateCheckDone()

                // If update is already downloaded, complete it
                if (updateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    showUpdateDownloadedSnackbar()
                    return@addOnSuccessListener
                }

                if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    val updatePriority = updateInfo.updatePriority()

                    // Immediate update for high priority (4-5), flexible otherwise (0-3)
                    val updateType = if (updatePriority >= 4) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE

                    if (updateInfo.isUpdateTypeAllowed(updateType)) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                updateInfo,
                                updateFlowLauncher,
                                AppUpdateOptions.newBuilder(updateType).build()
                            )
                            analyticsTracker.logEvent(
                                "in_app_update_prompt_shown",
                                mapOf(
                                    "update_type" to if (updateType == AppUpdateType.IMMEDIATE) "immediate" else "flexible",
                                    "priority" to updatePriority.toString()
                                )
                            )
                        } catch (e: IntentSender.SendIntentException) {
                            analyticsTracker.logEvent(
                                "in_app_update_start_failed",
                                mapOf("error" to e.message.orEmpty())
                            )
                            // Ignore; no safe fallback required for optional update prompt.
                        }
                    }
                } else if (force) {
                    // Only show "no update" message if user manually checked
                    Toast.makeText(this, getString(R.string.no_updates_available), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                analyticsTracker.logEvent(
                    "in_app_update_check_failed",
                    mapOf("error" to exception.message.orEmpty())
                )
                if (force) {
                    Toast.makeText(this, getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showUpdateDownloadedSnackbar() {
        Snackbar.make(binding.root, R.string.update_downloaded, Snackbar.LENGTH_INDEFINITE).apply {
            anchorView = binding.bottomBar
            setAction(R.string.restart) {
                analyticsTracker.logEvent("in_app_update_restart_clicked")
                appUpdateManager.completeUpdate()
            }
            setActionTextColor(getColor(R.color.primary))
            show()
        }
    }

    private fun shouldCheckForUpdatesNow(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastCheckedAt = prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
        return System.currentTimeMillis() - lastCheckedAt >= UPDATE_CHECK_COOLDOWN_MS
    }

    private fun markUpdateCheckDone() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putLong(KEY_LAST_UPDATE_CHECK_AT, System.currentTimeMillis())
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        feedAds.loadIfNeeded()

        // Check for stalled or downloaded updates
        appUpdateManager.appUpdateInfo.addOnSuccessListener { updateInfo ->
            when (updateInfo.installStatus()) {
                InstallStatus.DOWNLOADED -> showUpdateDownloadedSnackbar()
                InstallStatus.INSTALLING -> {
                    // Update is being installed in the background
                }
                else -> {
                    // If an immediate update was stalled, resume it
                    if (updateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                updateInfo,
                                updateFlowLauncher,
                                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                            )
                        } catch (_: IntentSender.SendIntentException) {
                            // Failed to resume update
                        }
                    }
                }
            }
        }

        when (currentTab) {
            Tab.SAVED -> loadFavorites()
            Tab.YOU -> renderProfile()
            else -> {}
        }
    }

    override fun onDestroy() {
        // Prevent Firebase memory leaks by removing listeners
        categoriesListener?.let { categoriesRef?.removeEventListener(it) }
        feedAds.destroy()
        appUpdateManager.unregisterListener(installStateUpdatedListener)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "wallapp_prefs"
        private const val KEY_FAVORITES_SORT_MODE = "favorites_sort_mode"
        private const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        private const val UPDATE_CHECK_COOLDOWN_MS = 2L * 24 * 60 * 60 * 1000 // 2 days
        private const val TRENDING_ITEMS_PER_CATEGORY = 20
        private const val FEEDBACK_EMAIL = "shubhamskyjnp@gmail.com"
        private const val STATE_TAB = "state_tab"
    }
}
