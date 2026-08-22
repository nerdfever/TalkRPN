# The RPN engine's button-pad tester.
#
# Every press goes down a pipe to the :repl process, which wraps THE Kotlin
# engine - the same RpnEngine.kt the watch compiles - so what this window
# shows is the engine's own behaviour, never a Python reimplementation's.
#
# Run it with any Python 3 (tkinter ships with the standard installer):
#
#     python tools/engine_tester.py
#
# The first run builds the :repl process via gradle (half a minute); after
# that it starts in a second or two.

import subprocess
import tkinter as tk
from pathlib import Path

# ---- Tweakables -------------------------------------------------------------

# The repo root, found from this file's own location.
REPO = Path(__file__).resolve().parent.parent

# Where gradle's installDist puts the runnable process. The build output
# lives on the SSD cache, per the repo's local.properties redirect.
REPL_SCRIPT = Path(
    r"C:\Users\davel\ssd_cache\AndroidBuilds\TalkRPN\repl\install\repl\bin\repl.bat"
)

# Watch mirroring: with the watch button ON, every press is also sent to
# the watch's CalcActivity as an adb broadcast, so the wrist and this
# window run the same token stream through two copies of the same engine -
# which therefore stay in lockstep. Toggling it ON also starts the
# activity on the watch, so its engine starts fresh alongside a fresh
# restart of this pad.
ADB = r"C:\Users\davel\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# The watch is found, not configured: toggling watch mode ON asks adb for
# its device list and picks the one that is not an emulator - with both an
# emulator and the watch attached, a bare adb call refuses to choose and
# every press would die on "more than one device". Set WATCH_SERIAL to pin
# one explicitly (e.g. "192.168.6.169:39265") and skip the search.
WATCH_SERIAL = ""
TOKEN_ACTION = "com.nerdfever.talkrpn.TOKEN"   # matches CalcActivity's receiver
CALC_ACTIVITY = "com.nerdfever.talkrpn/.CalcActivity"

# The look: the display's neon orange on black, monospace throughout.
BACKGROUND = "#000000"
NEON_ORANGE = "#FF5F1F"
LABEL_GREY = "#8A8A8A"
STACK_FONT = ("Consolas", 20)
DISPLAY_FONT = ("Consolas", 32, "bold")
BUTTON_FONT = ("Consolas", 14)

# The button pad, laid out as rows of (label, token) pairs. A token of None
# is a spacer; a single-entry row becomes one full-width bar (ENTER, as on
# the HPs). Tokens are the :repl line protocol's words.
PAD = [
    [("STO", "sto"), ("RCL", "rcl"), ("R\u2193", "rdn"), ("R\u2191", "rup")],
    [("x\u2194y", "swap"), ("LASTX", "lastx"), ("\u03c0", "pi"), ("CHS", "chs")],
    [("\u221ax", "sqrt"), ("1/x", "inv"), ("CLx", "clx"), ("CLEAR", "clear")],
    [("7", "7"), ("8", "8"), ("9", "9"), ("\u00f7", "/")],
    [("4", "4"), ("5", "5"), ("6", "6"), ("\u00d7", "*")],
    [("1", "1"), ("2", "2"), ("3", "3"), ("\u2212", "-")],
    [("0", "0"), (".", "."), ("EEX", "eex"), ("+", "+")],
    [("ENTER", "enter")],
]


def build_repl_if_needed() -> None:
    """One gradle build on first run; skipped once the script exists."""

    if REPL_SCRIPT.exists():
        return

    print("first run: building the :repl process (about half a minute)...")
    subprocess.run(
        [str(REPO / "gradlew.bat"), "-q", ":repl:installDist"],
        cwd=str(REPO),
        check=True,
    )


