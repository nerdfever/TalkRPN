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

# The look: the project's LED red on black, monospace throughout.
BACKGROUND = "#000000"
LED_RED = "#FF0000"
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

        # ---- The state readout: T Z Y above the big X display ----

        self.registers = {}
        for name in ("T", "Z", "Y"):
            row = tk.Frame(root, bg=BACKGROUND)
            row.pack(fill="x", padx=12)
            tk.Label(row, text=name, width=6, anchor="e", font=STACK_FONT,
                     fg=LABEL_GREY, bg=BACKGROUND).pack(side="left")
            value = tk.Label(row, text="", anchor="e", font=STACK_FONT,
                             fg=LED_RED, bg=BACKGROUND)
            value.pack(side="right")
            self.registers[name] = value

        # X gets the formatter's own rendering, big.
        self.display = tk.Label(root, text="", anchor="e", font=DISPLAY_FONT,
                                fg=LED_RED, bg=BACKGROUND)
        self.display.pack(fill="x", padx=12, pady=(6, 6))

        # LASTX and the one storage register, below.
        for name in ("LASTX", "STO"):
            row = tk.Frame(root, bg=BACKGROUND)
            row.pack(fill="x", padx=12)
            tk.Label(row, text=name, width=6, anchor="e", font=STACK_FONT,
                     fg=LABEL_GREY, bg=BACKGROUND).pack(side="left")
            value = tk.Label(row, text="", anchor="e", font=STACK_FONT,
                             fg=LED_RED, bg=BACKGROUND)
            value.pack(side="right")
            self.registers[name] = value

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

    def press(self, word: str) -> None:

        # One word down the pipe, one state line back.
        self.engine.stdin.write(word + "\n")
        self.engine.stdin.flush()

        self.read_state()

    def read_state(self) -> None:

        line = self.engine.stdout.readline()
        if not line:
            self.display.config(text="engine exited")
            return

        x, y, z, t, lastx, storage, error, display = line.rstrip("\n").split("\t")

        # The stack rows show the raw values; X shows the formatter's view,
        # exactly what the watch would render.
        self.registers["T"].config(text=t)
        self.registers["Z"].config(text=z)
        self.registers["Y"].config(text=y)
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
