package com.carlosvale.ytdownloader

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedUrl = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        setContent {
            GetMuviTheme {
                DownloaderScreen(initialUrl = sharedUrl.orEmpty())
            }
        }
    }
}
