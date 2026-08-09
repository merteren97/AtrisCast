package com.atrishub.atriscast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.atrishub.atriscast.ui.AtrisCastApp
import com.atrishub.atriscast.ui.AtrisCastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtrisCastTheme {
                AtrisCastApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val ACTION_SHOW_MIRROR = "com.atrishub.atriscast.action.SHOW_MIRROR"
    }
}
