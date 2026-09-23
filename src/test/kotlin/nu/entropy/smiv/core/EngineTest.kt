package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val engine = Engine()

    /** Simulated system clipboard (register 9). */
    private var clipboard: String? = null

    /** Type [keys] against [textWithCaret] (caret at `|`), applying each key's effects to a plain string. */
    private fun run(textWithCaret: String, keys: String): Result {
        val state = Editor(textWithCaret)
        for (key in keys) state.apply(engine.type(key, state.view(), ::clipboard))
        return state.result()
    }

    /** Run a single engine call, such as [Engine.paragraph], against [textWithCaret]. */
    private fun apply(textWithCaret: String, command: (TextView) -> List<Effect>): Result {
        val state = Editor(textWithCaret)
        state.apply(command(state.view()))
        return state.result()
    }

    private inner class Editor(textWithCaret: String) {
        var caret = textWithCaret.indexOf('|')
        var text = textWithCaret.replace("|", "")
        val ide = mutableListOf<Effect.Ide>()
        val messages = mutableListOf<String>()

        fun view() = TextView(text, caret)

        fun apply(effects: List<Effect>) {
            for (effect in effects) {
                when (effect) {
                    is Effect.Replace -> text = text.replaceRange(effect.start, effect.end, effect.text)
                    is Effect.MoveCaret -> caret = effect.offset
                    is Effect.SetClipboard -> clipboard = effect.text
                    is Effect.Ide -> ide += effect
                    is Effect.Message -> messages += effect.text
                }
            }
        }

        fun result() = Result(text.substring(0, caret) + "|" + text.substring(caret), ide, messages)
    }

    private data class Result(val text: String, val ide: List<Effect.Ide>, val messages: List<String>)

    // ---- modes ----

    @Test
    fun `i and space enter INSERT, escape returns to NAV`() {
        run("|abc", "i")
        assertEquals(Mode.INSERT, engine.state.mode)
        engine.escape()
        assertEquals(Mode.NAV, engine.state.mode)
        run("|abc", " ")
        assertEquals(Mode.INSERT, engine.state.mode)
    }

    @Test
    fun `space after a count is a register separator, not INSERT`() {
        run("|abc", "5 ")
        assertEquals(Mode.NAV, engine.state.mode)
        assertEquals("5 ", engine.commandLine)
        engine.cancelPending()
        assertEquals("", engine.commandLine)
    }

    @Test
    fun `keys are ignored in INSERT`() {
        engine.state.mode = Mode.INSERT
        assertEquals("|abc", run("|abc", "x").text)
    }

    @Test
    fun `I and k move then enter INSERT`() {
        assertEquals(listOf(Effect.Ide(IdeOp.LINE_START)), run("a|b", "I").ide)
        assertEquals(Mode.INSERT, engine.state.mode)
        engine.escape()
        assertEquals(listOf(Effect.Ide(IdeOp.LINE_END)), run("a|b", "k").ide)
        assertEquals(Mode.INSERT, engine.state.mode)
    }

    // ---- motions ----

    @Test
    fun `counted motions become repeated ide ops`() {
        assertEquals(listOf(Effect.Ide(IdeOp.DOWN, 10)), run("|a", "10s").ide)
        assertEquals(listOf(Effect.Ide(IdeOp.LINE_END, 1)), run("|a", "D").ide)
    }

    @Test
    fun `pending count is shown and cleared`() {
        run("|a", "12")
        assertEquals("12", engine.commandLine)
        run("|a", "a")
        assertEquals("", engine.commandLine)
        run("|a", "5z")
        assertEquals("", engine.commandLine)
    }

    @Test
    fun `word motions`() {
        assertEquals("foo bar|.baz", run("foo |bar.baz", "e").text)
        assertEquals("foo bar.baz|", run("foo |bar.baz", "3e").text)
        assertEquals("foo |bar.baz", run("foo bar|.baz", "q").text)
        assertEquals("foo bar|.baz", run("foo bar.|baz", "Q").text)
        assertEquals("foo |bar", run("|foo bar", "E").text)
        assertEquals("|foo bar", run("foo b|ar", "2q").text)
    }

    @Test
    fun `g goes to a line keeping the column`() {
        assertEquals("on|e\ntwo\nthree", run("one\ntw|o\nthree", "g").text)
        assertEquals("one\ntwo\nth|ree", run("one\ntw|o\nthree", "3g").text)
        assertEquals("one\ntwo\nth|ree", run("one\ntw|o\nthree", "99g").text)
        assertEquals("abcd\nb|", run("abc|d\nb", "2g").text)
    }

    @Test
    fun `G goes to the end, nG counts lines from the bottom`() {
        assertEquals("one\ntwo\nthree|", run("o|ne\ntwo\nthree", "G").text)
        assertEquals("one\ntwo\nt|hree", run("o|ne\ntwo\nthree", "1G").text)
        assertEquals("o|ne\ntwo\nthree", run("one\ntwo\nt|hree", "3G").text)
        assertEquals("o|ne\ntwo\nthree", run("one\ntwo\nt|hree", "9G").text)
    }

    @Test
    fun `m goes to a percentage of the document`() {
        val doc = "l0\nl1\nl2\nl3\nl4"
        assertEquals("l0\nl1\n|l2\nl3\nl4", run("l|0\nl1\nl2\nl3\nl4", "m").text)
        assertEquals("l0\nl1\nl2\n|l3\nl4", run("|$doc", "7m").text)
        assertEquals("|$doc", run("l0\nl|1\nl2\nl3\nl4", "1m").text)
    }

    @Test
    fun `percent jumps between brackets and quotes`() {
        assertEquals("f(a[b]c|)", run("f|(a[b]c)", "%").text)
        assertEquals("f|(a[b]c)", run("f(a[b]c|)", "%").text)
        assertEquals("(ab cd|)", run("(ab |cd)", "%").text)
        assertEquals("{\"hello|\"}", run("{|\"hello\"}", "%").text)
        assertEquals("|abc", run("|abc", "%").text)
    }

    @Test
    fun `paragraph motions`() {
        val text = "a\nb\n\n\nc\nd\n\ne"
        assertEquals("a\nb\n\n\n|c\nd\n\ne", apply("|$text") { view -> engine.paragraph(view, forward = true) }.text)
        assertEquals("a\nb\n\n\nc\nd\n\n|e", apply("a\nb\n\n\nc\n|d\n\ne") { view -> engine.paragraph(view, forward = true) }.text)
        assertEquals("a\nb\n\n\nc\nd\n\n|e", apply("a\nb\n\n\nc\nd\n\n|e") { view -> engine.paragraph(view, forward = true) }.text)
        assertEquals("|$text", apply("a\nb\n\n\nc\n|d\n\ne") { view -> engine.paragraph(view, forward = false) }.text)
    }

    // ---- editing ----

    @Test
    fun `x deletes characters into register 8`() {
        val result = run("a|bcd", "2x")
        assertEquals("a|d", result.text)
        assertEquals(Register("bc"), engine.state.registers[8])
        assertEquals(listOf("stored delete in register 8"), result.messages)
        assertEquals("abc|", run("abc|", "x").text)
    }

    @Test
    fun `x deletes a selection when no count is given`() {
        val effects = engine.type('x', TextView("abcdef", 4, 1, 4)) { null }
        assertEquals(Effect.Replace(1, 4, ""), effects[0])
        assertEquals(Effect.MoveCaret(1), effects[1])
    }

    @Test
    fun `X deletes words and B to line end`() {
        assertEquals("| bar", run("|foo bar", "X").text)
        assertEquals("fo|", run("fo|o bar", "2X").text)
        assertEquals(Register("o bar"), engine.state.registers[8])
        assertEquals("ab|\nef", run("ab|cd\nef", "B").text)
        assertEquals(Register("cd"), engine.state.registers[8])
    }

    @Test
    fun `b deletes lines`() {
        assertEquals("one\n|three", run("one\ntw|o\nthree", "b").text)
        assertEquals("one\n|two", run("one\ntwo\nthr|ee", "b").text)
        assertEquals("|", run("on|ly", "b").text)
        assertEquals("|four", run("o|ne\ntwo\nthree\nfour", "3b").text)
        assertEquals("|one", run("one\ntw|o\nthree", "5b").text)
        assertEquals(Register("two\nthree\n", linewise = true), engine.state.registers[8])
    }

    @Test
    fun `r replaces the character under the caret`() {
        assertEquals("a|Zc", run("a|bc", "rZ").text)
        assertEquals("a| c", run("a|bc", "r ").text)
        assertEquals("abc|\nd", run("abc|\nd", "rZ").text)
        assertEquals(Mode.NAV, engine.state.mode)
    }

    @Test
    fun `R deletes words and enters INSERT`() {
        assertEquals("foo | baz", run("foo b|ar baz", "R").text)
        assertEquals(Mode.INSERT, engine.state.mode)
        engine.escape()
        assertEquals("| baz", run("|foo bar baz", "2R").text)
        engine.escape()
        assertEquals("foo  |", run("foo | bar", "R").text)
        engine.escape()
        assertEquals("foo |", run("foo |", "R").text)
        assertEquals(Mode.NAV, engine.state.mode)
    }

    @Test
    fun `toggle case of characters and words keeps the caret`() {
        assertEquals("|ABc", run("|abC", "3§").text)
        assertEquals("ä|B1", run("ä|b1", "§").text)
        assertEquals("FO|O Bar", run("fo|o Bar", "°").text)
        assertEquals("FO|O bAR", run("fo|o Bar", "2°").text)
    }

    @Test
    fun `dash and underscore change text and enter INSERT`() {
        assertEquals("ab|\ncd", run("ab|cd\ncd", "-").text)
        assertEquals(Mode.INSERT, engine.state.mode)
        engine.escape()
        assertEquals("  |\nx", run("  ab|cd\nx", "_").text)
        assertEquals(Mode.INSERT, engine.state.mode)
    }

    @Test
    fun `ampersand joins lines through the IDE`() {
        assertEquals(listOf(Effect.Ide(IdeOp.JOIN_LINES)), run("a|\nb", "&").ide)
    }

    @Test
    fun `o and O open a line and enter INSERT`() {
        assertEquals(listOf(Effect.Ide(IdeOp.NEW_LINE_ABOVE)), run("|a", "O").ide)
        assertEquals(Mode.INSERT, engine.state.mode)
    }

    @Test
    fun `u undoes`() {
        assertEquals(listOf(Effect.Ide(IdeOp.UNDO)), run("|a", "u").ide)
    }

    // ---- registers and clipboard ----

    @Test
    fun `y yanks whole lines into register 0 and the clipboard`() {
        val result = run("one\ntw|o\nthree", "2y")
        assertEquals("two\nthree\n", clipboard)
        assertEquals("one\ntw|o\nthree", result.text)
        assertEquals(Register("two\nthree\n", linewise = true), engine.state.registers[0])
        assertEquals(listOf("stored yank in register 0"), result.messages)
    }

    @Test
    fun `Y yanks words`() {
        run("|foo bar baz", "2Y")
        assertEquals("foo bar", clipboard)
        assertEquals(Register("foo bar"), engine.state.registers[0])
    }

    @Test
    fun `count space register targets x and y`() {
        assertEquals("|ef", run("|abcdef", "4 3x").text)
        assertEquals(Register("abcd"), engine.state.registers[3])
        assertEquals(Register(), engine.state.registers[8])

        run("|one\ntwo\nthree", "2 5y")
        assertEquals(Register("one\ntwo\n", linewise = true), engine.state.registers[5])
        assertEquals("one\ntwo\n", clipboard)

        run("|abc", "1 9x")
        assertEquals("a", clipboard)
    }

    @Test
    fun `p pastes before, P after, from the clipboard`() {
        clipboard = "XY"
        assertEquals("a|XYbc", run("a|bc", "p").text)
        assertEquals("ab|XYc", run("a|bc", "P").text)
        assertEquals("abc|XY", run("abc|", "P").text)
    }

    @Test
    fun `p and P paste lines above and below`() {
        clipboard = "new\n"
        assertEquals("one\n|new\ntwo", run("one\nt|wo", "p").text)
        assertEquals("one\n|new\ntwo", run("o|ne\ntwo", "P").text)
        assertEquals("one\ntwo\n|new", run("one\nt|wo", "P").text)
    }

    @Test
    fun `digit p and P paste from a register`() {
        engine.state.registers[3] = Register("reg3")
        engine.state.registers[4] = Register("line\n", linewise = true)
        clipboard = "clip"
        val result = run("a|b", "3p")
        assertEquals("a|reg3b", result.text)
        assertEquals(listOf("pasted register 3"), result.messages)
        assertEquals("ab|reg3", run("a|b", "3P").text)
        assertEquals("x\n|line\ny", run("x\n|y", "4p").text)
        assertEquals("a|clipb", run("a|b", "9p").text)
    }

    @Test
    fun `0p pastes the last yank even after the clipboard changed`() {
        run("|abc", "y")
        clipboard = "other"
        assertEquals("|abc\nabc", run("|abc", "0p").text)
    }

    @Test
    fun `yank then p pastes the line even from the last line`() {
        assertEquals("one\n|two\ntwo", run("one\ntw|o", "yp").text)
        assertEquals("one\ntwo\n|two", run("one\ntw|o", "yP").text)
    }

    @Test
    fun `paste with empty register does nothing`() {
        assertEquals("a|bc", run("a|bc", "p").text)
        assertEquals("a|bc", run("a|bc", "5p").text)
    }

    @Test
    fun `digit v stores the clipboard in a register`() {
        clipboard = "saved\n"
        val result = run("|a", "2v")
        assertEquals(Register("saved\n", linewise = true), engine.state.registers[2])
        assertEquals(listOf("stored clipboard in register 2"), result.messages)

        clipboard = null
        assertEquals(listOf("clipboard empty"), run("|a", "3v").messages)
        assertEquals(Register(), engine.state.registers[3])
    }

    @Test
    fun `register 9 reads the live clipboard`() {
        clipboard = "copied with cmd+c"
        assertEquals(Register("copied with cmd+c"), engine.register(9) { clipboard })
        clipboard = "a line\n"
        assertTrue(engine.register(9) { clipboard }.linewise)
    }
}
