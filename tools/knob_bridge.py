# The knob bridge: turn the tweak knobs on the EMULATOR, watch the WATCH
# follow, live.
#
# The display's tuning panel writes every knob change to logcat as one
# state line (tag TalkRpnKnobs, format shared with DisplayTuning.kt's
# applyStateLine). This script tails the emulator's log for those lines
# and forwards each to the watch as a KNOBS broadcast, which CalcActivity
# applies to its own knobs. One direction only - emulator to watch - so
# no state can circle back and loop.
#
#     python tools/knob_bridge.py
#
# Runs until interrupted. Both devices must be attached and running the
# calculator; open the emulator's panel (tap the display) and turn knobs.

import subprocess
import sys

# ---- Tweakables -------------------------------------------------------------

ADB = r"C:\Users\davel\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# The logcat tag the panel writes and the broadcast the watch receives -
# both must match the constants in DisplayTuning.kt / CalcActivity.kt.
KNOB_LOG_TAG = "TalkRpnKnobs"
KNOBS_ACTION = "com.nerdfever.talkrpn.KNOBS"


def find_devices() -> tuple[str, str]:
    """The emulator's serial and the watch's, from adb's device list."""

    listing = subprocess.run(
        [ADB, "devices"], capture_output=True, text=True
    ).stdout

    emulator = ""
    watch = ""

    for line in listing.splitlines()[1:]:
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device":
            if parts[0].startswith("emulator-"):
                emulator = parts[0]
            else:
                watch = parts[0]

    return emulator, watch


def main() -> None:

    emulator, watch = find_devices()
    if not emulator or not watch:
        sys.exit(f"need both devices attached (emulator={emulator!r}, watch={watch!r})")

    print(f"bridging knobs: {emulator} -> {watch}  (Ctrl+C to stop)")

    # Tail the emulator's log from NOW (-T 1), this tag only.
    tail = subprocess.Popen(
        [ADB, "-s", emulator, "logcat", "-T", "1", "-s", f"{KNOB_LOG_TAG}:I"],
        stdout=subprocess.PIPE,
        text=True,
        bufsize=1,
    )

    for line in tail.stdout:

        # A logcat line ends "...TalkRpnKnobs: g=1.1000 hf=..."; everything
        # after the tag's colon is the state line itself.
        marker = f"{KNOB_LOG_TAG}: "
        at = line.find(marker)
        if at < 0:
            continue

        state = line[at + len(marker):].strip()
        if not state:
            continue

        # Forward, single-quoted: adb shell re-parses its arguments
        # through the device's shell.
        subprocess.run(
            [ADB, "-s", watch, "shell", "am", "broadcast",
             "-a", KNOBS_ACTION, "--es", "state", f"'{state}'"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

        print("->", state)


if __name__ == "__main__":
    main()
