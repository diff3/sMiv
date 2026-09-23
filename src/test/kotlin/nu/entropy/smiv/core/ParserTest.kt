package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ParserTest {
    private fun complete(buffer: String): Command =
        (Parser.parse(buffer) as ParseResult.Complete).command

    @Test
    fun `single keys map to actions`() {
        assertEquals(Action.LEFT, complete("a").action)
        assertEquals(Action.PAGE_DOWN, complete("S").action)
        assertEquals(Action.WORD_START_RIGHT, complete("E").action)
        assertEquals(Action.DELETE_LINE, complete("b").action)
    }

    @Test
    fun `swaps p and P semantics like MIV`() {
        assertEquals(Action.PASTE_BEFORE, complete("p").action)
        assertEquals(Action.PASTE_AFTER, complete("P").action)
    }

    @Test
    fun `digits alone are a partial count`() {
        assertEquals(ParseResult.Partial, Parser.parse("1"))
        assertEquals(ParseResult.Partial, Parser.parse("12"))
    }

    @Test
    fun `count applies to motions and x b y`() {
        assertEquals(Command(Action.DOWN, 10, true, "10s"), complete("10s"))
        assertEquals(5, complete("5x").count)
        assertEquals(3, complete("3b").count)
        assertEquals(2, complete("2y").count)
    }

    @Test
    fun `zero and huge counts are clamped`() {
        assertEquals(1, complete("0w").count)
        assertEquals(MAX_COUNT, complete("99999999w").count)
    }

    @Test
    fun `count on non countable action runs once`() {
        assertEquals(Command(Action.UNDO, 1, true, "3u"), complete("3u"))
    }

    @Test
    fun `digit before p or P is reserved for registers`() {
        assertEquals(ParseResult.Invalid, Parser.parse("2p"))
        assertEquals(ParseResult.Invalid, Parser.parse("2P"))
    }

    @Test
    fun `unknown keys and long sequences are invalid`() {
        assertEquals(ParseResult.Invalid, Parser.parse("z"))
        assertEquals(ParseResult.Invalid, Parser.parse("5zz"))
        assertEquals(ParseResult.Invalid, Parser.parse("2 3x"))
    }
}
