# SUMO HIGH PULL EXERCISE - DEBUG DOCUMENTATION

**Generated:** Static Code Analysis  
**Purpose:** Complete technical documentation of existing implementation  
**Status:** DO NOT MODIFY - Documentation Only

---

## 1. File Locations

### 1.1 Exercise Class File

| File | Path |
|------|------|
| **SumoHighPullExercise.kt** | `app/src/main/java/com/google/mediapipe/examples/poselandmarker/exercises/sumohighpull/SumoHighPullExercise.kt` |

**Total Lines:** ~240 lines

### 1.2 ExerciseManager Integration

| File | Path | Integration Point |
|------|------|-------------------|
| **ExerciseManager.kt** | `app/src/main/java/com/google/mediapipe/examples/poselandmarker/core/ExerciseManager.kt` | `setActiveExercise()` method accepts any `BaseExercise` subclass |

### 1.3 UI Selector Integration

| File | Path | Line Range |
|------|------|------------|
| **CameraFragment.kt** | `app/src/main/java/com/google/mediapipe/examples/poselandmarker/fragment/CameraFragment.kt` | Lines 502-505 |
| **fragment_camera.xml** | `app/src/main/res/layout/fragment_camera.xml` | Button `@+id/button_sumo_high_pull` |

**CameraFragment.kt - Button Handler (Lines 502-505):**
```kotlin
fragmentCameraBinding.buttonSumoHighPull.setOnClickListener {
    exerciseManager.setActiveExercise(SumoHighPullExercise())
    Log.d(TAG, "✅ Switched to Sumo High Pull")
}
```

### 1.4 Helper Methods Used

| Method | Source File | Purpose |
|--------|-------------|---------|
| `pose.calculateAngle()` | `NormalizedPose.kt` (Lines 26-42) | Computes angle between 3 landmarks |
| `pose.calculateDistance()` | `NormalizedPose.kt` (Lines 48-56) | Computes Euclidean distance between 2 landmarks |
| `pose.getLandmark()` | `NormalizedPose.kt` (Lines 61-63) | Safe access to individual landmarks |
| `notifyRepCountUpdated()` | `BaseExercise.kt` (Lines 130-138) | Triggers UI callback |
| `notifyStateChanged()` | `BaseExercise.kt` (Lines 143-151) | Triggers state callback |

---

## 2. State Machine Description

### 2.1 States/Phases

**Enum Definition (Lines 55-60):**
```kotlin
private enum class SumoPhase {
    READY,       // Waiting for user to squat
    SQUAT_DOWN,  // User in squat position
    PULL_UP,     // User standing and pulling arms up
    RETURN       // User returning to squat position
}
```

### 2.2 Initial State

```kotlin
private var currentPhase = SumoPhase.READY
```

**Set at:** Line 62

### 2.3 State Machine Text Diagram

```
                    ┌──────────────────────────────────────┐
                    │                                      │
                    ▼                                      │
              ┌──────────┐                                 │
              │  READY   │                                 │
              └────┬─────┘                                 │
                   │                                       │
                   │ kneeAngle < 130°                      │
                   ▼                                       │
              ┌──────────┐                                 │
              │SQUAT_DOWN│                                 │
              └────┬─────┘                                 │
                   │                                       │
         ┌─────────┴─────────────┐                         │
         │                       │                         │
         │ kneeAngle > 150°      │ kneeAngle > 150°       │
         │ AND                   │ AND                     │
         │ shoulderAngle > 60°   │ shoulderAngle < 40°     │
         ▼                       │                         │
    ┌──────────┐                 │                         │
    │ PULL_UP  │                 │                         │
    └────┬─────┘                 │                         │
         │                       │                         │
         │ shoulderAngle > 75°   │                         │
         ▼                       │                         │
    ┌──────────┐                 │                         │
    │  RETURN  │◄────────────────┘ (ABORT: stood without   │
    └────┬─────┘                     pull)                 │
         │                                                 │
         │ kneeAngle < 130°                                │
         │ AND                                             │
         │ shoulderAngle < 40°                             │
         │ AND                                             │
         │ cooldown passed                                 │
         │                                                 │
         │ ═══════════════════                             │
         │ ║ repCount++     ║                              │
         │ ═══════════════════                             │
         │                                                 │
         └─────────────────────────────────────────────────┘
```

