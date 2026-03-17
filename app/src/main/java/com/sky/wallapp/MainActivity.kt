package com.sky.wallapp

import android.content.Intent
import android.graphics.drawable.Drawable
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
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.sky.wallapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private var firebaseDatabase: FirebaseDatabase? = null
    private var mRef: DatabaseReference? = null
    private var firebaseRecyclerAdapter: FirebaseRecyclerAdapter<Model, ViewHolder>? = null
    private var categoriesList = mutableListOf<Category>()
    private var isTrendingMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)
        
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.appBarMain.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
        binding.navView.setCheckedItem(R.id.nav_home)

        firebaseDatabase = FirebaseDatabase.getInstance()
        mRef = firebaseDatabase?.getReference("random")
        
        initRecyclerView()
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

    private fun loadCategoriesToDrawer() {
        firebaseDatabase?.getReference("categories")
            ?.addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    categoriesList.clear()

                    val menu = binding.navView.menu
                    val subMenu = menu.findItem(R.id.nav_categories_parent).subMenu

                    subMenu?.clear()

                    for (postSnapshot in snapshot.children) {
                        val category = postSnapshot.getValue(Category::class.java)

                        category?.let {
                            categoriesList.add(it)

                            val itemId = categoriesList.size + 100

                            subMenu?.add(0, itemId, Menu.NONE, it.name)
                                ?.setIcon(R.drawable.ic_action_category)
                        }
                    }
                    
                    if (isTrendingMode) {
                        loadTrending()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadTrending() {
        isTrendingMode = true
        supportActionBar?.title = "Trending"
        binding.navView.setCheckedItem(R.id.nav_home)
        
        if (categoriesList.isEmpty()) {
            // If categories haven't loaded yet, wait for onDataChange
            return
        }

        val allPhotos = mutableListOf<Model>()
        var categoriesProcessed = 0
        val totalCategories = categoriesList.size

        for (category in categoriesList) {
            val path = category.path ?: continue
            firebaseDatabase?.getReference(path)?.limitToFirst(20)
                ?.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (postSnapshot in snapshot.children) {
                            postSnapshot.getValue(Model::class.java)?.let { allPhotos.add(it) }
                        }
                        categoriesProcessed++
                        if (categoriesProcessed == totalCategories) {
                            displayTrending(allPhotos)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        categoriesProcessed++
                        if (categoriesProcessed == totalCategories) {
                            displayTrending(allPhotos)
                        }
                    }
                })
        }
    }

    private fun displayTrending(photos: MutableList<Model>) {
        photos.shuffle()
        
        firebaseRecyclerAdapter?.stopListening()
        firebaseRecyclerAdapter = null
        
        val adapter = object : RecyclerView.Adapter<ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.row, parent, false)
                return ViewHolder(view)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                bindWallpaper(holder, photos[position])
            }

            override fun getItemCount(): Int = photos.size
        }
        binding.appBarMain.contentMain.recyclerView.adapter = adapter
    }

    private fun initRecyclerView() {
        binding.appBarMain.contentMain.recyclerView.apply {
            setHasFixedSize(true)
            itemAnimator = null 
        }
    }

    private fun bindWallpaper(holder: ViewHolder, model: Model) {
        holder.itemView.tag = false 

        val cloudinaryUrl = model.cloudinaryUrl
        val imageUrl: String?
        val thumbUrl: String?

        if (!cloudinaryUrl.isNullOrEmpty()) {
            thumbUrl = cloudinaryUrl.replace("/upload/", "/upload/w_300,q_auto,f_auto/")
            imageUrl = cloudinaryUrl.replace("/upload/", "/upload/q_auto,f_auto/")
        } else {
            thumbUrl = if (!model.thumbs.isNullOrEmpty()) model.thumbs else model.image
            imageUrl = model.image
        }

        Glide.with(this@MainActivity)
            .load(thumbUrl)
            .centerCrop()
            .thumbnail(
                if (!cloudinaryUrl.isNullOrEmpty()) {
                    Glide.with(this@MainActivity).load(cloudinaryUrl.replace("/upload/", "/upload/w_50,q_auto,f_auto/")).centerCrop()
                } else if (!model.thumbs.isNullOrEmpty()) {
                    Glide.with(this@MainActivity).load(model.thumbs).centerCrop()
                } else null
            )
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.color.surfaceVariant)
            .error(R.mipmap.ic_launcher_round)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, modelObj: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    holder.itemView.tag = false
                    return false
                }

                override fun onResourceReady(resource: Drawable, modelObj: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    holder.itemView.tag = true 
                    return false
                }
            })
            .into(holder.imageView)

        holder.textView.text = model.title
        holder.cardViewParent.setOnClickListener {
            val isLoaded = holder.itemView.tag as? Boolean ?: false
            if (isLoaded) {
                val intent = Intent(this@MainActivity, ImageActivity::class.java).apply {
                    putExtra("title", model.title)
                    putExtra("image", imageUrl)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this@MainActivity, "Please wait for the image to load", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAdapter(query: Query) {
        isTrendingMode = false
        firebaseRecyclerAdapter?.stopListening()
        
        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(query, Model::class.java)
            .build()

        firebaseRecyclerAdapter = object : FirebaseRecyclerAdapter<Model, ViewHolder>(options) {
            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Model) {
                bindWallpaper(holder, model)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.row, parent, false)
                return ViewHolder(view)
            }
        }
        
        firebaseRecyclerAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        firebaseRecyclerAdapter?.startListening()
        binding.appBarMain.contentMain.recyclerView.adapter = firebaseRecyclerAdapter
    }

    private fun firebaseDataLoad() {
        mRef?.let { setupAdapter(it) }
    }

    private fun firebaseSearch(searchText: String) {
        val queryText = searchText.lowercase()
        val searchQuery = mRef?.orderByChild("search")?.startAt(queryText)?.endAt(queryText + "\uf8ff")
        searchQuery?.let { setupAdapter(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        firebaseRecyclerAdapter?.stopListening()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        val menuItem = menu.findItem(R.id.action_search)
        val searchView = menuItem.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                firebaseSearch(s)
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                firebaseSearch(newText)
                return false
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.categories) {
            showCategoriesDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCategoriesDialog() {
        if (categoriesList.isEmpty()) {
            Toast.makeText(this, "Loading categories...", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryNames = categoriesList.map { it.name ?: "Unknown" }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Categories")
            .setIcon(R.drawable.ic_action_category)
            .setItems(categoryNames) { _, i ->
                val selectedCategory = categoriesList[i]
                mRef = firebaseDatabase?.getReference(selectedCategory.path ?: "random")
                firebaseDataLoad()
                supportActionBar?.title = selectedCategory.name
            }
            .show()
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setIcon(R.drawable.ic_exit_to_app_black_24dp)
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (val id = menuItem.itemId) {
            R.id.nav_home -> {
                loadTrending()
            }
            R.id.nav_contact -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("shubhamskyjnp@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Feedback: Wallpaper App")
                    type = "message/rfc822"
                }
                startActivity(Intent.createChooser(intent, "Send Email"))
            }
            R.id.nav_rate_us -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = "market://details?id=com.sky.wallapp".toUri()
                }
                startActivity(Intent.createChooser(intent, "Launch Google Play"))
            }
            R.id.nav_share -> shareApp()
            R.id.nav_privacy -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = "https://your-privacy-policy-url.com".toUri()
                }
                startActivity(intent)
            }
            else -> {
                if (id >= 100) {
                    val index = id - 101
                    if (index >= 0 && index < categoriesList.size) {
                        val selectedCategory = categoriesList[index]
                        mRef = firebaseDatabase?.getReference(selectedCategory.path ?: "random")
                        firebaseDataLoad()
                        supportActionBar?.title = selectedCategory.name
                    }
                }
            }
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Wallpaper App")
            putExtra(Intent.EXTRA_TEXT, "Check out this amazing wallpaper app: https://play.google.com/store/apps/details?id=com.sky.wallapp")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }
}
