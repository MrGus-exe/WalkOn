package com.example.walkon.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

// Sensor de passos com estratégia resiliente:
// 1) Sensor.TYPE_STEP_DETECTOR: dispara a cada passo (ideal se disponível).
// 2) Sensor.TYPE_STEP_COUNTER: acumulador de passos desde o boot (calcula a diferença).
// 3) Sensor.TYPE_ACCELEROMETER: detector de passos por pico de aceleração (garante
//    funcionamento em 100% dos aparelhos e emuladores que não têm sensor dedicado).
class StepSensor(context: Context, private val onStep: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepDetector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounter: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Base de passos para quando usar o STEP_COUNTER
    private var counterBaseValue = -1

    // Variáveis para o detector por ACELERÔMETRO (pico de aceleração)
    private var lastStepTimeNs = 0L
    private var isAccelerating = false

    val isAvailable: Boolean
        get() = stepDetector != null || stepCounter != null || accelerometer != null

    val sensorName: String
        get() = when {
            stepDetector != null -> "Step Detector (Hardware)"
            stepCounter != null -> "Step Counter (Hardware)"
            accelerometer != null -> "Acelerômetro (Software)"
            else -> "Indisponível"
        }

    fun start() {
        counterBaseValue = -1
        lastStepTimeNs = 0L
        isAccelerating = false

        // 1. Tenta STEP_DETECTOR
        val registeredDetector = stepDetector != null &&
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)

        // 2. Fallback: STEP_COUNTER se o detector não registrou
        val registeredCounter = !registeredDetector && stepCounter != null &&
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)

        // 3. Fallback universal: ACELERÔMETRO (funciona em emuladores e aparelhos sem sensor dedicado)
        if (!registeredDetector && !registeredCounter && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        counterBaseValue = -1
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                if (event.values.isNotEmpty() && event.values[0] >= 1.0f) {
                    onStep()
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val currentTotal = event.values[0].toInt()
                if (counterBaseValue < 0) {
                    counterBaseValue = currentTotal
                } else if (currentTotal > counterBaseValue) {
                    val diff = currentTotal - counterBaseValue
                    counterBaseValue = currentTotal
                    repeat(diff) { onStep() }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                // Gravidade padrão na Terra ~ 9.8 m/s²
                // Durante a passada, a aceleração sobe acima de ~11.8 m/s² e cai abaixo de 10.0 m/s².
                // Intervalo mínimo de 280ms entre passos para evitar falsos positivos de vibração.
                val nowNs = event.timestamp
                val minIntervalNs = 280_000_000L

                if (magnitude > 11.8f && !isAccelerating && (nowNs - lastStepTimeNs > minIntervalNs)) {
                    isAccelerating = true
                    lastStepTimeNs = nowNs
                    onStep()
                } else if (magnitude < 10.0f) {
                    isAccelerating = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
