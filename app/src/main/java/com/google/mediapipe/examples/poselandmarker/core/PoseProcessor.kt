package com.google.mediapipe.examples.poselandmarker.core

import android.util.Log
import com.google.mediapipe.examples.poselandmarker.DebugConfig
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose
import com.google.mediapipe.examples.poselandmarker.models.PoseLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * PoseProcessor - Converts MediaPipe results into normalized pose data.
 *
 * RESPONSIBILITIES:
 * - Extract landmarks from PoseLandmarkerResult
 * - Create NormalizedPose data model
 * - Validate pose quality (visibility, completeness)
 * - Abstract MediaPipe internals from exercise logic
 *
 * ARCHITECTURE:
 * This is the BRIDGE between MediaPipe SDK and your exercise engine.
 * Exercise logic should NEVER directly access MediaPipe types.
 *
 * FUTURE CROSS-PLATFORM:
 * - Flutter: Custom pose data from ML Kit or TensorFlow Lite
 * - Unity: Custom pose data from Barracuda or native plugins
 * - All platforms convert to NormalizedPose format
 */
object PoseProcessor {

    private const val TAG = "PoseProcessor"
    private var lastLogTime = 0L

    /**
     * Convert MediaPipe PoseLandmarkerResult to NormalizedPose.
     *
     * @param result MediaPipe detection result
     * @return NormalizedPose or null if invalid
     */
    fun processResult(result: PoseLandmarkerResult): NormalizedPose? {
        if (result.landmarks().isEmpty()) {
            return null
        }

        val landmarks = result.landmarks()[0]

        // Build landmark map - include ALL landmarks regardless of visibility
        // Let the exercise logic decide what's needed
        val landmarkMap = mutableMapOf<PoseLandmark, com.google.mediapipe.tasks.components.containers.NormalizedLandmark>()

        PoseLandmark.values().forEach { poseLandmark ->
            if (poseLandmark.index < landmarks.size) {
                landmarkMap[poseLandmark] = landmarks[poseLandmark.index]
            }
        }

        // NO VALIDATION - just return the pose
        // SquatExercise will handle missing landmarks gracefully
        return NormalizedPose(
            landmarks = landmarkMap,
            timestamp = result.timestampMs()
        )
    }

    /**
     * Check if pose has all required landmarks for exercise detection.
     *
     * REQUIRED LANDMARKS (STRICT - must be visible):
     * - Shoulders (LEFT, RIGHT)
     * - Hips (LEFT, RIGHT)
     * - Knees (LEFT, RIGHT)
     * - Ankles (LEFT, RIGHT)
     *
     * OPTIONAL LANDMARKS (nice to have but not required):
     * - Wrists (LEFT, RIGHT) - may not always be visible
     */
    private fun isPoseComplete(landmarks: Map<PoseLandmark, com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        // Core body landmarks - MUST be visible
        val requiredLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        )

        // Check all required landmarks exist and are visible
        // Lower visibility threshold from 0.5 to 0.3 for more lenient detection
        return requiredLandmarks.all { landmark ->
            val lm = landmarks[landmark]
            lm != null && lm.visibility().orElse(0f) > 0.3f
        }
    }

    /**
     * Get pose quality score (0.0 - 1.0).
     * Based on average visibility of key landmarks.
     *
     * Used for UI feedback:
     * - < 0.5: "Move closer to camera"
     * - 0.5-0.7: "Pose partially visible"
     * - > 0.7: "Good pose quality"
     */
    fun getPoseQuality(pose: NormalizedPose): Float {
        val keyLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE
        )

        val visibilities = keyLandmarks.mapNotNull { landmark ->
            pose.getLandmark(landmark)?.visibility()?.orElse(0f)
        }

        return if (visibilities.isEmpty()) {
            0f
        } else {
            visibilities.average().toFloat()
        }
    }
}

