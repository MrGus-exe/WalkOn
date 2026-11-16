package com.example.walkon

import com.example.walkon.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionTest {

    @Test
    fun angulos_do_norte() {
        assertEquals(Direction.NORTE, Direction.fromAzimuth(0f))
        assertEquals(Direction.NORTE, Direction.fromAzimuth(350f))
        assertEquals(Direction.NORTE, Direction.fromAzimuth(30f))
    }

    @Test
    fun angulos_de_leste_sul_e_oeste() {
        assertEquals(Direction.LESTE, Direction.fromAzimuth(90f))
        assertEquals(Direction.SUL, Direction.fromAzimuth(180f))
        assertEquals(Direction.OESTE, Direction.fromAzimuth(270f))
    }

    @Test
    fun angulo_negativo_e_maior_que_360() {
        assertEquals(Direction.OESTE, Direction.fromAzimuth(-90f))
        assertEquals(Direction.LESTE, Direction.fromAzimuth(450f))
    }
}
