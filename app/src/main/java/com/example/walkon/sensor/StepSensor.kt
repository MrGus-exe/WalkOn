package com.example.walkon.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

// Usa o sensor de passos do celular (TYPE_STEP_DETECTOR).
// Cada evento do sensor = 1 passo dado.
class StepSensor(context: Context, private val onStep: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    val isAvailable: Boolean get() = stepSensor != null

    fun start() {
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) onStep()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