### 2.4 Transition Conditions (Exact If Statements)

#### READY → SQUAT_DOWN (Lines 148-153)
```kotlin
if (kneeAngle < SQUAT_DOWN_TRIGGER) {
    currentPhase = SumoPhase.SQUAT_DOWN
    currentState = ExerciseState.IN_PROGRESS
    Log.i(TAG, "🔽 READY → SQUAT_DOWN | Knee: ${"%.0f".format(kneeAngle)}°")
}
```

#### SQUAT_DOWN → PULL_UP (Lines 159-162)
```kotlin
if (kneeAngle > STAND_UP && shoulderAngle > PULL_TRIGGER) {
    currentPhase = SumoPhase.PULL_UP
    Log.i(TAG, "⬆️ SQUAT_DOWN → PULL_UP | Knee: ... | Shoulder: ...")
}
```

#### SQUAT_DOWN → READY (Abort) (Lines 164-169)
```kotlin
else if (kneeAngle > STAND_UP && shoulderAngle < ARM_DOWN) {
    currentPhase = SumoPhase.READY
    currentState = ExerciseState.READY
    Log.w(TAG, "🔄 SQUAT_DOWN → READY (stood up without pull)")
}
```

#### PULL_UP → RETURN (Lines 175-178)
```kotlin
if (shoulderAngle > FULL_PULL) {
    currentPhase = SumoPhase.RETURN
    Log.i(TAG, "💪 PULL_UP → RETURN | Shoulder: ...")
}
```

#### PULL_UP → READY (Abort) (Lines 180-184)
```kotlin
else if (shoulderAngle < ARM_DOWN && kneeAngle < SQUAT_DOWN_TRIGGER) {
    currentPhase = SumoPhase.READY
    currentState = ExerciseState.READY
    Log.w(TAG, "🔄 PULL_UP → READY (aborted pull)")
}
```

#### RETURN → READY (COUNT REP) (Lines 190-211)
```kotlin
if (kneeAngle < SQUAT_DOWN_TRIGGER && shoulderAngle < ARM_DOWN) {
    val timeSinceLastRep = currentTime - lastRepTime

    if (timeSinceLastRep > COOLDOWN_MS || lastRepTime == 0L) {
        repCount++
        lastRepTime = currentTime
        currentState = ExerciseState.COMPLETED

        Log.i(TAG, "🔥 SUMO HIGH PULL COUNTED! Total: $repCount")

        notifyRepCountUpdated()
        notifyStateChanged("Rep completed!")
    }

    currentPhase = SumoPhase.READY
    currentState = ExerciseState.READY
    Log.i(TAG, "🔽 RETURN → READY | Knee: ...")
}
```

### 2.5 Where Rep Counting Happens

**File:** `SumoHighPullExercise.kt`  
**Line:** 199  
**Code:** `repCount++`  
**Phase:** RETURN → READY transition  
**Condition:** `kneeAngle < SQUAT_DOWN_TRIGGER && shoulderAngle < ARM_DOWN && cooldown passed`

---

## 3. Threshold Values Table

### 3.1 Knee Angle Thresholds

| Constant | Value | Purpose | Used In |
|----------|-------|---------|---------|
| `SQUAT_DOWN_TRIGGER` | 130° | Enter squat from standing | READY→SQUAT_DOWN, RETURN→READY |
| `STAND_UP` | 150° | Standing threshold | SQUAT_DOWN→PULL_UP |
| `MIN_VALID_KNEE_ANGLE` | 40° | Occlusion/noise filter | Validation (skip frame if below) |
| `MAX_VALID_KNEE_ANGLE` | 180° | Calculation error filter | Validation (skip frame if above) |

### 3.2 Shoulder Angle Thresholds

| Constant | Value | Purpose | Used In |
|----------|-------|---------|---------|
| `ARM_DOWN` | 40° | Arms relaxed/down | SQUAT_DOWN abort, RETURN count trigger |
| `PULL_TRIGGER` | 60° | Starting arm pull | SQUAT_DOWN→PULL_UP |
| `FULL_PULL` | 75° | Elbows fully up | PULL_UP→RETURN |

### 3.3 Validation Thresholds

