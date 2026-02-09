# 🔍 SQUAT COUNTER DEBUG REPORT

**Report Generated:** [DATE/TIME]  
**Issue:** Squat counting not working / inconsistent  
**Reporter:** [YOUR NAME]  
**Session ID:** [UNIQUE SESSION ID]

---

## 📱 ENVIRONMENT INFO

### Device Information
- **Device Model:** _____________________
- **Android Version:** _____________________
- **API Level:** _____________________
- **Screen Resolution:** _____________________
- **Camera Resolution:** _____________________
- **Available RAM:** _____________________
- **CPU Architecture:** _____________________

### App Build Information
- **App Version:** _____________________
- **Build Type:** Debug / Release
- **MediaPipe Version:** _____________________
- **MediaPipe Delegate:** CPU / GPU
- **Build Timestamp:** _____________________
- **Git Commit Hash:** _____________________

### Environment Conditions
- **Lighting Condition:** Bright / Normal / Dim
- **Background:** Simple / Complex / Moving
- **Distance from Camera:** Near (<1m) / Medium (1-2m) / Far (>2m)
- **Camera Type:** Front (Selfie) / Back
- **User Clothing:** Dark / Light / Patterned
- **Floor Surface:** Visible / Not Visible

---

## 🐛 PROBLEM DESCRIPTION

### Issue Summary
**What is broken:**
```
[Describe the issue: e.g., "Squats are not being counted at all" or "Only 1 out of 5 squats counted"]
```

**Expected Behavior:**
```
[What should happen: e.g., "Counter should increment from 0 to 1 after completing one full squat"]
```

**Actual Behavior:**
```
[What actually happens: e.g., "Counter stays at 0 even after performing 5 squats"]
```

### Reproduction Steps
1. 
2. 
3. 
4. 

### Frequency
- [ ] Always fails (0% success rate)
- [ ] Usually fails (< 25% success rate)
- [ ] Sometimes fails (25-75% success rate)
- [ ] Rarely fails (> 75% success rate)

### Previous Working State
- [ ] Never worked
- [ ] Worked before, broke after: _____________________
- [ ] Worked in previous version: _____________________
- [ ] Unknown

---

## 🔬 PIPELINE HEALTH CHECKLIST

### Camera Pipeline
- [ ] Camera preview is visible on screen
- [ ] Camera is using FRONT (selfie) camera
- [ ] Camera resolution is appropriate (check logs)
- [ ] Frame rate is stable (~30 FPS)
- [ ] No camera errors in logcat

**Camera Status:** ✅ OK / ⚠️ WARNING / ❌ FAILED  
**Notes:**
```
[Any camera-related observations]
```

---

### MediaPipe Inference Pipeline
- [ ] MediaPipe model loaded successfully
- [ ] Pose landmarks detected (skeleton overlay visible)
- [ ] Inference time is reasonable (< 50ms)
- [ ] GPU/CPU delegate initialized correctly
- [ ] No MediaPipe errors in logcat

**MediaPipe Status:** ✅ OK / ⚠️ WARNING / ❌ FAILED  
**Average Inference Time:** _____ ms  
**Notes:**
```
[Any MediaPipe-related observations]
```

---

### ExerciseManager Pipeline
- [ ] ExerciseManager initialized
- [ ] SquatExercise set as active exercise
- [ ] PoseProcessor successfully converting poses
- [ ] Pose frames reaching SquatExercise.processPose()
- [ ] No pose validation rejections

**ExerciseManager Status:** ✅ OK / ⚠️ WARNING / ❌ FAILED  
**Active Exercise:** _____________________  
**Pose Validation Pass Rate:** _____% (from logs)  
**Notes:**
```
[Any ExerciseManager-related observations]
```

---

### Squat Detection Logic
- [ ] SquatExercise.processPose() being called
- [ ] Knee angle calculations working
- [ ] State machine transitions occurring
- [ ] Validation checks passing (facingCamera, etc.)
- [ ] Rep increment logic executing

**Squat Logic Status:** ✅ OK / ⚠️ WARNING / ❌ FAILED  
**Notes:**
```
[Any squat logic observations]
```

---

### UI Update Pipeline
- [ ] ExerciseManager callbacks firing
- [ ] CameraFragment.onRepCountUpdated() called
- [ ] TextView update executing on UI thread
- [ ] Counter text visible on screen
- [ ] No UI threading errors

**UI Status:** ✅ OK / ⚠️ WARNING / ❌ FAILED  
**Notes:**
```
[Any UI-related observations]
```

