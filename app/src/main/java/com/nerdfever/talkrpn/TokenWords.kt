package com.nerdfever.talkrpn

import com.nerdfever.talkrpn.RpnEngine.Token

/*
 * The token vocabulary: one word, one press. THE one place a word becomes
 * a token, shared by the :repl process behind the desktop button pad and
 * the watch's adb-driven test receiver - so a word can never mean two
 * different presses on two surfaces. The eventual speech parser will speak
 * in these words too.
 */

object TokenWords {

    // DSP takes its argument by one-token lookahead, so it arrives here as
    // one word: "dsp 4".
    private const val DSP_PREFIX = "dsp "

    private val WORDS = mapOf(
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

    /** The word as a token, or null for a word that is not one. */
    fun parse(word: String): Token? {

        val line = word.trim().lowercase()

        return when {
            line.length == 1 && (line[0].isDigit() || line[0] == NumberFormatter.RADIX) ->
                Token.Digit(line[0])
            line.startsWith(DSP_PREFIX) ->
                line.removePrefix(DSP_PREFIX).toIntOrNull()?.let { Token.Dsp(it) }
            else -> WORDS[line]
        }
    }
}
