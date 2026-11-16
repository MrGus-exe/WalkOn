package com.example.walkon.model

// Uma leitura do GPS.
data class GpsPoint(
    val lat: Double,
    val lng: Double,
    val accuracy: Float, // margem de erro em metros (quanto menor, melhor)
    val time: Long       // quando foi lida (milissegundos)
)
