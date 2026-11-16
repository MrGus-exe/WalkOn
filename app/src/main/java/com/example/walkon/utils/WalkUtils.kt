package com.example.walkon.utils

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Funções puras (sem Android), fáceis de testar.
object WalkUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    // Fórmula de Haversine: distância em metros entre dois pontos (lat/lng).
    fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    // 1500 m -> "1.50 km" ; 320 m -> "320 m"
    fun formatDistance(meters: Double): String =
        if (meters >= 1000) String.format(java.util.Locale.US, "%.2f km", meters / 1000)
        else "${meters.toInt()} m"

    // 3725 s -> "01:02:05"
    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
