package com.example.walkon.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.example.walkon.model.GpsPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// Recebe a posição do GPS a cada 1 segundo.
// Usa o serviço de localização do Google; se o celular não tiver
// (ex.: alguns Huawei), usa o GPS do próprio Android (LocationManager).
class LocationTracker(
    context: Context,
    private val onLocation: (GpsPoint) -> Unit
) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var started = false

    // Callback do serviço do Google
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onLocation(it.toPoint()) }
        }
    }

    // Callback do GPS do Android (plano B)
    private val androidListener = LocationListener { onLocation(it.toPoint()) }

    private fun Location.toPoint() = GpsPoint(latitude, longitude, accuracy, time)

    @SuppressLint("MissingPermission") // a permissão é checada na MainActivity
    fun start() {
        if (started) return
        started = true

        // 1) Mostra logo a última posição conhecida (aparece na hora).
        client.lastLocation.addOnSuccessListener { it?.let { loc -> onLocation(loc.toPoint()) } }

        // 2) Pede atualizações a cada 1 segundo (rota mais detalhada).
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(true) // espera o GPS "firmar" antes da 1ª leitura
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { startAndroidGps() } // sem Google Play: usa o plano B
    }

    @SuppressLint("MissingPermission")
    private fun startAndroidGps() {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(provider, 1000L, 0f, androidListener, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        started = false
        client.removeLocationUpdates(callback)
        locationManager.removeUpdates(androidListener)
    }
}