| Constant | Value | Purpose | Effect When Failed |
|----------|-------|---------|-------------------|
| `MIN_VISIBILITY` | 0.5f | Landmark visibility threshold | Skip frame silently |
| `MIN_TORSO_LENGTH` | 0.08f | Body size in normalized coordinates | Skip frame silently |

### 3.4 Cooldown Values

| Constant | Value | Purpose |
|----------|-------|---------|
| `COOLDOWN_MS` | 1000L (1 second) | Prevent double counting |

### 3.5 No Torso Scaling Ratios Used

**Note:** Unlike SquatExercise and JumpingJackExercise, SumoHighPullExercise does NOT use torso-based ratio scaling for thresholds. All angle thresholds are absolute values.

---

## 4. Landmark Usage

### 4.1 All Landmarks Retrieved (Lines 71-80)

| Landmark | MediaPipe Index | Variable Name |
|----------|-----------------|---------------|
| LEFT_HIP | 23 | `leftHip` |
| LEFT_KNEE | 25 | `leftKnee` |
| LEFT_ANKLE | 27 | `leftAnkle` |
| RIGHT_HIP | 24 | `rightHip` |
| RIGHT_KNEE | 26 | `rightKnee` |
| RIGHT_ANKLE | 28 | `rightAnkle` |
| LEFT_SHOULDER | 11 | `leftShoulder` |
| RIGHT_SHOULDER | 12 | `rightShoulder` |
| LEFT_ELBOW | 13 | `leftElbow` |
| RIGHT_ELBOW | 14 | `rightElbow` |

### 4.2 Landmarks Used for Knee Angle Calculation (Lines 116-120)

**Left Leg (Primary):**
- Point A: `LEFT_HIP` (23)
- Vertex: `LEFT_KNEE` (25)
- Point C: `LEFT_ANKLE` (27)

**Right Leg (Fallback):**
- Point A: `RIGHT_HIP` (24)
- Vertex: `RIGHT_KNEE` (26)
- Point C: `RIGHT_ANKLE` (28)

**Selection Logic:**
```kotlin
val kneeAngle: Float = if (leftLegVisible) {
    pose.calculateAngle(LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
} else {
    pose.calculateAngle(RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
}
```

### 4.3 Landmarks Used for Shoulder Angle Calculation (Lines 128-132)

**Left Arm (Primary):**
- Point A: `LEFT_HIP` (23)
- Vertex: `LEFT_SHOULDER` (11)
- Point C: `LEFT_ELBOW` (13)

**Right Arm (Fallback):**
- Point A: `RIGHT_HIP` (24)
- Vertex: `RIGHT_SHOULDER` (12)
- Point C: `RIGHT_ELBOW` (14)

**Selection Logic:**
```kotlin
val shoulderAngle: Float = if (leftArmVisible) {
    pose.calculateAngle(LEFT_HIP, LEFT_SHOULDER, LEFT_ELBOW)
} else {
    pose.calculateAngle(RIGHT_HIP, RIGHT_SHOULDER, RIGHT_ELBOW)
}
```

### 4.4 Landmarks Used for Torso Length (Body Size Validation) (Line 110)

- `LEFT_SHOULDER` (11)
- `LEFT_HIP` (23)

```kotlin
val torsoLength = pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
```

### 4.5 Facing Camera Check

**NOT IMPLEMENTED** - SumoHighPullExercise does NOT include facing camera validation (unlike SquatExercise).

---

## 5. Angle Calculation Method

### 5.1 Function Location

**File:** `NormalizedPose.kt`  
**Lines:** 26-42  
**Function:** `calculateAngle(pointA, vertex, pointC)`

### 5.2 Input Points Order

```kotlin
fun calculateAngle(
    pointA: PoseLandmark,    // First point (e.g., Hip)
    vertex: PoseLandmark,    // Middle point/vertex (e.g., Knee)
    pointC: PoseLandmark     // Third point (e.g., Ankle)
): Float
```

**Angle is measured AT the vertex (middle point).**

### 5.3 Calculation Formula

```kotlin
val radians = Math.atan2((c.y() - b.y()).toDouble(), (c.x() - b.x()).toDouble()) -
        Math.atan2((a.y() - b.y()).toDouble(), (a.x() - b.x()).toDouble())
var angle = Math.abs(radians * 180.0 / Math.PI)
if (angle > 180.0) {
    angle = 360.0 - angle
}
return angle.toFloat()
```

