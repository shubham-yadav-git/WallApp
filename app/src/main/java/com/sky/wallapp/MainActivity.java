package com.sky.wallapp;

import androidx.annotation.NonNull;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.SearchView;


import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener{

    RecyclerView recyclerView;
    FirebaseDatabase firebaseDatabase;
    DatabaseReference mRef;
  //  ProgressBar progressBar;

    FirebaseRecyclerOptions<Model> firebaseRecyclerOptions;
    FirebaseRecyclerAdapter firebaseRecyclerAdapter;
    StaggeredGridLayoutManager staggeredGridLayoutManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        //send query to firebase instance
        firebaseDatabase = FirebaseDatabase.getInstance();
        mRef = firebaseDatabase.getReference("random");
    }

    //search data
    private void firebaseDataLoad() {


        // Staggered Grid layout manager
        staggeredGridLayoutManager= new StaggeredGridLayoutManager(3, LinearLayoutManager.VERTICAL);

        //RecyclerView

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(staggeredGridLayoutManager);
        firebaseRecyclerOptions= new FirebaseRecyclerOptions.Builder<Model>().setQuery(mRef,Model.class).build();
        FirebaseRecyclerAdapter<Model, ViewHolder> firebaseRecyclerAdapter =new FirebaseRecyclerAdapter<Model, ViewHolder>(firebaseRecyclerOptions) {
            @Override
            protected void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull final Model model) {
                String URL=model.getImage();
                Glide.with(MainActivity.this)
                        .load(URL)
                        // .placeholder(R.raw.gif.placeholder_circle)
                        .thumbnail(Glide.with(MainActivity.this).load(R.drawable.load))
                        .error(R.mipmap.ic_launcher_round)
                        .transition(DrawableTransitionOptions.withCrossFade(1000))
                        .into(holder.imageView);

                        holder.textView.setText(model.getTitle());
                        holder.cardViewParent.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(MainActivity.this,ImageActivity.class);
                        intent.putExtra("title",model.getTitle());
                        intent.putExtra("image", model.getImage());
                        startActivity(intent);
                    }
                });
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row,parent,false);
                return new ViewHolder(view);
            }
        };
        firebaseRecyclerAdapter.startListening();
        recyclerView.setAdapter(firebaseRecyclerAdapter);

    }

    //search data
    private void firebaseSearch(String searchText) {

        // Staggered Grid layout manager
        staggeredGridLayoutManager= new StaggeredGridLayoutManager(3, LinearLayoutManager.VERTICAL);

        //RecyclerView

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(staggeredGridLayoutManager);
        String query = searchText.toLowerCase();
        Query firebaseSearchQuery = mRef.orderByChild("search").startAt(query).endAt(query + "\uf8ff");

        firebaseRecyclerOptions= new FirebaseRecyclerOptions.Builder<Model>().setQuery(firebaseSearchQuery,Model.class).build();
        FirebaseRecyclerAdapter<Model, ViewHolder> firebaseRecyclerAdapter =new FirebaseRecyclerAdapter<Model, ViewHolder>(firebaseRecyclerOptions) {
            @Override
            protected void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull final Model model) {
                String URL=model.getImage();
                Glide.with(MainActivity.this)
                        .load(URL)
                        // .placeholder(R.raw.gif.placeholder_circle)
                        .thumbnail(Glide.with(MainActivity.this).load(R.drawable.load))
                        .error(R.mipmap.ic_launcher_round)
                        .transition(DrawableTransitionOptions.withCrossFade(1000))
                        .into(holder.imageView);

                holder.textView.setText(model.getTitle());
                holder.cardViewParent.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(MainActivity.this,ImageActivity.class);
                        intent.putExtra("title",model.getTitle());
                        intent.putExtra("image", model.getImage());
                        startActivity(intent);
                    }
                });
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row,parent,false);
                return new ViewHolder(view);
            }
        };
        firebaseRecyclerAdapter.startListening();
        recyclerView.setAdapter(firebaseRecyclerAdapter);
    }



    @Override
    protected void onStart() {

        super.onStart();

        firebaseDataLoad();
        if(firebaseRecyclerAdapter!=null) {
            firebaseRecyclerAdapter.stopListening();
        }
    }

    @Override
    protected void onResume() {
        // recyclerView.setAdapter(firebaseRecyclerAdapter);
        super.onResume();
        firebaseDataLoad();
        if(firebaseRecyclerAdapter!=null){
            firebaseRecyclerAdapter.startListening();
        }
    }

    @Override
    protected void onStop() {

        if(firebaseRecyclerAdapter!=null){
            firebaseRecyclerAdapter.stopListening();
        }
        super.onStop();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //inflate the menu this add the menu to action bar if it is present
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem menuItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menuItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                firebaseSearch(s);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                firebaseSearch(newText);
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.categories) {
            showCategoriesDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCategoriesDialog() {
        //options to display in dialog
        String[] sortOptions = {"Superheros", "Nature"};
        //create alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Categories").setIcon(R.drawable.ic_action_category) //set icon
                .setItems(sortOptions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        if (i == 0) {
                            mRef = firebaseDatabase.getReference("Data");

                        } else if (i == 1) {
                            mRef = firebaseDatabase.getReference("nature");
                        }
                        onStart();
                    }
                });
        builder.show();
    }


    @Override
    public void onBackPressed() {
        showExitDialog();
    }



    private void showExitDialog() {
        //options to display in dialog
        String[] sortOptions = {"Yes", "No"};
        //create alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Are you sure you want to exit the app?").setIcon(R.drawable.ic_exit_to_app_black_24dp) //set icon
                .setItems(sortOptions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // 0 means newest 1 means oldest
                        if (i == 0) {
                            finish();
                        } else if (i == 1) {

                        }
                    }
                });
        builder.show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        Intent intent = null, chooser = null;
        int id = menuItem.getItemId();

        if (id == R.id.nav_rate_us) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=com.sky.wallapp"));
            chooser = Intent.createChooser(intent, "Launch Google play");
            startActivity(chooser);
        } else if (id == R.id.nav_contact) {
            intent=new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_EMAIL,new String []{"shubhamskyjnp@gmail.com","okappsn@gmail.com"});
            intent.putExtra(Intent.EXTRA_SUBJECT,"Review of App");
            intent.putExtra(Intent.EXTRA_TEXT,"Hi , \nyour app is ..");
            intent.setType("message/rfc822");
            chooser=Intent.createChooser(intent,"Mail your review");
            startActivity(chooser);
        } else if(id == R.id.nav_share){
            shareApp();
        }else if(id == R.id.nav_home){
            mRef=firebaseDatabase.getReference("random");
            firebaseDataLoad();
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void shareApp() {
        Intent intent =new Intent(Intent.ACTION_SEND);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_SUBJECT,"Link to download"); //put the text
        intent.putExtra(Intent.EXTRA_TEXT,"https://play.google.com/store/apps/details?id=com.sky.wallapp" );
        intent.setType(("text/plain"));
        startActivity(Intent.createChooser(intent,"Share by :"));
    }

}
