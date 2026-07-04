package com.jhacode.chitrini.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.jhacode.chitrini.storage.AppwriteManager

class LandingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("LandingActivity", "🚀 Starting Chitrini...")

        // Fast Init
        AppwriteManager.init(applicationContext)

        val prefs: SharedPreferences = getSharedPreferences("chitrini_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", null)

        // 🔥 Navigate IMMEDIATELY to avoid hang
        if (username.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }

        finish()
    }
}
