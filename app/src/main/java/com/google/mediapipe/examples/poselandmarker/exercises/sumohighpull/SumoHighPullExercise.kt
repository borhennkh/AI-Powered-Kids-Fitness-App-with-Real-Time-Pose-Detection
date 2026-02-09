package com.google.mediapipe.examples.poselandmarker.exercises.sumohighpull

import android.util.Log
import com.google.mediapipe.examples.poselandmarker.exercises.base.BaseExercise
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose
import com.google.mediapipe.examples.poselandmarker.models.PoseLandmark

/**
 * Sumo High Pull Exercise Detection Module.
 *
 * EXERCISE DESCRIPTION:
 * - User starts in wide squat position with arms hanging down
 * - User stands up explosively while pulling elbows up/out
 * - Hands reach chest/shoulder height with elbows flared
 * - User returns to squat position
 * - Repeat
 *
 * DETECTION LOGIC:
 * - Four-phase state machine: READY → SQUAT_DOWN → PULL_UP → RETURN
 * - Knee angle: HIP → KNEE → ANKLE (squat detection)
 * - Shoulder angle: HIP → SHOULDER → ELBOW (arm pull detection)
 * - Elbow spatial validation: elbow must be above shoulder for valid pull
 * - Cooldown: 700ms between reps
 * - Return timeout: 2000ms failsafe to prevent stuck states
 *
 * STATE TRANSITIONS:
 * - READY: Wait for squat (kneeAngle < 140°) → SQUAT_DOWN
 * - SQUAT_DOWN: Wait for stand + arm pull (kneeAngle > 160° AND shoulderAngle > 55°) → PULL_UP
 * - PULL_UP: Wait for full pull (shoulderAngle > 65° AND elbowAboveShoulder) → RETURN
 * - RETURN: Wait for return to squat (kneeAngle < 140° AND shoulderAngle < 50°) → COUNT REP → READY
 */
class SumoHighPullExercise : BaseExercise() {

    companion object {
        private const val TAG = "SumoHighPullExercise"

        // ========== KNEE ANGLE THRESHOLDS ==========
        private const val SQUAT_DOWN_TRIGGER = 140f    // READY → SQUAT_DOWN
        private const val STAND_UP = 160f              // Standing threshold
        private const val MIN_VALID_KNEE_ANGLE = 30f   // Below = occlusion/noise

        // ========== SHOULDER ANGLE THRESHOLDS ==========
        // Angle between HIP → SHOULDER → ELBOW
        private const val ARM_DOWN = 50f               // Arms relaxed/down
        private const val PULL_TRIGGER = 55f           // Starting arm pull
        private const val FULL_PULL = 65f              // Elbows fully up

        // ========== VALIDATION ==========
        private const val MAX_VALID_KNEE_ANGLE = 180f  // Above = calculation error
        private const val MIN_VISIBILITY = 0.5f        // Landmark visibility threshold
        private const val MIN_TORSO_LENGTH = 0.06f     // Body size validation

        // ========== COOLDOWN ==========
        private const val COOLDOWN_MS = 700L           // Prevent double counting
    }

    // ========== STATE MACHINE ==========
    private enum class SumoPhase {
        READY,       // Waiting for user to squat
        SQUAT_DOWN,  // User in squat position
        PULL_UP,     // User standing and pulling arms up
        RETURN       // User returning to squat position
    }

    private var currentPhase = SumoPhase.READY
    private var lastRepTime: Long = 0L
    private var lastLogTime: Long = 0L
    private var returnStartTime: Long = 0L

