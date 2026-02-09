# 🎯 GAME-MODE SQUAT DETECTION - VISUAL GUIDE

## 📊 ANGLE THRESHOLDS VISUALIZATION

```
180° ┃                                    ← MAX (invalid above this)
     ┃
165° ┃  ╔══════════════════════════╗
160° ┃  ║  UP_ENTER (count rep!)   ║     ← Must cross to count
     ┃  ╚══════════════════════════╝
     ┃         ▲            ▲
     ┃         │            │
     ┃         │   READY    │
     ┃         │   ZONE     │
     ┃         │            │
135° ┃  ╔══════▼════════════╗
     ┃  ║  DOWN_ENTER       ║            ← Start descending
     ┃  ╚═══════════════════╝
     ┃         │
     ┃         │   DOWN
     ┃         │   ZONE
     ┃         │
105° ┃  ╔══════▼════════════╗
     ┃  ║  BOTTOM_EXIT      ║            ← Exit bottom (hysteresis)
     ┃  ╠═══════════════════╣
     ┃  ║  HYSTERESIS GAP   ║            ← 10° buffer zone
     ┃  ╠═══════════════════╣
 95° ┃  ║  BOTTOM_ENTER     ║            ← Enter bottom
     ┃  ╚═══════════════════╝
     ┃         │
     ┃         │  BOTTOM
     ┃         │  ZONE
     ┃         │
 40° ┃  ═══════▼════════════            ← MIN (invalid below this)
     ┃
  0° ┃
```

---

## 🔄 STATE MACHINE FLOW

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  👤 USER STANDING                                          │
│     Angle: 165°                                             │
│     State: READY                                            │
│     Count: 0                                                │
│                                                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ User bends knees
                       │ Angle drops to 130°
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  TRANSITION: READY → DOWN                                   │
│  Trigger: kneeAngle < 135°                                  │
│  Log: "STATE: READY → DOWN | Angle: 130.0°"                │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ User continues descending
                       │ Angle drops to 90°
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  TRANSITION: DOWN → BOTTOM                                  │
│  Trigger: kneeAngle < 95°                                   │
│  Log: "STATE: DOWN → BOTTOM | Angle: 90.0°"                │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ User holds bottom position
                       │ Angle: 85° (stays in BOTTOM)
                       │
                       │ User starts rising
                       │ Angle rises to 110°
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  TRANSITION: BOTTOM → UP                                    │
│  Trigger: kneeAngle > 105° (hysteresis exit!)              │
│  Log: "STATE: BOTTOM → UP | Angle: 110.0°"                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ User continues rising
                       │ Angle rises to 165°
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  TRANSITION: UP → READY (COUNT!)                            │
│  Trigger: kneeAngle > 160° AND cooldown passed              │
│  Log: "COUNT: SQUAT ✔ Count = 1"                           │
│  Log: "STATE: UP → READY (COUNT) | Angle: 165.0°"         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ ✅ REP COUNTED
                       │ Count: 1
                       │ State: READY
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  👤 USER STANDING (READY FOR NEXT SQUAT)                   │
│     Angle: 165°                                             │
│     State: READY                                            │
│     Count: 1                                                │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚙️ HYSTERESIS EXPLAINED

### Without Hysteresis (Old System):
```
User at 90° (threshold):
Frame 1: 89.8° → ENTER BOTTOM
Frame 2: 90.2° → EXIT BOTTOM
Frame 3: 89.9° → ENTER BOTTOM
Frame 4: 90.1° → EXIT BOTTOM
❌ PROBLEM: Bouncing between states (jitter)
```

### With Hysteresis (New System):
```
User at 90° (between thresholds):
Frame 1: 89.8° → ENTER BOTTOM (< 95°)
Frame 2: 90.2° → STAY IN BOTTOM (not > 105°)
Frame 3: 89.9° → STAY IN BOTTOM
Frame 4: 90.1° → STAY IN BOTTOM
...
Frame 10: 106° → EXIT BOTTOM (> 105°)
✅ SOLUTION: Stable state, no bouncing
```

**Key Concept:**
- **Entry threshold:** 95° (strict - must go below)
- **Exit threshold:** 105° (loose - must go above)
- **10° buffer zone** prevents ping-pong between states

---

## 🛡️ ANGLE VALIDATION FILTER

