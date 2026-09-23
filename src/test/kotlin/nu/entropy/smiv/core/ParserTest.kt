package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ParserTest {
    private fun complete(buffer: String): Command =
        (Parser.parse(buffer) as ParseResult.Complete).command

    private fun invalid(buffer: String) = assertEquals(buffer, ParseResult.Invalid, Parser.parse(buffer))
    private fun partial(buffer: String) = assertEquals(buffer, ParseResult.Partial, Parser.parse(buffer))

    @Test
    fun `single keys map to actions`() {
        assertEquals(Action.LEFT, complete("a").action)
        assertEquals(Action.PAGE_DOWN, complete("S").action)
        assertEquals(Action.WORD_START_RIGHT, complete("E").action)
        assertEquals(Action.DELETE_LINE, complete("b").action)
        assertEquals(Action.DELETE_WORD, complete("X").action)
        assertEquals(Action.DELETE_TO_LINE_END, complete("B").action)
        assertEquals(Action.YANK_WORD, complete("Y").action)
        assertEquals(Action.REPLACE_WORD, complete("R").action)
        assertEquals(Action.TOGGLE_CASE_CHAR, complete("§").action)
        assertEquals(Action.TOGGLE_CASE_WORD, complete("°").action)
        assertEquals(Action.CHANGE_TO_LINE_END, complete("c").action)
        assertEquals(Action.CHANGE_TO_LINE_START, complete("C").action)
        assertEquals(Action.BLOCK_FIRST_LINE, complete("-").action)
        assertEquals(Action.BLOCK_LAST_LINE, complete("_").action)
        assertEquals(Action.JOIN_LINES, complete("&").action)
        assertEquals(Action.JUMP_BRACKET_MATCH, complete("%").action)
        assertEquals(Action.INSERT_LINE_START, complete("I").action)
        assertEquals(Action.INSERT_LINE_END, complete("k").action)
        assertEquals(Action.REVERT_TO_SAVED, complete("U").action)
    }

    @Test
    fun `swaps p and P semantics like MIV`() {
        assertEquals(Action.PASTE_BEFORE, complete("p").action)
        assertEquals(Action.PASTE_AFTER, complete("P").action)
    }

    @Test
    fun `digits alone are a partial count`() {
        partial("1")
        partial("12")
    }

    @Test
    fun `count applies to countable actions only`() {
        assertEquals(Command(Action.DOWN, 10, true, "10s"), complete("10s"))
        assertEquals(5, complete("5x").count)
        assertEquals(3, complete("3X").count)
        assertEquals(2, complete("2Y").count)
        assertEquals(4, complete("4R").count)
        assertEquals(3, complete("3§").count)
        assertEquals(2, complete("2c").count)
        assertEquals(Command(Action.UNDO, 1, true, "3u"), complete("3u"))
        assertEquals(1, complete("3B").count)
    }

    @Test
    fun `zero and huge counts are clamped`() {
        assertEquals(1, complete("0w").count)
        assertEquals(MAX_COUNT, complete("99999999w").count)
    }

    @Test
    fun `r waits for the replacement character`() {
        partial("r")
        assertEquals(Command(Action.REPLACE_CHAR, sequence = "rZ", char = 'Z'), complete("rZ"))
        assertEquals(' ', complete("r ").char)
        assertEquals('x', complete("3rx").char)
        invalid("rab")
    }

    @Test
    fun `count space register x or y`() {
        partial("5 ")
        partial("5 3")
        assertEquals(Command(Action.DELETE_CHAR, 5, true, "5 3x", register = 3), complete("5 3x"))
        assertEquals(Command(Action.YANK_LINE, 10, true, "10 2y", register = 2), complete("10 2y"))
        assertEquals(9, complete("1 9x").register)
        invalid("5 a")
        invalid("12 y")
        invalid("2 yy")
        invalid("5 3z")
        invalid("5 3xx")
        invalid(" ")
    }

    @Test
    fun `register space x or y uses that register with count 1`() {
        assertEquals(Command(Action.YANK_LINE, 1, true, "2 y", register = 2), complete("2 y"))
        assertEquals(Command(Action.DELETE_CHAR, 1, true, "0 x", register = 0), complete("0 x"))
    }

    @Test
    fun `digit before p or P picks the register`() {
        assertEquals(Command(Action.PASTE_BEFORE, sequence = "3p", register = 3), complete("3p"))
        assertEquals(Command(Action.PASTE_AFTER, sequence = "3P", register = 3), complete("3P"))
        assertEquals(0, complete("0p").register)
        assertEquals(null, complete("p").register)
        invalid("12p")
    }

    @Test
    fun `digit v stores the clipboard`() {
        assertEquals(Command(Action.STORE_REGISTER, sequence = "2v", register = 2), complete("2v"))
        assertEquals(Action.SHOW_REGISTERS, complete("v").action)
        invalid("0v")
        invalid("12v")
    }

    @Test
    fun `goto commands`() {
        assertEquals(Command(Action.GOTO_LINE, 1, false, "g"), complete("g"))
        assertEquals(Command(Action.GOTO_LINE, 25, true, "25g"), complete("25g"))
        assertEquals(20_000, complete("20000g").count)
        assertEquals(Command(Action.GOTO_PERCENT, 50, false, "m"), complete("m"))
        assertEquals(Command(Action.GOTO_PERCENT, 30, true, "3m"), complete("3m"))
        invalid("12m")
        assertEquals(Action.DOC_END, complete("G").action)
        assertEquals(Command(Action.GOTO_LINE_FROM_BOTTOM, 3, true, "3G"), complete("3G"))
    }

    @Test
    fun `text objects`() {
        partial("\"")
        assertEquals(Command(Action.TEXT_OBJECT_YANK, sequence = "\"y", char = '"'), complete("\"y"))
        assertEquals(Command(Action.TEXT_OBJECT_DELETE, sequence = "(x", char = '('), complete("(x"))
        assertEquals(Command(Action.TEXT_OBJECT_PASTE, sequence = "!p", char = '!'), complete("!p"))
        partial("\" ")
        partial("\" 3")
        assertEquals(Command(Action.TEXT_OBJECT_YANK, sequence = "{ 3y", register = 3, char = '{'), complete("{ 3y"))
        invalid("! 3y")
        invalid("(z")
        invalid("( 3z")
    }

    @Test
    fun `repeat and search keys`() {
        assertEquals(Action.REPEAT, complete(".").action)
        assertEquals(Action.SEARCH_NEXT, complete("n").action)
        assertEquals(Action.SEARCH_PREVIOUS, complete("N").action)
    }

    @Test
    fun `unknown keys and long sequences are invalid`() {
        invalid("z")
        invalid("5zz")
        invalid("2 3p")
    }
}
