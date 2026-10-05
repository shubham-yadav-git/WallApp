package com.sky.wallapp

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Every public wallpaper, loaded the same way as the website (`usePublicRecords.js`):
 * 1. the cached feed.json snapshot, instantly;
 * 2. a fresh feed.json download;
 * 3. per category, a live listener only for images newer than the snapshot;
 * 4. without any snapshot, each category is read live in full.
 * Only `/categories` and `/{path}` are read — never the database root (rules forbid it).
 */
object WallpaperRepository {

    data class CategoryInfo(val path: String, val name: String)

    data class State(
        val categories: List<CategoryInfo> = emptyList(),
        val records: List<Wallpaper> = emptyList(),
        val loadedCategories: Set<String> = emptySet()
    ) {
        val allLoaded: Boolean
            get() = categories.isNotEmpty() && categories.all { it.path in loadedCategories }

        val byKey: Map<String, Wallpaper> by lazy { records.associateBy { it.key } }

        val hasPopular: Boolean by lazy { records.any { it.score > 0 } }
    }

    private const val TAG = "WallpaperRepository"
    private const val FEED_URL = "https://wallapp.shubhamy.in/feed.json"
    private const val CACHE_FILE = "feed.json"
    private const val FETCH_TIMEOUT_MS = 15_000L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val database by lazy { FirebaseDatabase.getInstance() }

    private var started = false
    private var cacheJob: Deferred<FeedSnapshot?>? = null
    private var snapshot: FeedSnapshot? = null
    /** False until we know whether a snapshot exists (cached or downloaded). */
    private var snapshotResolved = false
    private var liveCategories: List<CategoryInfo> = emptyList()
    private var builtFor: Pair<List<CategoryInfo>, FeedSnapshot?>? = null

    private val byCategory = LinkedHashMap<String, List<Wallpaper>>()
    private val loaded = mutableSetOf<String>()
    private val listeners = mutableListOf<Pair<Query, ValueEventListener>>()

    /** Parses the cached snapshot in the background (no network); call early, e.g. at app start. */
    fun prewarm(context: Context) {
        if (cacheJob != null) return
        val appContext = context.applicationContext
        cacheJob = scope.async(Dispatchers.IO) { readCache(appContext) }
    }

    /** Starts loading (idempotent). Call from the main thread. */
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        listenCategories()