```
┌─────────────────────────────────────────────────────────────┐
│  INCOMING ANGLE FROM CALCULATION                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
              ┌────────────────┐
              │ Is angle < 40°? │
              └────────┬────────┘
                       │
                ┌──────┴──────┐
                │             │
               YES           NO
                │             │
                ▼             ▼
         ┌─────────────┐  ┌────────────────┐
         │ REJECT      │  │ Is angle > 180°?│
         │ (occlusion) │  └────────┬────────┘
         └─────────────┘           │
                              ┌────┴────┐
                              │         │
                             YES       NO
                              │         │
                              ▼         ▼
                       ┌─────────────┐  ┌────────────────┐
                       │ REJECT      │  │ ✅ VALID ANGLE │
                       │ (error)     │  │ Process it     │
                       └─────────────┘  └────────────────┘

EXAMPLES:
  15° → REJECTED (likely occluded knee)
 165° → ACCEPTED (normal standing)
  90° → ACCEPTED (deep squat)
 200° → REJECTED (calculation error)
```

**Why This Helps:**
- Filters out landmark occlusion (spike to ~0°)
- Filters out calculation errors (spike to >180°)
- Prevents false state transitions
- Doesn't block gameplay (just skips bad frames)

---

## ⏱️ COOLDOWN MECHANISM

```
FIRST SQUAT:
  Count: 0 → 1
  lastSquatTime = 0 → currentTime (e.g., 10000ms)
  ✅ COUNTED (cooldown bypassed for first rep)

SECOND SQUAT (too fast):
  Time: 10450ms
  Elapsed: 10450 - 10000 = 450ms
  Cooldown: 700ms
  450ms < 700ms → ❌ BLOCKED
  State: Reset to READY without counting

THIRD SQUAT (enough time passed):
  Time: 10800ms
  Elapsed: 10800 - 10000 = 800ms
  Cooldown: 700ms
  800ms > 700ms → ✅ COUNTED
  Count: 1 → 2
  lastSquatTime = 10800ms
```

**Purpose:**
- Prevents double counting from jitter
- Prevents cheating (rapid partial movements)
- Allows normal gameplay (700ms is fast enough)

---

## 🎯 WHAT MAKES A VALID SQUAT

```
FULL SQUAT CYCLE (VALID):

Time  | Angle | State  | Action
------|-------|--------|---------------------------
 0ms  | 165°  | READY  | Standing
 500  | 130°  | DOWN   | Transition triggered ✓
 1000 | 100°  | DOWN   | Still descending
 1500 |  90°  | BOTTOM | Reached bottom ✓
 2000 |  85°  | BOTTOM | Holding bottom
 2500 | 110°  | UP     | Rising (hysteresis exit) ✓
 3000 | 140°  | UP     | Still rising
 3500 | 165°  | READY  | Standing ✓ COUNT = 1 ✅
```

```
PARTIAL SQUAT (INVALID):

Time  | Angle | State  | Action
------|-------|--------|---------------------------
 0ms  | 165°  | READY  | Standing
 500  | 130°  | DOWN   | Transition triggered ✓
 1000 | 110°  | DOWN   | Still descending
 1500 | 100°  | DOWN   | Not deep enough (> 95°) ❌
 2000 | 120°  | DOWN   | Rising back up
 2500 | 165°  | READY  | Safety reset (aborted)
       |       |        | COUNT = 0 (no count) ❌
```

```
TOO FAST (COOLDOWN):

Squat 1: 0ms → COUNT = 1 ✅
Squat 2: 600ms → BLOCKED (< 700ms) ❌
Squat 3: 1500ms → COUNT = 2 ✅
```

---

## 🚫 WHAT WAS REMOVED

### Old System Blockers:
```
❌ facingCamera check:
   if (shoulderWidth <= minShoulderWidth) {
       return; // BLOCKED
   }

❌ torsoLength check:
   if (torsoLength < 0.1f) {
       return; // BLOCKED
   }

❌ Arm position check:
   if (!armsForward) {
       // Warning but could affect transitions
   }

❌ Same threshold for entry/exit:
   ENTER: < 90°
   EXIT:  > 90°  // Same value = jitter risk
```

### New System (Simplified):
```
✅ ONLY checks:
   1. Are 3 landmarks present? (hip, knee, ankle)
   2. Is angle in valid range? (40° - 180°)
   3. Does angle cross thresholds?
   4. Has cooldown passed? (700ms)

✅ NO checks for:
   - Shoulder width
   - Facing camera
   - Arm position
   - Torso length
   - Pose visibility scores
```

---

## 📏 COMPARISON: OLD vs NEW

```
OLD THRESHOLDS:
  DOWN:   130° ──┐
                 ├─ 25° deadzone (wasted range)
  READY:  155° ──┘
  
  BOTTOM: 90°  ──┐
                 ├─ 0° gap (jitter risk!)
  EXIT:   90°  ──┘

NEW THRESHOLDS:
  DOWN:   135° ──┐
                 ├─ 25° deadzone (still exists)
  READY:  160° ──┘
  
  BOTTOM: 95°  ──┐
                 ├─ 10° hysteresis (stable!)
  EXIT:   105° ──┘
```

