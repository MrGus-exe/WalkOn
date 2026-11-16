package com.example.walkon

import com.example.walkon.model.GpsPoint
import com.example.walkon.utils.GpsFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsFilterTest {

    private val inicio = GpsPoint(lat = 0.0, lng = 0.0, accuracy = 5f, time = 0L)

    @Test
    fun primeiro_ponto_preciso_entra() {
        assertTrue(GpsFilter.shouldAdd(null, inicio))
    }

    @Test
    fun ponto_impreciso_e_ignorado() {
        val ruim = inicio.copy(accuracy = 40f) // margem de erro de 40 m
        assertFalse(GpsFilter.shouldAdd(null, ruim))
    }

    @Test
    fun tremida_menor_que_3_metros_e_ignorada() {
        val tremida = GpsPoint(0.00001, 0.0, 5f, 2_000L) // ~1 m
        assertFalse(GpsFilter.shouldAdd(inicio, tremida))
    }

    @Test
    fun caminhada_normal_entra() {
        val andou = GpsPoint(0.00005, 0.0, 5f, 4_000L) // ~5,5 m em 4 s (≈5 km/h)
        assertTrue(GpsFilter.shouldAdd(inicio, andou))
    }

    @Test
    fun pulo_rapido_demais_e_ignorado() {
        val pulo = GpsPoint(0.0003, 0.0, 5f, 2_000L) // ~33 m em 2 s (≈60 km/h)
        assertFalse(GpsFilter.shouldAdd(inicio, pulo))
    }

    @Test
    fun suaviza_com_a_media_dos_ultimos_3_pontos() {
        val pontos = listOf(
            GpsPoint(0.0, 0.0, 5f, 0L),     // fica de fora (só os 3 últimos contam)
            GpsPoint(1.0, 10.0, 5f, 1_000L),
            GpsPoint(2.0, 20.0, 5f, 2_000L),
            GpsPoint(6.0, 30.0, 5f, 3_000L)
        )
        val (lat, lng) = GpsFilter.smooth(pontos)
        assertEquals(3.0, lat, 0.0001)  // (1 + 2 + 6) / 3
        assertEquals(20.0, lng, 0.0001) // (10 + 20 + 30) / 3
    }
}