    // ========== SUMO HIGH PULL DETECTION ==========
    override fun processPose(pose: NormalizedPose) {

        // ========== STEP 1: CHECK IF BODY IS VISIBLE ==========
        val leftHip = pose.getLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getLandmark(PoseLandmark.LEFT_ANKLE)
        val rightHip = pose.getLandmark(PoseLandmark.RIGHT_HIP)
        val rightKnee = pose.getLandmark(PoseLandmark.RIGHT_KNEE)
        val rightAnkle = pose.getLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getLandmark(PoseLandmark.RIGHT_ELBOW)

        // Check leg visibility
        val leftLegVisible = leftHip != null && leftKnee != null && leftAnkle != null &&
            leftHip.visibility().orElse(0f) > MIN_VISIBILITY &&
            leftKnee.visibility().orElse(0f) > MIN_VISIBILITY &&
            leftAnkle.visibility().orElse(0f) > MIN_VISIBILITY

        val rightLegVisible = rightHip != null && rightKnee != null && rightAnkle != null &&
            rightHip.visibility().orElse(0f) > MIN_VISIBILITY &&
            rightKnee.visibility().orElse(0f) > MIN_VISIBILITY &&
            rightAnkle.visibility().orElse(0f) > MIN_VISIBILITY

        // Check arm visibility (for shoulder angle)
        val leftArmVisible = leftShoulder != null && leftElbow != null && leftHip != null &&
            leftShoulder.visibility().orElse(0f) > MIN_VISIBILITY &&
            leftElbow.visibility().orElse(0f) > MIN_VISIBILITY

        val rightArmVisible = rightShoulder != null && rightElbow != null && rightHip != null &&
            rightShoulder.visibility().orElse(0f) > MIN_VISIBILITY &&
            rightElbow.visibility().orElse(0f) > MIN_VISIBILITY

        // At least ONE leg and ONE arm must be visible - skip frame silently if not
        if ((!leftLegVisible && !rightLegVisible) || (!leftArmVisible && !rightArmVisible)) {
            return
        }

        // ========== STEP 2: CHECK BODY SIZE ==========
        val torsoLength = if (leftShoulder != null && leftHip != null) {
            pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        } else {
            pose.calculateDistance(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)
        }

        if (torsoLength < MIN_TORSO_LENGTH) {
            return
        }

        // ========== STEP 3: CALCULATE KNEE ANGLE ==========
        val kneeAngle: Float = if (leftLegVisible) {
            pose.calculateAngle(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        } else {
            pose.calculateAngle(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        }

        // Validate knee angle range
        if (kneeAngle < MIN_VALID_KNEE_ANGLE || kneeAngle > MAX_VALID_KNEE_ANGLE) {
            return
        }

        // ========== STEP 4: CALCULATE SHOULDER ANGLE (ARM PULL) ==========
        // HIP → SHOULDER → ELBOW angle - higher angle = elbow more raised
        val shoulderAngle: Float = if (leftArmVisible) {
            pose.calculateAngle(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
        } else {
            pose.calculateAngle(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
        }

        // ========== STEP 4b: SPATIAL ARM VALIDATION ==========
        val elbowAboveShoulder = if (leftArmVisible) {
            leftElbow!!.y() < leftShoulder!!.y()
        } else {
            rightElbow!!.y() < rightShoulder!!.y()
        }

        // ========== STEP 5: DEBUG LOGGING (once per second) ==========
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime > 1000L) {
            Log.d(TAG, "📐 Knee: ${"%.0f".format(kneeAngle)}° | Shoulder: ${"%.0f".format(shoulderAngle)}° | Phase: $currentPhase")
            lastLogTime = currentTime
        }

        // ========== STEP 6: STATE MACHINE TRANSITIONS ==========
        when (currentPhase) {
            SumoPhase.READY -> {
                currentState = ExerciseState.READY

                // READY → SQUAT_DOWN: Wait for squat
                if (kneeAngle < SQUAT_DOWN_TRIGGER) {
                    currentPhase = SumoPhase.SQUAT_DOWN
                    currentState = ExerciseState.IN_PROGRESS
                    Log.i(TAG, "🔽 READY → SQUAT_DOWN | Knee: ${"%.0f".format(kneeAngle)}°")
                }
            }

            SumoPhase.SQUAT_DOWN -> {
                currentState = ExerciseState.IN_PROGRESS

                // SQUAT_DOWN → PULL_UP: Stand up + start pulling arms
                if (kneeAngle > STAND_UP && shoulderAngle > PULL_TRIGGER) {
                    currentPhase = SumoPhase.PULL_UP
                    Log.i(TAG, "⬆️ SQUAT_DOWN → PULL_UP | Knee: ${"%.0f".format(kneeAngle)}° | Shoulder: ${"%.0f".format(shoulderAngle)}°")
                }
                // Abort if user stands without pulling
                else if (kneeAngle > STAND_UP && shoulderAngle < ARM_DOWN) {
                    // User stood up but didn't pull - reset
                    currentPhase = SumoPhase.READY
                    currentState = ExerciseState.READY
                    Log.w(TAG, "🔄 SQUAT_DOWN → READY (stood up without pull)")
                }
            }

            SumoPhase.PULL_UP -> {
                currentState = ExerciseState.IN_PROGRESS

                // PULL_UP → RETURN: Full pull achieved with elbow above shoulder
                if (shoulderAngle > FULL_PULL && elbowAboveShoulder) {
                    currentPhase = SumoPhase.RETURN
                    returnStartTime = currentTime
                    Log.i(TAG, "💪 PULL_UP → RETURN | Shoulder: ${"%.0f".format(shoulderAngle)}°")
                }
                // Abort if arms drop without completing pull
                else if (shoulderAngle < ARM_DOWN && kneeAngle < SQUAT_DOWN_TRIGGER) {
                    currentPhase = SumoPhase.READY
                    currentState = ExerciseState.READY
                    Log.w(TAG, "🔄 PULL_UP → READY (aborted pull)")
                }
            }

            SumoPhase.RETURN -> {
                currentState = ExerciseState.IN_PROGRESS

                // RETURN TIMEOUT FAILSAFE: Prevent getting stuck in RETURN phase
                if (currentTime - returnStartTime > 2000L) {
                    currentPhase = SumoPhase.READY
                    currentState = ExerciseState.READY
                    Log.w(TAG, "⏱️ RETURN timeout reset")
                    return
                }

                // RETURN → READY (COUNT REP): Back to squat with arms down
                if (kneeAngle < SQUAT_DOWN_TRIGGER && shoulderAngle < ARM_DOWN) {
                    val timeSinceLastRep = currentTime - lastRepTime

                    // Cooldown check to prevent double counting
                    if (timeSinceLastRep > COOLDOWN_MS || lastRepTime == 0L) {
                        // ✅ VALID REP - Count it!
                        repCount++
                        lastRepTime = currentTime
                        currentState = ExerciseState.COMPLETED

                        Log.i(TAG, "🔥 SUMO HIGH PULL COUNTED! Total: $repCount")

                        // Notify UI
                        notifyRepCountUpdated()
                        notifyStateChanged("Rep completed!")
                    }

                    // Always transition back to READY (will go to SQUAT_DOWN next frame since in squat)
                    currentPhase = SumoPhase.READY
                    currentState = ExerciseState.READY
                    Log.i(TAG, "🔽 RETURN → READY | Knee: ${"%.0f".format(kneeAngle)}°")
                }
            }
        }
    }

    override fun reset() {
        repCount = 0
        currentPhase = SumoPhase.READY
        currentState = ExerciseState.IDLE
        lastRepTime = 0L
        lastLogTime = 0L
        returnStartTime = 0L
        startTime = System.currentTimeMillis()
        Log.d(TAG, "🔄 SumoHighPullExercise reset")
    }

    override fun getName(): String = "Sumo High Pull"

    override fun getDescription(): String =
        "Stand with wide stance. Squat down with arms hanging. Stand up explosively while pulling elbows up and out. Return to squat position."

    override fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String> {
        val leftShoulder = pose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftHip = pose.getLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getLandmark(PoseLandmark.LEFT_ANKLE)
        val leftElbow = pose.getLandmark(PoseLandmark.LEFT_ELBOW)

        if (leftShoulder == null || leftHip == null || leftKnee == null ||
            leftAnkle == null || leftElbow == null) {
            return Pair(false, "Cannot detect full body. Step back from camera.")
        }

        val torsoLength = pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        if (torsoLength < MIN_TORSO_LENGTH) {
            return Pair(false, "Move further from camera for full body detection.")
        }

        return Pair(true, "Ready to start!")
    }
}

