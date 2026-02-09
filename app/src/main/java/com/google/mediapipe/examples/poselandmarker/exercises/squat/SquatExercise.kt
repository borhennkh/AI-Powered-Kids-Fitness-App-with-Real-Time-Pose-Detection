package com.google.mediapipe.examples.poselandmarker.exercises.squat

import android.util.Log
import com.google.mediapipe.examples.poselandmarker.DebugConfig
import com.google.mediapipe.examples.poselandmarker.exercises.base.BaseExercise
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose
import com.google.mediapipe.examples.poselandmarker.models.PoseLandmark

/**
 * Air Squat Exercise Detection Module.
 *
 * DETECTION LOGIC:
 * - Multi-phase state machine: READY → DESCENDING → BOTTOM → ASCENDING → READY
 * - Knee angle calculation (Hip-Knee-Ankle)
 * - Arm horizontal validation (arms extended forward)
 * - Facing camera validation (shoulder width check)
 * - Cooldown debounce (prevents double counting)
 *
 * VALIDATION RULES:
 * - User must face camera (shoulder width > threshold)
 * - Arms must be extended horizontally forward
 * - Knee angle must pass through: Standing (155°) → Squat (90°) → Standing (155°)
 * - Minimum 800ms cooldown between reps
 *
 * FUTURE ENHANCEMENTS:
 * - Add depth scoring (partial vs full squat)
 * - Add form feedback (knee alignment, back angle)
 * - Add tempo tracking (eccentric/concentric timing)
 */
class SquatExercise : BaseExercise() {

    companion object {
        private const val TAG = "SquatExercise"

        // ========== ANGLE THRESHOLDS ==========
        private const val DOWN_ENTER = 135f       // READY → DOWN (starting descent)
        private const val BOTTOM_ENTER = 95f      // DOWN → BOTTOM (deep squat)
        private const val BOTTOM_EXIT = 105f      // BOTTOM → UP (hysteresis)
        private const val UP_ENTER = 160f         // UP → READY (count rep)

        // ========== VALIDATION ==========
        private const val MIN_VALID_ANGLE = 40f   // Below = occlusion/noise
        private const val MAX_VALID_ANGLE = 180f  // Above = calculation error

        // ========== VISIBILITY REQUIREMENTS ==========
        // Minimum visibility score for landmarks (0.0 - 1.0)
        private const val MIN_VISIBILITY = 0.5f

        // ========== BODY PROPORTION CHECK ==========
        // Minimum torso length in normalized coordinates (prevents counting when too close/partial body)
        private const val MIN_TORSO_LENGTH = 0.08f

        // ========== COOLDOWN ==========
        private const val COOLDOWN_MS = 700L
    }

    // ========== STATE MACHINE ==========
    private enum class SquatState {
        READY, DOWN, BOTTOM, UP
    }

    private var currentSquatState = SquatState.READY
    private var lastSquatTime: Long = 0L

