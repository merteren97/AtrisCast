package com.atrishub.atriscast

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
}
