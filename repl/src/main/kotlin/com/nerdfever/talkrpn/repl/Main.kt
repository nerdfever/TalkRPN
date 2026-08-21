package com.nerdfever.talkrpn.repl

import com.nerdfever.talkrpn.NumberFormatter
import com.nerdfever.talkrpn.RpnEngine
import com.nerdfever.talkrpn.RpnEngine.Token

/*
 * The engine behind a pipe: one token word per input line, one state line
 * back after each. The Python button pad in tools/ is the client; this
 * process is deliberately nothing but plumbing, so every behaviour a tester
 * sees is the engine's own.
 *
 * In:  a digit character (0-9 or .), or a word from WORD_TOKENS below,
 *      or "dsp N", or "quit".
 * Out: tab-separated -  x y z t lastx storage error(0/1) display  - the
 *      registers all through the final formatter at the current DSP (the
 *      DSP rule governs every readout, not just X); display is the entry
 *      in progress verbatim while there is one (the HP way), formatted X
 *      otherwise, and "Error" when the flag is up.
 */

// The display the tester shows: the segment font's default field.
private const val FIELD_POSITIONS = 9

private val WORD_TOKENS = mapOf(
    "enter" to Token.Enter,
    "eex" to Token.Eex,
    "clx" to Token.ClearX,
    "clear" to Token.ClearStack,
    "chs" to Token.Chs,
    "swap" to Token.SwapXY,
    "rdn" to Token.RollDown,
    "rup" to Token.RollUp,
    "+" to Token.Add,
    "-" to Token.Subtract,
    "*" to Token.Multiply,
    "/" to Token.Divide,
    "sqrt" to Token.Sqrt,
    "inv" to Token.Reciprocal,
    "lastx" to Token.LastX,
    "pi" to Token.Pi,
    "sto" to Token.Sto,
    "rcl" to Token.Rcl,
)

fun main() {

    val engine = RpnEngine()
    val field = NumberFormatter.FieldShape(FIELD_POSITIONS, punctuationCostsCell = false)

    // The opening state, so the client can draw before the first press.
    emit(engine, field)

    while (true) {

        val line = readlnOrNull()?.trim()?.lowercase() ?: break
        if (line.isEmpty()) continue
        if (line == "quit") break

        // Parse the one line into the one token.
        val token = when {
            line.length == 1 && (line[0].isDigit() || line[0] == NumberFormatter.RADIX) ->
                Token.Digit(line[0])
            line.startsWith("dsp ") ->
                line.removePrefix("dsp ").toIntOrNull()?.let { Token.Dsp(it) }
            else -> WORD_TOKENS[line]
        }

        // An unknown word is reported, not guessed at.
        if (token == null) {
            System.err.println("unknown token: $line")
            emit(engine, field)
            continue
        }

        engine.press(token)
        emit(engine, field)
    }
}

/** One state line out, flushed - the pipe's reader is waiting on it. */
private fun emit(engine: RpnEngine, field: NumberFormatter.FieldShape) {

    // Every register readout obeys the DSP rule - the formatter at the
    // current places, never raw digits.
    fun formatted(value: Double) = NumberFormatter.format(
        value, NumberFormatter.Mode.FIX, engine.dspPlaces, field
    )

    // Mid-entry the display is the keystrokes so far, verbatim - the HP
    // way; the formatter takes over once entry ends.
    val display =
        if (engine.error) "Error"
        else if (engine.entry.isNotEmpty()) engine.entry
        else formatted(engine.x)

    println(
        listOf(
            formatted(engine.x), formatted(engine.y),
            formatted(engine.z), formatted(engine.t),
            formatted(engine.lastX), formatted(engine.storage),
            if (engine.error) 1 else 0,
            display,
        ).joinToString("\t")
    )

    System.out.flush()
}
