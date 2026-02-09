# 🎮 SQUAT COUNTER - QUICK TEST GUIDE

## ⚡ QUICK VERIFICATION (30 seconds)

### Test 1: Basic Squat
1. Stand in front of camera (front-facing/selfie)
2. Perform ONE full squat
3. **Expected:** Counter shows "Air Squat: 1"
4. **Log:** "SQUAT ✔ Count = 1"

✅ **PASS** = Counter increments  
❌ **FAIL** = Counter stays at 0

---

## 📋 FULL TEST SUITE (5 minutes)

### Test 2: Multiple Squats
- Perform 5 squats in a row
- **Expected:** Count reaches 5
- **Verify:** All reps counted

### Test 3: Partial Squat (Should NOT Count)
- Bend knees only ~110° (half squat)
- **Expected:** Count stays at 0
- **Reason:** Doesn't reach BOTTOM (< 95°)

### Test 4: Rapid Squats
- Perform 3 squats as fast as possible
- **Expected:** All 3 count (cooldown is 700ms)

### Test 5: Sideways Orientation
- Turn body 30-45° to side
- Perform squat
- **Expected:** Still counts ✅

### Test 6: Very Close to Camera
- Move very close (face fills screen)
- Perform squat
- **Expected:** Still counts ✅

### Test 7: Far from Camera
- Step back 2-3 meters
- Perform squat
- **Expected:** Still counts ✅ (if skeleton visible)

---

## 🔍 DEBUG CHECKLIST

### If Counter Stuck at 0:

**1. Check Skeleton Overlay:**
- Can you see green skeleton on screen?
- If NO → MediaPipe not detecting pose
- If YES → Continue to #2

**2. Check Logcat:**
```bash
adb logcat | findstr /i "SQUAT STATE COUNT"
```

**3. Look for State Transitions:**
```
✅ Good: READY → DOWN → BOTTOM → UP → READY
❌ Bad: READY (stuck - no transitions)
```

**4. Check Angle Values:**
```
✅ Good: Angle: 165° → 130° → 90° → 165°
❌ Bad: Angle never goes below 135° (not squatting deep enough)
```

**5. Check for Invalid Angle Warnings:**
```
⚠️ Invalid angle: 15.0° (ignored)
```
- If many invalid angles → landmarks not visible
- Solution: Better lighting, move closer

---

## 📊 WHAT MAKES A VALID SQUAT

### Requirements:
1. ✅ Knee angle starts > 160° (standing)
2. ✅ Knee angle drops < 135° (DOWN phase)
3. ✅ Knee angle reaches < 95° (BOTTOM phase)
4. ✅ Knee angle rises > 105° (UP phase)
5. ✅ Knee angle returns > 160° (count rep)
6. ✅ Time since last squat > 700ms (cooldown)

### What's NOT Required:
- ❌ Facing camera perfectly
- ❌ Arms in specific position
- ❌ Minimum distance from camera
- ❌ Perfect posture
- ❌ Both legs visible (uses left leg only)

---

## 🎯 EXPECTED LOG OUTPUT

### Normal Squat Cycle:
```
SQUAT: State: READY | Angle: 165.0° | Count: 0
STATE: READY → DOWN | Angle: 130.0°
STATE: DOWN → BOTTOM | Angle: 90.0°
STATE: BOTTOM → UP | Angle: 110.0°
COUNT: SQUAT ✔ Count = 1
STATE: UP → READY (COUNT) | Angle: 165.0° | Count: 1
```

### Partial Squat (No Count):
```
SQUAT: State: READY | Angle: 165.0°
STATE: READY → DOWN | Angle: 130.0°
SQUAT: State: DOWN | Angle: 110.0° (stuck - not deep enough)
STATE: DOWN → READY (aborted) | Angle: 165.0°
```

### Cooldown Block:
```
COUNT: SQUAT ✔ Count = 1
[User squats again immediately]
⏱️ Cooldown active (450ms < 700ms)
STATE: UP → READY (no count)
```

---

## 🔧 TROUBLESHOOTING

### Problem: Counter Stuck at 0

**Check 1: Is pose detected?**
```bash
adb logcat | findstr "PoseProcessor"
```
- Look for: "✅ Pose processed successfully"
- If not appearing → Landmarks not visible enough

**Check 2: Are state transitions happening?**
```bash
adb logcat | findstr "STATE:"
```
- Should see: READY → DOWN → BOTTOM → UP → READY
- If stuck in READY → User not bending enough (< 135°)

**Check 3: What are the angle values?**
```bash
adb logcat | findstr "Angle:"
```
- Standing: Should be 160-180°
- Squatting: Should go below 95°
- If angles never cross thresholds → ROM issue or camera angle

---

### Problem: Double Counting

**Symptom:** One squat counts as 2  
**Cause:** Cooldown too short or angle jitter  
**Fix:** Increase cooldown to 800ms or 1000ms

**Log to Check:**
```
COUNT: SQUAT ✔ Count = 1
[Less than 700ms later]
COUNT: SQUAT ✔ Count = 2  ← Should be blocked!
```

---

### Problem: Missed Reps

**Symptom:** Do 5 squats, only 3 count  
**Causes:**
1. **Partial squats** - Not reaching < 95°
2. **Not standing fully** - Not reaching > 160°
3. **Too fast** - Cooldown blocking (< 700ms)

**Solution:**
- Encourage deeper squats
- Stand fully upright between reps
- Slow down slightly

---

## 🎮 GAME DESIGN TIPS

### Difficulty Levels:

**Easy Mode:**
```kotlin
DOWN_ENTER = 140f
BOTTOM_ENTER = 100f
UP_ENTER = 155f
COOLDOWN_MS = 600L
```

**Normal Mode (Current):**
```kotlin
DOWN_ENTER = 135f
BOTTOM_ENTER = 95f
UP_ENTER = 160f
COOLDOWN_MS = 700L
```

**Hard Mode:**
```kotlin
DOWN_ENTER = 130f
BOTTOM_ENTER = 90f
UP_ENTER = 165f
COOLDOWN_MS = 800L
```

---

## 📞 QUICK SUPPORT

**Still not working?**

1. **Share these logs:**
   ```bash
   adb logcat -d | findstr /i "SQUAT STATE COUNT" > squat_debug.txt
   ```

2. **Share this info:**
   - Device model
   - Lighting conditions
   - Distance from camera
   - User age/height
   - Video of squat attempt (if possible)

3. **Check these files:**
   - `GAME_MODE_IMPLEMENTATION.md` - Full implementation details
   - `STATIC_DEBUG_REPORT.md` - Technical analysis

---

## ✅ SUCCESS CRITERIA

**The system works if:**
- ✅ 10 squats → count = 10 (100% accuracy)
- ✅ Works sideways (up to 45° rotation)
- ✅ Works close and far from camera
- ✅ Never double counts
- ✅ Kids can play without frustration

**Known Limitations:**
- ⚠️ Partial squats don't count (intentional)
- ⚠️ Very rapid squats (<700ms) may be blocked
- ⚠️ Requires left hip/knee/ankle visible

---

**Test Version:** Game-Mode Balanced  
**Date:** January 30, 2026  
**Status:** Ready for kid testing

