package com.nerdfever.talkrpn

import com.nerdfever.talkrpn.RpnEngine.Token
import com.nerdfever.talkrpn.SpokenTokens.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/*
 * The spoken-token parser, held to DESIGN.md's parser decisions: atomic
 * utterances, the sign rule, EEX only in number context, the recognizer's
 * rewrites, fix as a parsing word, and reserved escape words.
 */

class SpokenTokensTest {

    private val engine = RpnEngine()

    /** Parses, requires success, and presses every token. */
    private fun say(utterance: String) {
        when (val result = SpokenTokens.parse(utterance)) {
            is Result.Parsed -> result.tokens.forEach { engine.press(it) }
            is Result.Rejected -> fail("rejected at '${result.word}': $utterance")
            Result.Undo -> fail("unexpected undo: $utterance")
        }
    }

    private fun tokensOf(utterance: String): List<Token> =
        (SpokenTokens.parse(utterance) as Result.Parsed).tokens

    // ---- The canonical traces, spoken ------------------------------------------

    @Test fun fiveEnterRecallPlus() {
        say("three store clear all")
        say("five enter recall plus")
        assertEquals(8.0, engine.x, 0.0)
    }

    @Test fun twoEnterThreePlusFourTimes() {
        say("two enter three plus four times")
        assertEquals(20.0, engine.x, 0.0)
    }

    // ---- Numbers, spoken and rewritten ------------------------------------------

    @Test fun spokenDigitsAccumulateIntoOneNumber() {
        say("nine eight eight enter")
        assertEquals(988.0, engine.x, 0.0)
    }

    @Test fun theRecognizersNumeralsWork() {
        // The recognizer compounds numbers itself: "988", "2.5".
        say("988 enter 2.5 times")
        assertEquals(2470.0, engine.x, 0.0)
    }

    @Test fun spokenPointBuildsAFraction() {
        say("two point five enter")
        assertEquals(2.5, engine.x, 0.0)
    }

    @Test fun eexInNumberContext() {
        say("two point five e six enter")
        assertEquals(2.5e6, engine.x, 0.0)
    }

    @Test fun exponentIsTheStrongAliasForE() {
        say("two point five exponent six enter")
        assertEquals(2.5e6, engine.x, 0.0)
    }

    @Test fun theSignRuleSignsTheExponentField() {
        // "five e minus three" is 5e-3 - the sign word lands at the
        // exponent field's start, so it signs rather than subtracts.
        say("five e minus three enter")
        assertEquals(5e-3, engine.x, 0.0)
    }

    @Test fun minusBeforeAMantissaIsSubtract() {
        // The ambiguity that sharpened the sign rule: "five minus three"
        // must be 5, SUBTRACT, 3 - never 5 then -3.
        assertEquals(
            listOf(Token.Digit('5'), Token.Subtract, Token.Digit('3')),
            tokensOf("five minus three"),
        )
    }

    @Test fun timesTenToTheIsEexTheLongWay() {
        // The natural phrase, straight from the wrist: "to?" said the
        // vocabulary lacked it. Finished-utterance parsing makes it safe
        // despite "times" being a token.
        say("6.5 times ten to the 16 enter")
        assertEquals(6.5e16, engine.x, 1e3)

        // And bare, the engine supplies the implicit 1 - HP-21 style.
        say("times ten to the six enter")
        assertEquals(1e6, engine.x, 0.0)

        // Plain multiplication by a number still multiplies.
        say("clear all 3 enter 10 times")
        assertEquals(30.0, engine.x, 0.0)
    }

    @Test fun aSplitUtteranceKeepsItsE() {
        // The endpointer can cut "6.5 ... e 16" in two; the second
        // utterance parses with entry still open and keeps its meaning.
        say("6.5")
        when (val result = SpokenTokens.parse("e 16", entryOpen = true)) {
            is Result.Parsed -> result.tokens.forEach { engine.press(it) }
            else -> fail("split utterance rejected")
        }
        assertEquals(6.5e16, engine.x, 1e3)
    }

    @Test fun aFractionRewriteDivides() {
        // The recognizer writes "seven eighths"-ish speech as "7/8"; the
        // notation means the division, so that is what it does.
        say("7/8")
        assertEquals(0.875, engine.x, 0.0)
    }

    @Test fun eOutsideANumberIsRejected() {
        // The constant e is not in the engine yet; a stray weak "e" must
        // fail visibly rather than vanish.
        assertTrue(SpokenTokens.parse("e enter") is Result.Rejected)
    }

    // ---- Commands and rewrites ------------------------------------------------

    @Test fun theRecognizersSymbolRewritesWork() {
        // "/" for divide was observed; "*" and "+" cost nothing to accept.
        say("8 enter 2 /")
        assertEquals(4.0, engine.x, 0.0)
    }

    @Test fun theBarePostfixVerbsWork() {
        // The real RPN forms, straight from the first wrist test: the
        // display answered "divide?" because only "divided by" existed.
        say("six enter two divide")
        assertEquals(3.0, engine.x, 0.0)

        say("four multiply")
        assertEquals(12.0, engine.x, 0.0)

        say("two add")
        assertEquals(14.0, engine.x, 0.0)

        say("four subtract")
        assertEquals(10.0, engine.x, 0.0)
    }

    @Test fun squareRootIsOnePhrase() {
        say("nine square root")
        assertEquals(3.0, engine.x, 0.0)
    }

    @Test fun clearXAndClearAllAreDistinct() {
        assertEquals(listOf<Token>(Token.ClearX), tokensOf("clear x"))
        assertEquals(listOf<Token>(Token.ClearStack), tokensOf("clear all"))
    }

    @Test fun fixConsumesExactlyOneNumber() {
        assertEquals(listOf<Token>(Token.Dsp(4)), tokensOf("fix four"))
        assertEquals(listOf<Token>(Token.Dsp(2)), tokensOf("fix 2"))
    }

    // ---- Atomicity and reservations ----------------------------------------------

    @Test fun anUnknownWordRejectsTheWholeUtterance() {
        val result = SpokenTokens.parse("five foobar plus")
        assertEquals(Result.Rejected("foobar"), result)
    }

    @Test fun undoAloneIsTheUndoUtterance() {
        assertEquals(Result.Undo, SpokenTokens.parse("undo"))
        assertEquals(Result.Undo, SpokenTokens.parse("cancel"))
        assertEquals(Result.Undo, SpokenTokens.parse("escape"))
    }

    @Test fun undoInsideALongerUtteranceRejects() {
        assertEquals(Result.Rejected("undo"), SpokenTokens.parse("five undo plus"))
    }

    @Test fun trailLabelsCompactSpokenDigits() {
        assertEquals("23 times", SpokenTokens.trailLabel("two three times"))
        assertEquals("2.5 e 6 enter", SpokenTokens.trailLabel("two point five e six enter"))
        assertEquals("988 enter", SpokenTokens.trailLabel("988 enter"))
        assertEquals("988 plus 2", SpokenTokens.trailLabel("nine eight eight plus 2"))
    }

    @Test fun theVocabularyRejectsProperPrefixes() {
        // The startup rule itself: "clear" beside "clear x" must throw.
        try {
            SpokenTokens.assertNoProperPrefixes(
                listOf(listOf("clear"), listOf("clear", "x"))
            )
            fail("a proper prefix survived the check")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("prefix"))
        }
    }
}
