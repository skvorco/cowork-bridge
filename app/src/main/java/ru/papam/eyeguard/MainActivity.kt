package ru.papam.eyeguard

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ru.papam.eyeguard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val ui = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            updateLiveReadout()
            ui.postDelayed(this, 300)
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { ensureOverlayPermission() }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateLiveReadout() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make sure the launcher icon is visible whenever settings are opened.
        setLauncherIconVisible(true)

        binding.etThreshold.setText(prefs.thresholdCm.toString())
        binding.etDelay.setText(prefs.delaySeconds.toString())
        binding.etCalibDistance.setText(prefs.calibDistanceCm.toString())

        binding.btnPermissions.setOnClickListener { requestPermissions() }

        binding.btnStart.setOnClickListener {
            saveSettings()
            if (!hasCameraPermission()) {
                requestPermissions(); return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                ensureOverlayPermission(); return@setOnClickListener
            }
            prefs.monitoringEnabled = true
            FaceMonitorService.start(this)
            toast(getString(R.string.toast_started))
        }

        binding.btnStop.setOnClickListener {
            prefs.monitoringEnabled = false
            FaceMonitorService.stop(this)
            toast(getString(R.string.toast_stopped))
        }

        binding.btnCalibrate.setOnClickListener { calibrate() }

        binding.btnHideIcon.setOnClickListener { confirmHideIcon() }
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refresh)
        saveSettings()
    }

    private fun saveSettings() {
        binding.etThreshold.text.toString().toIntOrNull()?.let { prefs.thresholdCm = it.coerceIn(15, 80) }
        binding.etDelay.text.toString().toIntOrNull()?.let { prefs.delaySeconds = it.coerceIn(1, 30) }
        binding.etCalibDistance.text.toString().toIntOrNull()?.let { prefs.calibDistanceCm = it.coerceIn(15, 80) }
    }

    private fun updateLiveReadout() {
        val running = MonitorState.running
        binding.tvStatus.text = getString(
            if (running) R.string.status_running else R.string.status_stopped
        )
        val d = MonitorState.latestDistanceCm
        binding.tvDistance.text = when {
            !running -> "—"
            d == null -> getString(R.string.no_face)
            d == Float.MAX_VALUE -> "—"
            else -> getString(R.string.distance_cm, d.toInt())
        }
    }

    private fun calibrate() {
        saveSettings()
        if (!MonitorState.running) {
            toast(getString(R.string.calib_need_running))
            return
        }
        val e = MonitorState.latestE
        if (e == null) {
            toast(getString(R.string.calib_no_face))
            return
        }
        prefs.calibE = e
        toast(getString(R.string.calib_done, prefs.calibDistanceCm))
    }

    private fun confirmHideIcon() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hide_icon_title)
            .setMessage(R.string.hide_icon_message)
            .setPositiveButton(R.string.hide_icon_confirm) { _, _ ->
                setLauncherIconVisible(false)
                toast(getString(R.string.hide_icon_done))
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setLauncherIconVisible(visible: Boolean) {
        val component = ComponentName(this, "ru.papam.eyeguard.LauncherAlias")
        val state = if (visible)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        packageManager.setComponentEnabledSetting(
            component, state, PackageManager.DONT_KILL_APP
        )
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(perms.toTypedArray())
    }

    private fun ensureOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
