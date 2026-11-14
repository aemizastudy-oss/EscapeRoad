#!/usr/bin/env python3
"""Put temporary voices for the driving lines on the emulator (macOS only).

The Pleos on-device TTS fails on the Connect v2.1.0 emulator, so the drive chapter would be silent
there. This renders every line of app/src/main/assets/drive_lines.json (the car speaking to the driver)
with a macOS `say` voice and pushes them into the debug app's internal drop-in folder
(files/voice/voice_<id>.m4a, via run-as).
Nothing is added to the repository. Recorded files in res/raw (voice_<id>.mp3) replace these.

  python3 tools/push_demo_voices.py            # default user 10 (Pleos driver profile)
  python3 tools/push_demo_voices.py --user 0
"""
import argparse, json, os, shutil, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = os.path.join(ROOT, "app", "src", "main", "assets", "drive_lines.json")
VOICE = "Yuna"  # placeholder voice for the car


def adb():
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
    path = os.path.join(sdk, "platform-tools", "adb")
    return path if os.path.exists(path) else shutil.which("adb") or sys.exit("adb not found")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user", default="10")
    ap.add_argument("--rate", default="180")
    args = ap.parse_args()
    a = adb()
    pkg = "com.team.carescape"
    stage = "/data/local/tmp/escape_voice"
    subprocess.run([a, "shell", "rm", "-rf", stage, "&&", "mkdir", "-p", stage], check=True)
    with open(LINES, encoding="utf-8") as f:
        rows = [line for leg in json.load(f)["legs"] for line in leg["lines"]]
    with tempfile.TemporaryDirectory() as tmp:
        for r in rows:
            voice, text = VOICE, r["text"]
            aiff, m4a = os.path.join(tmp, "l.aiff"), os.path.join(tmp, f"voice_{r['id']}.m4a")
            subprocess.run(["say", "-v", voice, "-r", args.rate, "-o", aiff, text], check=True)
            subprocess.run(["afconvert", "-f", "m4af", "-d", "aac", "-b", "64000", aiff, m4a], check=True)
            subprocess.run([a, "push", m4a, f"{stage}/"], check=True, stdout=subprocess.DEVNULL)
            print(r["id"])
    subprocess.run([a, "shell", "chmod", "-R", "755", stage], check=True)
    # run-as works for debug builds only; it copies into the app's private files/voice.
    subprocess.run([a, "shell", "run-as", pkg, "--user", args.user, "sh", "-c",
                    f"'mkdir -p files/voice && rm -f files/voice/*.m4a && cp {stage}/*.m4a files/voice/'"], check=True)
    subprocess.run([a, "shell", "rm", "-rf", stage], check=False)
    print(f"{len(rows)} lines → {pkg} files/voice (user {args.user})")


if __name__ == "__main__":
    main()
