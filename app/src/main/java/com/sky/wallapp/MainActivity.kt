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
import com.sky.wallapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private var firebaseDatabase: FirebaseDatabase? = null
    private var mRef: DatabaseReference? = null
    private var adapter: FirebaseRecyclerAdapter<Model, ViewHolder>? = null
    private val categoriesList = mutableListOf<Category>()
    private var categoryAdapter: CategoryAdapter? = null
    private var isTrendingMode = true
    private var fullList = mutableListOf<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        firebaseDatabase = FirebaseDatabase.getInstance()

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.appBarMain.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)
        binding.appBarMain.contentMain.recyclerView.layoutManager = GridLayoutManager(this, 2)

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
                    val intent = Intent(this@MainActivity, ImageActivity::class.java)
                    intent.putExtra("image", model.image)
                    intent.putExtra("title", model.title)
                    startActivity(intent)
                }
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
                        categoryAdapter = CategoryAdapter(categoriesList) { selectedCategory ->
                            isTrendingMode = false

                            this@MainActivity.adapter?.stopListening()

                            mRef = firebaseDatabase?.getReference(selectedCategory.path ?: "random")
                            firebaseDataLoad()
                            supportActionBar?.title = selectedCategory.name
                            binding.drawerLayout.closeDrawer(GravityCompat.START)

                            binding.navView.menu.findItem(R.id.nav_home).isChecked = false
                            binding.navView.setCheckedItem(R.id.nav_categories_section)
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
        supportActionBar?.title = "Trending"
        binding.navView.setCheckedItem(R.id.nav_home)

        adapter?.stopListening()
        adapter = null

        if (categoriesList.isEmpty()) return

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

                            val finalList = allPhotos.shuffled()

                            binding.appBarMain.contentMain.recyclerView.adapter =
                                object : RecyclerView.Adapter<ViewHolder>() {

                                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                                        val view = LayoutInflater.from(parent.context)
                                            .inflate(R.layout.row, parent, false)
                                        return ViewHolder(view)
                                    }

                                    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                                        val model = finalList[position]

                                        holder.textView.text = model.title
                                        Glide.with(this@MainActivity)
                                            .load(model.image)
                                            .into(holder.imageView)

                                        holder.itemView.setOnClickListener {
                                            val intent = Intent(this@MainActivity, ImageActivity::class.java)
                                            intent.putExtra("image", model.image)
                                            intent.putExtra("title", model.title)
                                            startActivity(intent)
                                        }
                                    }

                                    override fun getItemCount(): Int = finalList.size
                                }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        categoriesProcessed++
                    }
                })
        }
    }

    private fun localSearch(searchText: String) {
        val firebaseSearchQuery = mRef?.orderByChild("title")
            ?.startAt(searchText)
            ?.endAt(searchText + "\uf8ff")

        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(firebaseSearchQuery!!, Model::class.java)
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
                    val intent = Intent(this@MainActivity, ImageActivity::class.java)
                    intent.putExtra("image", model.image)
                    intent.putExtra("title", model.title)
                    startActivity(intent)
                }
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

            R.id.nav_contact -> {
                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("shubhamskyjnp@gmail.com"))
                intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback for WallApp")
                intent.type = "message/rfc822"
                startActivity(Intent.createChooser(intent, "Choose Email Client"))
            }

            R.id.nav_share -> {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT,
                    "https://play.google.com/store/apps/details?id=$packageName")
                startActivity(Intent.createChooser(intent, "Share via"))
            }

            R.id.nav_rate_us -> {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }

            R.id.nav_privacy -> {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/shubhamy-google/WallApp/blob/main/privacy_policy.md")))
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
}