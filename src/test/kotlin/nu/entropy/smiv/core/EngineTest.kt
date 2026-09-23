package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val engine = Engine()

    /** Type [keys] against [text] with the caret at `|`, applying effects to a plain string. */
    private fun run(textWithCaret: String, keys: String, clipboard: String? = null): Result {
        var caret = textWithCaret.indexOf('|')
        var text = textWithCaret.replace("|", "")
        var copied: String? = null
        val ide = mutableListOf<Effect.Ide>()
        for (key in keys) {
            for (effect in engine.type(key, TextView(text, caret)) { clipboard }) {
                when (effect) {
                    is Effect.Replace -> text = text.replaceRange(effect.start, effect.end, effect.text)
                    is Effect.MoveCaret -> caret = effect.offset
                    is Effect.SetClipboard -> copied = effect.text
                    is Effect.Ide -> ide += effect
                    is Effect.Message -> Unit
                }
            }
        }
        return Result(text.substring(0, caret) + "|" + text.substring(caret), copied, ide)
    }

    private data class Result(val text: String, val clipboard: String?, val ide: List<Effect.Ide>)

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
    fun `keys are ignored in INSERT`() {
        engine.state.mode = Mode.INSERT
        assertEquals("|abc", run("|abc", "x").text)
    }

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
    fun `x deletes characters into register 8`() {
        assertEquals("a|d", run("a|bcd", "2x").text)
        assertEquals("bc", engine.state.registers[8].text)
        assertEquals("abc|", run("abc|", "x").text)
    }

    @Test
    fun `x deletes a selection when no count is given`() {
        val effects = engine.type('x', TextView("abcdef", 4, 1, 4)) { null }
        assertEquals(Effect.Replace(1, 4, ""), effects[0])
        assertEquals(Effect.MoveCaret(1), effects[1])
    }

    @Test
    fun `b deletes lines`() {
        assertEquals("one\n|three", run("one\ntw|o\nthree", "b").text)
        assertEquals("one\n|two", run("one\ntwo\nthr|ee", "b").text)
        assertEquals("|", run("on|ly", "b").text)
        assertEquals("|four", run("o|ne\ntwo\nthree\nfour", "3b").text)
        assertEquals("|one", run("one\ntw|o\nthree", "5b").text)
        assertEquals("two\nthree\n", engine.state.registers[8].text)
    }

    @Test
    fun `y yanks whole lines ending with a break`() {
        val result = run("one\ntw|o\nthree", "2y")
        assertEquals("two\nthree\n", result.clipboard)
        assertEquals("one\ntw|o\nthree", result.text)
        assertTrue(engine.state.registers[0].linewise)
    }

    @Test
    fun `p pastes before, P after (charwise)`() {
        assertEquals("a|XYbc", run("a|bc", "p", clipboard = "XY").text)
        assertEquals("ab|XYc", run("a|bc", "P", clipboard = "XY").text)
        assertEquals("abc|XY", run("abc|", "P", clipboard = "XY").text)
    }

    @Test
    fun `p and P paste lines above and below`() {
        assertEquals("one\n|new\ntwo", run("one\nt|wo", "p", clipboard = "new\n").text)
        assertEquals("one\n|new\ntwo", run("o|ne\ntwo", "P", clipboard = "new\n").text)
        assertEquals("one\ntwo\n|new", run("one\nt|wo", "P", clipboard = "new\n").text)
    }

    @Test
    fun `paste with empty clipboard does nothing`() {
        assertEquals("a|bc", run("a|bc", "p", clipboard = null).text)
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
}
