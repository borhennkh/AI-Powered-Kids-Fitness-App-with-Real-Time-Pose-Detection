package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(

    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,

    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,

    // GPU default
    var currentDelegate: Int = DELEGATE_GPU,

    var runningMode: RunningMode = RunningMode.LIVE_STREAM,

    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null

) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    fun setupPoseLandmarker() {

        val baseOptionBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }

        val modelName = when (currentModel) {
            MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
            MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
            else -> "pose_landmarker_full.task"
        }

        baseOptionBuilder.setModelAssetPath(modelName)

        try {

            val baseOptions = baseOptionBuilder.build()

            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            poseLandmarker =
                PoseLandmarker.createFromOptions(context, optionsBuilder.build())

        } catch (e: RuntimeException) {

            poseLandmarkerHelperListener?.onError(
                "PoseLandmarker init failed",
                GPU_ERROR
            )

            Log.e(TAG, "Init error: ${e.message}")
        }
    }

    // ================= LIVE CAMERA ================= //


    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {

        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

        bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        val matrix = Matrix().apply {

            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true
        // DEBUG: Track frame processing
        if (DebugConfig.DEBUG_MODE && DebugConfig.LOG_EVERY_FRAME) {
            Log.d(DebugConfig.TAG_PIPELINE, "detectLiveStream() called | " +
                "Camera: ${if (isFrontCamera) "FRONT" else "BACK"} | " +
                "Size: ${imageProxy.width}x${imageProxy.height}")
        }
        )

        imageProxy.close()

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    // ================= IMAGE MODE (GalleryFragment needs this) =================

    fun detectImage(image: Bitmap): ResultBundle? {

        val startTime = SystemClock.uptimeMillis()

        val mpImage = BitmapImageBuilder(image).build()

        poseLandmarker?.detect(mpImage)?.also { result ->

            val inferenceTime = SystemClock.uptimeMillis() - startTime

            return ResultBundle(
                listOf(result),
                inferenceTime,
                image.height,
                image.width
            )
        }

        poseLandmarkerHelperListener?.onError("Image detection failed", OTHER_ERROR)

        return null
    }

    // ================= VIDEO MODE (GalleryFragment needs this) =================

    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong() ?: return null

        val resultList = mutableListOf<PoseLandmarkerResult>()

        var timestampMs = 0L

        while (timestampMs < videoLengthMs) {

            val frame =
                retriever.getFrameAtTime(timestampMs * 1000)

            frame?.let {

                val bitmap =
                    if (it.config == Bitmap.Config.ARGB_8888) it
                    else it.copy(Bitmap.Config.ARGB_8888, false)

                val mpImage = BitmapImageBuilder(bitmap).build()

                poseLandmarker?.detectForVideo(mpImage, timestampMs)
                    ?.let { result ->
                        resultList.add(result)
                    }
            }

            timestampMs += inferenceIntervalMs
        }

        retriever.release()

        return ResultBundle(
            resultList,
            0,
            0,
            0
        )
    }

    // ================= CALLBACK =================

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {

        //  YOUR CUSTOM PIPELINE ENTRY
        processPoseLandmarks(result)

        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                SystemClock.uptimeMillis() - result.timestampMs(),
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {

        poseLandmarkerHelperListener?.onError(
            error.message ?: "Unknown error",
            OTHER_ERROR
        )
    }

    // ================= YOUR AI HOOK =================

    // ========== SQUAT PHASE STATE MACHINE (PRODUCTION-STABLE) ==========
    private enum class SquatPhase {
        READY,      // Standing upright, ready to start squat
        DESCENDING, // Moving down into squat
        BOTTOM,     // At bottom of squat (deep position)
        ASCENDING   // Rising back up to standing
    }

    // State machine variables
    private var squatCount = 0
        val inferenceTime = SystemClock.uptimeMillis() - result.timestampMs()

        // DEBUG: Track MediaPipe inference completion
        if (DebugConfig.DEBUG_MODE) {
            val landmarkCount = result.landmarks().firstOrNull()?.size ?: 0
            if (DebugConfig.LOG_EVERY_FRAME) {
                Log.d(DebugConfig.TAG_PIPELINE, "returnLivestreamResult() | " +
                    "InferenceTime: ${inferenceTime}ms | " +
                    "Landmarks: $landmarkCount")
            }
        }
    // ========== GAME-GRADE ANGLE THRESHOLDS (FORGIVING) ==========
    // Relaxed thresholds for reliable gameplay experience
    private val SQUAT_DOWN_ANGLE = 130f      // Start descending (from READY → DESCENDING)
    private val SQUAT_BOTTOM_ANGLE = 90f     // Reached bottom (from DESCENDING → BOTTOM)
                inferenceTime,

    // Cooldown and validation thresholds
    private val SQUAT_COOLDOWN = 800L // Milliseconds between valid reps (anti-double-count)

    // ========== SIMPLIFIED ARM VALIDATION (X/Y ONLY - NO Z-AXIS) ==========
    // All thresholds are RELATIVE to torso length for distance independence
    private val ARM_HORIZONTAL_Y_RATIO = 0.25f // Wrist-Shoulder Y tolerance (relative to torso)
    private val ARM_FORWARD_X_RATIO = 0.50f // Wrist X-distance tolerance (very forgiving)

    // ========== SIMPLIFIED FACING CAMERA (X-ONLY - NO Z-AXIS) ==========
    private val MIN_SHOULDER_WIDTH_RATIO = 0.18f // Shoulder width relative to torso (forgiving)

    private fun processPoseLandmarks(result: PoseLandmarkerResult) {

        fun onSquatCountUpdated(count: Int)
    }
}
