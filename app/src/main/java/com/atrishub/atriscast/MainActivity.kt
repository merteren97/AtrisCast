package com.atrishub.atriscast

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.atrishub.atriscast.receiver.ReceiverUiVisibility
import com.atrishub.atriscast.ui.AtrisCastApp
import com.atrishub.atriscast.ui.AtrisCastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWakeAndLockFlags()
        setContent {
            AtrisCastTheme {
                AtrisCastApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ReceiverUiVisibility.setVisible(true)
    }

    override fun onStop() {
        ReceiverUiVisibility.setVisible(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun applyWakeAndLockFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    companion object {
        const val ACTION_SHOW_MIRROR = "com.atrishub.atriscast.action.SHOW_MIRROR"
    }
}
