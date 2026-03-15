package com.sky.wallapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.sky.wallapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private var firebaseDatabase: FirebaseDatabase? = null
    private var mRef: DatabaseReference? = null
    private var firebaseRecyclerAdapter: FirebaseRecyclerAdapter<Model, ViewHolder>? = null
    private var staggeredGridLayoutManager: WrapStaggeredGridLayoutManager? = null

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

        firebaseDatabase = FirebaseDatabase.getInstance()
        mRef = firebaseDatabase?.getReference("random")
        
        firebaseDataLoad()

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
        staggeredGridLayoutManager = WrapStaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.appBarMain.contentMain.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = staggeredGridLayoutManager
        }

        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(mRef!!, Model::class.java)
            .build()

        firebaseRecyclerAdapter = object : FirebaseRecyclerAdapter<Model, ViewHolder>(options) {
            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Model) {
                Glide.with(this@MainActivity)
                    .load(model.image)
                    .thumbnail(Glide.with(this@MainActivity).load(R.drawable.load))
                    .error(R.mipmap.ic_launcher_round)
                    .transition(DrawableTransitionOptions.withCrossFade(1000))
                    .into(holder.imageView)

                holder.textView.text = model.title
                holder.cardViewParent.setOnClickListener {
                    val intent = Intent(this@MainActivity, ImageActivity::class.java).apply {
                        putExtra("title", model.title)
                        putExtra("image", model.image)
                    }
                    startActivity(intent)
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.row, parent, false)
                return ViewHolder(view)
            }
        }
        firebaseRecyclerAdapter?.startListening()
        binding.appBarMain.contentMain.recyclerView.adapter = firebaseRecyclerAdapter
    }

    private fun firebaseSearch(searchText: String) {
        val query = searchText.lowercase()
        val firebaseSearchQuery = mRef?.orderByChild("search")?.startAt(query)?.endAt(query + "\uf8ff")

        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(firebaseSearchQuery!!, Model::class.java)
            .build()

        firebaseRecyclerAdapter = object : FirebaseRecyclerAdapter<Model, ViewHolder>(options) {
            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Model) {
                Glide.with(this@MainActivity)
                    .load(model.image)
                    .thumbnail(Glide.with(this@MainActivity).load(R.drawable.load))
                    .error(R.mipmap.ic_launcher_round)
                    .transition(DrawableTransitionOptions.withCrossFade(1000))
                    .into(holder.imageView)

                holder.textView.text = model.title
                holder.cardViewParent.setOnClickListener {
                    val intent = Intent(this@MainActivity, ImageActivity::class.java).apply {
                        putExtra("title", model.title)
                        putExtra("image", model.image)
                    }
                    startActivity(intent)
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.row, parent, false)
                return ViewHolder(view)
            }
        }
        firebaseRecyclerAdapter?.startListening()
        binding.appBarMain.contentMain.recyclerView.adapter = firebaseRecyclerAdapter
    }

    override fun onStart() {
        super.onStart()
        firebaseRecyclerAdapter?.startListening()
    }

    override fun onStop() {
        firebaseRecyclerAdapter?.stopListening()
        super.onStop()
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
        val sortOptions = arrayOf("Superheros", "Nature")
        MaterialAlertDialogBuilder(this)
            .setTitle("Categories")
            .setIcon(R.drawable.ic_action_category)
            .setItems(sortOptions) { _, i ->
                mRef = if (i == 0) {
                    firebaseDatabase?.getReference("Data")
                } else {
                    firebaseDatabase?.getReference("nature")
                }
                firebaseDataLoad()
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
            R.id.nav_rate_us -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = "market://details?id=com.sky.wallapp".toUri()
                }
                startActivity(Intent.createChooser(intent, "Launch Google Play"))
            }
            R.id.nav_contact -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("shubhamskyjnp@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Feedback: Wallpaper App")
                    type = "message/rfc822"
                }
                startActivity(Intent.createChooser(intent, "Send Email"))
            }
            R.id.nav_share -> shareApp()
            R.id.nav_home -> {
                mRef = firebaseDatabase?.getReference("random")
                firebaseDataLoad()
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