---

## 📊 RAW LOGCAT OUTPUT

### Collection Command Used
```bash
adb logcat -c && adb logcat | findstr /i "PIPELINE SQUAT STATE COUNT CALLBACK"
```

### Test Performed
- **Number of Squats Performed:** _____
- **Expected Count:** _____
- **Actual Count Displayed:** _____
- **Test Duration:** _____ seconds

### Full Logcat Output
```
[PASTE FULL LOGCAT OUTPUT HERE]

Example:
PIPELINE: detectLiveStream() called | Frame #1234
PIPELINE: MediaPipe inference complete | Time: 25ms
PIPELINE: PoseProcessor validation PASSED
SQUAT: processPose() called | Angle: 160° Phase: READY
SQUAT: Validation | facingCamera=true armsForward=true
STATE: Phase transition | READY → DESCENDING | Angle: 125°
SQUAT: processPose() called | Angle: 85° Phase: BOTTOM
STATE: Phase transition | DESCENDING → BOTTOM | Angle: 85°
...
```

---

## 📐 KNEE ANGLE OBSERVATIONS

### Sample Data (10 frames around squat attempt)

| Frame # | Timestamp | Knee Angle | Phase      | Notes                    |
|---------|-----------|------------|------------|--------------------------|
| 1       |           | 160°       | READY      | Standing upright         |
| 2       |           | 155°       | READY      | Still standing           |
| 3       |           | 145°       | READY      | Starting to descend      |
| 4       |           | 130°       | READY      | Should transition soon   |
| 5       |           | 125°       | DESCENDING | Transition occurred ✅   |
| 6       |           | 100°       | DESCENDING | Going down               |
| 7       |           | 85°        | BOTTOM     | At bottom ✅             |
| 8       |           | 95°        | ASCENDING  | Rising up ✅             |
| 9       |           | 120°       | ASCENDING  | Still rising             |
| 10      |           | 160°       | READY      | Should count rep here ✅ |

**Observations:**
```
[Did angles follow expected pattern?]
[Were thresholds crossed correctly?]
[Any anomalies in angle calculations?]
```

---

## 🔄 SQUAT PHASE STATE BEHAVIOR

### State Machine Transitions Observed

```
Squat Attempt #1:
READY (160°) → DESCENDING (125°) → BOTTOM (85°) → ASCENDING (95°) → READY (160°) → ✅ COUNT
Status: [SUCCESS / FAILED / INCOMPLETE]

Squat Attempt #2:
[Record actual state flow]
Status: [SUCCESS / FAILED / INCOMPLETE]

Squat Attempt #3:
[Record actual state flow]
Status: [SUCCESS / FAILED / INCOMPLETE]
```

### State Machine Issues Detected
- [ ] Stuck in READY phase (never transitions to DESCENDING)
- [ ] Stuck in DESCENDING phase (never reaches BOTTOM)
- [ ] Stuck in BOTTOM phase (never transitions to ASCENDING)
- [ ] Stuck in ASCENDING phase (never returns to READY)
- [ ] Premature reset (goes back to READY before completing cycle)
- [ ] Double counting (counts multiple times for one squat)
- [ ] No issues detected ✅

**Notes:**
```
[Explain state machine behavior]
```

---

## ✅ VALIDATION FLAGS ANALYSIS

### Sample Validation Data (per frame)

| Frame # | kneeAngle | facingCamera | armsForward | shoulderWidth | torsoLength | Result        |
|---------|-----------|--------------|-------------|---------------|-------------|---------------|
| 1       | 160°      | true         | true        | 0.25          | 0.40        | ✅ Valid      |
| 2       | 125°      | true         | true        | 0.24          | 0.40        | ✅ Valid      |
| 3       | 125°      | false        | true        | 0.12          | 0.40        | ❌ Not facing |
| 4       | 85°       | true         | false       | 0.26          | 0.41        | ⚠️ Arms issue |

### Validation Failure Analysis
**facingCamera failures:**
- Count: _____ / _____ frames
- Reason: _____________________
- Pattern: _____________________

**armsForward failures:**
- Count: _____ / _____ frames
- Reason: _____________________
- Pattern: _____________________

**Other validation issues:**
```
[Describe any validation patterns]
```

---

## ⏱️ COOLDOWN BEHAVIOR

### Cooldown Configuration
- **SQUAT_COOLDOWN_MS:** 800ms
- **Implementation:** Working / Broken / Unknown

### Cooldown Timing Observations

