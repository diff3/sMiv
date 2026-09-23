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
        assertEquals(Command(Action.GOTO_PERCENT, 33, true, "33m"), complete("33m"))
        invalid("123m")
        invalid("0m")
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
    fun `f and F take a character and a count`() {
        partial("f")
        partial("3F")
        assertEquals(Command(Action.FIND_CHAR, 1, false, "f,", char = ','), complete("f,"))
        assertEquals(Command(Action.FIND_CHAR_BACKWARD, 3, true, "3F(", char = '('), complete("3F("))
        invalid("fab")
    }

    @Test
    fun `dash and underscore take a percentage digit`() {
        assertEquals(Command(Action.BLOCK_FIRST_LINE, 0, false, "-"), complete("-"))
        assertEquals(Command(Action.BLOCK_FIRST_LINE, 50, true, "5-"), complete("5-"))
        assertEquals(Command(Action.BLOCK_LAST_LINE, 30, true, "3_"), complete("3_"))
        assertEquals(Command(Action.BLOCK_FIRST_LINE, 15, true, "15-"), complete("15-"))
        assertEquals(Command(Action.BLOCK_LAST_LINE, 75, true, "75_"), complete("75_"))
        invalid("100-")
        invalid("0_")
        invalid("00-")
    }

    @Test
    fun `new single keys and text objects with delimiters`() {
        assertEquals(Action.TOGGLE_SELECT, complete("V").action)
        assertEquals(Action.REPEAT_FIND, complete(";").action)
        assertEquals(Action.SEARCH_WORD_FORWARD, complete("*").action)
        assertEquals(Action.SEARCH_WORD_BACKWARD, complete("#").action)
        assertEquals(Command(Action.MOVE_LINE_DOWN, 2, true, "2J"), complete("2J"))
        assertEquals(Action.MOVE_LINE_UP, complete("K").action)
        assertEquals(Action.INDENT, complete("L").action)
        assertEquals(Action.OUTDENT, complete("H").action)
        assertEquals(Action.CENTER_LINE, complete("z").action)
        assertEquals(Command(Action.TEXT_OBJECT_DELETE_AROUND, sequence = "\"X", char = '"'), complete("\"X"))
        assertEquals(Command(Action.TEXT_OBJECT_YANK_AROUND, sequence = "( 3Y", register = 3, char = '('), complete("( 3Y"))
    }

    @Test
    fun `repeat and search keys`() {
        assertEquals(Action.REPEAT, complete(".").action)
        assertEquals(Action.SEARCH_NEXT, complete("n").action)
        assertEquals(Action.SEARCH_PREVIOUS, complete("N").action)
    }

    @Test
    fun `unknown keys and long sequences are invalid`() {
        invalid("j")
        invalid("5jj")
        invalid("2 3p")
    }
}