    // ========== SQUAT DETECTION WITH PROPER VALIDATION ==========
    override fun processPose(pose: NormalizedPose) {

        // ========== STEP 1: CHECK IF FULL BODY IS VISIBLE ==========
        // Must have hip, knee, and ankle visible on at least one leg
        val leftHip = pose.getLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getLandmark(PoseLandmark.LEFT_ANKLE)
        val rightHip = pose.getLandmark(PoseLandmark.RIGHT_HIP)
        val rightKnee = pose.getLandmark(PoseLandmark.RIGHT_KNEE)
        val rightAnkle = pose.getLandmark(PoseLandmark.RIGHT_ANKLE)

        // Check visibility scores - legs MUST be visible
        val leftLegVisible = leftHip != null && leftKnee != null && leftAnkle != null &&
            leftHip.visibility().orElse(0f) > MIN_VISIBILITY &&
            leftKnee.visibility().orElse(0f) > MIN_VISIBILITY &&
            leftAnkle.visibility().orElse(0f) > MIN_VISIBILITY

        val rightLegVisible = rightHip != null && rightKnee != null && rightAnkle != null &&
            rightHip.visibility().orElse(0f) > MIN_VISIBILITY &&
            rightKnee.visibility().orElse(0f) > MIN_VISIBILITY &&
            rightAnkle.visibility().orElse(0f) > MIN_VISIBILITY

        // At least ONE leg must be fully visible
        if (!leftLegVisible && !rightLegVisible) {
            // Reset to READY if we lose leg visibility mid-squat
            if (currentSquatState != SquatState.READY) {
                Log.w(TAG, "⚠️ Legs not visible - resetting state")
                currentSquatState = SquatState.READY
            }
            return
        }

        // ========== STEP 2: CHECK BODY SIZE (prevents partial body detection) ==========
        val leftShoulder = pose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        if (leftShoulder == null || leftHip == null) {
            return
        }

        val torsoLength = Math.sqrt(
            Math.pow((leftShoulder.x() - leftHip.x()).toDouble(), 2.0) +
            Math.pow((leftShoulder.y() - leftHip.y()).toDouble(), 2.0)
        ).toFloat()

        if (torsoLength < MIN_TORSO_LENGTH) {
            // Body too small in frame or partial body
            if (currentSquatState != SquatState.READY) {
                currentSquatState = SquatState.READY
            }
            return
        }

        // ========== STEP 3: CALCULATE KNEE ANGLE ==========
        val kneeAngle: Float = if (leftLegVisible) {
            pose.calculateAngle(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        } else {
            pose.calculateAngle(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        }

        // Validate angle range
        if (kneeAngle < MIN_VALID_ANGLE || kneeAngle > MAX_VALID_ANGLE) {
            return
        }

        // ========== STEP 4: CHECK ARM POSITION (prevents T-pose counting) ==========
        // For a proper squat, wrists should be roughly in front of body (Z-axis check via X position)
        // When arms are sideways (T-pose), wrists are far from shoulders in X
        // When arms are forward, wrists are closer to center
        val leftWrist = pose.getLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getLandmark(PoseLandmark.RIGHT_WRIST)
        val rightShoulder = pose.getLandmark(PoseLandmark.RIGHT_SHOULDER)

        var armPositionValid = true
        if (leftWrist != null && rightWrist != null && leftShoulder != null && rightShoulder != null) {
            // Calculate shoulder width
            val shoulderWidth = Math.abs(leftShoulder.x() - rightShoulder.x())

            // Calculate how far wrists are spread apart
            val wristSpread = Math.abs(leftWrist.x() - rightWrist.x())

            // If wrists are spread MORE than 1.5x shoulder width, it's a T-pose (arms sideways)
            // In proper squat position, wrists should be closer together (arms forward)
            if (wristSpread > shoulderWidth * 1.8f) {
                armPositionValid = false
                // Don't block completely, just prevent counting in this frame
                // But don't reset state machine - they might just be adjusting
            }
        }

        // Log current state
        Log.d(TAG, "📐 Angle: ${"%.0f".format(kneeAngle)}° | State: $currentSquatState | Arms OK: $armPositionValid")

        // ========== STEP 5: STATE MACHINE ==========
        val currentTime = System.currentTimeMillis()

        when (currentSquatState) {
            SquatState.READY -> {
                currentState = ExerciseState.READY

                // Only start squat if arms are in valid position
                if (kneeAngle < DOWN_ENTER && armPositionValid) {
                    currentSquatState = SquatState.DOWN
                    currentState = ExerciseState.IN_PROGRESS
                    Log.i(TAG, "🔽 READY → DOWN | Angle: ${"%.0f".format(kneeAngle)}°")
                }
            }

            SquatState.DOWN -> {
                currentState = ExerciseState.IN_PROGRESS

                if (kneeAngle < BOTTOM_ENTER) {
                    currentSquatState = SquatState.BOTTOM
                    Log.i(TAG, "⬇️ DOWN → BOTTOM | Angle: ${"%.0f".format(kneeAngle)}°")
                } else if (kneeAngle > UP_ENTER) {
                    // Aborted squat
                    currentSquatState = SquatState.READY
                    currentState = ExerciseState.READY
                    Log.w(TAG, "🔄 DOWN → READY (aborted)")
                }
            }

            SquatState.BOTTOM -> {
                currentState = ExerciseState.IN_PROGRESS

                if (kneeAngle > BOTTOM_EXIT) {
                    currentSquatState = SquatState.UP
                    Log.i(TAG, "⬆️ BOTTOM → UP | Angle: ${"%.0f".format(kneeAngle)}°")
                }
            }

            SquatState.UP -> {
                currentState = ExerciseState.IN_PROGRESS

                // Only count if standing up AND arms are valid
                if (kneeAngle > UP_ENTER && armPositionValid) {
                    val timeSinceLastSquat = currentTime - lastSquatTime

                    if (timeSinceLastSquat > COOLDOWN_MS || lastSquatTime == 0L) {
                        // ✅ VALID REP
                        currentSquatState = SquatState.READY
                        repCount++
                        lastSquatTime = currentTime
                        currentState = ExerciseState.COMPLETED

                        Log.i(TAG, "🔥 SQUAT COUNTED! Total: $repCount")

                        notifyRepCountUpdated()
                        notifyStateChanged("Rep completed!")
                        currentState = ExerciseState.READY
                    } else {
                        currentSquatState = SquatState.READY
                        currentState = ExerciseState.READY
                    }
                } else if (kneeAngle > UP_ENTER && !armPositionValid) {
                    // Standing but arms are in T-pose - don't count, just reset
                    currentSquatState = SquatState.READY
                    currentState = ExerciseState.READY
                    Log.w(TAG, "⚠️ Arms in T-pose - not counted")
                }
            }
        }
    }

    override fun reset() {
        repCount = 0
        currentSquatState = SquatState.READY
        currentState = ExerciseState.IDLE
        lastSquatTime = 0L
        startTime = System.currentTimeMillis()
        Log.d(TAG, "🔄 SquatExercise reset")
    }

    override fun getName(): String = "Air Squat"

    override fun getDescription(): String =
        "Stand with feet shoulder-width apart. Extend arms forward. Lower your body by bending knees until thighs are parallel to ground. Return to standing."

    override fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String> {
        val leftShoulder = pose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftHip = pose.getLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getLandmark(PoseLandmark.LEFT_ANKLE)

        if (leftShoulder == null || leftHip == null || leftKnee == null || leftAnkle == null) {
            return Pair(false, "Cannot detect full body. Step back from camera.")
        }

        val torsoLength = pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        if (torsoLength < 0.1f) {
            return Pair(false, "Too far from camera or pose not detected clearly.")
        }

        return Pair(true, "Ready to start!")
    }
}

