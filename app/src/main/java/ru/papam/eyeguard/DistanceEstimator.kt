package ru.papam.eyeguard

import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Estimates the distance (in cm) from the device to the user's eyes.
 *
 * The estimate is based on the separation between the two eyes in the camera
 * frame, normalized by the image diagonal so it is independent of resolution
 * and frame rotation. The absolute scale comes from a one-time calibration:
 *
 *   distanceCm = calibDistanceCm * calibE / currentE
 *
 * where E is the normalized eye separation (eyeDistancePx / imageDiagonalPx).
 * Calibration is done once with the phone held at a known distance.
 */
object DistanceEstimator {

    /** Normalized, rotation-invariant eye separation, or null if not measurable. */
    fun normalizedEyeSeparation(
        leftEye: PointF?,
        rightEye: PointF?,
        imageWidth: Int,
        imageHeight: Int
    ): Float? {
        if (leftEye == null || rightEye == null) return null
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val eyePx = hypot((leftEye.x - rightEye.x).toDouble(), (leftEye.y - rightEye.y).toDouble())
        if (eyePx <= 0.0) return null
        val diagonal = sqrt((imageWidth.toLong() * imageWidth + imageHeight.toLong() * imageHeight).toDouble())
        if (diagonal <= 0.0) return null
        return (eyePx / diagonal).toFloat()
    }

    fun distanceCm(currentE: Float, calibDistanceCm: Int, calibE: Float): Float {
        if (currentE <= 0f || calibE <= 0f) return Float.MAX_VALUE
        return calibDistanceCm * calibE / currentE
    }
}
