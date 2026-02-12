# Technical Capability Summary — Pose Landmark Time‑Series Event Detection

**Context:** This document summarizes capabilities and implementation patterns from my MediaPipe Pose Landmarker exercise detection engine. It focuses on production-grade movement event detection from noisy landmark streams (normalized X/Y[/Z]) using signal processing + state machines.

---

## 1) Architecture Overview

### High-level pipeline
The system is structured as a clean pipeline where **pose acquisition** is isolated from **event logic**:

1. **Pose inference wrapper**: `PoseLandmarkerHelper.kt`
   - Runs MediaPipe Tasks API (live stream) on camera frames.
   - Emits `PoseLandmarkerResult` via callback.
   - Keeps inference concerns (GPU/CPU delegate, model selection, frame rotation/mirroring) out of exercise logic.

2. **Pose data model + geometry utilities**: `models/NormalizedPose.kt`
   - Normalizes access to landmarks through a stable map type.
   - Provides utilities for **distance** and **joint angle** calculation.
   - Keeps exercise logic independent of MediaPipe SDK surface types.

3. **MediaPipe → engine adapter**: `core/PoseProcessor.kt`
   - Converts `PoseLandmarkerResult` into `NormalizedPose` (timestamped).
   - (Optionally) computes a pose quality score.
   - Design intent: keep detection logic agnostic to upstream pose provider.

4. **Exercise/event routing**: `core/ExerciseManager.kt`
   - Owns the **active exercise**.
   - Forwards each pose frame to `activeExercise.processPose(pose)`.
   - Forwards rep/state events to UI via a listener.

5. **Per-exercise detection modules** (examples in repo):
   - `exercises/squat/SquatExercise.kt` (squat rep detection)
   - `exercises/jumpingjack/JumpingJackExercise.kt` (jumping jack detection)
   - `exercises/sumohighpull/SumoHighPullExercise.kt` (sumo high pull detection)

### State-machine-first event logic
Each movement detector is implemented as:
- a small **finite state machine (FSM)** (2–4 phases)
- explicit, readable **transition rules**
- rep counting only on a specific terminal transition
- cooldown/debounce to prevent duplicate detections

This design maps well to phase segmentation requirements (READY/DOWN/BOTTOM/UP, GROUND/AIR/LAND, etc.).

---

## 2) Signal Processing Strategy

The engine operates on real-world noisy pose landmarks (normalized coordinates). The strategy is pragmatic and mobile-friendly:

### A) Low-cost smoothing (EMA / moving average)
For high-FPS streams, lightweight smoothing is usually enough:
- **EMA (Exponential Moving Average)** for continuous signals (e.g., ankleY, hipY, knee angle):
  - `s[t] = α * x[t] + (1-α) * s[t-1]`
  - Typical `α` in 0.2–0.4 depending on FPS and jitter.
- **Short moving average** (3–5 frames) for derived features when EMA isn’t suitable.

Goal: reduce frame jitter without adding latency or heavy computation.

### B) Derivatives for motion cues (velocity/acceleration)
For event detection (e.g., jumping), velocity improves robustness:
- `v[t] = (y[t] - y[t-1]) / dt`
- optional acceleration: `a[t] = (v[t] - v[t-1]) / dt`

This helps differentiate:
- genuine takeoff vs. noisy spikes
- landing bounce vs. a new jump

### C) Hysteresis by design (not a single threshold)
To prevent “state bouncing” near thresholds, transitions use **separate enter/exit thresholds**:
- Enter AIR when signal crosses “strong” threshold
- Exit AIR only when it crosses a different “return” threshold

This is used in the exercise modules (e.g., open vs closed thresholds; down vs up angles).

---

## 3) Event Detection Design (Phase Segmentation + Anti-Double-Count)

### Core pattern
1. **Extract features** per frame
   - joint angles (e.g., hip-knee-ankle)
   - normalized distances (e.g., ankle spread / torso length)
   - position signals (e.g., ankleY)

2. **Validate** frame (cheap checks)
   - missing landmark(s) → skip frame
   - out-of-range computed angles → ignore frame (common occlusion spike handling)

3. **Update state machine**
   - transitions only when conditions are stable and meaningful

4. **Count rep** only on one terminal transition
   - plus cooldown/debounce window

### Cooldown / debouncing (duplicate prevention)
Duplicate counts often happen due to multi-peak motion (bounce) or threshold overlap.
A production approach:
- Store `lastEventTime`.
- Only count if `now - lastEventTime >= cooldownMs`.
- Cooldown is paired with hysteresis so the FSM cannot re-trigger on the same movement peak.

---

## 4) Jump Detection Approach (Ankle Y) — Preventing Double Counts

This is a robust “jump-like” detector based on ankle Y time series, suitable for Python/TypeScript implementation.

### Input signal
- Use **normalized Y** from pose landmarks.
- Build a stable ankle height signal:
  - `ankleY = mean(leftAnkle.y, rightAnkle.y)` if both available
  - else use the available ankle with the best visibility/confidence

