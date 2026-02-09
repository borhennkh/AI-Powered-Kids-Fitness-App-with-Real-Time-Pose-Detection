package com.google.mediapipe.examples.poselandmarker.models

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Normalized pose data structure for exercise detection.
 * All coordinates are normalized (0.0 to 1.0) for distance independence.
 *
 * This model provides clean access to pose landmarks without MediaPipe dependencies
 * in exercise logic. Future integration with Flutter/Unity will use this structure.
 */
data class NormalizedPose(
    val landmarks: Map<PoseLandmark, NormalizedLandmark>,
    val timestamp: Long
) {

    /**
     * Calculate angle between three points (in degrees).
     * Used for joint angle calculations like knee, elbow, etc.
     *
     * @param pointA First point (e.g., Hip)
     * @param vertex Middle point/vertex (e.g., Knee)
     * @param pointC Third point (e.g., Ankle)
     * @return Angle in degrees (0-180)
     */
    fun calculateAngle(
        pointA: PoseLandmark,
        vertex: PoseLandmark,
        pointC: PoseLandmark
    ): Float {
        val a = landmarks[pointA] ?: return 0f
        val b = landmarks[vertex] ?: return 0f
        val c = landmarks[pointC] ?: return 0f

        val radians = Math.atan2((c.y() - b.y()).toDouble(), (c.x() - b.x()).toDouble()) -
                Math.atan2((a.y() - b.y()).toDouble(), (a.x() - b.x()).toDouble())
        var angle = Math.abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle.toFloat()
    }

    /**
     * Calculate Euclidean distance between two landmarks.
     * Used for body measurements like torso length, shoulder width, etc.
     */
    fun calculateDistance(point1: PoseLandmark, point2: PoseLandmark): Float {
        val p1 = landmarks[point1] ?: return 0f
        val p2 = landmarks[point2] ?: return 0f

        return Math.sqrt(
            Math.pow((p1.x() - p2.x()).toDouble(), 2.0) +
            Math.pow((p1.y() - p2.y()).toDouble(), 2.0)
        ).toFloat()
    }

    /**
     * Get landmark by enum (safe access).
     */
    fun getLandmark(landmark: PoseLandmark): NormalizedLandmark? {
        return landmarks[landmark]
    }
}

/**
 * MediaPipe Pose 33-point landmark model.
 * Enum provides type-safe access to pose landmarks.
 */
enum class PoseLandmark(val index: Int) {
    NOSE(0),
    LEFT_EYE_INNER(1),
    LEFT_EYE(2),
    LEFT_EYE_OUTER(3),
    RIGHT_EYE_INNER(4),
    RIGHT_EYE(5),
    RIGHT_EYE_OUTER(6),
    LEFT_EAR(7),
    RIGHT_EAR(8),
    MOUTH_LEFT(9),
    MOUTH_RIGHT(10),
    LEFT_SHOULDER(11),
    RIGHT_SHOULDER(12),
    LEFT_ELBOW(13),
    RIGHT_ELBOW(14),
    LEFT_WRIST(15),
    RIGHT_WRIST(16),
    LEFT_PINKY(17),
    RIGHT_PINKY(18),
    LEFT_INDEX(19),
    RIGHT_INDEX(20),
    LEFT_THUMB(21),
    RIGHT_THUMB(22),
    LEFT_HIP(23),
    RIGHT_HIP(24),
    LEFT_KNEE(25),
    RIGHT_KNEE(26),
    LEFT_ANKLE(27),
    RIGHT_ANKLE(28),
    LEFT_HEEL(29),
    RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31),
    RIGHT_FOOT_INDEX(32)
}

