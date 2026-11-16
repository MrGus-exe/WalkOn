package com.example.walkon

import com.example.walkon.data.database.WalkDao
import com.example.walkon.data.repository.WalkRepository
import com.example.walkon.model.GpsPoint
import com.example.walkon.model.Walk
import com.example.walkon.viewmodel.WalkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// DAO falso, em memória, para testar sem banco de verdade.
class FakeWalkDao : WalkDao {
    val data = MutableStateFlow<List<Walk>>(emptyList())
    override suspend fun insert(walk: Walk) { data.value = data.value + walk }
    override suspend fun delete(walk: Walk) { data.value = data.value - walk }
    override fun getAll(): Flow<List<Walk>> = data
}

@OptIn(ExperimentalCoroutinesApi::class)
class WalkViewModelTest {

    private lateinit var dao: FakeWalkDao
    private lateinit var viewModel: WalkViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeWalkDao()
        viewModel = WalkViewModel(WalkRepository(dao))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun passos_nao_contam_antes_de_iniciar() {
        viewModel.onStep()
        assertEquals(0, viewModel.state.value.steps)
    }

    @Test
    fun conta_passos_depois_de_iniciar() {
        viewModel.start()
        viewModel.onStep()
        viewModel.onStep()
        assertTrue(viewModel.state.value.isTracking)
        assertEquals(2, viewModel.state.value.steps)
    }

    @Test
    fun soma_distancia_entre_pontos_gps() {
        viewModel.start()
        // Anda em linha reta ~11 m a cada 10 s
        for (i in 0..3) {
            viewModel.onLocation(GpsPoint(0.0001 * i, 0.0, accuracy = 5f, time = i * 10_000L))
        }
        // Com a média dos 3 últimos, a rota termina em 0,0002 (média de 1, 2 e 3): ~22 m
        assertEquals(22.2, viewModel.state.value.distanceMeters, 0.5)
        assertEquals(4, viewModel.state.value.route.size)
    }

    @Test
    fun ignora_pulo_do_gps_para_o_outro_lado_da_rua() {
        viewModel.start()
        viewModel.onLocation(GpsPoint(0.0, 0.0, accuracy = 5f, time = 0L))
        // 2 segundos depois o GPS "pula" 33 m (seria 60 km/h andando): ignora
        viewModel.onLocation(GpsPoint(0.0003, 0.0, accuracy = 5f, time = 2_000L))
        assertEquals(0.0, viewModel.state.value.distanceMeters, 0.01)
        assertEquals(1, viewModel.state.value.route.size)
    }

    @Test
    fun parar_salva_caminhada_no_banco() {
        viewModel.start(now = 0L)
        viewModel.onStep()
        viewModel.stop(now = 60_000L)

        assertFalse(viewModel.state.value.isTracking)
        assertEquals(1, dao.data.value.size)
        val saved = dao.data.value.first()
        assertEquals(1, saved.steps)
        assertEquals(60L, saved.durationSeconds)
    }

    @Test
    fun parar_sem_iniciar_nao_salva_nada() {
        viewModel.stop()
        assertTrue(dao.data.value.isEmpty())
    }
}
