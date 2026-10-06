package com.sky.wallapp

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
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
import com.sky.wallapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Home (masonry feed with search and category chips), Saved (favourites, collections, recently
 * viewed) and You (account + settings). Behaviour mirrors the website's PublicHome / SavedView.
 */
class MainActivity : AppCompatActivity(), FeedListener {

    private enum class Tab(val menuId: Int) {
        HOME(R.id.nav_home),
        SAVED(R.id.nav_saved),
        YOU(R.id.nav_you)
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var feedAds: FeedAds
    private lateinit var adapter: FeedAdapter

    private var currentTab = Tab.HOME
    private var savedTab = SavedTab.FAVORITES
    private var activeCategory = ALL
    private var rawQuery = ""
    private var query = "" // debounced, trimmed, lowercase
    private val handler = Handler(Looper.getMainLooper())
    private val applyQuery = Runnable {
        val next = rawQuery.trim().lowercase()
        if (next != query) {
            query = next
            render(scrollToTop = true)
        }
    }

    private var repo = WallpaperRepository.State()
    private var saved = SavedStore.emptyState()
    private var account = SavedSync.Account()
    private var signingIn = false

    // Home feed list cache (filtering + sorting 10k+ records is only redone when inputs change)
    private var feedInputs: Triple<List<Wallpaper>, String, String>? = null
    private var feedList: List<Wallpaper> = emptyList()

    // Paged pins of the current screen: revealed 30 at a time once their shapes are known
    private var pinListKey = ""
    private var pinList: List<Wallpaper> = emptyList()
    private var revealed = 0
    private var measuring = false
    private var generation = 0

    private var renderedChips: List<Pair<String, String>> = emptyList()
    private var suppressChipEvents = false
    private val tabScroll = mutableMapOf<Tab, Parcelable?>()
    private var pendingPin: String? = null
    private var migrated = false

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
        analyticsTracker.logEvent(
            if (result.resultCode == RESULT_OK) "in_app_update_flow_accepted" else "in_app_update_flow_dismissed"
        )
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val data = result.data ?: return@registerForActivityResult
        data.getStringExtra(ImageActivity.EXTRA_SELECT_CATEGORY)?.let { path ->
            binding.searchInput.setText("")
            goHome(path)
        }
        if (data.getBooleanExtra(ImageActivity.EXTRA_OPEN_SAVED, false)) goSaved(SavedTab.FAVORITES)
    }

