package com.wpradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wpradar.service.ListenerStatus
import com.wpradar.system.SystemStatus
import com.wpradar.ui.MainScreen
import com.wpradar.ui.theme.WpRadarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WpRadarApp
        setContent {
            WpRadarTheme {
                MainScreen(app)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!ListenerStatus.connected.value) SystemStatus.requestRebind(this)
    }
}
