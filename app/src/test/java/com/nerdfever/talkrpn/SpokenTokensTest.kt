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
            Result.Redo -> fail("unexpected redo: $utterance")
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

    @Test fun aTimeRewriteDropsItsColon() {
        // "three fifty five" comes back as "3:55"; the colon is the
        // recognizer's dressing, so the digits glue into 355.
        say("3:55 enter")
        assertEquals(355.0, engine.x, 0.0)

        assertEquals("355 ↵", SpokenTokens.trailLabel("3:55 enter"))
    }

    @Test fun eOutsideANumberIsTheConstant() {
        // The sheet's row-14 rule: "e" after digits is EEX, anywhere else
        // the base of the natural logs.
        say("e natural log")
        assertEquals(1.0, engine.x, 1e-12)
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

    // ---- The sheet's function tier ------------------------------------------------

    @Test fun powerAndSquared() {
        say("two enter 10 raise")
        assertEquals(1024.0, engine.x, 0.0)

        say("clear all nine squared")
        assertEquals(81.0, engine.x, 0.0)
    }

    @Test fun logsRoundTrip() {
        say("1000 log base ten")
        assertEquals(3.0, engine.x, 1e-12)

        say("clear all three antilog base ten")
        assertEquals(1000.0, engine.x, 1e-9)

        say("clear all five natural log natural antilog")
        assertEquals(5.0, engine.x, 1e-12)

        say("clear all e natural log")
        assertEquals(1.0, engine.x, 1e-12)
    }

    @Test fun trigHonoursTheAngleMode() {
        // Degrees is the power-on default.
        say("90 sine")
        assertEquals(1.0, engine.x, 1e-12)

        say("clear all radians pi enter two divide sine")
        assertEquals(1.0, engine.x, 1e-12)

        say("clear all degrees one arcsine")
        assertEquals(90.0, engine.x, 1e-9)
    }

    @Test fun polarAndRectangularRoundTrip() {
        // 3,4 -> r 5 at 53.13 degrees -> back to 3,4.
        say("degrees clear all four enter three polar")
        assertEquals(5.0, engine.x, 1e-9)

        say("rectangular")
        assertEquals(3.0, engine.x, 1e-9)
        assertEquals(4.0, engine.y, 1e-9)
    }

    @Test fun positiveAndNegativeSetTheSign() {
        say("five change sign positive")
        assertEquals(5.0, engine.x, 0.0)

        say("negative")
        assertEquals(-5.0, engine.x, 0.0)
    }

    @Test fun aviationDigitsAndDot() {
        say("niner fife dot oh one enter")
        assertEquals(95.01, engine.x, 0.0)
    }

    @Test fun rollAndDropAreRollDown() {
        say("one enter two enter three enter four roll")
        assertEquals(3.0, engine.x, 0.0)
        say("drop")
        assertEquals(2.0, engine.x, 0.0)
    }

    @Test fun scientificAndEngineeringAreParsingWords() {
        assertEquals(
            listOf<Token>(Token.SciMode(4)),
            tokensOf("scientific four"),
        )
        assertEquals(
            listOf<Token>(Token.EngMode(2)),
            tokensOf("engineering 2"),
        )
        assertEquals(listOf<Token>(Token.Dsp(3)), tokensOf("fixed three"))
    }

    @Test fun domainEscapesFailVisiblyAndLeaveTheMachine() {
        say("five change sign enter")
        engine.press(Token.Ln)
        assertTrue(engine.error)
        assertEquals(-5.0, engine.x, 0.0)

        engine.press(Token.Digit('2'))
        engine.press(Token.Asin)
        assertTrue(engine.error)
    }

    // ---- Atomicity and reservations ----------------------------------------------

    @Test fun anUnknownWordRejectsTheWholeUtterance() {
        val result = SpokenTokens.parse("five foobar plus")
        assertEquals(Result.Rejected("foobar"), result)
    }

    @Test fun theWristBatchOfRewrites() {
        // pie is pi; "* 10 ^" is EEX in symbol costume; "23e17" is
        // glued scientific notation - all straight from the diary.
        say("pie enter two times")
        assertEquals(6.283185307179586, engine.x, 1e-12)

        say("clear all 6.5 * 10 ^ 16 enter")
        assertEquals(6.5e16, engine.x, 1e3)

        say("clear all 23e17 enter")
        assertEquals(2.3e18, engine.x, 1e5)
    }

    @Test fun trailSymbolsReadAsSymbols() {
        assertEquals("6.5E16 ↵", SpokenTokens.trailLabel("6.5 times ten to the 16 enter"))
        assertEquals("6.5E16", SpokenTokens.trailLabel("6.5 * 10 ^ 16"))
        assertEquals("2 ↵ 3 +", SpokenTokens.trailLabel("two enter three plus"))
        assertEquals("5E-3", SpokenTokens.trailLabel("five e minus three"))
        assertEquals("9 √x", SpokenTokens.trailLabel("nine square root"))
        assertEquals("π ×", SpokenTokens.trailLabel("pie times"))
    }

    @Test fun redoAndTheStrongUndoForms() {
        assertEquals(Result.Undo, SpokenTokens.parse("undo that"))
        assertEquals(Result.Undo, SpokenTokens.parse("undo it"))
        assertEquals(Result.Redo, SpokenTokens.parse("redo"))
        assertEquals(Result.Redo, SpokenTokens.parse("redo that"))
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
        assertEquals("23 ×", SpokenTokens.trailLabel("two three times"))
        assertEquals("2.5E6 ↵", SpokenTokens.trailLabel("two point five e six enter"))
        assertEquals("988 ↵", SpokenTokens.trailLabel("988 enter"))
        assertEquals("988 + 2", SpokenTokens.trailLabel("nine eight eight plus 2"))

        // The continuation merge leans on this gluing: the old label and
        // the new utterance, re-compacted, read as the number formed.
        assertEquals("1.51535 ×", SpokenTokens.trailLabel("1.515 35 *"))
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
