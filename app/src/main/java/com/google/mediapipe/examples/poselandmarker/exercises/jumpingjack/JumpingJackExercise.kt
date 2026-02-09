package com.google.mediapipe.examples.poselandmarker.exercises.jumpingjack

import android.util.Log
import com.google.mediapipe.examples.poselandmarker.exercises.base.BaseExercise
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose
import com.google.mediapipe.examples.poselandmarker.models.PoseLandmark
import kotlin.math.abs

/**
 * Jumping Jack Exercise Detection Module.
 *
 * KID-FRIENDLY VERSION - Relaxed thresholds for children's imperfect movement.
 *
 * DETECTION LOGIC:
 * - Two-state machine: CLOSED ↔ OPEN
 * - Arm angle: HIP → SHOULDER → WRIST (left side only)
 * - Leg spread: normalized ankle horizontal distance
 * - Cooldown: 400ms between reps
 *
 * VALIDATION RULES (RELAXED FOR KIDS):
 * - ARM OPEN: angle > 120° (was 150°)
 * - ARM CLOSED: angle < 80° (was 60°)
 * - LEGS OPEN: normalizedSpread > 0.5 (was 0.7)
 * - LEGS CLOSED: normalizedSpread < 0.6 (was 0.4)
 *
 * STATE TRANSITIONS:
 * - CLOSED → OPEN: arms open AND legs open
 * - OPEN → CLOSED: arms closed AND legs closed → COUNT REP
 */
class JumpingJackExercise : BaseExercise() {

    companion object {
        private const val TAG = "JumpingJackExercise"

        // ========== ARM THRESHOLDS (RELAXED FOR KIDS) ==========
        private const val ARM_OPEN_ANGLE = 120f      // Was 150° - now more forgiving
        private const val ARM_CLOSED_ANGLE = 80f     // Was 60° - now more forgiving

        // ========== LEG THRESHOLDS (RELAXED FOR KIDS) ==========
        private const val LEGS_OPEN_RATIO = 0.5f     // Was 0.7 - now more forgiving
        private const val LEGS_CLOSED_RATIO = 0.6f   // Was 0.4 - now more forgiving (wider hysteresis)

        // ========== BODY NORMALIZATION ==========
        private const val MIN_TORSO_LENGTH = 0.06f   // Was 0.1 - works at farther distances

        // ========== COOLDOWN ==========
        private const val COOLDOWN_MS = 400L         // Was 500 - faster rep counting
    }

    // ========== STATE MACHINE ==========
    private enum class JackPhase {
        CLOSED,
        OPEN
    }

    private var currentPhase = JackPhase.CLOSED
    private var lastRepTime: Long = 0L
    private var lastLogTime: Long = 0L

    // ========== JUMPING JACK DETECTION ==========
    override fun processPose(pose: NormalizedPose) {

        // ========== STEP 1: GET REQUIRED LANDMARKS ==========
        val leftShoulder = pose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftHip = pose.getLandmark(PoseLandmark.LEFT_HIP)
        val leftWrist = pose.getLandmark(PoseLandmark.LEFT_WRIST)
        val leftAnkle = pose.getLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getLandmark(PoseLandmark.RIGHT_ANKLE)

        // Skip frame silently if landmarks missing - DO NOT reset state
        if (leftShoulder == null || leftHip == null || leftWrist == null ||
            leftAnkle == null || rightAnkle == null) {
            return
        }

        // ========== STEP 2: CALCULATE TORSO LENGTH (NORMALIZATION) ==========
        val torsoLength = pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)

        // Skip frame if body too small - DO NOT reset state
        if (torsoLength < MIN_TORSO_LENGTH) {
            return
        }

        // ========== STEP 3: CALCULATE ARM ANGLE (HIP → SHOULDER → WRIST) ==========
        val armAngle = pose.calculateAngle(
            PoseLandmark.LEFT_HIP,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.LEFT_WRIST
        )


        // ========== STEP 4: CALCULATE LEG SPREAD (NORMALIZED) ==========
        val legSpread = abs(leftAnkle.x() - rightAnkle.x())
        val normalizedSpread = legSpread / torsoLength

        // ========== STEP 5: DETERMINE ARM AND LEG STATES ==========
        val armsOpen = armAngle > ARM_OPEN_ANGLE
        val armsClosed = armAngle < ARM_CLOSED_ANGLE
        val legsOpen = normalizedSpread > LEGS_OPEN_RATIO
        val legsClosed = normalizedSpread < LEGS_CLOSED_RATIO

        // ========== STEP 6: DEBUG LOGGING (once per second) ==========
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime > 1000L) {
            Log.d(TAG, "📐 Arm: ${"%.0f".format(armAngle)}° | Legs: ${"%.2f".format(normalizedSpread)} | " +
                "Phase: $currentPhase | ArmsOpen: $armsOpen | LegsOpen: $legsOpen")
            lastLogTime = currentTime
        }

        // ========== STEP 7: STATE MACHINE TRANSITIONS ==========
        when (currentPhase) {
            JackPhase.CLOSED -> {
                currentState = ExerciseState.READY

                // CLOSED → OPEN: Both arms and legs must be open
                if (armsOpen && legsOpen) {
                    currentPhase = JackPhase.OPEN
                    currentState = ExerciseState.IN_PROGRESS
                    Log.i(TAG, "🔼 CLOSED → OPEN | Arm: ${"%.0f".format(armAngle)}° | Legs: ${"%.2f".format(normalizedSpread)}")
                }
            }

            JackPhase.OPEN -> {
                currentState = ExerciseState.IN_PROGRESS

                // OPEN → CLOSED: Both arms and legs must be closed → COUNT REP
                if (armsClosed && legsClosed) {
                    val timeSinceLastRep = currentTime - lastRepTime

                    // Cooldown check to prevent double counting
                    if (timeSinceLastRep > COOLDOWN_MS || lastRepTime == 0L) {
                        // ✅ VALID REP - Count it!
                        repCount++
                        lastRepTime = currentTime
                        currentState = ExerciseState.COMPLETED

                        Log.i(TAG, "🔥 JUMPING JACK COUNTED! Total: $repCount")

                        // Notify UI
                        notifyRepCountUpdated()
                        notifyStateChanged("Rep completed!")
                    }

                    // Always transition back to CLOSED
                    currentPhase = JackPhase.CLOSED
                    currentState = ExerciseState.READY
                    Log.i(TAG, "🔽 OPEN → CLOSED | Arm: ${"%.0f".format(armAngle)}° | Legs: ${"%.2f".format(normalizedSpread)}")
                }
            }
        }
    }

    override fun reset() {
        repCount = 0
        currentPhase = JackPhase.CLOSED
        currentState = ExerciseState.IDLE
        lastRepTime = 0L
        lastLogTime = 0L
        startTime = System.currentTimeMillis()
        Log.d(TAG, "🔄 JumpingJackExercise reset")
    }

    override fun getName(): String = "Jumping Jack"

    override fun getDescription(): String =
        "Stand with feet together and arms at sides. Jump while spreading legs and raising arms overhead. Return to starting position."

    override fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String> {
        val leftShoulder = pose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftHip = pose.getLandmark(PoseLandmark.LEFT_HIP)
        val leftAnkle = pose.getLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftShoulder == null || leftHip == null || leftAnkle == null || rightAnkle == null) {
            return Pair(false, "Cannot detect full body. Step back from camera.")
        }

        val torsoLength = pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        if (torsoLength < MIN_TORSO_LENGTH) {
            return Pair(false, "Move further from camera for full body detection.")
        }

        return Pair(true, "Ready to start!")
    }
}

