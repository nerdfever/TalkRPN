package com.nerdfever.talkrpn.repl

import com.nerdfever.talkrpn.NumberFormatter
import com.nerdfever.talkrpn.RegisterReadout
import com.nerdfever.talkrpn.RpnEngine
import com.nerdfever.talkrpn.SpokenTokens
import com.nerdfever.talkrpn.TokenWords

/*
 * The engine behind a pipe: one token word per input line, one state line
 * back after each. The Python button pad in tools/ is the client; this
 * process is deliberately nothing but plumbing, so every behaviour a tester
 * sees is the engine's own. The vocabulary lives in [TokenWords] and the
 * formatting rules in [RegisterReadout] - both shared with the watch, so
 * this pipe and the wrist can never disagree.
 *
 * In:  a word [TokenWords] knows - a digit character, "enter", "+",
 *      "dsp N", ... - or "say <utterance>", a whole spoken line through
 *      [SpokenTokens], or "quit".
 * Out: tab-separated -  x y z t lastx storage error(0/1) display  - the
 *      registers all through the final formatter at the current DSP (the
 *      DSP rule governs every readout, not just X); display is the entry
 *      in progress verbatim while there is one (the HP way), formatted X
 *      otherwise, and "Error" when the flag is up.
 */

// The display the tester shows: the segment font's default field.
private const val FIELD_POSITIONS = 9

fun main() {

    // The one field size feeds both the formatter and the engine's entry
    // limit, exactly as CalcActivity wires the watch.
    val engine = RpnEngine(FIELD_POSITIONS)
    val field = NumberFormatter.FieldShape(FIELD_POSITIONS, punctuationCostsCell = false)

    // The opening state, so the client can draw before the first press.
    emit(engine, field)

    while (true) {

        val line = readlnOrNull()?.trim() ?: break
        if (line.isEmpty()) continue
        if (line.lowercase() == "quit") break

        // A whole spoken utterance, through the parser - atomically, so
        // a rejected utterance leaves the engine exactly as it stood.
        if (line.lowercase().startsWith("say ")) {
            when (val result = SpokenTokens.parse(line.drop(4))) {
                is SpokenTokens.Result.Parsed -> result.tokens.forEach(engine::press)
                is SpokenTokens.Result.Rejected -> System.err.println("rejected: ${result.word}")
            }
            emit(engine, field)
            continue
        }

        val token = TokenWords.parse(line)

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

    val registers = RegisterReadout.registerTexts(engine, field)

    println(
        listOf(
            registers["X"], registers["Y"], registers["Z"], registers["T"],
            registers["LASTX"], registers["STO"],
            if (engine.error) 1 else 0,
            RegisterReadout.displayText(engine, field),
        ).joinToString("\t")
    )

    System.out.flush()
}