**Key Improvements:**
- ✅ Hysteresis added (10° buffer)
- ✅ Stricter standing requirement (160° vs 155°)
- ✅ Slightly easier bottom entry (95° vs 90°)
- ✅ Faster cooldown (700ms vs 800ms)

---

## 🎮 DIFFICULTY TUNING

```
EASY MODE (Kids 6-8):
  135° ─────┐
            ├─ Start easier
  140° ─────┘
  
  100° ─────┐
            ├─ Don't need deep squat
  110° ─────┘
  
  155° ───── Count sooner
  600ms ──── Fast gameplay

NORMAL MODE (Kids 9-12):
  135° ─────┐
            ├─ Current settings
  160° ─────┘
  
   95° ─────┐
            ├─ Balanced
  105° ─────┘
  
  160° ───── Standard
  700ms ──── Balanced

HARD MODE (Older):
  130° ─────┐
            ├─ More ROM required
  165° ─────┘
  
   90° ─────┐
            ├─ Deeper squats
  100° ─────┘
  
  165° ───── Full standing
  800ms ──── Prevent cheating
```

---

## ✅ VALIDATION EXAMPLES

```
VALID SQUATS:
✅ Perfect form: 165° → 85° → 165° (full ROM)
✅ Good form:    160° → 90° → 162° (adequate)
✅ Acceptable:   161° → 94° → 161° (minimal)
✅ Sideways:     165° → 85° → 165° (rotated 30°)
✅ Close camera: 165° → 85° → 165° (close distance)
✅ Far camera:   165° → 85° → 165° (far distance)

INVALID SQUATS:
❌ Too shallow:  165° → 100° → 165° (doesn't reach < 95°)
❌ Partial up:   165° → 85° → 155° (doesn't reach > 160°)
❌ Too fast:     Rep at 0ms, rep at 600ms (< 700ms)
❌ Occluded:     165° → 15° → 165° (spike rejected)
```

---

## 🔍 DEBUG VISUALIZATION

```
NORMAL OPERATION:
┌────┬────┬────┬────┬────┬────┬────┬────┐
│165°│130°│100°│ 90°│110°│140°│165°│165°│  Angle
├────┼────┼────┼────┼────┼────┼────┼────┤
│ R  │ D  │ D  │ B  │ U  │ U  │ R  │ R  │  State
├────┼────┼────┼────┼────┼────┼────┼────┤
│ 0  │ 0  │ 0  │ 0  │ 0  │ 0  │ 1  │ 1  │  Count
└────┴────┴────┴────┴────┴────┴────┴────┘
   ↑    ↑         ↑    ↑         ↑
   │    │         │    │         └─ REP COUNTED ✅
   │    │         │    └─ Hysteresis exit
   │    │         └─ Reached bottom
   │    └─ Started descending
   └─ Standing ready

STUCK IN READY (user not squatting):
┌────┬────┬────┬────┬────┬────┐
│165°│165°│164°│166°│165°│165°│  Angle
├────┼────┼────┼────┼────┼────┤
│ R  │ R  │ R  │ R  │ R  │ R  │  State (stuck)
├────┼────┼────┼────┼────┼────┤
│ 0  │ 0  │ 0  │ 0  │ 0  │ 0  │  Count
└────┴────┴────┴────┴────┴────┘
Problem: Angle never drops below 135°

STUCK IN DOWN (not squatting deep enough):
┌────┬────┬────┬────┬────┬────┬────┬────┐
│165°│130°│110°│100°│110°│120°│165°│165°│  Angle
├────┼────┼────┼────┼────┼────┼────┼────┤
│ R  │ D  │ D  │ D  │ D  │ D  │ R  │ R  │  State
├────┼────┼────┼────┼────┼────┼────┼────┤
│ 0  │ 0  │ 0  │ 0  │ 0  │ 0  │ 0  │ 0  │  Count
└────┴────┴────┴────┴────┴────┴────┴────┘
                          ↑
                          └─ Safety reset (aborted)
Problem: Angle never drops below 95° (bottom)

OCCLUSION SPIKE (filtered):
┌────┬────┬────┬────┬────┬────┬────┐
│165°│130°│ 15°│100°│ 90°│110°│165°│  Angle
├────┼────┼────┼────┼────┼────┼────┤
│ R  │ D  │ D* │ D  │ B  │ U  │ R  │  State
├────┼────┼────┼────┼────┼────┼────┤
│ 0  │ 0  │ 0  │ 0  │ 0  │ 0  │ 1  │  Count
└────┴────┴────┴────┴────┴────┴────┘
        ↑
        └─ 15° rejected (invalid), frame skipped
           State stays in DOWN ✅
```

---

**This visual guide complements:**
- GAME_MODE_IMPLEMENTATION.md (technical details)
- QUICK_TEST_GUIDE.md (testing procedures)
- IMPLEMENTATION_SUMMARY.md (overview)

