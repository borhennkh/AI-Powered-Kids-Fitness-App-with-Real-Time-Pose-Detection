# 🏗️ CLEAN ARCHITECTURE DOCUMENTATION
# MediaPipe Pose Exercise Detection Engine

**Project:** Smart Childhood - Gamified Kids Fitness App  
**Date:** January 2026  
**Architecture:** Modular AI Motion Detection Engine  
**Future Platform Support:** Android Native, Flutter, Unity

---

## 📋 TABLE OF CONTENTS

1. [Architecture Overview](#architecture-overview)
2. [Module Breakdown](#module-breakdown)
3. [Data Flow](#data-flow)
4. [File Structure](#file-structure)
5. [How to Add New Exercises](#how-to-add-new-exercises)
6. [Cross-Platform Integration](#cross-platform-integration)
7. [Performance & Threading](#performance--threading)

---

## 🏛️ ARCHITECTURE OVERVIEW

### Design Principles

✅ **Separation of Concerns**: MediaPipe, Exercise Logic, and UI are completely decoupled  
✅ **SOLID Principles**: Single Responsibility, Open/Closed, Dependency Inversion  
✅ **Clean Interfaces**: No MediaPipe dependencies in exercise modules  
✅ **Extensibility**: Easy to add new exercises without touching existing code  
✅ **Cross-Platform Ready**: Core logic portable to Flutter/Unity  

### Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │CameraFragment│  │ DebugOverlay │  │CounterTextView│      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ ExerciseManagerListener (callbacks)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      CORE ENGINE LAYER                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              ExerciseManager                          │  │
│  │  - Routes poses to active exercise                    │  │
│  │  - Switches exercises dynamically                     │  │
│  │  - Emits events to UI                                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ▲                                 │
│                            │ NormalizedPose                  │
│                            │                                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              PoseProcessor                            │  │
│  │  - Converts MediaPipe results to NormalizedPose       │  │
│  │  - Validates pose quality                             │  │
│  │  - Abstracts MediaPipe internals                      │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ PoseLandmarkerResult
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  MEDIAPIPE WRAPPER LAYER                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │            PoseLandmarkerHelper                       │  │
│  │  - MediaPipe SDK initialization                       │  │
│  │  - Camera frame processing                            │  │
│  │  - GPU/CPU delegate management                        │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ Camera Frames
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      CAMERA LAYER                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              CameraX (Android)                        │  │
│  │  - Front camera (LENS_FACING_FRONT)                   │  │
│  │  - ImageAnalysis pipeline                             │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Exercise Module Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    EXERCISE MODULES                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              BaseExercise (Abstract)                  │  │
│  │  - processPose(pose: NormalizedPose)                  │  │
│  │  - reset()                                            │  │
│  │  - getRepCount()                                      │  │
│  │  - getCurrentState()                                  │  │
│  │  - isPoseValid()                                      │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ▲                                 │
│                            │ Implements                      │
│          ┌─────────────────┼─────────────────┐             │
│          │                 │                 │              │
│  ┌───────────────┐ ┌──────────────┐ ┌──────────────┐      │
│  │ SquatExercise │ │JumpingJack.. │ │ JumpExercise │      │
│  │               │ │              │ │              │       │
│  │ - Knee angle  │ │ - Arm up/down│ │ - Vertical   │      │
│  │ - Arm forward │ │ - Leg apart  │ │   velocity   │      │
│  │ - State mach. │ │ - Sync check │ │ - Flight time│      │
│  └───────────────┘ └──────────────┘ └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 MODULE BREAKDOWN

### 1. **PoseLandmarkerHelper** (MediaPipe Wrapper)

**Location:** `PoseLandmarkerHelper.kt`

**Responsibility:** ONLY MediaPipe operations

**What it does:**
- Initializes MediaPipe Pose Landmarker
- Receives camera frames from CameraX
- Runs MediaPipe inference (GPU/CPU)
- Returns `PoseLandmarkerResult` via callback

**What it does NOT do:**
- ❌ No exercise logic
- ❌ No angle calculations
- ❌ No state machines
- ❌ No rep counting

**Key Methods:**
```kotlin
fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean)
fun setupPoseLandmarker()
fun clearPoseLandmarker()
```

**Callbacks:**
```kotlin
interface LandmarkerListener {
    fun onResults(resultBundle: ResultBundle)
    fun onError(error: String, errorCode: Int)
}
```

---

### 2. **PoseProcessor** (Data Converter)

**Location:** `core/PoseProcessor.kt`

**Responsibility:** Convert MediaPipe results to clean data model

**What it does:**
- Extracts landmarks from `PoseLandmarkerResult`
- Creates `NormalizedPose` object
- Validates pose completeness (all required landmarks visible)
- Calculates pose quality score

**Key Methods:**
```kotlin
fun processResult(result: PoseLandmarkerResult): NormalizedPose?
fun getPoseQuality(pose: NormalizedPose): Float
```

**Why it exists:**
- Exercise modules should NEVER depend on MediaPipe SDK
- Future platforms (Flutter/Unity) will have their own pose processors
- Single source of truth for pose data format

---

### 3. **NormalizedPose** (Data Model)

**Location:** `models/NormalizedPose.kt`

**Responsibility:** Clean pose data structure

**What it contains:**
- Map of `PoseLandmark` → `NormalizedLandmark`
- Timestamp
- Helper methods for calculations

**Key Methods:**
```kotlin
fun calculateAngle(pointA, vertex, pointC): Float
fun calculateDistance(point1, point2): Float
fun getLandmark(landmark: PoseLandmark): NormalizedLandmark?
```

**Why it exists:**
- Distance-independent (all coordinates 0.0-1.0)
- Platform-agnostic
- Easy to serialize for Flutter/Unity communication

---

### 4. **ExerciseManager** (Routing & Coordination)

**Location:** `core/ExerciseManager.kt`

**Responsibility:** Central exercise coordination

**What it does:**
- Holds reference to active exercise
- Routes pose frames to active exercise
- Switches exercises dynamically
- Forwards exercise events to UI layer

**Key Methods:**
```kotlin
fun setActiveExercise(exercise: BaseExercise)
fun processPose(pose: NormalizedPose)
fun resetCurrentExercise()
fun getCurrentRepCount(): Int
```

**Callbacks:**
```kotlin
interface ExerciseManagerListener {
    fun onRepCountUpdated(count: Int, exerciseName: String)
    fun onStateChanged(state: ExerciseState, details: String)
    fun onValidationError(reason: String)
    fun onExerciseComplete(totalReps: Int, duration: Long)
    fun onExerciseChanged(exerciseName: String)
}
```

**Why it exists:**
- Single entry point for all exercises
- Easy to add exercise switching UI
- Future Flutter/Unity integration point

---

### 5. **BaseExercise** (Abstract Interface)

**Location:** `exercises/base/BaseExercise.kt`

**Responsibility:** Define exercise contract

**Abstract Methods (MUST implement):**
```kotlin
fun processPose(pose: NormalizedPose)
fun reset()
fun getRepCount(): Int
fun getCurrentState(): ExerciseState
fun getName(): String
fun getDescription(): String
fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String>
```

**Shared State:**
- `repCount: Int`
- `currentState: ExerciseState`
- `listener: ExerciseListener?`
- `startTime: Long`

**Helper Methods:**
```kotlin
protected fun notifyRepCountUpdated()
protected fun notifyStateChanged(details: String)
protected fun notifyValidationError(reason: String)
```

---

### 6. **SquatExercise** (Implementation Example)

**Location:** `exercises/squat/SquatExercise.kt`

**Responsibility:** Air squat detection

**Detection Logic:**
- Multi-phase state machine: `READY → DESCENDING → BOTTOM → ASCENDING → READY`
- Knee angle calculation (Hip-Knee-Ankle)
- Arm horizontal validation
- Facing camera validation
- Cooldown debounce (800ms)

**Thresholds:**
```kotlin
SQUAT_DOWN_ANGLE = 130°
SQUAT_BOTTOM_ANGLE = 90°
SQUAT_UP_ANGLE = 155°
ARM_HORIZONTAL_Y_RATIO = 0.25f (relative to torso)
ARM_FORWARD_X_RATIO = 0.50f (forgiving)
MIN_SHOULDER_WIDTH_RATIO = 0.18f
SQUAT_COOLDOWN_MS = 800ms
```

**State Machine:**
```
READY (standing) 
  → kneeAngle < 130° + arms forward + facing camera
  → DESCENDING

DESCENDING (going down)
  → kneeAngle < 90°
  → BOTTOM

BOTTOM (deep squat)
  → kneeAngle > 90°
  → ASCENDING

ASCENDING (rising up)
  → kneeAngle > 155° + cooldown passed
  → READY (COUNT REP)
```

---

### 7. **CameraFragment** (UI Controller)

**Location:** `fragment/CameraFragment.kt`

**Responsibility:** Camera setup and UI coordination

**What it does:**
- Initializes CameraX
- Creates PoseLandmarkerHelper
- Creates ExerciseManager
- Implements both listeners:
  - `PoseLandmarkerHelper.LandmarkerListener`
  - `ExerciseManager.ExerciseManagerListener`
- Routes MediaPipe results to ExerciseManager
- Updates UI based on exercise events

**Key Flow:**
```kotlin
onResults(resultBundle) {
    // 1. Update overlay for landmark visualization
    overlay.setResults(result)
    
    // 2. Convert to NormalizedPose
    val normalizedPose = PoseProcessor.processResult(result)
    
    // 3. Route to ExerciseManager
    exerciseManager.processPose(normalizedPose)
}
```

---

## 🔄 DATA FLOW

### Complete Pipeline (Frame-by-Frame)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. CAMERA CAPTURE                                            │
│    CameraX captures frame (front camera)                     │
│    → ImageProxy (RGBA_8888)                                  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. MEDIAPIPE PROCESSING                                      │
│    PoseLandmarkerHelper.detectLiveStream()                   │
│    → Bitmap transformation (rotation + flip for selfie)      │
│    → MPImage                                                 │
│    → MediaPipe inference (GPU/CPU)                           │
│    → PoseLandmarkerResult (33 landmarks)                     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. CALLBACK TO UI                                            │
│    CameraFragment.onResults(resultBundle)                    │
│    → Update OverlayView (draw skeleton)                      │
│    → Update inference time display                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. POSE CONVERSION                                           │
│    PoseProcessor.processResult(result)                       │
│    → Extract landmarks                                       │
│    → Validate completeness                                   │
│    → Create NormalizedPose                                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. EXERCISE ROUTING                                          │
│    ExerciseManager.processPose(normalizedPose)               │
│    → Forward to activeExercise                               │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. EXERCISE LOGIC                                            │
│    SquatExercise.processPose(pose)                           │
│    → Calculate knee angle                                    │
│    → Validate arms + facing camera                           │
│    → Update state machine                                    │
│    → Count reps (if state ASCENDING → READY)                │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 7. EXERCISE CALLBACKS                                        │
│    SquatExercise → ExerciseManager → CameraFragment          │
│    → onRepCountUpdated(count, "Air Squat")                   │
│    → onStateChanged(state, details)                          │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 8. UI UPDATE                                                 │
│    CameraFragment updates TextViews                          │
│    → exercise_counter_text: "Air Squat: 5"                   │
│    → exercise_state_text: "Ready"                            │
└─────────────────────────────────────────────────────────────┘
```

**Frame Rate:** ~30 FPS (camera) → ~30 FPS (MediaPipe) → ~30 FPS (exercise logic)

**Latency Breakdown:**
- Camera capture: 0ms (async)
- MediaPipe inference: 15-30ms (GPU)
- Pose processing: <1ms
- Exercise logic: <1ms
- UI update: <1ms
- **Total: ~20-35ms per frame**

---

## 📂 FILE STRUCTURE

```
app/src/main/java/com/google/mediapipe/examples/poselandmarker/
│
├── 📁 core/                           # CORE ENGINE (Reusable across platforms)
│   ├── ExerciseManager.kt             # Exercise routing & coordination
│   └── PoseProcessor.kt               # MediaPipe → NormalizedPose converter
│
├── 📁 models/                          # DATA MODELS (Platform-agnostic)
│   └── NormalizedPose.kt              # Clean pose data + helper methods
│
├── 📁 exercises/                       # EXERCISE MODULES (Plug & play)
│   ├── 📁 base/
│   │   └── BaseExercise.kt            # Abstract exercise interface
│   ├── 📁 squat/
│   │   └── SquatExercise.kt           # ✅ IMPLEMENTED
│   ├── 📁 jumpingjack/
│   │   └── JumpingJackExercise.kt     # ⚠️ STUB (to be implemented)
│   └── 📁 jump/
│       └── JumpExercise.kt            # ⚠️ STUB (to be implemented)
│
├── 📁 fragment/                        # UI LAYER (Android-specific)
│   ├── CameraFragment.kt              # Camera + Exercise coordination
│   ├── GalleryFragment.kt             # Video/image processing
│   └── PermissionsFragment.kt         # Camera permissions
│
├── PoseLandmarkerHelper.kt            # MEDIAPIPE WRAPPER (Android-specific)
├── OverlayView.kt                     # Skeleton visualization
├── MainViewModel.kt                   # UI state management
└── MainActivity.kt                    # App entry point
```

### File Classification

**CORE LOGIC (Reusable):**
- ✅ `core/ExerciseManager.kt`
- ✅ `core/PoseProcessor.kt`
- ✅ `models/NormalizedPose.kt`
- ✅ `exercises/base/BaseExercise.kt`
- ✅ `exercises/squat/SquatExercise.kt`

**UI ONLY (Android-specific):**
- 📱 `fragment/CameraFragment.kt`
- 📱 `OverlayView.kt`
- 📱 `MainViewModel.kt`
- 📱 `MainActivity.kt`

**MEDIAPIPE WRAPPER (Platform-specific):**
- 🔧 `PoseLandmarkerHelper.kt` (Android + MediaPipe SDK)

---

## ➕ HOW TO ADD NEW EXERCISES

### Step 1: Create Exercise Class

Create file: `exercises/myexercise/MyExercise.kt`

```kotlin
package com.google.mediapipe.examples.poselandmarker.exercises.myexercise

import com.google.mediapipe.examples.poselandmarker.exercises.base.BaseExercise
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose
import com.google.mediapipe.examples.poselandmarker.models.PoseLandmark

class MyExercise : BaseExercise() {
    
    companion object {
        private const val TAG = "MyExercise"
        
        // Define your thresholds here
        private const val MY_THRESHOLD = 120f
    }
    
    // Your state machine enum (if needed)
    private enum class MyPhase {
        READY,
        PHASE_1,
        PHASE_2
    }
    
    private var phase = MyPhase.READY
    
    override fun processPose(pose: NormalizedPose) {
        // 1. Extract landmarks
        val shoulder = pose.getLandmark(PoseLandmark.LEFT_SHOULDER) ?: return
        val elbow = pose.getLandmark(PoseLandmark.LEFT_ELBOW) ?: return
        
        // 2. Calculate metrics
        val angle = pose.calculateAngle(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST
        )
        
        // 3. State machine logic
        when (phase) {
            MyPhase.READY -> {
                if (angle < MY_THRESHOLD) {
                    phase = MyPhase.PHASE_1
                    currentState = ExerciseState.IN_PROGRESS
                }
            }
            MyPhase.PHASE_1 -> {
                // ... your logic
            }
            MyPhase.PHASE_2 -> {
                // Count rep
                repCount++
                notifyRepCountUpdated()
                phase = MyPhase.READY
            }
        }
    }
    
    override fun reset() {
        repCount = 0
        phase = MyPhase.READY
        currentState = ExerciseState.IDLE
    }
    
    override fun getRepCount(): Int = repCount
    override fun getCurrentState(): ExerciseState = currentState
    override fun getName(): String = "My Exercise"
    override fun getDescription(): String = "Description of my exercise"
    
    override fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String> {
        // Validate pose before starting
        return Pair(true, "Ready!")
    }
}
```

### Step 2: Add Button to UI

Edit `res/layout/fragment_camera.xml`:

```xml
<Button
    android:id="@+id/button_my_exercise"
    android:layout_width="120dp"
    android:layout_height="40dp"
    android:layout_marginTop="4dp"
    android:text="My Exercise"
    android:textSize="12sp"
    android:backgroundTint="#FF5722" />
```

### Step 3: Wire Button in CameraFragment

Edit `CameraFragment.kt` → `initExerciseSwitcher()`:

```kotlin
fragmentCameraBinding.buttonMyExercise.setOnClickListener {
    exerciseManager.setActiveExercise(MyExercise())
}
```

**DONE!** No changes needed in:
- ❌ PoseLandmarkerHelper
- ❌ ExerciseManager
- ❌ PoseProcessor
- ❌ MediaPipe pipeline

---

## 🌐 CROSS-PLATFORM INTEGRATION

### Flutter Integration (Planned)

**Architecture:**
```
Flutter App
    ↓ Platform Channel
Android Native (ExerciseManager)
    ↓ JNI / Platform Channel
Exercise Logic (Kotlin)
```

**Flutter Side:**
```dart
class ExerciseEngine {
  static const platform = MethodChannel('exercise_engine');
  
  Future<void> setExercise(String exerciseName) async {
    await platform.invokeMethod('setExercise', {'name': exerciseName});
  }
  
  Stream<ExerciseEvent> get exerciseEvents {
    return EventChannel('exercise_events').receiveBroadcastStream();
  }
}
```

**Android Side (Platform Channel Handler):**
```kotlin
class ExerciseEnginePlugin : FlutterPlugin, MethodCallHandler {
    private val exerciseManager = ExerciseManager()
    
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "setExercise" -> {
                val name = call.argument<String>("name")
                when (name) {
                    "squat" -> exerciseManager.setActiveExercise(SquatExercise())
                    // ...
                }
            }
        }
    }
}
```

**Reusable Components:**
- ✅ ExerciseManager
- ✅ PoseProcessor (adapt to ML Kit or TFLite)
- ✅ All BaseExercise implementations
- ✅ NormalizedPose data model

**Platform-Specific:**
- 🔧 Pose detection (ML Kit instead of MediaPipe)
- 🔧 Platform channel handlers

---

### Unity Integration (Planned)

**Architecture:**
```
Unity Game (C#)
    ↓ JNI / Android Plugin
Android Native (ExerciseManager)
    ↓
Exercise Logic (Kotlin)
```

**Unity Side (C#):**
```csharp
public class ExerciseEngine : MonoBehaviour {
    private AndroidJavaObject exerciseManager;
    
    void Start() {
        exerciseManager = new AndroidJavaObject("com.yourapp.ExerciseManagerBridge");
        exerciseManager.Call("setExercise", "squat");
    }
    
    void OnRepCountUpdated(string data) {
        // Called from native via UnitySendMessage
        int count = JsonUtility.FromJson<RepData>(data).count;
        UpdateUI(count);
    }
}
```

**Android Bridge (Kotlin):**
```kotlin
class ExerciseManagerBridge : ExerciseManager.ExerciseManagerListener {
    private val exerciseManager = ExerciseManager()
    
    fun setExercise(name: String) {
        when (name) {
            "squat" -> exerciseManager.setActiveExercise(SquatExercise())
        }
    }
    
    override fun onRepCountUpdated(count: Int, exerciseName: String) {
        // Send to Unity
        UnityPlayer.UnitySendMessage(
            "ExerciseEngine",
            "OnRepCountUpdated",
            """{"count": $count, "name": "$exerciseName"}"""
        )
    }
}
```

**Reusable Components:**
- ✅ ExerciseManager
- ✅ All BaseExercise implementations
- ✅ NormalizedPose data model

---

## ⚡ PERFORMANCE & THREADING

### Thread Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ MAIN THREAD (UI)                                             │
│  - CameraFragment UI updates                                 │
│  - TextView updates (counter, state)                         │
│  - Button clicks                                             │
│  - Toast messages                                            │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ runOnUiThread()
                            │
┌─────────────────────────────────────────────────────────────┐
│ BACKGROUND THREAD (Single Thread Executor)                   │
│  - MediaPipe initialization                                  │
│  - MediaPipe.detectAsync()                                   │
│  - Camera frame preprocessing                                │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │
┌─────────────────────────────────────────────────────────────┐
│ MEDIAPIPE INTERNAL THREADS                                   │
│  - GPU inference (TensorFlow Lite GPU Delegate)              │
│  - Landmark post-processing                                  │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ Callback (async)
                            │
┌─────────────────────────────────────────────────────────────┐
│ CALLBACK THREAD (MediaPipe Thread)                           │
│  - returnLivestreamResult()                                  │
│  - PoseProcessor.processResult()                             │
│  - ExerciseManager.processPose()                             │
│  - SquatExercise.processPose()                               │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ runOnUiThread() for UI updates
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ MAIN THREAD (UI Update)                                      │
│  - Update counter text                                       │
│  - Update state text                                         │
└─────────────────────────────────────────────────────────────┘
```

### Performance Characteristics

**Frame Processing Time:**
- Camera capture: 0ms (async)
- MediaPipe inference: 15-30ms (GPU)
- Pose normalization: <1ms
- Exercise logic: <1ms
- Total: **~20-35ms per frame**

**Frame Rate:**
- Target: 30 FPS
- Actual: 28-30 FPS (depends on device)

**Memory Usage:**
- MediaPipe model: ~15MB (GPU delegate)
- Bitmap buffers: ~5MB
- Exercise state: <1MB
- Total: **~20MB**

**GPU Usage:**
- MediaPipe inference uses GPU delegate by default
- Falls back to CPU if GPU fails
- Can toggle in UI (bottom sheet settings)

### Optimization Tips

**DO:**
- ✅ Use GPU delegate for MediaPipe
- ✅ Keep exercise logic lightweight (<1ms)
- ✅ Batch UI updates (don't update every frame)
- ✅ Use debouncing for validation errors

**DON'T:**
- ❌ Don't run heavy calculations in exercise logic
- ❌ Don't update UI every frame (causes jank)
- ❌ Don't use Z-axis unless absolutely necessary (less stable)
- ❌ Don't add logging in hot paths (use periodic logging)

---

## 🎯 KEY TAKEAWAYS

### What Makes This Architecture Good

1. **Separation of Concerns**
   - MediaPipe logic isolated in PoseLandmarkerHelper
   - Exercise logic isolated in exercise modules
   - UI logic isolated in CameraFragment

2. **Extensibility**
   - Add new exercises without touching existing code
   - Just implement BaseExercise interface
   - Plug & play

3. **Cross-Platform Ready**
   - Core logic (ExerciseManager, BaseExercise, NormalizedPose) is portable
   - Only MediaPipe wrapper and UI need platform-specific implementation
   - Flutter/Unity can reuse 80% of logic

4. **Clean Interfaces**
   - Exercise modules never touch MediaPipe SDK
   - UI never touches MediaPipe SDK
   - Everything communicates via clean callbacks

5. **Testable**
   - Each module can be unit tested independently
   - Mock NormalizedPose for exercise testing
   - Mock ExerciseManager for UI testing

### Next Steps

**Short Term:**
- ✅ Squat detection complete
- ⚠️ Implement JumpingJackExercise
- ⚠️ Implement JumpExercise
- ⚠️ Add exercise switching UI improvements
- ⚠️ Add pose quality feedback UI

**Medium Term:**
- 📱 Flutter integration (platform channels)
- 📱 Unity integration (Android plugin)
- 🎮 Add game scoring system
- 🎮 Add multiplayer support

**Long Term:**
- 🌐 Backend integration (Firebase)
- 🌐 Leaderboards
- 🌐 Exercise library expansion
- 🌐 AI form feedback (pose quality scoring)

---

## 📞 CONTACT & SUPPORT

**Questions about architecture?**
- Review this document
- Check inline code comments
- Each module has extensive documentation

**Adding a new exercise?**
- Follow the "How to Add New Exercises" section
- Copy SquatExercise.kt as template
- Implement BaseExercise interface

**Cross-platform integration?**
- Start with ExerciseManager as integration point
- Reuse PoseProcessor logic (adapt pose source)
- All BaseExercise implementations are portable

---

**ARCHITECTURE VERSION:** 1.0  
**LAST UPDATED:** January 2026  
**STATUS:** Production-Ready (Squat), Extensible (Future Exercises)

