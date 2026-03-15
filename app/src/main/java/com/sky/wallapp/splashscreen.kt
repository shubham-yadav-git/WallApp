package com.sky.wallapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView

@SuppressLint("CustomSplashScreen")
class splashscreen : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashscreen)
        
        val imageView = findViewById<ImageView>(R.id.imageView2)
        val animation = AnimationUtils.loadAnimation(applicationContext, R.anim.zoom_in)
        imageView.startAnimation(animation)

        val secondsDelayed = 1
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@splashscreen, MainActivity::class.java))
            overridePendingTransition(R.anim.slide_right, R.anim.slide_left)
            finish()
        }, (secondsDelayed * 1500).toLong())
    }
}