class EngineTester:

    def __init__(self, root: tk.Tk) -> None:

        self.root = root
        root.title("TalkRPN engine tester")
        root.configure(bg=BACKGROUND)

        # The engine process, line-buffered both ways.
        self.engine = subprocess.Popen(
            [str(REPL_SCRIPT)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            cwd=str(REPO),
            text=True,
            bufsize=1,
        )

        # ---- The state readout: T Z Y X above the big display ----
        # Every row obeys the DSP rule - the engine formats all of them at
        # the current places before they reach this window. X appears
        # twice: with the other registers, and big underneath as the
        # watch's display - which differs mid-entry, when it shows the
        # keystrokes so far instead.

        self.registers = {}
        for name in ("T", "Z", "Y", "X"):
            row = tk.Frame(root, bg=BACKGROUND)
            row.pack(fill="x", padx=12)
            tk.Label(row, text=name, width=6, anchor="e", font=STACK_FONT,
                     fg=LABEL_GREY, bg=BACKGROUND).pack(side="left")
            value = tk.Label(row, text="", anchor="e", font=STACK_FONT,
                             fg=NEON_ORANGE, bg=BACKGROUND)
            value.pack(side="right")
            self.registers[name] = value

        # X gets the formatter's own rendering, big.
        self.display = tk.Label(root, text="", anchor="e", font=DISPLAY_FONT,
                                fg=NEON_ORANGE, bg=BACKGROUND)
        self.display.pack(fill="x", padx=12, pady=(6, 6))

        # LASTX and the one storage register, below.
        for name in ("LASTX", "STO"):
            row = tk.Frame(root, bg=BACKGROUND)
            row.pack(fill="x", padx=12)
            tk.Label(row, text=name, width=6, anchor="e", font=STACK_FONT,
                     fg=LABEL_GREY, bg=BACKGROUND).pack(side="left")
            value = tk.Label(row, text="", anchor="e", font=STACK_FONT,
                             fg=NEON_ORANGE, bg=BACKGROUND)
            value.pack(side="right")
            self.registers[name] = value

        # ---- The watch mirror toggle ----

        self.to_watch = False
        self.watch_serial = ""
        self.watch_button = tk.Button(
            root, text="watch: off", font=BUTTON_FONT,
            command=self.toggle_watch,
        )
        self.watch_button.pack(pady=(8, 0))

        # ---- The pad ----

        pad = tk.Frame(root, bg=BACKGROUND)
        pad.pack(padx=12, pady=(10, 12))

        for row_number, row in enumerate(PAD):

            # A single-entry row is a full-width bar, spanning all columns.
            span = len(PAD[0]) if len(row) == 1 else 1

            for column, (label, word) in enumerate(row):
                if word is None:
                    continue
                tk.Button(
                    pad, text=label, font=BUTTON_FONT, width=6, height=1,
                    command=lambda w=word: self.press(w),
                ).grid(row=row_number, column=column, columnspan=span,
                       padx=3, pady=3, sticky="ew")

        # The opening state line, so the window starts populated.
        self.read_state()

    def find_watch(self) -> str:

        # The pinned serial wins; otherwise ask adb and take the device
        # that is not an emulator. Empty string means no watch found.
        if WATCH_SERIAL:
            return WATCH_SERIAL

        listing = subprocess.run(
            [ADB, "devices"], capture_output=True, text=True
        ).stdout

        for line in listing.splitlines()[1:]:
            parts = line.split()
            if len(parts) == 2 and parts[1] == "device" and not parts[0].startswith("emulator-"):
                return parts[0]

        return ""

    def toggle_watch(self) -> None:

        # Turning the mirror ON locates the watch and (re)starts
        # CalcActivity, so the watch's engine begins fresh - restart this
        # pad at the same time and the two run the same state from the
        # same origin.
        if not self.to_watch:

            self.watch_serial = self.find_watch()

            # No watch attached: say so on the button and stay off.
            if not self.watch_serial:
                self.watch_button.config(text="watch: none")
                return

            self.to_watch = True
            self.watch_button.config(text="watch: on")
            self.adb("shell", "am", "start", "-n", CALC_ACTIVITY)

        else:
            self.to_watch = False
            self.watch_button.config(text="watch: off")

    def adb(self, *args: str) -> None:

        # Fire and forget: a press must not wait on the radio. Failures are
        # silent by design - the watch view simply not updating IS the error
        # report, and the wrist is right there to see it. Always pinned to
        # the watch's serial: with an emulator also attached, an unpinned
        # adb refuses to pick one.
        subprocess.Popen(
            [ADB, "-s", self.watch_serial, *args],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

    def press(self, word: str) -> None:

        # One word down the pipe, one state line back.
        self.engine.stdin.write(word + "\n")
        self.engine.stdin.flush()

        # The same word to the wrist, when mirroring. Single-quoted because
        # adb shell re-parses its arguments through the device's shell,
        # which would otherwise glob the "*" of multiply.
        if self.to_watch:
            self.adb("shell", "am", "broadcast", "-a", TOKEN_ACTION,
                     "--es", "token", f"'{word}'")

        self.read_state()

    def read_state(self) -> None:

        line = self.engine.stdout.readline()
        if not line:
            self.display.config(text="engine exited")
            return

        x, y, z, t, lastx, storage, error, display = line.rstrip("\n").split("\t")

        # The register rows arrive already formatted at the current DSP;
        # the big display is the watch's view - entry keystrokes
        # mid-entry, formatted X otherwise.
        self.registers["T"].config(text=t)
        self.registers["Z"].config(text=z)
        self.registers["Y"].config(text=y)
        self.registers["X"].config(text=x)
        self.registers["LASTX"].config(text=lastx)
        self.registers["STO"].config(text=storage)
        self.display.config(text=display)


def main() -> None:

    build_repl_if_needed()

    root = tk.Tk()
    EngineTester(root)
    root.mainloop()


if __name__ == "__main__":
    main()