> Note: In MediaPipe normalized coordinates, Y typically increases downward. The logic below assumes you pick a consistent sign convention (e.g., use `height = 1 - ankleY` so “up” increases height).

### Step 1 — Baseline normalization (distance + drift tolerance)
Maintain a slowly-updating standing baseline:
- `baseline = EMA(ankleHeight, αSlow)` where `αSlow` is small (e.g., 0.02–0.05)
- Compute displacement: `d = ankleHeight - baseline`

This compensates for:
- kids moving closer/farther
- slight camera pitch changes
- gradual posture drift

### Step 2 — Smoothing
Apply a faster EMA for event logic:
- `h = EMA(d, αFast)` (e.g., 0.25–0.35)

### Step 3 — Velocity/acceleration estimates
Compute velocity using timestamps to handle FPS variance:
- `dt = (t - tPrev)` (seconds)
- `v = (h - hPrev) / dt`

### Step 4 — Phased state machine with hysteresis
Use a 3-phase FSM:

**States:** `GROUND → AIR → LANDING → GROUND` (LANDING can be merged into AIR if you want simpler)

**Key thresholds (example, tuned per data):**
- `TAKEOFF_V_ENTER` : minimum upward velocity to start a jump
- `TAKEOFF_H_ENTER` : minimum displacement above baseline to confirm takeoff
- `LAND_H_EXIT` : displacement threshold to confirm landing return (smaller than enter)

**Transitions:**
- **GROUND → AIR** when:
  - `v > TAKEOFF_V_ENTER` **AND** `h > TAKEOFF_H_ENTER`
  - (optional) require condition to persist for N frames or N ms

- **AIR → LANDING** when:
  - velocity changes sign (peak passed) **OR** `v < 0` for a short duration

- **LANDING → GROUND** when:
  - `h < LAND_H_EXIT` for a small window (e.g., 2–3 frames)

### Step 5 — Double-count prevention (multi-peak landing bounce)
Multi-peak landing bounce usually looks like: `AIR → small dip → second dip`.
Prevent duplicates via:

1) **Cooldown window**
- Count the jump once when entering `GROUND` (LANDING→GROUND), but only if:
  - `now - lastJumpTime >= cooldownMs` (e.g., 400–800 ms)

2) **Re-arm condition**
- The detector must see a stable GROUND condition (`h` near baseline) before it can takeoff again.

### Edge-case handling
- **Missing landmarks**: skip update; don’t reset FSM unless missing persists for a long timeout.
- **Occlusion spikes**: clamp or ignore if `|h|` exceeds plausible biomechanical range.
- **FPS variance**: compute `dt` from timestamps; cap dt if needed to avoid huge derivative spikes.

---

## 5) Robustness & Edge Case Handling

### Noisy, real-world landmark data
Practical failure modes and mitigations:
- **Threshold jitter** → hysteresis thresholds + short stability windows
- **Multi-peak motion** → FSM + cooldown + re-arm condition
- **Occlusion spikes** → range checks + skip invalid frames (don’t “teleport” state)
- **Partial body in frame** → fail-soft policy (skip frames, preserve state)

### Clean separation of concerns
The pipeline is organized so you can:
- swap pose provider (MediaPipe/OpenPose/custom) by re-implementing PoseProcessor
- keep event logic unchanged because it depends only on normalized landmark data

---

## 6) Production Readiness

### Performance (real-time)
- Constant-time features per frame: a few distances/angles + simple arithmetic.
- No heavy filtering or optimization loops.
- Logging can be rate-limited (e.g., once/sec) to avoid throughput impact.

### Deterministic and testable logic
The FSM-based approach is deterministic and can be tested offline:
- record landmark JSON per frame
- replay through detectors
- compare expected events vs detected events

---

## 7) Optional Enhancements (Low-risk, High-value)

1) **Confidence score per event**
- Combine feature margins (how far past thresholds), signal stability, and landmark visibility.

2) **Structured event output contract**
Example:
```json
{
  "type": "jump",
  "startTs": 123456789,
  "peakTs": 123456920,
  "endTs": 123457050,
  "repIndex": 12,
  "confidence": 0.86,
  "features": {
    "takeoffV": 0.42,
    "peakHeight": 0.18,
    "flightTimeMs": 280
  }
}
```

3) **Config-driven thresholds**
- Keep constants in a config object for fast tuning per population (kids/adults) and camera setups.

4) **Offline validation harness**
- CLI tool (Python or Node) to:
  - load recorded landmarks
  - run detector
  - output events + diagnostics (plots optional)

5) **Performance monitoring hooks**
- lightweight timers (per stage) + counters for dropped frames / invalid frames.

---

## Relevant Code Footprint (from my engine)
- Pipeline wrapper: `app/src/main/java/.../PoseLandmarkerHelper.kt`
- Adapter/model: `core/PoseProcessor.kt`, `models/NormalizedPose.kt`
- Routing: `core/ExerciseManager.kt`
- Detectors (FSM + cooldown patterns):
  - `exercises/jumpingjack/JumpingJackExercise.kt`
  - `exercises/sumohighpull/SumoHighPullExercise.kt`
  - `exercises/squat/SquatExercise.kt`