### 5.4 Output Range

- **Minimum:** 0°
- **Maximum:** 180°
- **Returns:** 0f if any landmark is missing

### 5.5 Where Angle Is Called in SumoHighPullExercise

| Angle Type | Line Number | Call |
|------------|-------------|------|
| Knee Angle | 117 or 119 | `pose.calculateAngle(LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)` or right side |
| Shoulder Angle | 129 or 131 | `pose.calculateAngle(LEFT_HIP, LEFT_SHOULDER, LEFT_ELBOW)` or right side |

---

## 6. Gating Conditions

### 6.1 All Conditions That Block Rep Counting

| Condition | Where Checked | Lines | Effect on Failure | User Feedback |
|-----------|---------------|-------|-------------------|---------------|
| Leg visibility | Lines 83-94 | `return` (skip frame) | **NONE** - Silent |
| Arm visibility | Lines 97-103 | `return` (skip frame) | **NONE** - Silent |
| Both leg AND arm visibility | Line 106 | `return` (skip frame) | **NONE** - Silent |
| leftShoulder or leftHip null | Lines 109-111 | `return` (skip frame) | **NONE** - Silent |
| Torso length < 0.08 | Lines 113-115 | `return` (skip frame) | **NONE** - Silent |
| Knee angle < 40° OR > 180° | Lines 123-125 | `return` (skip frame) | **NONE** - Silent |
| Cooldown not passed | Lines 194-195 | Skip rep count only | **NONE** - Silent |
| Wrong state machine phase | State machine logic | No transition | **NONE** - Silent |

### 6.2 Visibility Check Details (Lines 83-106)

**Left Leg Visibility:**
```kotlin
val leftLegVisible = leftHip != null && leftKnee != null && leftAnkle != null &&
    leftHip.visibility().orElse(0f) > MIN_VISIBILITY &&
    leftKnee.visibility().orElse(0f) > MIN_VISIBILITY &&
    leftAnkle.visibility().orElse(0f) > MIN_VISIBILITY
```

**Right Leg Visibility:**
```kotlin
val rightLegVisible = rightHip != null && rightKnee != null && rightAnkle != null &&
    rightHip.visibility().orElse(0f) > MIN_VISIBILITY &&
    rightKnee.visibility().orElse(0f) > MIN_VISIBILITY &&
    rightAnkle.visibility().orElse(0f) > MIN_VISIBILITY
```

**Left Arm Visibility:**
```kotlin
val leftArmVisible = leftShoulder != null && leftElbow != null && leftHip != null &&
    leftShoulder.visibility().orElse(0f) > MIN_VISIBILITY &&
    leftElbow.visibility().orElse(0f) > MIN_VISIBILITY
```

**Right Arm Visibility:**
```kotlin
val rightArmVisible = rightShoulder != null && rightElbow != null && rightHip != null &&
    rightShoulder.visibility().orElse(0f) > MIN_VISIBILITY &&
    rightElbow.visibility().orElse(0f) > MIN_VISIBILITY
```

**Gating Condition (Line 106):**
```kotlin
if ((!leftLegVisible && !rightLegVisible) || (!leftArmVisible && !rightArmVisible)) {
    return
}
```

### 6.3 Torso Length Check (Lines 109-115)

```kotlin
if (leftShoulder == null || leftHip == null) {
    return
}

val torsoLength = pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)

if (torsoLength < MIN_TORSO_LENGTH) {
    return
}
```

### 6.4 Cooldown Check (Lines 194-197)

```kotlin
val timeSinceLastRep = currentTime - lastRepTime

if (timeSinceLastRep > COOLDOWN_MS || lastRepTime == 0L) {
    // ✅ VALID REP - Count it!
    repCount++
    ...
}
```

---

## 7. Data Flow Path

