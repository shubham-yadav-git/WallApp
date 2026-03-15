package com.sky.wallapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@SuppressLint("CustomSplashScreen")
class Splashscreen : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashscreen)
        
        val imageView = findViewById<ImageView>(R.id.imageView2)
        val animation = AnimationUtils.loadAnimation(applicationContext, R.anim.zoom_in)
        imageView.startAnimation(animation)

        val secondsDelayed = 1
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@Splashscreen, MainActivity::class.java))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.slide_right,
                    R.anim.slide_left
                )
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_right, R.anim.slide_left)
            }
            finish()
        }, (secondsDelayed * 1500).toLong())
    }
}
