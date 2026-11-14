#!/usr/bin/env python3
"""Generate the drive-chapter BGM loop (original, lyric-free, no samples).

Low drone + slow pulse + sparse pentatonic bell tones. Every frequency is a
multiple of 1/LOOP_S Hz and bell tails wrap around, so the loop is seamless.
Usage: python3 tools/make_drive_bgm.py app/src/main/res/raw/bgm_drive_siyeok.wav
"""
import math, random, struct, sys, wave

RATE = 22050
LOOP_S = 32
N = RATE * LOOP_S
TAU = 2 * math.pi

def q(f):  # snap to a whole number of cycles per loop
    return round(f * LOOP_S) / LOOP_S

buf = [0.0] * N
# Drone: A1 + E2 + A2, each slowly breathing (LFO cycles fit the loop exactly).
for f, amp, lfo in ((55, .30, 2), (82.5, .16, 3), (110, .10, 5)):
    f = q(f)
    for i in range(N):
        t = i / RATE
        breath = .75 + .25 * math.sin(TAU * lfo * t / LOOP_S)
        buf[i] += amp * breath * math.sin(TAU * f * t)

# Heartbeat-like soft pulse every 2 s (low thump, no percussion samples).
for beat in range(LOOP_S // 2):
    start = int(beat * 2 * RATE)
    for k in range(int(.35 * RATE)):
        t = k / RATE
        buf[(start + k) % N] += .22 * math.exp(-t * 14) * math.sin(TAU * 48 * t)

# Sparse bells: A minor pentatonic, deterministic pattern.
random.seed(1795)
scale = [220, 261.63, 293.66, 329.63, 392, 440, 523.25]
for k in range(10):
    start = int((k * 3.2 + random.uniform(0, .6)) * RATE)
    f = q(random.choice(scale))
    for j in range(int(4 * RATE)):
        t = j / RATE
        env = math.exp(-t * 1.4) * min(1, t * 200)
        s = math.sin(TAU * f * t) + .35 * math.sin(TAU * 2.76 * f * t) * math.exp(-t * 3)
        buf[(start + j) % N] += .09 * env * s

peak = max(abs(x) for x in buf)
gain = .8 / peak
out = sys.argv[1] if len(sys.argv) > 1 else "bgm_siyeok_drive.wav"
with wave.open(out, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(x * gain * 32767)) for x in buf))
print(out, f"{LOOP_S}s", f"{N * 2 // 1024} KiB")
