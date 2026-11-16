package com.example.walkon.utils

import com.example.walkon.model.GpsPoint

// Filtro do GPS: decide se uma leitura é boa o suficiente para entrar na rota.
// O GPS do celular "pula" (perto de prédios pode errar 20-30 m), então
// jogamos fora as leituras ruins para a rota e a distância não ficarem erradas.
object GpsFilter {

    const val MAX_ACCURACY_METERS = 25f // margem de erro maior que isso = imprecisa
    const val MIN_MOVE_METERS = 3.0     // menos que isso = só "tremida" do GPS
    const val MAX_SPEED_MS = 7.0        // 7 m/s ≈ 25 km/h: ninguém caminha tão rápido

    // A leitura é precisa o suficiente?
    fun isAccurate(point: GpsPoint): Boolean = point.accuracy <= MAX_ACCURACY_METERS

    // A leitura deve entrar na rota?
    fun shouldAdd(last: GpsPoint?, new: GpsPoint): Boolean {
        if (!isAccurate(new)) return false          // 1) imprecisa
        if (last == null) return true               // primeiro ponto bom
        val meters = WalkUtils.distanceBetween(last.lat, last.lng, new.lat, new.lng)
        if (meters < MIN_MOVE_METERS) return false  // 2) tremida, não andou de verdade
        val seconds = (new.time - last.time) / 1000.0
        if (seconds <= 0) return false
        return meters / seconds <= MAX_SPEED_MS     // 3) pulo impossível
    }

    const val SMOOTH_POINTS = 3 // quantos pontos entram na média

    // Suaviza a rota: a posição é a média dos últimos pontos bons.
    // Assim a linha fica lisa, sem zigue-zague do GPS.
    fun smooth(recent: List<GpsPoint>): Pair<Double, Double> {
        val last = recent.takeLast(SMOOTH_POINTS)
        return last.map { it.lat }.average() to last.map { it.lng }.average()
    }
}
