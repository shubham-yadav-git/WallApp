package com.sky.wallapp;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.view.View;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;

import java.text.SimpleDateFormat;
import java.util.Locale;

import static android.Manifest.*;

public class ImageActivity extends AppCompatActivity {

    ImageView imageView;
    Button saveBtn, shareBtn, wallBtn;
    String titlev, imageUrl;

    public static final int WRITE_EXTERNAL_STORAGE_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        initPhotoError();

        //initialization
        saveBtn = findViewById(R.id.btn_save);
        shareBtn = findViewById(R.id.btn_share);
        wallBtn = findViewById(R.id.btn_wall);
        imageView = findViewById(R.id.image_view);
        //Actionbar
        ActionBar actionBar = getSupportActionBar();
        //ActionBar Title
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        titlev = getIntent().getStringExtra("title");
        imageUrl = getIntent().getStringExtra("image");


        //get data from intent
        //   byte [] bytes=getIntent().getByteArrayExtra("image");
        //       final Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        TextView tv_url = findViewById(R.id.title);
        //  Picasso.get().load(imageUrl).placeholder(R.drawable.load).into(imageView);
        Glide.with(this)
                .load(imageUrl)
                .into(imageView);
        //set data to views

        //

        //pass this data to new activity

        // imageView.setImageBitmap(bitmap);
        Animation animFadeIn = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.fade_in);
        imageView.startAnimation(animFadeIn);

        actionBar.setTitle(titlev);

        //get image from imageView as bitmap
        //   bmp =((BitmapDrawable)imageView.getDrawable()).getBitmap();
        //buttons actions
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //if OS is>=marshmallow we need runtime permission to save image
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                        String[] permission = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
                        //Show popup to grant permission
                        requestPermissions(permission, WRITE_EXTERNAL_STORAGE_CODE);
                    } else {
                        //if already permission granted
                        saveImage();
                    }

                } else {
                    //system os is<marshmallow
                    saveImage();
                    ;
                }
            }
        });

        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareImage();
            }
        });

        wallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ImageActivity.this, "Please wait setting wallpaper...", Toast.LENGTH_LONG).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    openWallDialog();
                }

            }
        });

    }

    private void openWallDialog() {


        Drawable mDrawable = imageView.getDrawable();
        final Bitmap mBitmap = ((BitmapDrawable) mDrawable).getBitmap();
        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());

        String[] wallOptions={"HomeScreen","LockScreen","HomeScreen And Lock Screen"};
        AlertDialog.Builder builder=new AlertDialog.Builder(ImageActivity.this);
        builder.setTitle("Choose Option")
                .setItems(wallOptions, new DialogInterface.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(i==0){
                    try {
                        wallpaperManager.setBitmap(mBitmap,null,true,WallpaperManager.FLAG_SYSTEM);
                        Toast.makeText(ImageActivity.this, "HomeScreen Set Successfully", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(ImageActivity.this, "Wall not set" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }else if(i==1){
                    try {
                        wallpaperManager.setBitmap(mBitmap,null,true,WallpaperManager.FLAG_LOCK);
                        Toast.makeText(ImageActivity.this, "LockScreen Set Successfully", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(ImageActivity.this, "Wall not set" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }else {
                    try {
                        wallpaperManager.setBitmap(mBitmap);
                        Toast.makeText(ImageActivity.this, "Wallpaper Set Successfully", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(ImageActivity.this, "Wall not set" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.show();


    }

    private void shareImage() {
        try {
            String s = titlev;

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_SUBJECT, s); //put the text
            intent.putExtra(Intent.EXTRA_TEXT, imageUrl);
            intent.setType(("text/plain"));
            startActivity(Intent.createChooser(intent, "Share by :"));

        } catch (Exception e) {
            Toast.makeText(this, "Unable to share because" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initPhotoError() {
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            builder.detectFileUriExposure();
        }
    }

    public void saveImage() {
        //time stamp, for image name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis());
        imageView.setDrawingCacheEnabled(true);
        Bitmap bitmap = imageView.getDrawingCache();
        //path to save external storage
        File path = Environment.getExternalStorageDirectory();
        //create folder name wallpaper
        File dir = new File(path + "/Wallapp/");
        dir.mkdirs();
        //image name
        String imageName = titlev + timeStamp + ".JPEG";
        File file = new File(dir, imageName);


        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();
            Toast.makeText(this, imageName + "saved to" + dir, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    //handle on back press goto previous activity
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE_CODE:
                //If request code is cancelled the result array are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveImage();
                } else {
                    Toast.makeText(this, "Enable Permission to save Image", Toast.LENGTH_LONG).show();
                }
        }
    }
}
