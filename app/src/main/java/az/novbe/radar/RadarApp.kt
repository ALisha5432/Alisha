package az.novbe.radar

import android.app.Application
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.radar.Notifier

class RadarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Repo.init(this)
        Notifier.ensureChannels(this)
    }
}