| Rep # | Last Rep Time | Current Time | Time Diff | Cooldown OK? | Counted? |
|-------|---------------|--------------|-----------|--------------|----------|
| 1     | 0             | 1000         | 1000ms    | ✅ Yes       | Yes      |
| 2     | 1000          | 1500         | 500ms     | ❌ No        | No       |
| 3     | 1000          | 2100         | 1100ms    | ✅ Yes       | Yes      |

**Cooldown Issues Detected:**
- [ ] Cooldown too aggressive (blocking valid reps)
- [ ] Cooldown not working (double counting)
- [ ] Cooldown timing incorrect
- [ ] No issues detected ✅

**Notes:**
```
[Cooldown behavior observations]
```

---

## 📡 CALLBACK STATUS

### Callback Chain Analysis

```
Expected Flow:
SquatExercise.repCount++ 
  → notifyRepCountUpdated() 
    → ExerciseManager.onRepCountUpdated() 
      → CameraFragment.onRepCountUpdated() 
        → UI thread: exerciseCounterText.text = "Air Squat: 1"
```

### Callback Execution Trace (from logs)

**Squat Attempt #1:**
```
[PASTE CALLBACK LOGS]
Example:
COUNT: repCount incremented | repCount=1
CALLBACK: notifyRepCountUpdated() called | count=1 exercise="Air Squat"
CALLBACK: ExerciseManager forwarding | count=1
CALLBACK: CameraFragment.onRepCountUpdated() | count=1 exercise="Air Squat"
CALLBACK: UI update scheduled | runOnUiThread
CALLBACK: TextView updated | text="Air Squat: 1"
```

**Callback Breakpoints:**
- [ ] repCount++ executed
- [ ] notifyRepCountUpdated() called
- [ ] ExerciseManager received callback
- [ ] CameraFragment received callback
- [ ] runOnUiThread() executed
- [ ] TextView.text updated
- [ ] UI actually shows new value

**Callback Status:** ✅ COMPLETE / ⚠️ PARTIAL / ❌ FAILED  
**Broken at step:** _____________________

---

## 🎯 UI UPDATE STATUS

### UI Threading Analysis
- [ ] UI updates happen on main thread (verified in logs)
- [ ] No "CalledFromWrongThreadException" errors
- [ ] TextView binding is valid (_fragmentCameraBinding != null)
- [ ] Counter TextView is visible on screen
- [ ] No UI layout issues blocking text

### Visual Verification
- **Counter TextView ID:** exercise_counter_text
- **Current Displayed Value:** "_____"
- **Expected Value:** "_____"
- **Text Color:** White / Other: _____
- **Text Size:** 32sp / Other: _____
- **Visibility:** VISIBLE / INVISIBLE / GONE

**UI Update Status:** ✅ OK / ⚠️ WARNING / ❌ FAILED  
**Notes:**
```
[UI update observations]
```

---

## 🔧 THRESHOLD VALUES SNAPSHOT

### Current Configuration (from code)

**Angle Thresholds:**
```kotlin
SQUAT_DOWN_ANGLE = 130f    // READY → DESCENDING
SQUAT_BOTTOM_ANGLE = 90f   // DESCENDING → BOTTOM, BOTTOM → ASCENDING
SQUAT_UP_ANGLE = 155f      // ASCENDING → READY (COUNT REP)
```

**Validation Thresholds:**
```kotlin
ARM_HORIZONTAL_Y_RATIO = 0.25f      // Wrist-Shoulder Y tolerance
ARM_FORWARD_X_RATIO = 0.50f         // Wrist X-distance tolerance
MIN_SHOULDER_WIDTH_RATIO = 0.18f    // Shoulder width / torso length
```

**Timing Thresholds:**
```kotlin
SQUAT_COOLDOWN_MS = 800L   // Minimum time between reps
```

**Pose Validation Thresholds:**
```kotlin
VISIBILITY_THRESHOLD = 0.3f   // Landmark visibility (0.0-1.0)
```

