package ru.papam.eyeguard

/** Lightweight shared state between the monitoring service and the UI. */
object MonitorState {
    /** Most recent normalized eye separation, or null if no face is visible. */
    @Volatile
    var latestE: Float? = null

    /** Most recent estimated distance in cm, or null if no face is visible. */
    @Volatile
    var latestDistanceCm: Float? = null

    /** Whether the monitoring service is currently running. */
    @Volatile
    var running: Boolean = false
}
