# 🔍 STATIC CODE ANALYSIS REPORT
## MediaPipe Squat Counter Logic

**Analysis Date:** January 30, 2026  
**Target File:** `SquatExercise.kt`  
**Analysis Type:** Static code review - NO code changes, NO refactoring  
**Purpose:** Identify why squat counting may fail or get stuck at 0

---

## 📊 1. CONSTANTS AND THRESHOLDS EXTRACTION

### Angle Thresholds

| Constant | Value | Purpose | Usage | Critical Notes |
|----------|-------|---------|-------|----------------|
| `SQUAT_DOWN_ANGLE` | 130° | Start descending trigger | READY → DESCENDING transition | **Must be LESS than this to transition** |
| `SQUAT_BOTTOM_ANGLE` | 90° | Deep squat position | DESCENDING → BOTTOM, BOTTOM → ASCENDING | **Bidirectional threshold** (< to enter, > to exit) |
| `SQUAT_UP_ANGLE` | 155° | Fully standing position | ASCENDING → READY (COUNT REP) | **Must be GREATER than this to count rep** |

**Angle Range Analysis:**
- Standing position: > 155° (fully upright)
- Descending trigger: < 130° (25° deadzone from standing)
- Bottom position: < 90° (40° descent range)
- Full cycle: 155° → 130° → 90° → 155° (65° total range)

⚠️ **CRITICAL OBSERVATION:** There is a **25° deadzone** between SQUAT_UP_ANGLE (155°) and SQUAT_DOWN_ANGLE (130°) where NO state transitions occur in READY phase.

---

### Validation Thresholds (Body-Relative)

| Constant | Value | Purpose | Calculation | Blocking? |
|----------|-------|---------|-------------|-----------|
| `ARM_HORIZONTAL_Y_RATIO` | 0.25f | Wrist-Shoulder Y tolerance | `abs(wristY - shoulderY) < torsoLength * 0.25` | ❌ WARNING ONLY |
| `ARM_FORWARD_X_RATIO` | 0.50f | Wrist X-distance tolerance | `abs(wristX - shoulderX) < torsoLength * 0.50` | ❌ WARNING ONLY |
| `MIN_SHOULDER_WIDTH_RATIO` | 0.18f | Facing camera validation | `shoulderWidth > torsoLength * 0.18` | ✅ **BLOCKS READY→DESCENDING** |

**Scaling Factor:** `torsoLength = distance(LEFT_SHOULDER, LEFT_HIP)`

---

### Timing Thresholds

| Constant | Value | Purpose | Impact |
|----------|-------|---------|--------|
| `SQUAT_COOLDOWN_MS` | 800ms | Anti-double-count protection | **BLOCKS rep counting if < 800ms since last rep** |
| `torsoLength` minimum | 0.1f | Pose validity check | Early return if torsoLength < 0.1 |

---

### Pose Validation (PoseProcessor)

| Threshold | Value | Purpose | Impact |
|-----------|-------|---------|--------|
| Landmark visibility | 0.3f | Minimum visibility score | **BLOCKS pose processing if any core landmark visibility < 0.3** |

**Required Core Landmarks:**
- LEFT_SHOULDER, RIGHT_SHOULDER
- LEFT_HIP, RIGHT_HIP
- LEFT_KNEE, RIGHT_KNEE
- LEFT_ANKLE, RIGHT_ANKLE

⚠️ **CRITICAL:** If ANY of these 8 landmarks has visibility < 0.3, the **entire pose is rejected** and `processPose()` is NEVER called.

---

## 🔄 2. STATE MACHINE ANALYSIS

### State Diagram

```
┌──────────┐
│  READY   │ ← Initial state
└────┬─────┘
     │ Entry: kneeAngle < 130° AND facingCamera = true
     ▼
┌──────────┐
│DESCENDING│
└────┬─────┘
     │ Entry: kneeAngle < 90°
     ▼
┌──────────┐
│  BOTTOM  │
└────┬─────┘
     │ Entry: kneeAngle > 90°
     ▼
┌──────────┐
│ASCENDING │
└────┬─────┘
     │ Entry: kneeAngle > 155° AND cooldown passed
     ▼
┌──────────┐
│  READY   │ ← REP COUNTED HERE ✅
└──────────┘
```

---

### Phase: READY

**Entry Condition:**
- Initial state OR
- Completed full squat cycle OR
- Reset from incomplete squat

**Exit Condition (Normal):**
```kotlin
if (kneeAngle < SQUAT_DOWN_ANGLE && facingCamera) {
    squatPhase = SquatPhase.DESCENDING
}
```
- Requires: `kneeAngle < 130°` **AND** `facingCamera == true`

**What BLOCKS Transition:**
1. ❌ `kneeAngle >= 130°` (user not bending enough)
2. ❌ `facingCamera == false` (shoulder width too narrow)
3. ❌ `torsoLength < 0.1f` (pose too small - early return before state machine)

