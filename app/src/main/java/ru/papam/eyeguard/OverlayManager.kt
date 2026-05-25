package ru.papam.eyeguard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

/**
 * Shows a full-screen warning overlay on top of every other app, blocking
 * interaction until the phone is moved far enough away.
 */
class OverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show() {
        main.post {
            if (view != null) return@post
            val type =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                // Cover everything and keep the screen on, but DO let the
                // overlay receive touches so the app underneath is blocked.
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START

            val v = LayoutInflater.from(context).inflate(R.layout.overlay_warning, null)
            // Swallow all touches so the blocked app cannot be used.
            v.setOnTouchListener { _, _ -> true }
            try {
                windowManager.addView(v, params)
                view = v
            } catch (_: Exception) {
            }
        }
    }

    fun hide() {
        main.post {
            val v = view ?: return@post
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
            view = null
        }
    }

    fun isShowing(): Boolean = view != null
}
