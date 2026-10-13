package az.novbe.radar.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import az.novbe.radar.data.Prefs
import az.novbe.radar.radar.AlarmPlayer
import az.novbe.radar.radar.RadarService

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            RadarTheme {
                AppRoot(resumeTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        AlarmPlayer.stop()
        val s = Prefs.s
        if (s.setupDone && s.enabled) RadarService.ensureRunning(this)
    }
}
