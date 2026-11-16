package com.example.walkon.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Tabela "walks" do banco: cada linha é uma caminhada salva.
@Entity(tableName = "walks")
data class Walk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,            // momento em que a caminhada começou (milissegundos)
    val steps: Int,            // passos contados pelo sensor
    val distanceMeters: Double,// distância calculada pelo GPS
    val durationSeconds: Long  // quanto tempo durou
)
