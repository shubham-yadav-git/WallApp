package com.sky.wallapp

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.MenuItemCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.sky.wallapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private var firebaseDatabase: FirebaseDatabase? = null
    private var mRef: DatabaseReference? = null
    private var firebaseRecyclerAdapter: FirebaseRecyclerAdapter<Model, ViewHolder>? = null
    private var staggeredGridLayoutManager: StaggeredGridLayoutManager? = null

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
    }

    private fun firebaseDataLoad() {
        staggeredGridLayoutManager = StaggeredGridLayoutManager(3, LinearLayoutManager.VERTICAL)
        binding.appBarMain.contentMain.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = staggeredGridLayoutManager
        }

        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(mRef!!, Model::class.java)
            .build()

        firebaseRecyclerAdapter = object : FirebaseRecyclerAdapter<Model, ViewHolder>(options) {
            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Model) {
                val url = model.image
                Glide.with(this@MainActivity)
                    .load(url)
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
        staggeredGridLayoutManager = StaggeredGridLayoutManager(3, LinearLayoutManager.VERTICAL)
        binding.appBarMain.contentMain.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = staggeredGridLayoutManager
        }

        val query = searchText.lowercase()
        val firebaseSearchQuery = mRef?.orderByChild("search")?.startAt(query)?.endAt(query + "\uf8ff")

        val options = FirebaseRecyclerOptions.Builder<Model>()
            .setQuery(firebaseSearchQuery!!, Model::class.java)
            .build()

        firebaseRecyclerAdapter = object : FirebaseRecyclerAdapter<Model, ViewHolder>(options) {
            override fun onBindViewHolder(holder: ViewHolder, position: Int, model: Model) {
                val url = model.image
                Glide.with(this@MainActivity)
                    .load(url)
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
        firebaseDataLoad()
        firebaseRecyclerAdapter?.stopListening()
    }

    override fun onResume() {
        super.onResume()
        firebaseDataLoad()
        firebaseRecyclerAdapter?.startListening()
    }

    override fun onStop() {
        firebaseRecyclerAdapter?.stopListening()
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        val menuItem = menu.findItem(R.id.action_search)
        val searchView = MenuItemCompat.getActionView(menuItem) as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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
        AlertDialog.Builder(this)
            .setTitle("Categories")
            .setIcon(R.drawable.ic_action_category)
            .setItems(sortOptions) { _, i ->
                mRef = if (i == 0) {
                    firebaseDatabase?.getReference("Data")
                } else {
                    firebaseDatabase?.getReference("nature")
                }
                onStart()
            }
            .show()
    }

    override fun onBackPressed() {
        showExitDialog()
    }

    private fun showExitDialog() {
        val sortOptions = arrayOf("Yes", "No")
        AlertDialog.Builder(this)
            .setTitle("Are you sure you want to exit the app?")
            .setIcon(R.drawable.ic_exit_to_app_black_24dp)
            .setItems(sortOptions) { _, i ->
                if (i == 0) finish()
            }
            .show()
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        var intent: Intent? = null
        var chooser: Intent? = null
        val id = menuItem.itemId

        when (id) {
            R.id.nav_rate_us -> {
                intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.sky.wallapp")
                }
                chooser = Intent.createChooser(intent, "Launch Google play")
                startActivity(chooser)
            }
            R.id.nav_contact -> {
                intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("shubhamskyjnp@gmail.com", "okappsn@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Review of App")
                    putExtra(Intent.EXTRA_TEXT, "Hi , \nyour app is ..")
                    type = "message/rfc822"
                }
                chooser = Intent.createChooser(intent, "Mail your review")
                startActivity(chooser)
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_SUBJECT, "Link to download")
            putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=com.sky.wallapp")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Share by :"))
    }
}
