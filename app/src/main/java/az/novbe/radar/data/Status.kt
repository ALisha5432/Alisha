package az.novbe.radar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Radarın canlı vəziyyəti — UI bunu izləyir. */
data class RadarStatus(
    val serviceRunning: Boolean = false,
    val accessibilityOn: Boolean = false,
    val scanning: Boolean = false,
    val phase: String = "",
    val lastScanAt: Long = 0L,
    val lastScanOk: Boolean = false,
    val nextScanAt: Long = 0L,
    val lastError: String? = null,
    val totalScans: Int = 0,
) {
    /** Hər şey hazırdırmı? */
    fun healthy(hasInterval: Boolean): Boolean = serviceRunning && accessibilityOn && lastError == null
}

object Status {
    private val _flow = MutableStateFlow(RadarStatus())
    val flow: StateFlow<RadarStatus> = _flow
    val cur: RadarStatus get() = _flow.value

    fun update(f: (RadarStatus) -> RadarStatus) {
        _flow.value = f(_flow.value)
    }

    fun setScanning(on: Boolean, phase: String = "") = update { it.copy(scanning = on, phase = phase) }
}
