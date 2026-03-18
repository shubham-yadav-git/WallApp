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
    private var adapter: FirebaseRecyclerAdapter<Model, ViewHolder>? = null
    private val categoriesList = mutableListOf<Category>()
    private var isTrendingMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        firebaseDatabase = FirebaseDatabase.getInstance()
        mRef = firebaseDatabase?.getReference("trending")

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.appBarMain.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)

        binding.appBarMain.contentMain.recyclerView.layoutManager = GridLayoutManager(this, 2)

        firebaseDataLoad()
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

                    // Setup the compact categories RecyclerView inside the drawer
                    val categoriesItem = binding.navView.menu.findItem(R.id.nav_categories_container)
                    val recyclerView = categoriesItem.actionView as? RecyclerView
                    
                    recyclerView?.apply {
                        layoutManager = GridLayoutManager(this@MainActivity, 2)
                        adapter = CategoryAdapter(categoriesList) { selectedCategory ->
                            mRef = firebaseDatabase?.getReference(selectedCategory.path ?: "random")
                            firebaseDataLoad()
                            supportActionBar?.title = selectedCategory.name
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                            binding.navView.setCheckedItem(R.id.nav_categories_section)
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
        mRef = firebaseDatabase?.getReference("trending")
        firebaseDataLoad()
        supportActionBar?.title = "Trending"
    }

    private fun localSearch(searchText: String) {
        val firebaseSearchQuery = mRef?.orderByChild("title")?.startAt(searchText)?.endAt(searchText + "\uf8ff")

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
        val menuItem = menu.findItem(R.id.action_search)
        val searchView = menuItem.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                localSearch(s)
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                localSearch(newText)
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
        when (menuItem.itemId) {
            R.id.nav_home -> {
                loadTrending()
                menuItem.isChecked = true
            }
            R.id.nav_contact -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("shubhamskyjnp@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Feedback for WallApp")
                    type = "message/rfc822"
                }
                startActivity(Intent.createChooser(intent, "Choose Email Client"))
            }
            R.id.nav_share -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Download WallApp")
                    putExtra(Intent.EXTRA_TEXT, "Check out this cool wallpaper app: https://play.google.com/store/apps/details?id=${packageName}")
                }
                startActivity(Intent.createChooser(intent, "Share via"))
            }
            R.id.nav_rate_us -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${packageName}")
                }
                startActivity(intent)
            }
            R.id.nav_privacy -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://github.com/shubhamy-google/WallApp/blob/main/privacy_policy.md")
                }
                startActivity(intent)
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
