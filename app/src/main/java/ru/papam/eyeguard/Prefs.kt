package ru.papam.eyeguard

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("eyeguard", Context.MODE_PRIVATE)

    var thresholdCm: Int
        get() = sp.getInt(KEY_THRESHOLD, 30)
        set(v) = sp.edit().putInt(KEY_THRESHOLD, v).apply()

    var delaySeconds: Int
        get() = sp.getInt(KEY_DELAY, 3)
        set(v) = sp.edit().putInt(KEY_DELAY, v).apply()

    // Distance (cm) used during calibration and the normalized eye-distance
    // measured at that moment. distance = calibDistance * calibE / currentE
    var calibDistanceCm: Int
        get() = sp.getInt(KEY_CALIB_DIST, 30)
        set(v) = sp.edit().putInt(KEY_CALIB_DIST, v).apply()

    var calibE: Float
        get() = sp.getFloat(KEY_CALIB_E, 0.11f)
        set(v) = sp.edit().putFloat(KEY_CALIB_E, v).apply()

    var monitoringEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    companion object {
        private const val KEY_THRESHOLD = "threshold_cm"
        private const val KEY_DELAY = "delay_seconds"
        private const val KEY_CALIB_DIST = "calib_distance_cm"
        private const val KEY_CALIB_E = "calib_e"
        private const val KEY_ENABLED = "monitoring_enabled"
    }
}
