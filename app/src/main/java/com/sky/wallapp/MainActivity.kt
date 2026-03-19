package com.sky.wallapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.firebase.analytics.FirebaseAnalytics
import com.sky.wallapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private var firebaseDatabase: FirebaseDatabase? = null
    private var mRef: DatabaseReference? = null
    private var adapter: FirebaseRecyclerAdapter<Model, ViewHolder>? = null
    private val categoriesList = mutableListOf<Category>()
    private var categoryAdapter: CategoryAdapter? = null
    private var isTrendingMode = true
    private var isFavoritesMode = false
    private var fullList = mutableListOf<Model>()
    private lateinit var analyticsTracker: AnalyticsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        // Enable disk persistence so cached wallpapers load on slow/no connection
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        firebaseDatabase = FirebaseDatabase.getInstance()
        analyticsTracker = AnalyticsTracker(FirebaseAnalytics.getInstance(this))
        analyticsTracker.logEvent("app_open")

        // Initialise AdMob and load the banner ad
        MobileAds.initialize(this) {}
        val adRequest = AdRequest.Builder().build()
        binding.appBarMain.contentMain.adView.loadAd(adRequest)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.appBarMain.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)
        binding.appBarMain.contentMain.recyclerView.layoutManager = GridLayoutManager(this, 2)
        showLoading()

        loadCategoriesToDrawer()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    showExitDialog()
                }
            }
        })
    }

    private fun firebaseDataLoad() {
        val query: Query = mRef!!
        showLoading()

        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(query, Model::class.java)
            .build()

        adapter = object : FirebaseRecyclerAdapter<Model, ViewHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.row, parent, false)
                return ViewHolder(view)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Model) {
                holder.textView.text = model.title
                Glide.with(applicationContext).load(model.image).into(holder.imageView)

                holder.itemView.setOnClickListener {
                    analyticsTracker.logEvent(
                        "wallpaper_open",
                        mapOf("source" to "category", "title" to model.title)
                    )
                    val intent = Intent(this@MainActivity, ImageActivity::class.java)
                    intent.putExtra("image", model.image)
                    intent.putExtra("title", model.title)
                    startActivity(intent)
                }
            }

            override fun onDataChanged() {
                updateListState(itemCount)
            }

            override fun onError(error: DatabaseError) {
                showEmpty(getString(R.string.empty_wallpapers))
            }
        }

        binding.appBarMain.contentMain.recyclerView.adapter = adapter
        adapter?.startListening()
    }

    private fun loadCategoriesToDrawer() {
        firebaseDatabase?.getReference("categories")
            ?.addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    categoriesList.clear()

                    snapshot.children.forEach { postSnapshot ->
                        postSnapshot.getValue(Category::class.java)?.let {
                            categoriesList.add(it)
                        }
                    }

                    // ✅ Load trending AFTER categories
                    if (isTrendingMode) {
                        loadTrending()
                    }

                    val categoriesItem = binding.navView.menu.findItem(R.id.nav_categories_container)
                    val recyclerView = categoriesItem.actionView as? RecyclerView

                    recyclerView?.apply {
                        layoutManager = GridLayoutManager(this@MainActivity, 2)
                        categoryAdapter = CategoryAdapter(categoriesList, firebaseDatabase!!) { selectedCategory ->
                            isTrendingMode = false
                            isFavoritesMode = false

                            this@MainActivity.adapter?.stopListening()

                            mRef = firebaseDatabase?.getReference(selectedCategory.path ?: "random")
                            firebaseDataLoad()
                            supportActionBar?.title = selectedCategory.name
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                            analyticsTracker.logEvent(
                                "category_select",
                                mapOf(
                                    "category_name" to selectedCategory.name,
                                    "category_path" to selectedCategory.path
                                )
                            )

                            showCategorySelectedState()
                        }
                        adapter = categoryAdapter
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadTrending() {
        isTrendingMode = true
        isFavoritesMode = false
        supportActionBar?.title = "Trending"
        showTrendingSelectedState()
        showLoading()
        analyticsTracker.logEvent("open_trending")

        adapter?.stopListening()
        adapter = null

        if (categoriesList.isEmpty()) {
            showEmpty(getString(R.string.empty_wallpapers))
            return
        }

        val allPhotos = mutableListOf<Model>()
        var categoriesProcessed = 0
        val totalCategories = categoriesList.size

        for (category in categoriesList) {
            val path = category.path ?: continue

            firebaseDatabase?.getReference(path)
                ?.limitToFirst(20)
                ?.addListenerForSingleValueEvent(object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (postSnapshot in snapshot.children) {
                            val model = postSnapshot.getValue(Model::class.java)
                            if (model != null) {
                                allPhotos.add(model)
                            }
                        }

                        categoriesProcessed++

                        if (categoriesProcessed == totalCategories) {
                            fullList.clear()
                            fullList.addAll(allPhotos)
                            bindStaticList(allPhotos.shuffled())
                            analyticsTracker.logEvent(
                                "trending_loaded",
                                mapOf("item_count" to allPhotos.size.toString())
                            )
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        categoriesProcessed++
                    }
                })
        }
    }

    private fun loadFavorites() {
        isTrendingMode = false
        isFavoritesMode = true
        supportActionBar?.title = getString(R.string.nav_favorites)

        adapter?.stopListening()
        adapter = null

        val favorites = FavoritesStore.getFavorites(this)
        fullList.clear()
        fullList.addAll(favorites)
        bindStaticList(favorites)
        binding.navView.setCheckedItem(R.id.nav_favorites)

        analyticsTracker.logEvent("open_favorites", mapOf("item_count" to favorites.size.toString()))
    }

    private fun bindStaticList(items: List<Model>) {
        updateListState(items.size)
        binding.appBarMain.contentMain.recyclerView.adapter =
            object : RecyclerView.Adapter<ViewHolder>() {

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.row, parent, false)
                    return ViewHolder(view)
                }

                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    val model = items[position]

                    holder.textView.text = model.title
                    Glide.with(this@MainActivity)
                        .load(model.image)
                        .into(holder.imageView)

                    holder.itemView.setOnClickListener {
                        analyticsTracker.logEvent(
                            "wallpaper_open",
                            mapOf("source" to "trending", "title" to model.title)
                        )
                        val intent = Intent(this@MainActivity, ImageActivity::class.java)
                        intent.putExtra("image", model.image)
                        intent.putExtra("title", model.title)
                        startActivity(intent)
                    }
                }

                override fun getItemCount(): Int = items.size
            }
    }

    private fun localSearch(searchText: String) {
        val query = searchText.trim()

        if (isTrendingMode || isFavoritesMode) {
            if (query.isEmpty()) {
                bindStaticList(if (isTrendingMode) fullList.shuffled() else fullList)
            } else {
                val filtered = fullList.filter {
                    it.title?.contains(query, ignoreCase = true) == true
                }
                bindStaticList(filtered)
                if (filtered.isEmpty()) {
                    showEmpty(getString(R.string.empty_search_results))
                }
                analyticsTracker.logEvent(
                    "search_trending",
                    mapOf("query" to query, "result_count" to filtered.size.toString())
                )
            }
            return
        }

        val ref = mRef ?: return
        val firebaseSearchQuery = mRef?.orderByChild("title")
            ?.startAt(searchText)
            ?.endAt(searchText + "\uf8ff")

        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(firebaseSearchQuery ?: ref, Model::class.java)
            .build()
        showLoading()

        adapter = object : FirebaseRecyclerAdapter<Model, ViewHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.row, parent, false)
                return ViewHolder(view)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Model) {
                holder.textView.text = model.title
                Glide.with(applicationContext).load(model.image).into(holder.imageView)

                holder.itemView.setOnClickListener {
                    analyticsTracker.logEvent(
                        "wallpaper_open",
                        mapOf("source" to "search_category", "title" to model.title)
                    )
                    val intent = Intent(this@MainActivity, ImageActivity::class.java)
                    intent.putExtra("image", model.image)
                    intent.putExtra("title", model.title)
                    startActivity(intent)
                }
            }

            override fun onDataChanged() {
                if (itemCount == 0 && query.isNotEmpty()) {
                    showEmpty(getString(R.string.empty_search_results))
                } else {
                    updateListState(itemCount)
                }
                analyticsTracker.logEvent(
                    "search_category",
                    mapOf("query" to query, "result_count" to itemCount.toString())
                )
            }

            override fun onError(error: DatabaseError) {
                showEmpty(getString(R.string.empty_wallpapers))
            }
        }

        binding.appBarMain.contentMain.recyclerView.adapter = adapter
        adapter?.startListening()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                localSearch(s)
                analyticsTracker.logEvent("search_submit", mapOf("query" to s.trim()))
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                localSearch(newText)
                return false
            }
        })
        return true
    }


    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.nav_home -> loadTrending()

            R.id.nav_favorites -> loadFavorites()

            R.id.nav_contact -> {
                analyticsTracker.logEvent("nav_contact")
                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("shubhamskyjnp@gmail.com"))
                intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback for WallApp")
                intent.type = "message/rfc822"
                startActivity(Intent.createChooser(intent, "Choose Email Client"))
            }

            R.id.nav_share -> {
                analyticsTracker.logEvent("nav_share_app")
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT,
                    "https://play.google.com/store/apps/details?id=$packageName")
                startActivity(Intent.createChooser(intent, "Share via"))
            }

            R.id.nav_rate_us -> {
                analyticsTracker.logEvent("nav_rate_app")
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }

            R.id.nav_privacy -> {
                startActivity(Intent(this, PrivacyPolicyActivity::class.java))
            }
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showTrendingSelectedState() {
        val trendingItem = binding.navView.menu.findItem(R.id.nav_home)
        trendingItem.isCheckable = true
        trendingItem.isChecked = true
        binding.navView.menu.findItem(R.id.nav_favorites).isChecked = false
        binding.navView.setCheckedItem(R.id.nav_home)
        categoryAdapter?.clearSelection()
    }

    private fun showCategorySelectedState() {
        binding.navView.menu.findItem(R.id.nav_home).isChecked = false
        binding.navView.menu.findItem(R.id.nav_favorites).isChecked = false
    }

    private fun showLoading() {
        binding.appBarMain.contentMain.loadingProgress.visibility = android.view.View.VISIBLE
        binding.appBarMain.contentMain.emptyStateText.visibility = android.view.View.GONE
        binding.appBarMain.contentMain.recyclerView.visibility = android.view.View.GONE
    }

    private fun showEmpty(message: String) {
        binding.appBarMain.contentMain.loadingProgress.visibility = android.view.View.GONE
        binding.appBarMain.contentMain.recyclerView.visibility = android.view.View.GONE
        binding.appBarMain.contentMain.emptyStateText.text = message
        binding.appBarMain.contentMain.emptyStateText.visibility = android.view.View.VISIBLE
    }

    private fun updateListState(itemCount: Int) {
        binding.appBarMain.contentMain.loadingProgress.visibility = android.view.View.GONE
        if (itemCount > 0) {
            binding.appBarMain.contentMain.recyclerView.visibility = android.view.View.VISIBLE
            binding.appBarMain.contentMain.emptyStateText.visibility = android.view.View.GONE
        } else {
            showEmpty(
                if (isFavoritesMode) getString(R.string.empty_favorites)
                else getString(R.string.empty_wallpapers)
            )
        }
    }

    // ── AdView lifecycle ────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        binding.appBarMain.contentMain.adView.resume()
        if (isFavoritesMode) {
            loadFavorites()
        }
    }

    override fun onPause() {
        binding.appBarMain.contentMain.adView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.appBarMain.contentMain.adView.destroy()
        super.onDestroy()
    }
}
