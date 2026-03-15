package com.sky.wallapp

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sky.wallapp.databinding.ActivityImageBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageBinding
    private var titlev: String? = null
    private var imageUrl: String? = null

    companion object {
        const val WRITE_EXTERNAL_STORAGE_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initPhotoError()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        titlev = intent.getStringExtra("title")
        imageUrl = intent.getStringExtra("image")

        Glide.with(this)
            .load(imageUrl)
            .into(binding.imageView)

        val animFadeIn = AnimationUtils.loadAnimation(applicationContext, R.anim.fade_in)
        binding.imageView.startAnimation(animFadeIn)

        binding.btnSave.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), WRITE_EXTERNAL_STORAGE_CODE)
                } else {
                    saveImage()
                }
            } else {
                saveImage()
            }
        }

        binding.btnShare.setOnClickListener {
            shareImage()
        }

        binding.btnWall.setOnClickListener {
            openWallDialog()
        }
    }

    private fun openWallDialog() {
        val mDrawable = binding.imageView.drawable
        if (mDrawable !is BitmapDrawable) {
            Toast.makeText(this, "Image not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        val mBitmap = mDrawable.bitmap
        val wallpaperManager = WallpaperManager.getInstance(applicationContext)

        val wallOptions = arrayOf("Home Screen", "Lock Screen", "Both")
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Wallpaper")
            .setItems(wallOptions) { _, i ->
                Toast.makeText(this@ImageActivity, "Setting wallpaper...", Toast.LENGTH_SHORT).show()
                try {
                    when (i) {
                        0 -> {
                            wallpaperManager.setBitmap(mBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                            Toast.makeText(this, "Home Screen set successfully", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            wallpaperManager.setBitmap(mBitmap, null, true, WallpaperManager.FLAG_LOCK)
                            Toast.makeText(this, "Lock Screen set successfully", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            wallpaperManager.setBitmap(mBitmap)
                            Toast.makeText(this, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun shareImage() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_SUBJECT, titlev)
                putExtra(Intent.EXTRA_TEXT, "Check out this wallpaper: $imageUrl")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initPhotoError() {
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        builder.detectFileUriExposure()
    }

    private fun saveImage() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        
        val mDrawable = binding.imageView.drawable
        if (mDrawable !is BitmapDrawable) {
            Toast.makeText(this, "Image not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = mDrawable.bitmap
        
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(path, "WallApp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        val fileName = "${titlev?.replace(" ", "_")}_$timeStamp.jpg"
        val file = File(dir, fileName)

        try {
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            Toast.makeText(this, "Saved to $dir", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_EXTERNAL_STORAGE_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImage()
            } else {
                Toast.makeText(this, "Permission required to save", Toast.LENGTH_LONG).show()
            }
        }
    }
}