### Threshold Appropriateness
- [ ] Thresholds match user's ROM (Range of Motion)
- [ ] Thresholds too strict (user can't meet requirements)
- [ ] Thresholds too lenient (false positives)
- [ ] Thresholds appropriate ✅

**Recommended Adjustments:**
```
[If thresholds need adjustment, suggest values]
```

---

## 🧮 ANGLE CALCULATION VERIFICATION

### calculateAngle() Function Test

**Input Test Case:**
```kotlin
// Standing position (should be ~160-180°)
Hip:    (x: 0.5, y: 0.4)
Knee:   (x: 0.5, y: 0.6)
Ankle:  (x: 0.5, y: 0.8)

Expected Angle: ~180° (straight leg)
Actual Angle from logs: _____°
```

**Input Test Case:**
```kotlin
// Squat position (should be ~90°)
Hip:    (x: 0.5, y: 0.5)
Knee:   (x: 0.5, y: 0.6)
Ankle:  (x: 0.6, y: 0.7)

Expected Angle: ~90° (bent leg)
Actual Angle from logs: _____°
```

### Angle Calculation Issues
- [ ] Angles always return 0°
- [ ] Angles return NaN or Infinity
- [ ] Angles inverted (180° when should be 90°)
- [ ] Angles fluctuate wildly frame-to-frame
- [ ] Angles calculated correctly ✅

**Notes:**
```
[Angle calculation observations]
```

---

## 🔍 POSE PROCESSOR VALIDATION

### PoseProcessor.processResult() Analysis

**Pose Acceptance Rate:**
- Total frames analyzed: _____
- Poses passed validation: _____
- Poses rejected: _____
- Pass rate: _____%

**Rejection Reasons (from logs):**
```
Example:
PoseProcessor: ⚠️ Pose incomplete - core landmarks not visible enough (Frame #123)
PoseProcessor: ⚠️ No landmarks detected in frame (Frame #145)
```

**Landmark Visibility Sample:**

| Landmark       | Visibility | Required? | Pass? |
|----------------|------------|-----------|-------|
| LEFT_SHOULDER  | 0.85       | Yes       | ✅    |
| RIGHT_SHOULDER | 0.82       | Yes       | ✅    |
| LEFT_HIP       | 0.78       | Yes       | ✅    |
| RIGHT_HIP      | 0.75       | Yes       | ✅    |
| LEFT_KNEE      | 0.65       | Yes       | ✅    |
| RIGHT_KNEE     | 0.62       | Yes       | ✅    |
| LEFT_ANKLE     | 0.45       | Yes       | ✅    |
| RIGHT_ANKLE    | 0.42       | Yes       | ✅    |
| LEFT_WRIST     | 0.25       | No        | N/A   |
| RIGHT_WRIST    | 0.22       | No        | N/A   |

**PoseProcessor Status:** ✅ OK / ⚠️ WARNING / ❌ FAILED  
**Notes:**
```
[PoseProcessor behavior]
```

---

## 📝 DEVELOPER NOTES

### Hypotheses
1. 
2. 
3. 

### Additional Observations
```
[Any other relevant observations not covered above]
```

### Potential Root Causes
- [ ] Camera pipeline issue
- [ ] MediaPipe inference issue
- [ ] Pose validation too strict
- [ ] Angle calculation bug
- [ ] State machine logic error
- [ ] Validation flags incorrect
- [ ] Cooldown blocking reps
- [ ] Callback not firing
- [ ] UI threading issue
- [ ] TextView not updating
- [ ] Other: _____________________

### Recommended Next Steps
1. 
2. 
3. 

### Code Changes Since Last Working Version
```
[List any recent code changes]
```

### Related Issues / PRs
- 
- 

---

## 📎 ATTACHMENTS

### Screenshots
- [ ] App UI showing counter value
- [ ] Skeleton overlay (if visible)
- [ ] Logcat output
- [ ] User performing squat (for reference)

### Video Recording
- [ ] Screen recording of squat attempt (if available)
- [ ] Video duration: _____ seconds
- [ ] Video shows: _____________________

### Log Files
- [ ] Full logcat.txt attached
- [ ] Filtered SQUAT logs attached
- [ ] Filtered CALLBACK logs attached

---

## ✅ CHECKLIST FOR REPORT COMPLETION

Before submitting this debug report, verify:

- [ ] All sections filled out
- [ ] Logcat output pasted
- [ ] At least 3 squat attempts documented
- [ ] Angle data recorded for at least 10 frames
- [ ] Validation flags recorded
- [ ] Callback trace included
- [ ] Device info complete
- [ ] Build info complete
- [ ] Screenshots/video attached (if possible)

---

## 🚀 QUICK DIAGNOSIS

**Most Likely Issue (based on data):**
```
[Your assessment based on the data collected]
```

**Confidence Level:** High / Medium / Low

**Quick Fix Attempt:**
```
[If you have a hypothesis, suggest a quick fix to test]
```

---

**Report End**  
**Generated by:** [YOUR NAME]  
**Date:** [DATE]  
**Time Spent Debugging:** _____ minutes

