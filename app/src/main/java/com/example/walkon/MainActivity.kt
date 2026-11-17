package com.example.walkon

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.walkon.data.database.AppDatabase
import com.example.walkon.data.repository.WalkRepository
import com.example.walkon.databinding.ActivityMainBinding
import com.example.walkon.databinding.ItemWalkBinding
import com.example.walkon.location.LocationTracker
import com.example.walkon.model.Direction
import com.example.walkon.model.GpsPoint
import com.example.walkon.model.Walk
import com.example.walkon.sensor.CompassSensor
import com.example.walkon.sensor.StepSensor
import com.example.walkon.utils.GpsFilter
import com.example.walkon.utils.WalkUtils
import com.example.walkon.viewmodel.TrackingState
import com.example.walkon.viewmodel.WalkViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// View: só mostra dados e repassa ações do usuário para o ViewModel.
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var stepSensor: StepSensor
    private lateinit var locationTracker: LocationTracker
    private lateinit var compassSensor: CompassSensor
    private var map: MapLibreMap? = null

    private val viewModel: WalkViewModel by viewModels {
        WalkViewModel.Factory(WalkRepository(AppDatabase.getInstance(this).walkDao()))
    }

    // Se true, começa a caminhada logo depois que a pessoa aceitar as permissões.
    private var startAfterPermission = false

    // Cores atuais do app (mudam conforme a direção da bússola).
    private var mainColor = 0   // cor forte: topo e botão
    private var lightColor = 0  // cor clara: passos, rota e destaques
    private var lastWalks: List<Walk> = emptyList()

    // Pede as permissões de localização e de atividade física.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasLocationPermission()) {
                startGps()
                if (!hasPreciseLocation()) {
                    Toast.makeText(this, "Escolha \"Precisa\" na localização para a rota funcionar", Toast.LENGTH_LONG).show()
                }
                if (startAfterPermission) startWalk()
            } else {
                setGpsStatus("⚠️ Sem permissão de localização")
                Toast.makeText(this, "Permita a localização para o GPS funcionar", Toast.LENGTH_LONG).show()
            }
            startAfterPermission = false
        }

    // Resposta da janela "Ativar localização do celular".
    private val locationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) locationTracker.start()
            else setGpsStatus("⚠️ Localização do celular desligada")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this) // precisa vir antes de inflar o MapView
        binding = ActivityMainBinding.inflate(layoutInflater)
        // Barra de status transparente com ícones brancos (tema escuro).
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContentView(binding.root)
        // Afasta o conteúdo da barra de status e da barra de navegação.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // O verde do cabeçalho vai até atrás da barra de status.
            val dp18 = (18 * resources.displayMetrics.density).toInt()
            binding.header.setPadding(binding.header.paddingLeft, bars.top + dp18,
                binding.header.paddingRight, binding.header.paddingBottom)
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        // Sensor e GPS mandam os dados para o ViewModel.
        stepSensor = StepSensor(this) { viewModel.onStep() }
        locationTracker = LocationTracker(this) { point ->
            showGpsQuality(point)
            viewModel.onLocation(point) // o ViewModel filtra e só usa se a caminhada estiver ativa
            updateMyLocationOnMap(point)
        }
        compassSensor = CompassSensor(this) { azimuth -> viewModel.onAzimuth(azimuth) }

        setupMap(savedInstanceState)

        binding.btnStartStop.setOnClickListener {
            when {
                viewModel.state.value.isTracking -> stopWalk()
                hasPreciseLocation() && hasActivityRecognition() -> startWalk()
                else -> { startAfterPermission = true; askPermissions() }
            }
        }

        // Ao abrir o app: confere a internet e liga o GPS (ou pede permissão).
        if (!hasInternet()) {
            Toast.makeText(this, "Sem internet: o mapa não vai carregar", Toast.LENGTH_LONG).show()
        }
        if (hasLocationPermission()) startGps()
        if (!hasPreciseLocation() || !hasActivityRecognition()) askPermissions() // sem permissão completa: pede

        // Deixa arrastar o mapa sem a tela rolar junto.
        binding.mapView.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        // Observa o ViewModel e atualiza a tela.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.walks.collect { showHistory(it) } }
                launch { viewModel.direction.collect { showDirection(it) } }
            }
        }
    }

    // Aceita localização precisa OU aproximada.
    private fun hasLocationPermission() =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    // A rota só funciona bem com a localização PRECISA (GPS de verdade).
    private fun hasPreciseLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // Permissão para sensores de passos no Android 10+
    private fun hasActivityRecognition() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setGpsStatus(text: String) {
        binding.tvGps.text = text
    }

    // Mostra se o sinal está bom e a margem de erro (ex.: "±8 m").
    private fun showGpsQuality(point: GpsPoint) {
        val erro = "±${point.accuracy.toInt()} m"
        setGpsStatus(
            when {
                !hasPreciseLocation() -> "⚠️ GPS aproximado: ative o preciso"
                GpsFilter.isAccurate(point) -> "📍 GPS conectado ($erro)"
                else -> "📡 Sinal fraco ($erro), vá para céu aberto"
            }
        )
    }

    // Confere se a localização do celular está ligada; se não, mostra a janela
    // do Android pedindo para ligar. Depois liga o GPS do app.
    private fun startGps() {
        setGpsStatus(if (hasPreciseLocation()) "📡 Procurando GPS..." else "⚠️ Ative a localização precisa")
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build())
            .build()
        LocationServices.getSettingsClient(this).checkLocationSettings(request)
            .addOnSuccessListener { locationTracker.start() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    locationSettingsLauncher.launch(IntentSenderRequest.Builder(e.resolution).build())
                } else {
                    locationTracker.start() // tenta mesmo assim
                }
            }
    }

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms += Manifest.permission.ACTIVITY_RECOGNITION
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startWalk() {
        if (!stepSensor.isAvailable) {
            Toast.makeText(this, "Este aparelho não tem sensor de passos", Toast.LENGTH_LONG).show()
        }
        viewModel.start()
        stepSensor.start()
        binding.chronometer.base = SystemClock.elapsedRealtime()
        binding.chronometer.start()
    }

    private fun stopWalk() {
        viewModel.stop()
        stepSensor.stop()
        binding.chronometer.stop()
        Toast.makeText(this, "Caminhada salva!", Toast.LENGTH_SHORT).show()
    }

    private fun render(state: TrackingState) {
        binding.tvSteps.text = state.steps.toString()
        binding.tvDistance.text = WalkUtils.formatDistance(state.distanceMeters)
        binding.btnStartStop.text = if (state.isTracking) "■  Parar caminhada" else "▶  Iniciar caminhada"
        binding.tvGreeting.text = if (state.isTracking) "Caminhando..." else "Bora caminhar?"
        paintButton(state.isTracking)
        drawRoute(state.route)
    }

    // Troca as cores do app inteiro conforme a direção:
    // Norte = verde, Leste = azul, Sul = vermelho, Oeste = amarelo.
    private fun showDirection(direction: Direction) {
        val (forte, clara) = when (direction) {
            Direction.NORTE -> R.color.dir_norte to R.color.dir_norte_light
            Direction.LESTE -> R.color.dir_leste to R.color.dir_leste_light
            Direction.SUL -> R.color.dir_sul to R.color.dir_sul_light
            Direction.OESTE -> R.color.dir_oeste to R.color.dir_oeste_light
        }
        mainColor = ContextCompat.getColor(this, forte)
        lightColor = ContextCompat.getColor(this, clara)

        // Topo
        binding.headerBg.backgroundTintList = ColorStateList.valueOf(mainColor)

        // Pílula da bússola: fundo escuro com borda e texto na cor clara
        val density = resources.displayMetrics.density
        binding.tvDirection.background = GradientDrawable().apply {
            cornerRadius = 50 * density
            setColor(ContextCompat.getColor(this@MainActivity, R.color.bg_dark))
            setStroke((2 * density).toInt(), lightColor)
        }
        binding.tvDirection.setTextColor(lightColor)
        binding.tvDirection.text = direction.label

        // Passos, botão, linha da rota e histórico
        binding.tvSteps.setTextColor(lightColor)
        paintButton(viewModel.state.value.isTracking)
        map?.style?.getLayerAs<LineLayer>(ROUTE_LAYER)
            ?.setProperties(PropertyFactory.lineColor(lightColor))
        showHistory(lastWalks)
    }

    // Botão: cor forte para iniciar; cor clara (com texto escuro) para parar.
    private fun paintButton(isTracking: Boolean) {
        if (mainColor == 0) return
        val fundo = if (isTracking) lightColor else mainColor
        val texto = if (isTracking) ContextCompat.getColor(this, R.color.bg_dark) else Color.WHITE
        binding.btnStartStop.backgroundTintList = ColorStateList.valueOf(fundo)
        binding.btnStartStop.setTextColor(texto)
    }

    // Monta um card para cada caminhada salva.
    private fun showHistory(walks: List<Walk>) {
        lastWalks = walks
        val fmt = SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault())
        binding.historyContainer.removeAllViews()
        for (walk in walks) {
            val item = ItemWalkBinding.inflate(layoutInflater, binding.historyContainer, false)
            item.tvItemDate.text = fmt.format(Date(walk.date))
            item.tvItemInfo.text = "${WalkUtils.formatDistance(walk.distanceMeters)}  •  " +
                WalkUtils.formatDuration(walk.durationSeconds)
            item.tvItemSteps.text = walk.steps.toString()
            if (lightColor != 0) item.tvItemSteps.setTextColor(lightColor)
            item.root.setOnLongClickListener {
                viewModel.delete(walk)
                true
            }
            binding.historyContainer.addView(item.root)
        }
        binding.tvEmpty.text = if (walks.isEmpty()) "nenhuma ainda" else "segure para apagar"
    }

    // ---------- Mapa ----------

    private fun setupMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            // Mapa gratuito (OpenFreeMap), não precisa de chave de API.
            mapLibreMap.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark")) { style ->
                style.addSource(GeoJsonSource(ROUTE_SOURCE))
                style.addLayer(
                    LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                        PropertyFactory.lineColor(if (lightColor != 0) lightColor else Color.GREEN),
                        PropertyFactory.lineWidth(5f)
                    )
                )
                mapLibreMap.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(-15.79, -47.88)).zoom(3.0).build()
                drawRoute(viewModel.state.value.route)
                showMyLocation()
            }
        }
    }

    // Mostra a bolinha azul da localização da pessoa e faz o mapa seguir ela.
    @SuppressLint("MissingPermission") // só chamamos depois de checar a permissão
    private fun showMyLocation() {
        val map = map ?: return
        val style = map.style ?: return
        if (!hasLocationPermission()) return
        val location = map.locationComponent
        if (!location.isLocationComponentActivated) {
            // A posição vem do nosso LocationTracker (não do motor interno do mapa).
            location.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style)
                    .useDefaultLocationEngine(false)
                    .build()
            )
        }
        location.isLocationComponentEnabled = true
        location.cameraMode = CameraMode.TRACKING // câmera acompanha a pessoa
        location.renderMode = RenderMode.NORMAL
    }

    // Move a bolinha azul para a nova posição.
    // Usa o mesmo filtro da rota: ignora leituras imprecisas, tremidas e pulos
    // (menos a primeira leitura, para a bolinha aparecer logo).
    private var firstFix = true
    private var lastDotPoint: GpsPoint? = null
    private fun updateMyLocationOnMap(point: GpsPoint) {
        if (!firstFix && !GpsFilter.shouldAdd(lastDotPoint, point)) return
        lastDotPoint = point
        val component = map?.locationComponent ?: return
        if (!component.isLocationComponentActivated) showMyLocation()
        if (!component.isLocationComponentActivated) return // mapa ainda carregando
        component.forceLocationUpdate(Location("gps").apply {
            latitude = point.lat
            longitude = point.lng
            accuracy = point.accuracy // o mapa desenha o círculo da margem de erro
        })
        if (firstFix) { component.zoomWhileTracking(17.0); firstFix = false }
    }

    // Desenha o caminho percorrido.
    private fun drawRoute(route: List<Pair<Double, Double>>) {
        val style = map?.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return
        val points = route.map { Point.fromLngLat(it.second, it.first) }
        source.setGeoJson(LineString.fromLngLats(points))
    }

    // O MapView precisa receber os eventos do ciclo de vida.
    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        compassSensor.start()
        if (viewModel.state.value.isTracking) stepSensor.start()
    }
    override fun onPause() { super.onPause(); binding.mapView.onPause(); compassSensor.stop() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        stepSensor.stop()
        locationTracker.stop()
        binding.mapView.onDestroy()
        super.onDestroy()
    }

    companion object {
        private const val ROUTE_SOURCE = "route-source"
        private const val ROUTE_LAYER = "route-layer"
    }
}