**What Could Cause STUCK in READY:**
- **User stands too upright** (angle never drops below 130°)
- **User turns sideways** (shoulder width fails validation)
- **User too far from camera** (torsoLength < 0.1f)
- **Landmark detection fails** (PoseProcessor rejects pose before it reaches state machine)

⚠️ **CRITICAL ISSUE:** If `facingCamera == false`, user will be **permanently stuck in READY** even if performing perfect squats with correct angles. No warning is visible to user except occasional log message.

---

### Phase: DESCENDING

**Entry Condition:**
- From READY when `kneeAngle < 130°` AND `facingCamera == true`

**Exit Conditions:**

**Normal Exit:**
```kotlin
if (kneeAngle < SQUAT_BOTTOM_ANGLE) {
    squatPhase = SquatPhase.BOTTOM
}
```
- Requires: `kneeAngle < 90°`

**Safety Reset:**
```kotlin
else if (kneeAngle > SQUAT_UP_ANGLE) {
    squatPhase = SquatPhase.READY  // Incomplete squat
}
```
- Triggers if user stands back up without reaching bottom

**What BLOCKS Transition:**
1. ❌ `kneeAngle >= 90°` (user doesn't squat deep enough)
2. ❌ Angle fluctuation keeps bouncing between 90°-155° range

**What Could Cause STUCK in DESCENDING:**
- **Partial squat** (user stops at ~100-120° and holds position)
- **Angle calculation jitter** (noise prevents clean < 90° detection)
- **User doing pulses** (bouncing in 90-130° range without committing)

⚠️ **SAFETY MECHANISM:** If angle > 155°, resets to READY (prevents infinite DESCENDING state)

---

### Phase: BOTTOM

**Entry Condition:**
- From DESCENDING when `kneeAngle < 90°`

**Exit Condition:**
```kotlin
if (kneeAngle > SQUAT_BOTTOM_ANGLE) {
    squatPhase = SquatPhase.ASCENDING
}
```
- Requires: `kneeAngle > 90°`

**What BLOCKS Transition:**
1. ❌ `kneeAngle <= 90°` (user holds deep squat position)

**What Could Cause STUCK in BOTTOM:**
- **User pauses at bottom** (holds squat position)
- **Angle calculation noise** (90° threshold jitter)

⚠️ **NO SAFETY RESET:** If user gets stuck in BOTTOM, there's **no escape** except moving angle > 90°. No timeout mechanism.

---

### Phase: ASCENDING

**Entry Condition:**
- From BOTTOM when `kneeAngle > 90°`

**Exit Conditions:**

**Normal Exit (COUNT REP):**
```kotlin
if (kneeAngle > SQUAT_UP_ANGLE) {
    if (timeSinceLastSquat > SQUAT_COOLDOWN_MS || lastSquatTime == 0L) {
        squatPhase = SquatPhase.READY
        repCount++  // ✅ REP COUNTED HERE
    }
}
```
- Requires: `kneeAngle > 155°` **AND** cooldown passed

**Safety Reset:**
```kotlin
else if (kneeAngle < SQUAT_BOTTOM_ANGLE) {
    squatPhase = SquatPhase.BOTTOM  // User squatted again
}
```

**What BLOCKS Transition:**
1. ❌ `kneeAngle <= 155°` (user doesn't stand fully upright)
2. ❌ `timeSinceLastSquat <= 800ms` (cooldown active)

**What Could Cause STUCK in ASCENDING:**
- **User doesn't stand fully** (stops at 140-150° thinking they're done)
- **Rapid squats** (cooldown blocks counting)
- **Angle jitter at 155° threshold** (bounces around threshold)

⚠️ **COOLDOWN BEHAVIOR:** Even if angle > 155°, if cooldown hasn't passed, phase resets to READY **WITHOUT counting rep**. User gets no feedback about why rep didn't count.

---

## ⚠️ 3. GATING CONDITIONS ANALYSIS

### Condition 1: `facingCamera`

**Calculation:**
```kotlin
val shoulderWidth = Math.abs(leftShoulder.x() - rightShoulder.x())
val minShoulderWidth = torsoLength * MIN_SHOULDER_WIDTH_RATIO  // 0.18
val facingCamera = shoulderWidth > minShoulderWidth
```

**When ACTIVE:**
- READY → DESCENDING transition **REQUIRES** `facingCamera == true`

**How It Can BLOCK Counting:**
1. User turns **sideways** to camera → shoulders align on same X coordinate → `shoulderWidth ≈ 0`
2. User is **very far** from camera → `torsoLength` very small → `minShoulderWidth` threshold too easy to fail
3. Camera angle is **not perpendicular** to user → shoulder width appears compressed

**Result if FAILS:**
- User **STUCK in READY** phase
- Can perform perfect squats with correct angles
- **Counter stays at 0**
- Only error: occasional log message (user doesn't see it)

⚠️ **CRITICAL BLOCKER:** This is the **#1 most likely reason** squats don't count.

---

### Condition 2: `armsForward`

**Calculation:**
```kotlin
val wristShoulderYDiff = Math.abs(leftWristLandmark.y() - leftShoulder.y())
val wristShoulderXDiff = Math.abs(leftWristLandmark.x() - leftShoulder.x())
val isArmYHorizontal = wristShoulderYDiff < (torsoLength * 0.25)
val isArmXReasonable = wristShoulderXDiff < (torsoLength * 0.50)
val armsForward = isArmYHorizontal && isArmXReasonable
```

**When ACTIVE:**
- ❌ **NOT USED FOR STATE TRANSITIONS** (only generates warnings)

**Default Behavior:**
```kotlin
var armsForward = true  // Default to true if wrists not visible
```

**Result:**
- **Does NOT block counting** ✅
- Only generates validation error messages

---

### Condition 3: Angle Thresholds

**READY → DESCENDING:**
```kotlin
kneeAngle < SQUAT_DOWN_ANGLE  // Must be < 130°
```

**How It Can BLOCK:**
- User bends knees but **not enough** (stays at 135-140°)
- Angle calculation returns **wrong value** due to landmark noise

**DESCENDING → BOTTOM:**
```kotlin
kneeAngle < SQUAT_BOTTOM_ANGLE  // Must be < 90°
```

**How It Can BLOCK:**
- User does **partial squats** (stops at 100-110°)
- Angle calculation **never crosses 90°** threshold

**BOTTOM → ASCENDING:**
```kotlin
kneeAngle > SQUAT_BOTTOM_ANGLE  // Must be > 90°
```

**How It Can BLOCK:**
- Angle jitter **prevents clean exit** from bottom

**ASCENDING → READY (COUNT):**
```kotlin
kneeAngle > SQUAT_UP_ANGLE  // Must be > 155°
```

**How It Can BLOCK:**
- User doesn't **stand fully upright** (stops at 145-150°)
- Angle calculation **maxes out below 155°**

---

### Condition 4: Cooldown

**Calculation:**
```kotlin
val timeSinceLastSquat = currentTime - lastSquatTime
if (timeSinceLastSquat > SQUAT_COOLDOWN_MS || lastSquatTime == 0L) {
    // Count rep
}
```

**When ACTIVE:**
- ASCENDING → READY transition (rep counting)

**How It Can BLOCK:**
- User does squats **faster than 800ms**
- Rapid pulsing movements trigger phase transitions within cooldown window

**Result if FAILS:**
- Phase resets to READY **without counting**
- No visual feedback to user
- User thinks rep should have counted

⚠️ **SILENT FAILURE:** Cooldown blocks are **logged but not shown to user**.

---

### Condition 5: `torsoLength` Minimum

**Check:**
```kotlin
if (torsoLength < 0.1f) {
    currentState = ExerciseState.IDLE
    return  // Early exit - state machine never runs
}
```

**How It Can BLOCK:**
- User is **very far** from camera
- Landmarks detected but **very close together** in normalized space
- Pose detection quality is poor

**Result:**
- **State machine never executes**
- No phase transitions possible
- Counter permanently stuck at 0

---

### Condition 6: Pose Validation (PoseProcessor)

**Check (in PoseProcessor.kt):**
```kotlin
val requiredLandmarks = [LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, 
                         LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE]

return requiredLandmarks.all { landmark ->
    lm != null && lm.visibility().orElse(0f) > 0.3f
}
```

**How It Can BLOCK:**
- **ANY** of the 8 core landmarks has visibility ≤ 0.3
- Poor lighting conditions
- User wearing dark clothing against dark background
- Camera exposure issues

**Result if FAILS:**
- `processResult()` returns `null`
- `ExerciseManager.processPose()` is **never called**
- State machine **never runs**
- Counter **permanently stuck at 0**

⚠️ **SILENT REJECTION:** Pose rejection happens **before** SquatExercise even sees the data.

---

## 🚨 4. LOGIC CONTRADICTIONS & ISSUES

### Issue #1: Threshold Overlap - BOTTOM Phase Bidirectionality

**Problem:**
```kotlin
SQUAT_BOTTOM_ANGLE = 90f  // Used for BOTH entry AND exit
```

**Entry (DESCENDING → BOTTOM):**
```kotlin
if (kneeAngle < SQUAT_BOTTOM_ANGLE) { ... }  // < 90°
```

**Exit (BOTTOM → ASCENDING):**
```kotlin
if (kneeAngle > SQUAT_BOTTOM_ANGLE) { ... }  // > 90°
```

**Contradiction:**
- If angle is **exactly 90°**, neither condition triggers
- If angle **oscillates around 90°** (89.9° ↔ 90.1°), rapid phase switching occurs
- **No hysteresis** - same threshold for entry and exit

**Risk:**
- Angle jitter at 90° causes **phase ping-pong**
- User could get **stuck oscillating** between DESCENDING and BOTTOM
- Or between BOTTOM and ASCENDING

---

### Issue #2: Angle Calculation Returns 0° on Landmark Failure

**Problem:**
```kotlin
fun calculateAngle(...): Float {
    val a = landmarks[pointA] ?: return 0f  // ❌ Returns 0° if landmark missing
    val b = landmarks[vertex] ?: return 0f
    val c = landmarks[pointC] ?: return 0f
    ...
}
```

**Consequence:**
- If **any** landmark (hip, knee, ankle) is missing, angle = 0°
- 0° is **far below** all thresholds
- State machine sees `kneeAngle = 0°`

**What Happens:**
```kotlin
// In READY phase:
if (kneeAngle < SQUAT_DOWN_ANGLE) { ... }  // 0 < 130 ✅ TRIGGERS
    squatPhase = DESCENDING

// In DESCENDING phase:
if (kneeAngle < SQUAT_BOTTOM_ANGLE) { ... }  // 0 < 90 ✅ TRIGGERS
    squatPhase = BOTTOM

// In BOTTOM phase:
if (kneeAngle > SQUAT_BOTTOM_ANGLE) { ... }  // 0 > 90 ❌ NEVER TRIGGERS
    // STUCK IN BOTTOM FOREVER
```

**Risk:**
- Transient landmark occlusion causes **immediate jump to BOTTOM**
- User then **STUCK in BOTTOM** until landmarks recover
- Impossible to escape without valid angle > 90°

---

### Issue #3: Distance Calculation Returns 0 on Landmark Failure

**Problem:**
```kotlin
fun calculateDistance(...): Float {
    val p1 = landmarks[point1] ?: return 0f  // ❌ Returns 0 if landmark missing
    val p2 = landmarks[point2] ?: return 0f
    ...
}
```

**Consequence:**
- If shoulder or hip missing, `torsoLength = 0`
- Fails `torsoLength < 0.1f` check
- Early return - **state machine never runs**

---

### Issue #4: No Escape from BOTTOM Phase

**Problem:**
```kotlin
SquatPhase.BOTTOM -> {
    // Transition to ASCENDING
    if (kneeAngle > SQUAT_BOTTOM_ANGLE) {
        squatPhase = SquatPhase.ASCENDING
    }
    // ❌ NO ELSE CLAUSE - NO SAFETY RESET
}
```

**Consequence:**
- If user gets stuck in BOTTOM (e.g., angle = 0° from landmark failure), **no escape**
- Unlike DESCENDING phase which has safety reset to READY if angle > 155°
- **Infinite BOTTOM state** possible

---

### Issue #5: Cooldown Resets Phase Without Counting

**Problem:**
```kotlin
if (timeSinceLastSquat > SQUAT_COOLDOWN_MS || lastSquatTime == 0L) {
    // Count rep
    repCount++
} else {
    // ❌ Cooldown active - NO COUNT
    squatPhase = SquatPhase.READY  // But still resets phase!
}
```

**Consequence:**
- User completes full ROM (155° → 90° → 155°)
- Cooldown blocks count
- Phase resets to READY
- **Rep lost** - user gets no feedback
- Next squat will count (cooldown passed), but **this one is gone**

**User Experience:**
- Performs 5 rapid squats
- Only 2-3 count (others lost to cooldown)
- User **confused** why not all squats counted

---

### Issue #6: facingCamera Can Block Mid-Cycle

**Current Behavior:**
```kotlin
if (!facingCamera) {
    currentState = ExerciseState.IDLE
    // ❌ But doesn't return - processing continues
}
```

**Then:**
```kotlin
// READY phase:
if (kneeAngle < SQUAT_DOWN_ANGLE && facingCamera) { ... }
```

**Problem:**
- `facingCamera` is **only checked in READY phase**
- If user starts squat facing camera, then **turns sideways mid-squat**, they can still complete the rep
- But if they turn sideways **before starting**, they're blocked

**Inconsistency:**
- Validation is **not continuous** throughout cycle
- Only gates **initial transition**

---

## 🧮 5. ANGLE COMPUTATION ANALYSIS

### Method: `calculateAngle(pointA, vertex, pointC)`

**Implementation:**
```kotlin
val radians = Math.atan2((c.y() - b.y()).toDouble(), (c.x() - b.x()).toDouble()) -
              Math.atan2((a.y() - b.y()).toDouble(), (a.x() - b.x()).toDouble())
var angle = Math.abs(radians * 180.0 / Math.PI)
if (angle > 180.0) {
    angle = 360.0 - angle
}
return angle.toFloat()
```

**What It Computes:**
- Angle between two vectors:
  - Vector 1: vertex → pointA (knee → hip)
  - Vector 2: vertex → pointC (knee → ankle)
- Result: Interior angle at vertex (knee joint angle)

---

### Expected Angle Ranges

**Standing (legs straight):**
- Hip-Knee-Ankle should be ~170-180° (nearly straight line)
- Code returns angles in range [0°, 180°]
- Expected: **155-180°**

**Deep Squat (90° knee bend):**
- Hip-Knee-Ankle forms right angle
- Expected: **80-100°** (perfect squat ~90°)

**Partial Squat:**
- Expected: **100-150°**

---

### Risks with Coordinate Noise

**Problem 1: Landmark Jitter**
- MediaPipe landmarks **fluctuate** frame-to-frame
- Even stationary pose has ±1-3 pixel jitter
- In normalized space (0-1), this translates to ±0.003-0.01

**Impact on Angles:**
- At standing position (170°), ±2° jitter
- At squat position (90°), ±5° jitter (more sensitive to position changes)

**Consequence:**
- Angle may **bounce** around thresholds: 89° → 91° → 89° → 90°
- Causes rapid phase transitions (DESCENDING ↔ BOTTOM ↔ ASCENDING)

---

**Problem 2: Occlusion Recovery**
- Landmark temporarily occluded → returns 0°
- Landmark recovers → jumps back to correct angle
- **Angle spike**: 150° → 0° → 145° in 3 frames

**Consequence:**
- False state transitions
- Unintended rep counting or phase resets

---

**Problem 3: Distance Dependency**
- Normalized coordinates (0-1) are relative to **frame size**, not real-world distance
- User closer to camera → larger coordinate changes for same movement
- User farther → smaller coordinate changes

**Impact:**
- Angle calculation is **distance-independent** ✅ (uses normalized coords)
- But **noise-to-signal ratio** changes with distance:
  - Close: High signal, low noise % → stable angles
  - Far: Low signal, high noise % → unstable angles

---

**Problem 4: 2D Projection of 3D Movement**
- MediaPipe outputs 2D normalized landmarks (X, Y in image plane)
- Squat is 3D movement (user bends knees in Z-axis too)
- 2D angle changes depending on **camera angle relative to user**

**Example:**
- User facing camera straight on: knee angle visible in 2D
- User at 30° angle to camera: knee bend **appears smaller** in 2D
- **Same physical squat, different detected angles**

**Risk:**
- If user is not **perfectly perpendicular** to camera, angles may not reach thresholds
- User at 45° angle might never get below 130° even in deep squat

---

### Possible Unstable Behavior

**Scenario 1: Threshold Bouncing**
```
Frame 1: kneeAngle = 89.8° → DESCENDING → BOTTOM (triggers)
Frame 2: kneeAngle = 90.2° → BOTTOM → ASCENDING (triggers)
Frame 3: kneeAngle = 89.9° → ASCENDING → BOTTOM (safety reset triggers)
```
- User **stuck in BOTTOM/ASCENDING oscillation**

**Scenario 2: Noise-Induced False Transition**
```
Frame 1: kneeAngle = 132° → READY (stable)
Frame 2: kneeAngle = 128° (noise spike) → DESCENDING (false trigger)
Frame 3: kneeAngle = 133° → DESCENDING → READY (safety reset)
```
- False transition causes confusion

**Scenario 3: Occlusion Cascade**
```
Frame 1: kneeAngle = 150° → READY
Frame 2: Knee occluded → kneeAngle = 0° → READY → DESCENDING → BOTTOM (cascade)
Frame 3: Knee visible again → kneeAngle = 148° → STUCK IN BOTTOM (can't exit)
```
- Single frame occlusion causes **permanent stuck state**

---

## 📏 6. SCALING LOGIC ANALYSIS

### torsoLength Calculation

**Code:**
```kotlin
val torsoLength = pose.calculateDistance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
```

**Calculation:**
```kotlin
return Math.sqrt(
    Math.pow((p1.x() - p2.x()).toDouble(), 2.0) +
    Math.pow((p1.y() - p2.y()).toDouble(), 2.0)
).toFloat()
```

**Measured:**
- 2D Euclidean distance in **normalized coordinate space** (0-1)
- NOT real-world distance in cm/meters

**Typical Values:**
- Close to camera: `torsoLength ≈ 0.35-0.50`
- Medium distance: `torsoLength ≈ 0.20-0.35`
- Far from camera: `torsoLength ≈ 0.10-0.20`

---

### Ratio-Based Thresholds

**Shoulder Width Validation:**
```kotlin
val minShoulderWidth = torsoLength * MIN_SHOULDER_WIDTH_RATIO  // * 0.18
```

**Example Calculations:**

| Distance | torsoLength | minShoulderWidth | Actual shoulderWidth (frontal) | Pass? |
|----------|-------------|------------------|-------------------------------|-------|
| Close | 0.40 | 0.072 | ~0.15-0.20 | ✅ Yes |
| Medium | 0.25 | 0.045 | ~0.10-0.12 | ✅ Yes |
| Far | 0.12 | 0.0216 | ~0.05-0.06 | ✅ Yes |
| Very Far | 0.08 | 0.0144 | ~0.03-0.04 | ⚠️ Marginal |

**Observation:**
- Ratio-based scaling **works well** across distances ✅
- Maintains proportional validation regardless of camera distance

---

**Arm Validation:**
```kotlin
val isArmYHorizontal = wristShoulderYDiff < (torsoLength * 0.25)
val isArmXReasonable = wristShoulderXDiff < (torsoLength * 0.50)
```

**Example:**
- `torsoLength = 0.30`
- `maxYDiff = 0.075` (7.5% of frame height)
- `maxXDiff = 0.15` (15% of frame width)

**Observations:**
- Very **forgiving** thresholds (50% of torso length for X!)
- Works across distances ✅

---

### Edge Cases

**Edge Case 1: Child Very Close to Camera**

**Scenario:**
- Child's face fills most of frame
- Body landmarks at edges of frame
- `torsoLength ≈ 0.6-0.8` (very large)

**Calculations:**
- `minShoulderWidth = 0.70 * 0.18 = 0.126`
- Actual shoulder width (child frontal): ~0.20-0.25
- **PASS** ✅

**Risk:**
- **None** - ratio scaling handles this ✅

---

**Edge Case 2: Child Very Far from Camera**

**Scenario:**
- Full body visible but small in frame
- `torsoLength ≈ 0.08-0.12` (very small)

**Calculations:**
- `minShoulderWidth = 0.10 * 0.18 = 0.018`
- Actual shoulder width: ~0.03-0.05
- **PASS** ✅

**Risk:**
- Approaching `torsoLength < 0.1f` **hard limit** ⚠️
- If torsoLength = 0.09, early return triggers → **no counting**
- If torsoLength = 0.08, pose rejected → **no counting**

**Failure Mode:**
- Very far distance → `torsoLength < 0.1f` → **instant block**

---

**Edge Case 3: Child Sideways to Camera**

**Scenario:**
- Child at 90° angle to camera
- Shoulders aligned on same X coordinate
- `shoulderWidth ≈ 0.01-0.03` (very narrow)

**Calculations:**
- `torsoLength = 0.30` (normal)
- `minShoulderWidth = 0.054`
- Actual: 0.02
- **FAIL** ❌

**Result:**
- `facingCamera = false`
- **STUCK IN READY** forever
- **Counter stays at 0**

⚠️ **CRITICAL EDGE CASE:** Sideways orientation = **total failure**

---

**Edge Case 4: Sitting vs Standing**

**Scenario:**
- User is sitting (hips and shoulders at similar Y coordinate)
- `torsoLength` calculated based on X/Y distance

**Problem:**
- Sitting: shoulder-hip distance **appears shorter** in 2D
- `torsoLength ≈ 0.15` (appears small)
- May trigger `torsoLength < 0.1f` check

**Risk:**
- Marginal - depends on sitting posture

---

## 🔗 7. ARCHITECTURE FLOW ANALYSIS

### Data Flow Path

```
CameraX (ImageProxy)
    ↓
PoseLandmarkerHelper.detectLiveStream()
    ↓
MediaPipe Inference (GPU/CPU)
    ↓
PoseLandmarkerHelper.returnLivestreamResult()
    ↓
CameraFragment.onResults(resultBundle)
    ↓
PoseProcessor.processResult(result)
    │
    ├─→ isPoseComplete() validation
    │   └─→ If FAIL: return null ❌ [BLOCKS PIPELINE]
    │
    └─→ If PASS: return NormalizedPose ✅
            ↓
ExerciseManager.processPose(pose)
    ↓
SquatExercise.processPose(pose)
    │
    ├─→ torsoLength < 0.1f check
    │   └─→ If FAIL: return ❌ [BLOCKS STATE MACHINE]
    │
    ├─→ State Machine Execution
    │   └─→ Phase transitions based on angles
    │
    └─→ If REP COUNTED:
            ↓
        notifyRepCountUpdated()
            ↓
        BaseExercise → listener.onRepCountUpdated()
            ↓
        ExerciseManager.onRepCountUpdated()
            ↓
        CameraFragment.onRepCountUpdated()
            ↓
        runOnUiThread {
            exerciseCounterText.text = "Air Squat: $count"
        }
```

---

### Critical Checkpoints

**Checkpoint 1: Pose Validation (PoseProcessor)**
- **Where:** `PoseProcessor.processResult()`
- **Blocks if:** Any core landmark visibility ≤ 0.3
- **Result if blocked:** `processPose()` NEVER called
- **User sees:** No error, counter stays at 0

---

**Checkpoint 2: Torso Length Validation**
- **Where:** `SquatExercise.processPose()` line ~82
- **Blocks if:** `torsoLength < 0.1f`
- **Result if blocked:** State machine NEVER runs, early return
- **User sees:** No error, counter stays at 0

---

**Checkpoint 3: Facing Camera Validation**
- **Where:** `SquatExercise.processPose()` READY phase
- **Blocks if:** `shoulderWidth <= minShoulderWidth`
- **Result if blocked:** STUCK in READY phase
- **User sees:** Validation error message (if logging enabled), counter stays at 0

---

**Checkpoint 4: Angle Thresholds**
- **Where:** Each phase transition
- **Blocks if:** Angle doesn't cross threshold
- **Result if blocked:** Stuck in current phase
- **User sees:** No explicit error, counter doesn't increment

---

**Checkpoint 5: Cooldown**
- **Where:** ASCENDING → READY transition (rep counting)
- **Blocks if:** `timeSinceLastSquat <= 800ms`
- **Result if blocked:** Phase resets without counting
- **User sees:** No error, rep silently lost

---

### Callback Chain Verification

**Success Path:**
```kotlin
// SquatExercise.kt line ~265
repCount++
notifyRepCountUpdated()

// BaseExercise.kt
protected fun notifyRepCountUpdated() {
    listener?.onRepCountUpdated(repCount, getName())
}

// ExerciseManager.kt (implements BaseExercise.ExerciseListener)
override fun onRepCountUpdated(count: Int, exerciseName: String) {
    managerListener?.onRepCountUpdated(count, exerciseName)
}

// CameraFragment.kt (implements ExerciseManager.ExerciseManagerListener)
override fun onRepCountUpdated(count: Int, exerciseName: String) {
    activity?.runOnUiThread {
        exerciseCounterText.text = "$exerciseName: $count"
    }
}
```

**Verified:** Callback chain is **intact** ✅

**Potential Failure Points:**
1. `listener == null` in BaseExercise (not set)
2. `managerListener == null` in ExerciseManager (not set)
3. `_fragmentCameraBinding == null` in CameraFragment (destroyed)
4. `activity == null` (fragment detached)

**Actual Setup (from code review):**
```kotlin
// CameraFragment.onViewCreated()
exerciseManager.setListener(this)  // ✅ Listener set
exerciseManager.setActiveExercise(SquatExercise())  // ✅ Exercise set with listener
```

**Conclusion:** Callback chain should work IF state machine reaches rep counting ✅

---

## 🚨 8. DEBUG SUMMARY

### Top 5 Most Likely Reasons Counter Stuck at 0

#### #1: Not Facing Camera (facingCamera = false)
**Probability:** 🔴 **VERY HIGH**

**Cause:**
- User turned sideways (even slightly)
- Shoulder width too narrow: `shoulderWidth <= torsoLength * 0.18`

**Symptoms:**
- Counter stays at 0
- Can perform perfect squats
- Angles are correct
- Just can't transition from READY to DESCENDING

**How to Verify:**
- Check logs for: "Not facing camera"
- Check shoulderWidth value in logs

**Why Most Likely:**
- This is the **ONLY** validation that **blocks state machine entry**
- User may not realize they need to face camera perfectly
- Even 15-20° rotation can fail this check

---

#### #2: Pose Validation Rejection (PoseProcessor)
**Probability:** 🟠 **HIGH**

**Cause:**
- ANY core landmark has visibility ≤ 0.3
- Poor lighting
- Dark clothing
- User too far from camera

**Symptoms:**
- Counter stays at 0
- `processPose()` never called
- State machine never runs
- Complete silence - no errors visible to user

**How to Verify:**
- Check logs for: "Pose incomplete - core landmarks not visible enough"
- Check landmark visibility values

**Why Likely:**
- Happens **before** exercise logic
- Invisible to user
- Common with poor camera conditions

---

#### #3: Angle Doesn't Cross Thresholds
**Probability:** 🟡 **MEDIUM**

**Cause:**
- User doesn't squat deep enough (angle stays > 90°)
- User doesn't stand fully (angle stays < 155°)
- Camera angle makes ROM appear smaller
- Angle calculation issues

**Symptoms:**
- May transition to DESCENDING
- Gets stuck in DESCENDING or ASCENDING
- Counter stays at 0

**How to Verify:**
- Check angle values in logs
- Look for pattern: angle hovering near but not crossing thresholds

**Why Medium Probability:**
- Thresholds are forgiving (130° / 90° / 155°)
- Most users naturally exceed these ranges
- But possible with limited ROM or poor camera angle

---

#### #4: torsoLength < 0.1f
**Probability:** 🟡 **MEDIUM**

**Cause:**
- User very far from camera
- Pose detection quality poor
- Landmarks very close together in normalized space

**Symptoms:**
- Counter stays at 0
- Early return before state machine
- Logs: "Pose too small"

**How to Verify:**
- Check torsoLength value in logs

**Why Medium Probability:**
- Hard limit that's easy to hit when far from camera
- But typically user would move closer naturally

---

#### #5: Cooldown Blocking Reps
**Probability:** 🟢 **LOW** (for stuck at 0)

**Cause:**
- User doing squats faster than 800ms
- Cooldown prevents counting

**Symptoms:**
- Counter increments **sometimes** but not always
- User doing rapid squats
- Some reps lost

**How to Verify:**
- Check logs for: "REP BLOCKED by cooldown"
- Check timeSinceLastSquat values

**Why Low Probability for "Stuck at 0":**
- Cooldown only affects **second and subsequent reps**
- **First rep** has `lastSquatTime == 0L` → cooldown bypassed
- So counter should reach at least 1
- If stuck at 0, cooldown is **not the cause**

---

### Conditions That Are Too Strict

#### ⚠️ STRICT #1: facingCamera Validation

**Current Threshold:**
```kotlin
shoulderWidth > torsoLength * 0.18
```

**Problem:**
- Requires nearly **perfect frontal alignment**
- Even 20° rotation can fail
- Blocks ALL counting if failed

**Evidence:**
- Used in **READY → DESCENDING** transition only
- Most critical gating condition

**Recommendation for Analysis:**
- Check if user can maintain frontal orientation
- Consider if threshold is appropriate for kids (may move/rotate)

---

#### ⚠️ STRICT #2: Pose Visibility Threshold

**Current Threshold:**
```kotlin
lm.visibility().orElse(0f) > 0.3f  // For ALL 8 core landmarks
```

**Problem:**
- **ANY** single landmark below 0.3 → entire pose rejected
- Too strict in poor lighting
- No graceful degradation

**Evidence:**
- Happens before exercise logic
- Total pipeline block

---

#### ⚠️ STRICT #3: torsoLength Minimum

**Current Threshold:**
```kotlin
torsoLength < 0.1f  // Hard cutoff
```

**Problem:**
- Arbitrary threshold
- No scaling or adaptation
- Total block when user is far

**Evidence:**
- Early return - no recovery possible

---

### Most Fragile Phase Transition

#### 🔴 **MOST FRAGILE: READY → DESCENDING**

**Why:**
1. **Two conditions required:**
   - `kneeAngle < 130°` (reasonable)
   - `facingCamera == true` (**very strict**)

2. **No retry mechanism:**
   - If facingCamera fails, user **stuck forever**
   - No timeout, no escape

3. **Invisible to user:**
   - Only log message
   - User doesn't know why it's not working

4. **Most common failure point:**
   - This is where counting **begins**
   - If this fails, counter **never increments**

---

#### 🟠 **SECOND MOST FRAGILE: ASCENDING → READY (REP COUNT)**

**Why:**
1. **Two conditions required:**
   - `kneeAngle > 155°` (requires full standing)
   - `cooldown passed` (800ms)

2. **Silent failure:**
   - If cooldown blocks, no user feedback
   - Phase resets, rep lost

3. **Timing-dependent:**
   - Unlike other transitions (purely angle-based), this depends on **time**
   - Creates race condition with user's squat speed

---

#### 🟡 **THIRD MOST FRAGILE: DESCENDING → BOTTOM**

**Why:**
1. **Single threshold with no hysteresis:**
   - `kneeAngle < 90°` for entry
   - `kneeAngle > 90°` for exit
   - **Same value** = jitter risk

2. **Noise sensitivity:**
   - 90° is mid-range where noise is higher
   - Oscillation possible

3. **But has safety reset:**
   - If stuck, can reset to READY if angle > 155°
   - Less catastrophic than READY → DESCENDING failure

---

## 📋 FINAL ANALYSIS

### System Behavior Summary

**Current Logic:**
- Well-structured state machine ✅
- Body-relative scaling ✅
- Cooldown protection ✅
- Safety resets (partial) ✅

**Critical Weaknesses:**
- **facingCamera** is overly strict gating condition
- No hysteresis on angle thresholds (jitter risk)
- Pose validation rejects entire pose if any landmark weak
- No escape from BOTTOM phase
- Silent failures (user gets no feedback on why counting fails)
- Angle calculation returns 0° on error (causes cascading failures)

---

### Debugging Recommendations

**To diagnose "stuck at 0" issue:**

1. **Check logs for these patterns (in order of likelihood):**
   ```
   Priority 1: "Not facing camera" → #1 cause
   Priority 2: "Pose incomplete" → #2 cause  
   Priority 3: "Pose too small" → torsoLength issue
   Priority 4: Angle values not crossing thresholds
   Priority 5: "Cooldown active" (only if counter > 0 sometimes)
   ```

2. **Verify data flow:**
   ```
   - Is processPose() being called? (log should show)
   - Is state machine executing? (should see phase logs)
   - Are angles being calculated? (should see angle values)
   - Is facingCamera true? (should see validation logs)
   ```

3. **Check environmental factors:**
   ```
   - Lighting (affects landmark visibility)
   - Distance from camera (affects torsoLength)
   - Camera angle (affects 2D angle perception)
   - User orientation (affects facingCamera)
   ```

---

**End of Static Analysis Report**  
**Total Issues Identified:** 15 potential failure modes  
**Critical Blockers:** 3 (facingCamera, pose validation, torsoLength)  
**Most Likely Root Cause:** facingCamera validation failure

---

