package com.google.mediapipe.examples.poselandmarker

/**
 * Debug configuration for squat counter troubleshooting.
 *
 * USAGE:
 * Set DEBUG_MODE = true to enable all debug logging.
 * Set DEBUG_MODE = false for production (removes log overhead).
 *
 * LOG TAGS:
 * - PIPELINE: Camera + MediaPipe flow
 * - SQUAT: Squat detection logic
 * - STATE: Phase state transitions
 * - COUNT: Rep counting events
 * - CALLBACK: UI callback events
 */
object DebugConfig {

    /**
     * Master debug switch.
     * Turn this ON when debugging squat counter issues.
     * Turn this OFF for production builds.
     */
    const val DEBUG_MODE = true

    /**
     * Log every frame (warning: very verbose!)
     * Only enable for short debug sessions.
     */
    const val LOG_EVERY_FRAME = false

    /**
     * Log validation details (facingCamera, armsForward, etc.)
     */
    const val LOG_VALIDATION = true

    /**
     * Log angle calculations
     */
    const val LOG_ANGLES = true

    /**
     * Log state machine transitions
     */
    const val LOG_STATE_TRANSITIONS = true

    /**
     * Log callback execution
     */
    const val LOG_CALLBACKS = true

    /**
     * Log pose processor validation
     */
    const val LOG_POSE_VALIDATION = true

    // Log tag constants
    const val TAG_PIPELINE = "PIPELINE"
    const val TAG_SQUAT = "SQUAT"
    const val TAG_STATE = "STATE"
    const val TAG_COUNT = "COUNT"
    const val TAG_CALLBACK = "CALLBACK"
}

