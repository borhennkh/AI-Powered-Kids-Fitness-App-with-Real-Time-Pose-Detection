package com.google.mediapipe.examples.poselandmarker.exercises.base

import com.google.mediapipe.examples.poselandmarker.DebugConfig
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose

/**
 * Base interface for all exercise detection modules.
 *
 * ARCHITECTURE:
 * - Each exercise (Squat, JumpingJack, Jump, etc.) implements this interface
 * - ExerciseManager routes pose frames to the active exercise
 * - UI layer receives callbacks via ExerciseListener
 *
 * FUTURE CROSS-PLATFORM INTEGRATION:
 * - Flutter: Flutter app will communicate via platform channels
 * - Unity: Unity plugin will receive exercise events via JNI
 * - This interface remains the same across platforms
 */
abstract class BaseExercise {

    /**
     * Exercise state for UI feedback and game logic.
     */
    enum class ExerciseState {
        IDLE,           // No valid pose detected
        READY,          // User in starting position
        IN_PROGRESS,    // Mid-exercise (e.g., descending in squat)
        COMPLETED       // Rep completed
    }

    /**
     * Listener for exercise events (callbacks to UI layer).
     */
    interface ExerciseListener {
        /**
         * Called when rep count changes.
         * @param count Total rep count
         * @param exerciseName Name of the exercise
         */
        fun onRepCountUpdated(count: Int, exerciseName: String)

        /**
         * Called when exercise state changes.
         * @param state New state
         * @param details Optional state details (e.g., "Squat depth: 90°")
         */
        fun onStateChanged(state: ExerciseState, details: String = "")

        /**
         * Called when exercise validation fails.
         * @param reason Why the rep was invalid (e.g., "Arms not horizontal")
         */
        fun onValidationError(reason: String)

        /**
         * Called when exercise set is completed.
         * @param totalReps Total reps completed
         * @param duration Total duration in seconds
         */
        fun onExerciseComplete(totalReps: Int, duration: Long)
    }

    // ========== ABSTRACT METHODS (Must be implemented by each exercise) ==========

    /**
     * Process a pose frame and update exercise state.
     * This is the main detection loop called by ExerciseManager.
     *
     * @param pose Normalized pose data
     */
    abstract fun processPose(pose: NormalizedPose)

    /**
     * Reset exercise state and rep count.
     * Called when:
     * - User switches exercises
     * - User starts a new session
     * - UI requests reset
     */
    abstract fun reset()

    /**
     * Get exercise name (e.g., "Squat", "Jumping Jack").
     */
    abstract fun getName(): String

    /**
     * Get exercise description for UI.
     */
    abstract fun getDescription(): String

    /**
     * Check if pose is valid for this exercise.
     * Used for pre-start validation.
     *
     * @param pose Normalized pose data
     * @return Pair<Boolean, String> (isValid, reason)
     */
    abstract fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String>

    // ========== SHARED STATE (Available to all exercises) ==========

    // Use backing field to avoid conflicts
    private var _repCount: Int = 0
    private var _currentState: ExerciseState = ExerciseState.IDLE

    /**
     * Current rep count - readable by all, writable by subclasses only
     */
    var repCount: Int
        get() = _repCount
        protected set(value) { _repCount = value }

    /**
     * Current exercise state - readable by all, writable by subclasses only
     */
    var currentState: ExerciseState
        get() = _currentState
        protected set(value) { _currentState = value }

    private var listener: ExerciseListener? = null
    protected var startTime: Long = 0L

    /**
     * Set listener for exercise events.
     */
    fun setListener(listener: ExerciseListener?) {
        this.listener = listener
    }

    /**
     * Notify listener of rep count update.
     */
    protected fun notifyRepCountUpdated() {
        // DEBUG: Log callback firing
        if (DebugConfig.DEBUG_MODE && DebugConfig.LOG_CALLBACKS) {
            android.util.Log.d(DebugConfig.TAG_CALLBACK, "notifyRepCountUpdated() called | " +
                "count=$repCount | " +
                "exercise=${getName()} | " +
                "listener=${if (listener != null) "SET" else "NULL"}")
        }
        listener?.onRepCountUpdated(repCount, getName())
    }

    /**
     * Notify listener of state change.
     */
    protected fun notifyStateChanged(details: String = "") {
        // DEBUG: Log state change callback
        if (DebugConfig.DEBUG_MODE && DebugConfig.LOG_CALLBACKS) {
            android.util.Log.d(DebugConfig.TAG_CALLBACK, "notifyStateChanged() | " +
                "state=$currentState | " +
                "details='$details'")
        }
        listener?.onStateChanged(currentState, details)
    }

    /**
     * Notify listener of validation error.
     */
    protected fun notifyValidationError(reason: String) {
        listener?.onValidationError(reason)
    }

    /**
     * Notify listener of exercise completion.
     */
    protected fun notifyExerciseComplete() {
        val duration = (System.currentTimeMillis() - startTime) / 1000
        listener?.onExerciseComplete(repCount, duration)
    }
}

