package com.example.walkon

import com.example.walkon.utils.WalkUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkUtilsTest {

    @Test
    fun distancia_entre_mesmo_ponto_e_zero() {
        assertEquals(0.0, WalkUtils.distanceBetween(-23.5, -46.6, -23.5, -46.6), 0.001)
    }

    @Test
    fun distancia_de_um_grau_de_latitude_e_aprox_111km() {
        val d = WalkUtils.distanceBetween(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun formata_distancia_em_metros_e_km() {
        assertEquals("320 m", WalkUtils.formatDistance(320.4))
        assertEquals("1.50 km", WalkUtils.formatDistance(1500.0))
    }

    @Test
    fun formata_duracao() {
        assertEquals("01:02:05", WalkUtils.formatDuration(3725))
        assertEquals("00:00:00", WalkUtils.formatDuration(0))
    }
}
