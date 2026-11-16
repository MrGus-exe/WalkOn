package com.example.walkon.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

// Bússola: junta o acelerômetro com o magnetômetro para saber
// para onde o celular está apontando (ângulo de 0° a 360°).
class CompassSensor(context: Context, private val onAzimuth: (Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    fun start() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> event.values.copyInto(gravity)
            Sensor.TYPE_MAGNETIC_FIELD -> event.values.copyInto(geomagnetic)
        }
        val rotation = FloatArray(9)
        if (SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotation, orientation)
            // orientation[0] é o azimute em radianos; convertemos para graus.
            val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            onAzimuth((degrees + 360) % 360)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
