package com.google.mediapipe.examples.poselandmarker.core

import android.util.Log
import com.google.mediapipe.examples.poselandmarker.DebugConfig
import com.google.mediapipe.examples.poselandmarker.exercises.base.BaseExercise
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose

/**
 * ExerciseManager - Central routing and coordination layer.
 *
 * RESPONSIBILITIES:
 * - Hold reference to active exercise
 * - Route pose frames to active exercise
 * - Switch exercises dynamically
 * - Forward exercise events to UI layer
 * - Manage exercise lifecycle
 *
 * ARCHITECTURE:
 * CameraFragment → PoseEngine → ExerciseManager → ActiveExercise → ExerciseListener → UI
 *
 * FUTURE CROSS-PLATFORM:
 * - Flutter: Platform channel will communicate with ExerciseManager
 * - Unity: JNI bridge will receive events from ExerciseManager
 * - This class remains the single source of truth for exercise state
 */
class ExerciseManager : BaseExercise.ExerciseListener {

    companion object {
        private const val TAG = "ExerciseManager"
    }

    // ========== STATE ==========
    private var activeExercise: BaseExercise? = null
    private var managerListener: ExerciseManagerListener? = null

    /**
     * Listener interface for UI layer.
     */
    interface ExerciseManagerListener {
        fun onRepCountUpdated(count: Int, exerciseName: String)
        fun onStateChanged(state: BaseExercise.ExerciseState, details: String)
        fun onValidationError(reason: String)
        fun onExerciseComplete(totalReps: Int, duration: Long)
        fun onExerciseChanged(exerciseName: String)
    }

    // ========== PUBLIC API ==========

    /**
     * Set active exercise.
     * Automatically resets previous exercise and starts new one.
     */
    fun setActiveExercise(exercise: BaseExercise) {
        // Reset previous exercise
        activeExercise?.reset()

        // Set new exercise
        activeExercise = exercise
        exercise.setListener(this)
        exercise.reset() // Ensure clean state

        // DEBUG: Log exercise change
        if (DebugConfig.DEBUG_MODE) {
            android.util.Log.i(TAG, "✅ Active exercise set to: ${exercise.getName()}")
        }
        Log.d(TAG, "✅ Active exercise set to: ${exercise.getName()}")

        // Notify UI
        managerListener?.onExerciseChanged(exercise.getName())
    }

    /**
     * Get current active exercise.
     */
    fun getActiveExercise(): BaseExercise? = activeExercise

    /**
     * Process pose frame and forward to active exercise.
     * This is called by PoseEngine on every camera frame.
     */
    fun processPose(pose: NormalizedPose) {
        activeExercise?.processPose(pose)
    }

    /**
     * Reset current exercise.
     */
    fun resetCurrentExercise() {
        activeExercise?.reset()
        Log.d(TAG, "🔄 Current exercise reset")
    }

    /**
     * Set listener for exercise events.
     */
    fun setListener(listener: ExerciseManagerListener?) {
        this.managerListener = listener
    }

    /**
     * Get current rep count.
     */
    fun getCurrentRepCount(): Int {
        return activeExercise?.repCount ?: 0
    }

    /**
     * Get current exercise state.
     */
    fun getCurrentState(): BaseExercise.ExerciseState {
        return activeExercise?.currentState ?: BaseExercise.ExerciseState.IDLE
    }

    /**
     * Get current exercise name.
     */
    fun getCurrentExerciseName(): String {
        return activeExercise?.getName() ?: "None"
    }

    /**
     * Check if pose is valid for current exercise.
     */
    fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String> {
        return activeExercise?.isPoseValid(pose) ?: Pair(false, "No exercise selected")
    }

    // ========== BASEEXERCISE.EXERCISELISTENER IMPLEMENTATION ==========
    // Forward all events to UI layer

    override fun onRepCountUpdated(count: Int, exerciseName: String) {
        // DEBUG: Log callback forwarding
        if (DebugConfig.DEBUG_MODE && DebugConfig.LOG_CALLBACKS) {
            android.util.Log.d(DebugConfig.TAG_CALLBACK, "ExerciseManager forwarding onRepCountUpdated | " +
                "count=$count | " +
                "exercise=$exerciseName | " +
                "listener=${if (managerListener != null) "SET" else "NULL"}")
        }
        managerListener?.onRepCountUpdated(count, exerciseName)
    }

    override fun onStateChanged(state: BaseExercise.ExerciseState, details: String) {
        // DEBUG: Log state change forwarding
        if (DebugConfig.DEBUG_MODE && DebugConfig.LOG_CALLBACKS) {
            android.util.Log.d(DebugConfig.TAG_CALLBACK, "ExerciseManager forwarding onStateChanged | " +
                "state=$state | " +
                "details='$details'")
        }
        managerListener?.onStateChanged(state, details)
    }

    override fun onValidationError(reason: String) {
        managerListener?.onValidationError(reason)
    }

    override fun onExerciseComplete(totalReps: Int, duration: Long) {
        // DEBUG: Log exercise completion
        if (DebugConfig.DEBUG_MODE) {
            android.util.Log.i(TAG, "Exercise complete | reps=$totalReps duration=${duration}s")
        }
        managerListener?.onExerciseComplete(totalReps, duration)
    }
}

