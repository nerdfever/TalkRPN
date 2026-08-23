package com.nerdfever.talkrpn

/*
 * The engine's registers as display text - the DSP rule applied in one
 * place, shared by the watch screen and the :repl process behind the
 * desktop pad, so every readout everywhere formats the same way.
 */

object RegisterReadout {

    /**
     * Every register through the final formatter at the engine's current
     * DSP. Keyed by the display's register names.
     */
    fun registerTexts(
        engine: RpnEngine,
        field: NumberFormatter.FieldShape,
    ): Map<String, String> {

        fun formatted(value: Double) = NumberFormatter.format(
            value, NumberFormatter.Mode.FIX, engine.dspPlaces, field
        )

        return mapOf(
            "T" to formatted(engine.t),
            "Z" to formatted(engine.z),
            "Y" to formatted(engine.y),
            "X" to formatted(engine.x),
            "LASTX" to formatted(engine.lastX),
            "STO" to formatted(engine.storage),
        )
    }

    /**
     * What the X display proper shows: the keystrokes so far while entry
     * is in progress - the HP way, with the HP-55's habit of always
     * showing the mantissa's decimal point ("5" reads "5.") - formatted X
     * otherwise, and the error word over everything while the flag is up.
     */
    fun displayText(
        engine: RpnEngine,
        field: NumberFormatter.FieldShape,
    ): String = when {
        engine.error -> "Error"
        engine.entry.isNotEmpty() -> withEntryRadix(engine.entry)
        else -> NumberFormatter.format(
            engine.x, NumberFormatter.Mode.FIX, engine.dspPlaces, field
        )
    }

    /**
     * The HP-55 habit: a mantissa under entry always shows its decimal
     * point, even before one is typed. The point belongs to the mantissa,
     * so with an exponent open it lands before the marker - "5E3" reads
     * "5.E3" - and an entry that already carries a radix is left alone.
     */
    private fun withEntryRadix(entry: String): String {

        val marker = entry.indexOf(NumberFormatter.EXPONENT_MARKER)
        val mantissa = if (marker >= 0) entry.take(marker) else entry

        if (mantissa.contains(NumberFormatter.RADIX)) return entry

        val exponentPart = if (marker >= 0) entry.substring(marker) else ""
        return mantissa + NumberFormatter.RADIX + exponentPart
    }
}
