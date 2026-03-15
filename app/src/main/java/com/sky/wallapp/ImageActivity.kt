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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
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

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        titlev = intent.getStringExtra("title")
        imageUrl = intent.getStringExtra("image")

        Glide.with(this)
            .load(imageUrl)
            .into(binding.imageView)

        val animFadeIn = AnimationUtils.loadAnimation(applicationContext, R.anim.fade_in)
        binding.imageView.startAnimation(animFadeIn)

        supportActionBar?.title = titlev

        binding.btnSave.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
            Toast.makeText(this@ImageActivity, "Please wait setting wallpaper...", Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                openWallDialog()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun openWallDialog() {
        val mDrawable = binding.imageView.drawable
        if (mDrawable !is BitmapDrawable) return
        
        val mBitmap = mDrawable.bitmap
        val wallpaperManager = WallpaperManager.getInstance(applicationContext)

        val wallOptions = arrayOf("HomeScreen", "LockScreen", "HomeScreen And Lock Screen")
        AlertDialog.Builder(this)
            .setTitle("Choose Option")
            .setItems(wallOptions) { _, i ->
                try {
                    when (i) {
                        0 -> {
                            wallpaperManager.setBitmap(mBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                            Toast.makeText(this, "HomeScreen Set Successfully", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            wallpaperManager.setBitmap(mBitmap, null, true, WallpaperManager.FLAG_LOCK)
                            Toast.makeText(this, "LockScreen Set Successfully", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            wallpaperManager.setBitmap(mBitmap)
                            Toast.makeText(this, "Wallpaper Set Successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Wall not set: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun shareImage() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_SUBJECT, titlev)
                putExtra(Intent.EXTRA_TEXT, imageUrl)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(intent, "Share by :"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to share because ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initPhotoError() {
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        builder.detectFileUriExposure()
    }

    private fun saveImage() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        binding.imageView.isDrawingCacheEnabled = true
        val bitmap = binding.imageView.drawingCache
        
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(path, "Wallapp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        val imageName = "${titlev}_$timeStamp.JPEG"
        val file = File(dir, imageName)

        try {
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            Toast.makeText(this, "$imageName saved to $dir", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Enable Permission to save Image", Toast.LENGTH_LONG).show()
            }
        }
    }
}
