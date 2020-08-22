package com.sky.wallapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;


public class splashscreen extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splashscreen);
        ImageView imageView=findViewById(R.id.imageView2);
        Animation animation= AnimationUtils.loadAnimation(getApplicationContext(),R.anim.zoom_in);
        imageView.startAnimation(animation);

        int secondsDelayed = 1;
        new Handler().postDelayed(new Runnable() {
            public void run() {
                startActivity(new Intent(splashscreen.this, MainActivity.class));
                overridePendingTransition(R.anim.slide_right,R.anim.slide_left);
                finish();
            }
        }, secondsDelayed * 1500);
    }
}