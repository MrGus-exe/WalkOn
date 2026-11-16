package com.example.walkon.model

// As 4 direções da bússola.
enum class Direction(val label: String) {
    NORTE("Norte"),
    LESTE("Leste"),
    SUL("Sul"),
    OESTE("Oeste");

    companion object {
        // Converte o ângulo da bússola (0° a 360°) em uma direção.
        // 0° = Norte, 90° = Leste, 180° = Sul, 270° = Oeste.
        fun fromAzimuth(azimuth: Float): Direction {
            val angle = ((azimuth % 360) + 360) % 360 // garante 0..360 (tira negativos)
            return when {
                angle >= 315 || angle < 45 -> NORTE
                angle < 135 -> LESTE
                angle < 225 -> SUL
                else -> OESTE
            }
        }
    }
}
