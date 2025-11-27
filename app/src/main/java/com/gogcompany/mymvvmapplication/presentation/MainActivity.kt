package com.gogcompany.mymvvmapplication.presentation

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.gogcompany.mymvvmapplication.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
//class MainActivity : AppCompatActivity(R.layout.activity_main)
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // اینجا می‌تونی تنظیمات window رو ست کنی
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)
    }
}