### 7.1 Complete Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CAMERA FRAME                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CameraX ImageAnalysis                                                       │
│  File: CameraFragment.kt                                                     │
│  Function: ImageAnalysis.Analyzer.analyze()                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  PoseLandmarkerHelper.detectLiveStream()                                     │
│  File: PoseLandmarkerHelper.kt                                               │
│  Purpose: Send frame to MediaPipe                                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  MediaPipe Pose Landmarker (ML Inference)                                    │
│  Returns: PoseLandmarkerResult                                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CameraFragment.onResults()                                                  │
│  File: CameraFragment.kt                                                     │
│  Lines: 400-427                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  PoseProcessor.processResult(result)                                         │
│  File: PoseProcessor.kt                                                      │
│  Lines: 36-57                                                                │
│  Returns: NormalizedPose                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  exerciseManager.processPose(normalizedPose)                                 │
│  File: CameraFragment.kt, Line: 424                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ExerciseManager.processPose()                                               │
│  File: ExerciseManager.kt                                                    │
│  Lines: 78-80                                                                │
│  Code: activeExercise?.processPose(pose)                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  SumoHighPullExercise.processPose(pose)                                      │
│  File: SumoHighPullExercise.kt                                               │
│  Lines: 68-216                                                               │
│  Contains: All detection logic + state machine                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ (When rep counted)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  notifyRepCountUpdated()                                                     │
│  File: BaseExercise.kt                                                       │
│  Lines: 130-138                                                              │
│  Code: listener?.onRepCountUpdated(repCount, getName())                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ExerciseManager.onRepCountUpdated() (implements ExerciseListener)           │
│  File: ExerciseManager.kt                                                    │
│  Lines: 121-130                                                              │
│  Code: managerListener?.onRepCountUpdated(count, exerciseName)               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CameraFragment.onRepCountUpdated() (implements ExerciseManagerListener)     │
│  File: CameraFragment.kt                                                     │
│  Lines: 442-450                                                              │
│  Code: fragmentCameraBinding.exerciseCounterText.text = "$exerciseName: $count"│
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  UI UPDATED: TextView shows "Sumo High Pull: X"                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Rep Count Callback Chain

### 8.1 Where repCount++ Happens

**File:** `SumoHighPullExercise.kt`  
**Line:** 199  
**Code:**
```kotlin
repCount++
```

### 8.2 How Count Travels to UI

| Step | File | Line(s) | Method | Action |
|------|------|---------|--------|--------|
| 1 | SumoHighPullExercise.kt | 199 | `processPose()` | `repCount++` |
| 2 | SumoHighPullExercise.kt | 204 | `processPose()` | `notifyRepCountUpdated()` |
| 3 | BaseExercise.kt | 137 | `notifyRepCountUpdated()` | `listener?.onRepCountUpdated(repCount, getName())` |
| 4 | ExerciseManager.kt | 125 | `onRepCountUpdated()` | `managerListener?.onRepCountUpdated(count, exerciseName)` |
| 5 | CameraFragment.kt | 445-447 | `onRepCountUpdated()` | `exerciseCounterText.text = "$exerciseName: $count"` |

### 8.3 Listener Methods Used

| Interface | Method | Implemented By |
|-----------|--------|----------------|
| `BaseExercise.ExerciseListener` | `onRepCountUpdated(count: Int, exerciseName: String)` | `ExerciseManager` |
| `ExerciseManager.ExerciseManagerListener` | `onRepCountUpdated(count: Int, exerciseName: String)` | `CameraFragment` |

---

## 9. Known Limitations (From Code Analysis Only)

### 9.1 No Hysteresis on Shoulder Angle Transitions

**Problem:** PULL_TRIGGER (60°) and FULL_PULL (75°) use the same entry threshold logic. There is no separate exit threshold.

**Example:**
- Enter PULL_UP when `shoulderAngle > 60°`
- If shoulderAngle oscillates around 60° due to noise, may cause rapid state changes

**Location:** Lines 159-162, 175-178

### 9.2 No Facing Camera Validation

**Problem:** Unlike SquatExercise, SumoHighPullExercise does NOT validate if user is facing the camera.

**Risk:** User could perform movement sideways and still get counted.

**Comparison:**
- SquatExercise: Has shoulder width check
- JumpingJackExercise: No facing check
- SumoHighPullExercise: No facing check

### 9.3 Silent Failures with No User Feedback

**Problem:** All gating conditions use `return` to skip the frame silently. User receives no indication of WHY reps are not counting.

**Affected Conditions:**
- Leg/arm visibility failed → Silent
- Torso too small → Silent
- Knee angle out of range → Silent

### 9.4 Same Threshold for Entry and Exit on Knee Angle

