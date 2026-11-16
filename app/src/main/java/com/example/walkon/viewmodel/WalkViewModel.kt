package com.example.walkon.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.walkon.data.repository.WalkRepository
import com.example.walkon.model.Direction
import com.example.walkon.model.GpsPoint
import com.example.walkon.model.Walk
import com.example.walkon.utils.GpsFilter
import com.example.walkon.utils.WalkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Estado da caminhada atual que a tela mostra.
data class TrackingState(
    val isTracking: Boolean = false,
    val steps: Int = 0,
    val distanceMeters: Double = 0.0,
    val startTime: Long = 0L,
    val route: List<Pair<Double, Double>> = emptyList() // (latitude, longitude)
)

// ViewModel: regras da caminhada. Não conhece a tela, só o estado.
class WalkViewModel(private val repository: WalkRepository) : ViewModel() {

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    // Direção da bússola (Norte, Sul, Leste, Oeste).
    private val _direction = MutableStateFlow(Direction.NORTE)
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    fun onAzimuth(azimuth: Float) {
        _direction.value = Direction.fromAzimuth(azimuth)
    }

    // Histórico vindo do banco.
    val walks: StateFlow<List<Walk>> = repository.walks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start(now: Long = System.currentTimeMillis()) {
        _state.value = TrackingState(isTracking = true, startTime = now)
        goodPoints.clear()
    }

    fun onStep() {
        val s = _state.value
        if (s.isTracking) _state.value = s.copy(steps = s.steps + 1)
    }

    // Últimas leituras boas do GPS (usadas no filtro e na média).
    private val goodPoints = mutableListOf<GpsPoint>()

    fun onLocation(point: GpsPoint) {
        val s = _state.value
        if (!s.isTracking) return
        // 1) Filtro: ignora leitura imprecisa, tremida ou pulo.
        if (!GpsFilter.shouldAdd(goodPoints.lastOrNull(), point)) return
        goodPoints += point

        // 2) Suavização: o ponto da rota é a média das últimas leituras boas.
        val smoothed = GpsFilter.smooth(goodPoints)
        val previous = s.route.lastOrNull()
        val extra = if (previous == null) 0.0
                    else WalkUtils.distanceBetween(previous.first, previous.second, smoothed.first, smoothed.second)
        _state.value = s.copy(
            route = s.route + smoothed,
            distanceMeters = s.distanceMeters + extra
        )
    }

    // Para a caminhada e salva no banco.
    fun stop(now: Long = System.currentTimeMillis()) {
        val s = _state.value
        if (!s.isTracking) return
        val walk = Walk(
            date = s.startTime,
            steps = s.steps,
            distanceMeters = s.distanceMeters,
            durationSeconds = (now - s.startTime) / 1000
        )
        _state.value = s.copy(isTracking = false)
        viewModelScope.launch { repository.save(walk) }
    }

    fun delete(walk: Walk) {
        viewModelScope.launch { repository.delete(walk) }
    }

    // Fábrica para criar o ViewModel passando o repositório.
    class Factory(private val repository: WalkRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WalkViewModel(repository) as T
    }
}
