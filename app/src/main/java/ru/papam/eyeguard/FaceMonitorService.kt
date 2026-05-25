package ru.papam.eyeguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.ExperimentalGetImage
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceMonitorService : LifecycleService() {

    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayManager
    private lateinit var detector: FaceDetector
    private lateinit var analysisExecutor: ExecutorService

    private var smoothedE: Float? = null
    private var closeSinceMs: Long = 0L
    private var lastProcessMs: Long = 0L

    @ExperimentalGetImage
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        overlay = OverlayManager(this)
        analysisExecutor = Executors.newSingleThreadExecutor()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        detector = FaceDetection.getClient(options)

        startInForeground()
        startCamera()
        MonitorState.running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startInForeground() {
        val channelId = "eyeguard_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_eye)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @ExperimentalGetImage
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(480, 640))
                    .build()
                analysis.setAnalyzer(analysisExecutor, ::analyze)

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun analyze(imageProxy: ImageProxy) {
        // Throttle to ~5 fps to save battery.
        val now = System.currentTimeMillis()
        if (now - lastProcessMs < 200) {
            imageProxy.close()
            return
        }
        lastProcessMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val width = mediaImage.width
        val height = mediaImage.height

        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val left = face?.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val right = face?.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val e = DistanceEstimator.normalizedEyeSeparation(left, right, width, height)
                handleMeasurement(e)
            }
            .addOnFailureListener { handleMeasurement(null) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleMeasurement(rawE: Float?) {
        if (rawE == null) {
            smoothedE = null
            MonitorState.latestE = null
            MonitorState.latestDistanceCm = null
            closeSinceMs = 0L
            overlay.hide()
            return
        }

        // Exponential moving average to reduce jitter.
        val prev = smoothedE
        val e = if (prev == null) rawE else prev * 0.6f + rawE * 0.4f
        smoothedE = e
        MonitorState.latestE = e

        val distance = DistanceEstimator.distanceCm(e, prefs.calibDistanceCm, prefs.calibE)
        MonitorState.latestDistanceCm = distance

        val threshold = prefs.thresholdCm
        val delayMs = prefs.delaySeconds * 1000L
        val now = System.currentTimeMillis()

        if (distance < threshold) {
            if (closeSinceMs == 0L) closeSinceMs = now
            if (now - closeSinceMs >= delayMs) overlay.show()
        } else {
            closeSinceMs = 0L
            if (distance > threshold + HYSTERESIS_CM) overlay.hide()
        }
    }

    override fun onDestroy() {
        MonitorState.running = false
        overlay.hide()
        try {
            detector.close()
        } catch (_: Exception) {
        }
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FaceMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val HYSTERESIS_CM = 5

        fun start(context: Context) {
            val intent = Intent(context, FaceMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FaceMonitorService::class.java))
        }
    }
}
