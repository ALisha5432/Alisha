package com.wpradar.service

import kotlinx.coroutines.flow.MutableStateFlow

/** Ana ekranda göstərmək üçün dinləyicinin canlı vəziyyəti (proses daxilində). */
object ListenerStatus {
    val connected = MutableStateFlow(false)
    val lastWhatsAppEventAt = MutableStateFlow(0L)
}