        scope.launch {
            prewarm(appContext)
            val cached = cacheJob?.await()
            if (cached != null) {
                snapshot = cached
                snapshotResolved = true
                rebuild()
            }

            val fresh = withContext(Dispatchers.IO) { download(appContext) }
            when {
                fresh != null && fresh.generatedAt != cached?.generatedAt -> snapshot = fresh
                fresh == null && cached == null -> Log.w(TAG, "No feed.json; loading categories live")
            }
            snapshotResolved = true
            rebuild()
        }
    }

    /**
     * Finds one wallpaper by key: loaded records, then the cached snapshot, then the database.
     * Usable without [start] (e.g. from the daily wallpaper worker).
     */
    suspend fun lookup(context: Context, key: String): Wallpaper? {
        _state.value.byKey[key]?.let { return it }
        val (path, id) = Wallpaper.splitKey(key) ?: return null

        val cached = snapshot ?: withContext(Dispatchers.IO) { readCache(context.applicationContext) }
        cached?.items?.get(path)?.firstOrNull { it.id == id }?.let { row ->
            val name = cached.categories.firstOrNull { it.first == path }?.second ?: path
            return FeedSnapshot.toWallpaper(row, path, name, cached.popular[path]?.get(id) ?: 0)
        }

        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                database.getReference(path).child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (cont.isActive) cont.resume(Wallpaper.fromSnapshot(snapshot, path, path))
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        }
    }

    // ── Categories ──────────────────────────────────────────────────────────────

    private fun listenCategories() {
        database.getReference("categories").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveCategories = sortCategories(snapshot)
                rebuild()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Categories failed: ${error.message}")
            }
        })
    }

    /** By `order` ascending (missing last), then by push key — same rule as the website. */
    private fun sortCategories(snapshot: DataSnapshot): List<CategoryInfo> {
        data class Raw(val id: String, val order: Long, val info: CategoryInfo)
        return snapshot.children.mapNotNull { child ->
            val data = child.value as? Map<*, *> ?: return@mapNotNull null
            val path = (data["path"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = (data["name"] as? String)?.takeIf { it.isNotBlank() } ?: path
            val order = when (val o = data["order"]) {
                is Number -> o.toLong()
                is String -> o.toLongOrNull()
                else -> null
            } ?: Long.MAX_VALUE
            Raw(child.key.orEmpty(), order, CategoryInfo(path, name))
        }.sortedWith(compareBy<Raw> { it.order }.thenBy { it.id }).map { it.info }
    }

    // ── Records ─────────────────────────────────────────────────────────────────

    private fun rebuild() {
        val categories = liveCategories.ifEmpty {
            snapshot?.categories?.map { CategoryInfo(it.first, it.second) }.orEmpty()
        }
        if (categories.isEmpty() || !snapshotResolved) {
            publish(categories)
            return
        }
        if (builtFor == categories to snapshot) return
        builtFor = categories to snapshot

        removeListeners()
        byCategory.clear()
        loaded.clear()

        categories.forEach { category ->
            val rows = snapshot?.items?.get(category.path)
            if (!rows.isNullOrEmpty()) listenNewer(category, rows) else listenAll(category)
        }
        publish(categories)
    }

    private fun listenNewer(category: CategoryInfo, rows: List<FeedSnapshot.Row>) {
        val scores = snapshot?.popular?.get(category.path).orEmpty()
        val base = rows.map { FeedSnapshot.toWallpaper(it, category.path, category.name, scores[it.id] ?: 0) }
        byCategory[category.path] = base
        loaded += category.path

        val query = database.getReference(category.path).orderByKey().startAfter(rows.last().id)
        addListener(query, object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val added = parseChildren(snapshot, category)
                if (added.isEmpty() && byCategory[category.path] === base) return
                byCategory[category.path] = base + added
                publish()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    private fun listenAll(category: CategoryInfo) {
        addListener(database.getReference(category.path), object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                byCategory[category.path] = parseChildren(snapshot, category)
                loaded += category.path
                publish()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Category ${category.path} failed: ${error.message}")
                byCategory[category.path] = emptyList()
                loaded += category.path
                publish()
            }
        })
    }

    private fun parseChildren(snapshot: DataSnapshot, category: CategoryInfo): List<Wallpaper> =
        snapshot.children
            .mapNotNull { Wallpaper.fromSnapshot(it, category.path, category.name) }
            .sortedBy { it.id }

    private fun addListener(query: Query, listener: ValueEventListener) {
        query.addValueEventListener(listener)
        listeners += query to listener
    }

    private fun removeListeners() {
        listeners.forEach { (query, listener) -> query.removeEventListener(listener) }
        listeners.clear()
    }

    private fun publish(categories: List<CategoryInfo> = _state.value.categories) {
        _state.value = State(
            categories = categories,
            records = categories.flatMap { byCategory[it.path].orEmpty() },
            loadedCategories = loaded.toSet()
        )
    }

    // ── feed.json ───────────────────────────────────────────────────────────────

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    private fun readCache(context: Context): FeedSnapshot? = runCatching {
        val file = cacheFile(context)
        if (file.exists()) file.bufferedReader().use { FeedSnapshot.parse(it) } else null
    }.getOrNull()

    /** Downloads feed.json (gzip is negotiated transparently) and caches it if valid. */
    private fun download(context: Context): FeedSnapshot? = runCatching {
        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = FETCH_TIMEOUT_MS.toInt()
            readTimeout = FETCH_TIMEOUT_MS.toInt()
            useCaches = false
        }
        try {
            val isJson = connection.contentType.orEmpty().contains("json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK || !isJson) return@runCatching null
            // Stream to a temp file, then parse it; only a valid snapshot replaces the cache.
            val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.bufferedReader().use { FeedSnapshot.parse(it) }?.also { tmp.renameTo(cacheFile(context)) }
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "feed.json download failed: ${it.message}") }.getOrNull()
}