    private val collectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val name = result.data?.getStringExtra(CollectionActivity.EXTRA_DELETED_NAME) ?: return@registerForActivityResult
        val undo = CollectionActivity.pendingUndo
        CollectionActivity.pendingUndo = null
        snackbar(getString(R.string.deleted_collection, name), getString(R.string.undo)) { undo?.invoke() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyticsTracker = AnalyticsTracker(FirebaseAnalytics.getInstance(this))
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        analyticsTracker.logEvent("app_open")
        checkForAppUpdates()

        // Sponsored pins in the Home feed (native ads styled like wallpapers)
        MobileAds.initialize(this) {}
        feedAds = FeedAds(this, getString(R.string.native_ad_unit_id))
        feedAds.onAdsChanged = { if (currentTab == Tab.HOME) render() }
        feedAds.loadIfNeeded()

        SavedSync.init(this)
        WallpaperRepository.start(this)

        setupInsets()
        setupFeed()
        setupHeader()
        setupProfile()
        setupBottomNav()
        setupBackHandling()

        savedInstanceState?.let { state ->
            activeCategory = state.getString(STATE_CATEGORY, ALL)
            savedTab = SavedTab.entries.firstOrNull { it.name == state.getString(STATE_SAVED_TAB) } ?: SavedTab.FAVORITES
        }
        handleDeepLink(intent)

        val restoredTab = savedInstanceState?.getString(STATE_TAB)
            ?.let { name -> Tab.entries.firstOrNull { it.name == name } }
            ?: currentTab
        selectTab(restoredTab)
        if (restoredTab != Tab.HOME) binding.bottomNav.selectedItemId = restoredTab.menuId

        observe()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, currentTab.name)
        outState.putString(STATE_CATEGORY, activeCategory)
        outState.putString(STATE_SAVED_TAB, savedTab.name)
        super.onSaveInstanceState(outState)
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { WallpaperRepository.state.collect { repo = it; onRepoChanged() } }
                launch { SavedRepository.state.collect { saved = it; onSavedChanged() } }
                launch { SavedSync.account.collect { account = it; renderAccount(); render() } }
            }
        }
    }

    private fun onRepoChanged() {
        if (repo.allLoaded && !migrated) {
            migrated = true
            SavedRepository.migrateLegacyFavorites(repo.records)
        }
        // Unknown category (e.g. from a link): fall back to All once categories are known
        if (repo.categories.isNotEmpty() && activeCategory != ALL && activeCategory != POPULAR &&
            repo.categories.none { it.path == activeCategory }
        ) {
            activeCategory = ALL
        }
        renderChips()
        openPendingPin()
        render()
    }

    private fun onSavedChanged() {
        val count = saved.favorites.size
        binding.bottomNav.getOrCreateBadge(R.id.nav_saved).apply {
            isVisible = count > 0
            number = count
            maxNumber = 99
            backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.primary)
            badgeTextColor = ContextCompat.getColor(this@MainActivity, R.color.white)
        }
        render()
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
            listOf(binding.feedRecycler, binding.profileScroll).forEach {
                if (it.paddingBottom != bottomSpace) it.updatePadding(bottom = bottomSpace)
            }
        }
    }

    private fun setupFeed() {
        adapter = FeedAdapter(this)
        binding.feedRecycler.layoutManager = FeedAdapter.newLayoutManager(this)
        binding.feedRecycler.adapter = adapter
        binding.feedRecycler.itemAnimator = null // tiles appear in place; no reflow animation
        binding.feedRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = checkLoadMore()
        })
    }

    private fun setupHeader() {
        binding.brand.setOnClickListener {
            binding.searchInput.setText("")
            selectCategory(ALL)
        }

        binding.searchInput.doAfterTextChanged { text ->
            val next = text?.toString().orEmpty()
            // Like Pinterest, starting a search looks across every category
            if (rawQuery.isEmpty() && next.isNotEmpty() && activeCategory != ALL) selectCategory(ALL)
            rawQuery = next
            binding.searchClear.isVisible = next.isNotEmpty()
            handler.removeCallbacks(applyQuery)
            handler.postDelayed(applyQuery, SEARCH_DEBOUNCE_MS)
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            hideKeyboard()
            if (rawQuery.isNotBlank()) analyticsTracker.logEvent("search", mapOf("search_term" to rawQuery.trim()))
            true
        }
        binding.searchClear.setOnClickListener { binding.searchInput.setText("") }

        binding.headerSignIn.setOnClickListener { signIn() }
        binding.headerAvatar.setOnClickListener { binding.bottomNav.selectedItemId = R.id.nav_you }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val tab = Tab.entries.firstOrNull { it.menuId == item.itemId } ?: return@setOnItemSelectedListener false
            selectTab(tab)
            true
        }
        binding.bottomNav.setOnItemReselectedListener {
            binding.appBar.setExpanded(true)
            binding.feedRecycler.smoothScrollToPosition(0)
            binding.profileScroll.smoothScrollTo(0, 0)
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentTab != Tab.HOME -> binding.bottomNav.selectedItemId = R.id.nav_home
                    rawQuery.isNotEmpty() -> binding.searchInput.setText("")
                    activeCategory != ALL -> selectCategory(ALL)
                    else -> showExitDialog()
                }
            }
        })
    }

    // ── Navigation ──────────────────────────────────────────────────────────────

    private fun selectTab(tab: Tab) {
        if (tab != currentTab) tabScroll[currentTab] = binding.feedRecycler.layoutManager?.onSaveInstanceState()
        val restore = if (tab != currentTab) tabScroll[tab] else null
        currentTab = tab
        binding.appBar.setExpanded(true, false)
        hideKeyboard()

        binding.homeHeader.isVisible = tab == Tab.HOME
        binding.categoryScroll.isVisible = tab == Tab.HOME
        binding.screenTitle.isVisible = tab != Tab.HOME
        binding.screenTitle.setText(if (tab == Tab.YOU) R.string.tab_you else R.string.tab_saved)
        binding.feedRecycler.isVisible = tab != Tab.YOU
        binding.profileScroll.isVisible = tab == Tab.YOU

        pinListKey = "" // pins of the new screen start from the first batch
        render()
        if (restore != null) binding.feedRecycler.post { binding.feedRecycler.layoutManager?.onRestoreInstanceState(restore) }
    }

    private fun goHome(category: String = activeCategory) {
        if (currentTab != Tab.HOME) binding.bottomNav.selectedItemId = R.id.nav_home
        selectCategory(category)
    }

    private fun goSaved(tab: SavedTab) {
        savedTab = tab
        if (currentTab != Tab.SAVED) binding.bottomNav.selectedItemId = R.id.nav_saved else render(scrollToTop = true)
    }

    private fun selectCategory(path: String) {
        val changed = path != activeCategory
        activeCategory = path
        checkChip(path)
        binding.appBar.setExpanded(true)
        if (changed) analyticsTracker.logEvent("select_content", mapOf("content_type" to "category", "item_id" to path))
        render(scrollToTop = true)
    }

    // ── Chips ───────────────────────────────────────────────────────────────────

    private fun renderChips() {
        val chips = buildList {
            add(ALL to getString(R.string.category_all))
            if (repo.hasPopular || activeCategory == POPULAR) add(POPULAR to getString(R.string.category_popular))
            repo.categories.forEach { add(it.path to it.name) }
        }
        if (chips == renderedChips) return
        renderedChips = chips

        val group = binding.categoryChips
        suppressChipEvents = true
        group.removeAllViews()
        chips.forEach { (path, name) ->
            val chip = layoutInflater.inflate(R.layout.item_category_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = name
            chip.tag = path
            group.addView(chip)
        }
        checkChip(activeCategory)
        suppressChipEvents = false

        group.setOnCheckedStateChangeListener { chipGroup, ids ->
            if (suppressChipEvents) return@setOnCheckedStateChangeListener
            val chip = ids.firstOrNull()?.let { chipGroup.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            selectCategory(chip.tag as String)
        }
    }

    private fun checkChip(path: String) {
        val group = binding.categoryChips
        val chip = (0 until group.childCount).map { group.getChildAt(it) as Chip }.firstOrNull { it.tag == path } ?: return
        if (!chip.isChecked) {
            val previous = suppressChipEvents
            suppressChipEvents = true
            chip.isChecked = true
            suppressChipEvents = previous
        }
        binding.categoryScroll.post {
            val target = chip.left - (binding.categoryScroll.width - chip.width) / 2
            binding.categoryScroll.smoothScrollTo(target.coerceAtLeast(0), 0)
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    private fun render(scrollToTop: Boolean = false) {
        if (!::adapter.isInitialized) return
        when (currentTab) {
            Tab.HOME -> adapter.submitList(homeRows()) { afterSubmit(scrollToTop) }
            Tab.SAVED -> adapter.submitList(savedRows()) { afterSubmit(scrollToTop) }
            Tab.YOU -> renderProfile()
        }
    }

    private fun afterSubmit(scrollToTop: Boolean) {
        if (scrollToTop) binding.feedRecycler.scrollToPosition(0)
        binding.feedRecycler.post { checkLoadMore() }
    }

    private fun isFeedReady() = when (activeCategory) {
        ALL, POPULAR -> repo.allLoaded
        else -> activeCategory in repo.loadedCategories
    }

    /** Filter + order like the website: All shuffled per session, a category newest first, Popular by score. */
    private fun homeFeed(): List<Wallpaper> {
        val inputs = Triple(repo.records, activeCategory, query)
        val cached = feedInputs
        if (cached != null && cached.first === inputs.first && cached.second == inputs.second && cached.third == inputs.third) {
            return feedList
        }
        val matches = repo.records.filter { image ->
            if (activeCategory != ALL && activeCategory != POPULAR && image.category != activeCategory) return@filter false
            query.isEmpty() ||
                "${image.title.orEmpty()} ${image.search.orEmpty()} ${image.categoryName}".lowercase().contains(query)
        }
        // Shuffle keys computed once per image, not once per comparison
        fun shuffleKeys() = matches.associateWith { PinUtils.hash(PinUtils.SEED + it.key) }
        feedList = when (activeCategory) {
            POPULAR -> shuffleKeys().let { keys ->
                matches.sortedWith(compareByDescending<Wallpaper> { it.score }.thenBy { keys.getValue(it) })
            }
            ALL -> shuffleKeys().let { keys -> matches.sortedBy { keys.getValue(it) } }
            else -> matches.reversed() // newest first (push keys sort by time)
        }
        feedInputs = inputs
        return feedList
    }

    private fun recentItems(): List<Wallpaper> = saved.recent.mapNotNull { repo.byKey[it.key] }

    private fun homeRows(): List<FeedRow> = buildList {
        val ready = isFeedReady()
        val feed = if (ready) homeFeed() else emptyList()

        if (activeCategory == ALL && query.isEmpty()) {
            val recent = recentItems()
            if (recent.size >= 2) add(FeedRow.Recent(recent.take(RECENT_STRIP_MAX), showSeeAll = true))
        }

        val categoryName = repo.categories.firstOrNull { it.path == activeCategory }?.name
        val title = when {
            query.isNotEmpty() -> getString(R.string.heading_results, rawQuery.trim())
            activeCategory == POPULAR -> getString(R.string.heading_popular)
            categoryName != null -> getString(R.string.heading_category, categoryName)
            else -> getString(R.string.heading_discover)
        }
        val count = if (ready) {
            resources.getQuantityString(R.plurals.wallpaper_count, feed.size, NumberFormat.getInstance().format(feed.size))
        } else {
            null
        }
        add(FeedRow.Heading(title, count))

        if (!ready) {
            add(FeedRow.Loading)
            return@buildList
        }
        if (feed.isEmpty()) {
            val message = if (query.isNotEmpty()) getString(R.string.no_wallpapers_found_for, rawQuery.trim())
            else getString(R.string.no_wallpapers_found)
            val action = if (query.isNotEmpty() || activeCategory != ALL) getString(R.string.show_all_wallpapers) else null
            add(FeedRow.Empty(null, message, action, ACTION_SHOW_ALL))
            return@buildList
        }

        addPins(feed, "home|$activeCategory|$query", withAds = true)
    }

    private fun savedRows(): List<FeedRow> = buildList {
        add(FeedRow.SyncBanner(account))
        add(FeedRow.SavedTabs(savedTab, saved.favorites.size, saved.collections.size, saved.recent.size))

        when (savedTab) {
            SavedTab.FAVORITES -> {
                if (saved.favorites.isEmpty()) {
                    add(emptyRow(R.string.empty_favourites_title, R.string.empty_favourites_text, R.drawable.ic_favorite_border_24))
                } else if (!repo.allLoaded) {
                    add(FeedRow.Loading)
                } else {
                    addPins(saved.favorites.mapNotNull { repo.byKey[it.key] }, "saved|favorites", withAds = false)
                }
            }
            SavedTab.RECENT -> {
                if (saved.recent.isEmpty()) {
                    add(emptyRow(R.string.empty_recent_title, R.string.empty_recent_text, R.drawable.ic_history_24))
                } else if (!repo.allLoaded) {
                    add(FeedRow.Loading)
                } else {
                    add(FeedRow.SubAction(getString(R.string.clear_history), ACTION_CLEAR_HISTORY))
                    addPins(recentItems(), "saved|recent", withAds = false)
                }
            }
            SavedTab.COLLECTIONS -> {
                if (saved.collections.isEmpty()) {
                    add(emptyRow(R.string.empty_collections_title, R.string.empty_collections_text, R.drawable.ic_collections_24))
                } else {
                    saved.collections.forEach { c ->
                        add(FeedRow.CollectionCard(c, c.keys.mapNotNull { repo.byKey[it] }.take(3)))
                    }
                }
            }
        }
    }

    private fun emptyRow(title: Int, text: Int, icon: Int) =
        FeedRow.Empty(getString(title), getString(text), getString(R.string.browse_wallpapers), ACTION_BROWSE, icon)

    /** Adds the revealed pins of [list] (and, on Home, a sponsored pin after every 15th). */
    private fun MutableList<FeedRow>.addPins(list: List<Wallpaper>, key: String, withAds: Boolean) {
        if (key != pinListKey) {
            pinListKey = key
            generation += 1
            measuring = false
            revealed = 0
            pinList = list
            loadMore()
        } else {
            pinList = list
            revealed = revealed.coerceAtMost(list.size)
        }
        if (revealed == 0) {
            add(FeedRow.Loading)
            return
        }
        val favorites = saved.favorites.mapTo(HashSet()) { it.key }
        val ads = if (withAds) feedAds.ads else emptyList()
        for (i in 0 until revealed) {
            val image = list[i]
            add(FeedRow.Pin(image, i, image.key in favorites))
            if (ads.isNotEmpty() && (i + 1) % PinUtils.AD_EVERY == 0) {
                val slot = (i + 1) / PinUtils.AD_EVERY - 1
                add(FeedRow.Ad(slot, ads[slot % ads.size]))
            }
        }
    }

    private fun loadMore() {
        if (measuring || revealed >= pinList.size) return
        measuring = true
        val gen = generation
        val start = revealed
        val size = if (start == 0) FIRST_PAGE_SIZE else PAGE_SIZE // something on screen sooner
        val batch = pinList.subList(start, minOf(start + size, pinList.size)).toList()
        lifecycleScope.launch {
            RatioCache.measure(this@MainActivity, batch)
            if (gen != generation) return@launch // screen changed while measuring
            measuring = false
            revealed = start + batch.size
            render()
        }
    }

    private fun checkLoadMore() {
        if (currentTab == Tab.YOU || revealed >= pinList.size) return
        val layoutManager = binding.feedRecycler.layoutManager as? StaggeredGridLayoutManager ?: return
        val last = layoutManager.findLastVisibleItemPositions(null).maxOrNull() ?: return
        if (last >= adapter.itemCount - LOAD_AHEAD) loadMore()
    }

    // ── Feed callbacks ──────────────────────────────────────────────────────────

    override fun onPinClick(row: FeedRow.Pin) {
        val source = if (currentTab == Tab.HOME) "feed" else savedTab.name.lowercase()
        openPin(row.image, pinList, source)
    }

    override fun onPinLongClick(row: FeedRow.Pin) {
        binding.feedRecycler.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        toggleSave(row.image)
    }

    override fun onRecentClick(items: List<Wallpaper>, index: Int) = openPin(items[index], items, "recent")

    override fun onRecentSeeAll() = goSaved(SavedTab.RECENT)

    override fun onRecentClear() = clearRecent()

    override fun onSubAction(actionId: Int) {
        if (actionId == ACTION_CLEAR_HISTORY) clearRecent()
    }

    override fun onEmptyAction(actionId: Int) {
        binding.searchInput.setText("")
        handler.removeCallbacks(applyQuery)
        rawQuery = ""
        query = ""
        goHome(ALL)
    }

    override fun onSignIn() = signIn()

    override fun onSavedTab(tab: SavedTab) {
        savedTab = tab
        render(scrollToTop = true)
    }

    override fun onCollectionClick(collection: SavedStore.Collection) {
        collectionLauncher.launch(CollectionActivity.intent(this, collection.id))
    }

    private fun clearRecent() {
        val undo = SavedRepository.clearRecent()
        snackbar(getString(R.string.cleared_recent), getString(R.string.undo)) { undo() }
    }

    private fun toggleSave(image: Wallpaper) {
        val added = SavedRepository.toggleFavorite(image)
        if (added) {
            snackbar(getString(R.string.saved_to_favourites), getString(R.string.view)) { goSaved(SavedTab.FAVORITES) }
        } else {
            snackbar(getString(R.string.removed_from_favourites), getString(R.string.undo)) {
                SavedRepository.toggleFavorite(image)
            }
        }
    }

    private fun openPin(image: Wallpaper, list: List<Wallpaper>, source: String) {
        analyticsTracker.logEvent(
            "select_content",
            mapOf(
                "content_type" to "wallpaper",
                "item_id" to image.key,
                "item_name" to image.displayTitle,
                "category" to image.categoryName
            )
        )
        detailLauncher.launch(ImageActivity.intent(this, list, list.indexOfFirst { it.key == image.key }.coerceAtLeast(0), source))
    }

    private fun snackbar(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        Snackbar.make(binding.root, message, if (action != null) 6000 else Snackbar.LENGTH_SHORT).apply {
            anchorView = binding.bottomBar
            if (action != null && onAction != null) setAction(action) { onAction() }
            show()
        }
    }

    // ── Deep links (App Links: https://wallapp.shubhamy.in/?c=…&pin=…) ─────────

    private fun handleDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.host != WEB_HOST) return
        uri.getQueryParameter("c")?.takeIf { it.isNotBlank() }?.let { activeCategory = it }
        pendingPin = uri.getQueryParameter("pin")?.takeIf { it.isNotBlank() }
        if (uri.getQueryParameter("view") == "saved") {
            savedTab = when (uri.getQueryParameter("tab")) {
                "collections" -> SavedTab.COLLECTIONS
                "recent" -> SavedTab.RECENT
                else -> SavedTab.FAVORITES
            }
            currentTab = Tab.SAVED
        }
        intent.data = null
        openPendingPin()
    }

    private fun openPendingPin() {
        val key = pendingPin ?: return
        val image = repo.byKey[key]
        if (image != null) {
            pendingPin = null
            val list = if (isFeedReady()) homeFeed().takeIf { feed -> feed.any { it.key == key } } else null
            openPin(image, list ?: listOf(image), "link")
        } else if (repo.allLoaded) {
            pendingPin = null
            Toast.makeText(this, R.string.wallpaper_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Account ─────────────────────────────────────────────────────────────────

    private fun signIn() {
        if (signingIn) return
        setSigningIn(true)
        lifecycleScope.launch {
            val result = SavedSync.signInWithGoogle(this@MainActivity)
            setSigningIn(false)
            when (result) {
                is SavedSync.SignInResult.Success -> snackbar(
                    result.name?.let { getString(R.string.signed_in_as, it) } ?: getString(R.string.signed_in)
                )
                is SavedSync.SignInResult.Failed -> snackbar(result.message, getString(R.string.details)) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.sign_in_error_title)
                        .setMessage(result.detail ?: result.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    /** Disables the sign-in buttons and shows "Opening Google…" while the picker/auth runs. */
    private fun setSigningIn(active: Boolean) {
        signingIn = active
        val profile = binding.profile
        profile.accountGoogle.isEnabled = !active
        profile.accountGoogle.setText(if (active) R.string.opening_google else R.string.continue_with_google)
        binding.headerSignIn.isEnabled = !active
        binding.headerSignIn.setText(if (active) R.string.opening_google else R.string.sign_in)
    }

    private fun signOut() {
        lifecycleScope.launch {
            SavedSync.signOut(this@MainActivity)
            snackbar(getString(R.string.signed_out))
        }
    }

    private fun renderAccount() {
        val user = account.user
        binding.headerSignIn.isVisible = account.known && user == null
        binding.headerAvatar.isVisible = user != null
        binding.headerAvatarInitial.isVisible = user != null && user.photoUrl == null
        if (user != null) {
            val initial = (user.displayName ?: user.email ?: "?").first().uppercase()
            binding.headerAvatarInitial.text = initial
            if (user.photoUrl != null) Glide.with(this).load(user.photoUrl).circleCrop().into(binding.headerAvatar)
            else binding.headerAvatar.setImageDrawable(null)
        }
        if (currentTab == Tab.YOU) renderProfile()
    }

    // ── You (account + settings) ────────────────────────────────────────────────

    private fun setupProfile() {
        val profile = binding.profile
        profile.accountGoogle.setOnClickListener { signIn() }
        profile.accountSignOut.setOnClickListener { signOut() }
        profile.rowTheme.setOnClickListener { showThemeDialog() }
        profile.rowAutoWallpaper.setOnClickListener { handleAutoWallpaperAction() }
        profile.rowRunNow.setOnClickListener { runAutoWallpaperNow() }
        profile.rowCheckUpdate.setOnClickListener {
            analyticsTracker.logEvent("check_updates_manual")
            checkForAppUpdates(force = true)
        }
        profile.rowWebsite.setOnClickListener {
            analyticsTracker.logEvent("nav_visit_website")
            openWebsite()
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

    /**
     * Opens the website in the browser. The site root is one of our App Links, so a plain
     * VIEW intent could route straight back here; the browser selector avoids that.
     */
    private fun openWebsite() {
        val uri = "https://$WEB_HOST/".toUri()
        val inBrowser = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        }
        runCatching { startActivity(inBrowser) }
            .recoverCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun renderProfile() {
        val profile = binding.profile
        val user = account.user
        profile.accountSignedOut.isVisible = account.known && user == null
        profile.accountSignedIn.isVisible = user != null
        profile.accountSignOut.isVisible = user != null
        if (user != null) {
            profile.accountName.text = user.displayName ?: getString(R.string.account)
            profile.accountEmail.text = user.email
            profile.accountEmail.isVisible = user.email != null
            profile.accountInitial.text = (user.displayName ?: user.email ?: "?").first().uppercase()
            if (user.photoUrl != null) Glide.with(this).load(user.photoUrl).circleCrop().into(profile.accountAvatar)
            else profile.accountAvatar.setImageDrawable(null)
            profile.accountSync.text = when (account.status) {
                SavedSync.Status.ERROR -> getString(R.string.sync_error)
                SavedSync.Status.SYNCING -> getString(R.string.sync_syncing)
                else -> getString(R.string.sync_synced)
            }
            val icon = if (account.status == SavedSync.Status.ERROR) R.drawable.ic_cloud_off_24 else R.drawable.ic_cloud_done_24
            profile.accountSync.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            profile.accountSync.compoundDrawablesRelative[0]?.setTint(ContextCompat.getColor(this, R.color.onSurfaceVariant))
        }

        profile.themeValue.setText(ThemeSetting.current(this).label)

        val autoEnabled = AutoWallpaperManager.isEnabled(this)
        profile.switchAutoWallpaper.isChecked = autoEnabled
        profile.rowRunNow.isVisible = autoEnabled
    }

    private fun showThemeDialog() {
        val modes = ThemeSetting.Mode.entries
        val current = ThemeSetting.current(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(modes.map { getString(it.label) }.toTypedArray(), modes.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                val mode = modes[which]
                if (mode != current) {
                    analyticsTracker.logEvent("theme_changed", mapOf("mode" to mode.name.lowercase()))
                    ThemeSetting.set(this, mode) // recreates the activity in the new theme
                }
            }
            .show()
    }

    private fun handleAutoWallpaperAction() {
        if (AutoWallpaperManager.isEnabled(this)) {
            AutoWallpaperManager.disable(this)
            analyticsTracker.logEvent("auto_wallpaper_disabled")
            renderProfile()
            Toast.makeText(this, getString(R.string.auto_wallpaper_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        if (!AutoWallpaperManager.hasEligibleFavorites()) {
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
        if (!AutoWallpaperManager.hasEligibleFavorites()) {
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

    private fun hideKeyboard() {
        binding.searchInput.clearFocus()
        WindowCompat.getInsetsController(window, binding.searchInput).hide(WindowInsetsCompat.Type.ime())
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
                            analyticsTracker.logEvent("in_app_update_start_failed", mapOf("error" to e.message.orEmpty()))
                        }
                    }
                } else if (force) {
                    Toast.makeText(this, getString(R.string.no_updates_available), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                analyticsTracker.logEvent("in_app_update_check_failed", mapOf("error" to exception.message.orEmpty()))
                if (force) Toast.makeText(this, getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUpdateDownloadedSnackbar() {
        Snackbar.make(binding.root, R.string.update_downloaded, Snackbar.LENGTH_INDEFINITE).apply {
            anchorView = binding.bottomBar
            setAction(R.string.restart) {
                analyticsTracker.logEvent("in_app_update_restart_clicked")
                appUpdateManager.completeUpdate()
            }
            show()
        }
    }

    private fun shouldCheckForUpdatesNow(): Boolean {
        val lastCheckedAt = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
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
                InstallStatus.INSTALLING -> Unit
                else -> {
                    if (updateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                updateInfo,
                                updateFlowLauncher,
                                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                            )
                        } catch (_: IntentSender.SendIntentException) {
                        }
                    }
                }
            }
        }
        if (currentTab == Tab.YOU) renderProfile()
    }

    override fun onDestroy() {
        handler.removeCallbacks(applyQuery)
        feedAds.destroy()
        appUpdateManager.unregisterListener(installStateUpdatedListener)
        super.onDestroy()
    }

    companion object {
        private const val ALL = "All"
        private const val POPULAR = "popular"
        private const val WEB_HOST = "wallapp.shubhamy.in"
        private const val PAGE_SIZE = 30
        private const val FIRST_PAGE_SIZE = 12
        private const val LOAD_AHEAD = 10
        private const val RECENT_STRIP_MAX = 12
        private const val SEARCH_DEBOUNCE_MS = 200L
        private const val ACTION_SHOW_ALL = 1
        private const val ACTION_BROWSE = 2
        private const val ACTION_CLEAR_HISTORY = 3
        private const val PREFS_NAME = "wallapp_prefs"
        private const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        private const val UPDATE_CHECK_COOLDOWN_MS = 2L * 24 * 60 * 60 * 1000 // 2 days
        private const val FEEDBACK_EMAIL = "shubhamskyjnp@gmail.com"
        private const val STATE_TAB = "state_tab"
        private const val STATE_CATEGORY = "state_category"
        private const val STATE_SAVED_TAB = "state_saved_tab"
    }
}