**Problem:** `SQUAT_DOWN_TRIGGER = 130°` is used for both:
- READY → SQUAT_DOWN (entry)
- RETURN → READY (exit/count trigger)

**Risk:** If user's knee angle oscillates around 130°, could cause state machine confusion.

### 9.5 Left Side Preference

**Problem:** Left side landmarks are always checked first. Right side is only used as fallback.

**Code:**
```kotlin
val kneeAngle: Float = if (leftLegVisible) {
    // Use left side
} else {
    // Fallback to right
}
```

**Risk:** If left landmarks are visible but noisy, right side (potentially cleaner) won't be used.

### 9.6 Torso Length Check Uses Left Side Only

**Problem:** `calculateDistance(LEFT_SHOULDER, LEFT_HIP)` - if left shoulder/hip is occluded, check fails even if right side is visible.

**Location:** Line 110

### 9.7 Potential State Machine Dead-End

**Scenario:** In RETURN phase, if:
- `kneeAngle < SQUAT_DOWN_TRIGGER` (in squat)
- `shoulderAngle >= ARM_DOWN` (arms still up, but below FULL_PULL)

**Result:** Code enters empty `else if` branch (Lines 213-216) and waits indefinitely until arms come down.

### 9.8 No Abort Path from RETURN Phase

**Problem:** Once in RETURN phase, the only exits are:
1. Complete rep (squat + arms down)
2. Wait indefinitely

There is no timeout or abort mechanism if user stops mid-exercise.

---

## 10. Raw Code References

### 10.1 Main Logic Sections

| Section | File | Lines | Description |
|---------|------|-------|-------------|
| Package & Imports | SumoHighPullExercise.kt | 1-7 | Package declaration and imports |
| Class Documentation | SumoHighPullExercise.kt | 9-30 | KDoc describing exercise logic |
| Constants | SumoHighPullExercise.kt | 33-52 | All threshold values in companion object |
| State Machine Enum | SumoHighPullExercise.kt | 55-60 | SumoPhase enum definition |
| Instance Variables | SumoHighPullExercise.kt | 62-64 | currentPhase, lastRepTime, lastLogTime |
| Landmark Retrieval | SumoHighPullExercise.kt | 71-80 | Getting all 10 landmarks from pose |
| Visibility Checks | SumoHighPullExercise.kt | 83-106 | Left/right leg and arm visibility logic |
| Body Size Validation | SumoHighPullExercise.kt | 109-115 | Torso length check |
| Knee Angle Calculation | SumoHighPullExercise.kt | 118-125 | Calculate and validate knee angle |
| Shoulder Angle Calculation | SumoHighPullExercise.kt | 128-132 | Calculate shoulder angle |
| Debug Logging | SumoHighPullExercise.kt | 135-139 | Once-per-second log |
| State Machine - READY | SumoHighPullExercise.kt | 143-153 | READY phase logic |
| State Machine - SQUAT_DOWN | SumoHighPullExercise.kt | 155-169 | SQUAT_DOWN phase logic |
| State Machine - PULL_UP | SumoHighPullExercise.kt | 171-184 | PULL_UP phase logic |
| State Machine - RETURN | SumoHighPullExercise.kt | 186-216 | RETURN phase logic + rep counting |
| reset() Method | SumoHighPullExercise.kt | 219-227 | Reset state machine and counters |
| getName() Method | SumoHighPullExercise.kt | 229 | Returns "Sumo High Pull" |
| getDescription() Method | SumoHighPullExercise.kt | 231-232 | Returns exercise description |
| isPoseValid() Method | SumoHighPullExercise.kt | 234-250 | Pre-start validation |

### 10.2 Integration Points

| Integration | File | Lines | Code |
|-------------|------|-------|------|
| Button Handler | CameraFragment.kt | 502-505 | `buttonSumoHighPull.setOnClickListener { exerciseManager.setActiveExercise(SumoHighPullExercise()) }` |
| Import Statement | CameraFragment.kt | 47 | `import ...exercises.sumohighpull.SumoHighPullExercise` |
| XML Button | fragment_camera.xml | ~77-85 | `<Button android:id="@+id/button_sumo_high_pull" ...>` |

---

## End of Debug Documentation

**DO NOT MODIFY CODE BASED ON THIS DOCUMENT.**  
**This is for analysis and debugging reference only.**

