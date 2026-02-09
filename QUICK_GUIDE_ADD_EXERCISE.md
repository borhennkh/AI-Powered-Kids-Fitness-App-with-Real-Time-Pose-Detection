# 🎯 QUICK GUIDE: Adding a New Exercise

## 3-Step Process

### Step 1: Create Exercise Class

**File:** `exercises/myexercise/MyExercise.kt`

```kotlin
package com.google.mediapipe.examples.poselandmarker.exercises.myexercise

import android.util.Log
import com.google.mediapipe.examples.poselandmarker.exercises.base.BaseExercise
import com.google.mediapipe.examples.poselandmarker.models.NormalizedPose
import com.google.mediapipe.examples.poselandmarker.models.PoseLandmark

class MyExercise : BaseExercise() {
    
    companion object {
        private const val TAG = "MyExercise"
        // Your thresholds here
    }
    
    override fun processPose(pose: NormalizedPose) {
        // Your detection logic
        // Example:
        val angle = pose.calculateAngle(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST
        )
        
        // State machine logic
        // When rep complete:
        repCount++
        notifyRepCountUpdated()
    }
    
    override fun reset() {
        repCount = 0
        currentState = ExerciseState.IDLE
        startTime = System.currentTimeMillis()
    }
    
    override fun getRepCount(): Int = repCount
    override fun getCurrentState(): ExerciseState = currentState
    override fun getName(): String = "My Exercise"
    override fun getDescription(): String = "Exercise description"
    override fun isPoseValid(pose: NormalizedPose): Pair<Boolean, String> {
        return Pair(true, "Ready!")
    }
}
```

### Step 2: Add UI Button

**File:** `res/layout/fragment_camera.xml`

Add inside the `<LinearLayout>` in exercise switcher:

```xml
<Button
    android:id="@+id/button_my_exercise"
    android:layout_width="120dp"
    android:layout_height="40dp"
    android:layout_marginTop="4dp"
    android:text="My Exercise"
    android:textSize="12sp"
    android:backgroundTint="#YOUR_COLOR" />
```

### Step 3: Wire Button

**File:** `fragment/CameraFragment.kt`

Add in `initExerciseSwitcher()`:

```kotlin
fragmentCameraBinding.buttonMyExercise.setOnClickListener {
    exerciseManager.setActiveExercise(MyExercise())
}
```

**DONE!** Build and test.

---

## Common Patterns

### State Machine Pattern

```kotlin
private enum class MyPhase {
    READY,
    PHASE_1,
    PHASE_2
}

private var phase = MyPhase.READY

override fun processPose(pose: NormalizedPose) {
    when (phase) {
        MyPhase.READY -> {
            if (condition) {
                phase = MyPhase.PHASE_1
                currentState = ExerciseState.IN_PROGRESS
            }
        }
        MyPhase.PHASE_1 -> {
            if (condition) {
                phase = MyPhase.PHASE_2
            }
        }
        MyPhase.PHASE_2 -> {
            if (condition) {
                repCount++
                notifyRepCountUpdated()
                phase = MyPhase.READY
                currentState = ExerciseState.READY
            }
        }
    }
}
```

### Angle Calculation

```kotlin
val angle = pose.calculateAngle(
    PoseLandmark.POINT_A,  // First point
    PoseLandmark.POINT_B,  // Vertex (middle point)
    PoseLandmark.POINT_C   // Third point
)
// Returns angle in degrees (0-180)
```

### Distance Calculation

```kotlin
val distance = pose.calculateDistance(
    PoseLandmark.POINT_A,
    PoseLandmark.POINT_B
)
// Returns normalized distance (0.0-1.0)
```

### Cooldown Protection

```kotlin
private var lastRepTime: Long = 0L
private val COOLDOWN_MS = 800L

// When counting rep:
val currentTime = System.currentTimeMillis()
if (currentTime - lastRepTime > COOLDOWN_MS) {
    repCount++
    lastRepTime = currentTime
    notifyRepCountUpdated()
}
```

### Body Size Normalization

```kotlin
val torsoLength = pose.calculateDistance(
    PoseLandmark.LEFT_SHOULDER,
    PoseLandmark.LEFT_HIP
)

// Use relative thresholds:
val threshold = torsoLength * RATIO_CONSTANT
```

---

## Tips

✅ **DO:**
- Use state machines for multi-phase movements
- Use body-relative thresholds (torsoLength * ratio)
- Add cooldown protection (prevent double counting)
- Log state transitions for debugging
- Notify on state changes for UI feedback

❌ **DON'T:**
- Use absolute thresholds (distance-dependent)
- Use Z-axis unless necessary (less stable)
- Update UI in exercise logic (use callbacks)
- Add heavy calculations (keep <1ms)
- Use MediaPipe types directly

---

## Testing

```kotlin
// Manually test:
1. Build app
2. Select your exercise
3. Perform movement
4. Check logs:
   adb logcat | grep "MyExercise"
5. Verify counter updates
```

---

## Examples

**Look at:**
- `SquatExercise.kt` - Full implementation reference
- `JumpingJackExercise.kt` - Stub template

**Key sections:**
- State machine logic
- Angle calculations
- Cooldown pattern
- Validation checks

