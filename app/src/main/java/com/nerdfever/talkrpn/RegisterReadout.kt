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
     * What the X display proper shows: the keystrokes so far, verbatim,
     * while entry is in progress - the HP way - formatted X otherwise,
     * and the error word over everything while the flag is up.
     */
    fun displayText(
        engine: RpnEngine,
        field: NumberFormatter.FieldShape,
    ): String = when {
        engine.error -> "Error"
        engine.entry.isNotEmpty() -> engine.entry
        else -> NumberFormatter.format(
            engine.x, NumberFormatter.Mode.FIX, engine.dspPlaces, field
        )
    }
}
