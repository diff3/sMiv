package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val engine = Engine()

    /** Simulated system clipboard (register 9). */
    private var clipboard: String? = null

    /**
     * Type [keys] against [textWithCaret] (caret at `|`), applying each key's effects to a
     * plain string. `\n` is Enter, `\b` is Backspace and `\u001b` is Escape.
     */
    private fun run(textWithCaret: String, keys: String): Result {
        val state = Editor(textWithCaret)
        for (key in keys) {
            when (key) {
                '\n' -> state.apply(
                    engine.enter(state.view(), ::clipboard)
                        ?: listOf(Effect.Replace(state.caret, state.caret, "\n"), Effect.MoveCaret(state.caret + 1)),
                )
                '\b' -> engine.backspace()
                '\u001b' -> state.apply(engine.escape())
                else -> state.apply(engine.type(key, state.view(), ::clipboard))
            }
        }
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
        var highlight: Effect.Highlight? = null
        var flash: Effect.Flash? = null
        var registersShown: Effect.ShowRegisters? = null

        fun view() = TextView(text, caret)

        fun apply(effects: List<Effect>) {
            for (effect in effects) {
                when (effect) {
                    is Effect.Replace -> text = text.replaceRange(effect.start, effect.end, effect.text)
                    is Effect.MoveCaret -> caret = effect.offset
                    is Effect.SetClipboard -> clipboard = effect.text
                    is Effect.Ide -> ide += effect
                    is Effect.Message -> messages += effect.text
                    is Effect.Highlight -> highlight = effect
                    is Effect.Flash -> flash = effect
                    is Effect.ShowRegisters -> registersShown = effect
                }
            }
        }

        fun result(): Result {
            // Like the IDE, keep the caret inside the text after edits that shorten it.
            val at = caret.coerceIn(0, text.length)
            return Result(text.substring(0, at) + "|" + text.substring(at), ide, messages, highlight, flash, registersShown)
        }
    }

    private data class Result(
        val text: String,
        val ide: List<Effect.Ide>,
        val messages: List<String>,
        val highlight: Effect.Highlight? = null,
        val flash: Effect.Flash? = null,
        val registersShown: Effect.ShowRegisters? = null,
    )

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
    fun `c changes to line end like Vim C`() {
        assertEquals("ab|\nef", run("ab|cd\nef", "c").text)
        assertEquals(Mode.INSERT, engine.state.mode)
        assertEquals(Register("cd"), engine.state.registers[8])
        engine.escape()
        assertEquals("ab|\ngh", run("ab|cd\nef\ngh", "2c").text)
        assertEquals(Register("cd\nef"), engine.state.registers[8])
        engine.escape()
        assertEquals("ab|\nef", run("ab|\nef", "c").text)
        assertEquals(Mode.INSERT, engine.state.mode)
    }

    @Test
    fun `C changes from line start to the caret, keeping indentation`() {
        assertEquals("|cd\nef", run("ab|cd\nef", "C").text)
        assertEquals(Mode.INSERT, engine.state.mode)
        assertEquals(Register("ab"), engine.state.registers[8])
        engine.escape()
        assertEquals("    |cd", run("    ab|cd", "C").text)
        engine.escape()
        assertEquals("|  abcd", run("  |  abcd", "C").text)
        engine.escape()
        assertEquals("x\n|ab", run("x\n|ab", "C").text)
        assertEquals(Mode.INSERT, engine.state.mode)
    }

    @Test
    fun `dash and underscore go to the first and last line in the block`() {
        assertEquals("fun x() {\n  |rad 1\n  rad 2\n  rad 3\n}", run("fun x() {\n  rad 1\n  ra|d 2\n  rad 3\n}", "-").text)
        assertEquals("fun x() {\n  rad 1\n  rad 2\n  |rad 3\n}", run("fun x() {\n  rad 1\n  ra|d 2\n  rad 3\n}", "_").text)
        assertEquals("fun x() {\n  |rad 1\n  rad 2\n  rad 3\n}", run("fun x() |{\n  rad 1\n  rad 2\n  rad 3\n}", "-").text)
        assertEquals("fun x() {\n  rad 1\n  rad 2\n  |rad 3\n}", run("fun x() {\n  rad 1\n  rad 2\n  rad 3\n|}", "_").text)
        assertEquals(Mode.NAV, engine.state.mode)
    }

    @Test
    fun `dash and underscore use the innermost block`() {
        assertEquals("{\n  if (a) {\n    |one\n    two\n  }\n  after\n}", run("{\n  if (a) {\n    one\n    t|wo\n  }\n  after\n}", "-").text)
        assertEquals("{\n  |if (a) {\n    one\n    two\n  }\n  after\n}", run("{\n  if (a) {\n    one\n    two\n  }\n  af|ter\n}", "-").text)
        assertEquals("{\n  if (a) {\n    one\n    two\n  }\n  |after\n}", run("{\n  if (a) {\n    one\n    two\n  }\n  af|ter\n}", "_").text)
    }

    @Test
    fun `dash and underscore inside a one-line block`() {
        assertEquals("f(|abc)", run("f(ab|c)", "-").text)
        assertEquals("f(ab|c)", run("f(|abc)", "_").text)
        assertEquals("f(|)", run("f(|)", "_").text)
        assertEquals("|abc", run("|abc", "-").text)
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

    @Test
    fun `U reverts to the saved version through the IDE`() {
        assertEquals(listOf(Effect.Ide(IdeOp.REVERT_TO_SAVED)), run("|a", "U").ide)
    }

    @Test
    fun `revert replaces only the part that differs from the saved text`() {
        assertEquals(Effect.Replace(4, 11, "two"), TextOps.minimalReplacement("one changed three", "one two three"))
        assertEquals(Effect.Replace(3, 3, "\nnew"), TextOps.minimalReplacement("abc", "abc\nnew"))
        assertEquals(Effect.Replace(0, 3, ""), TextOps.minimalReplacement("xxxabc", "abc"))
        assertEquals(Effect.Replace(2, 3, ""), TextOps.minimalReplacement("aaa", "aa"))
        assertEquals(null, TextOps.minimalReplacement("same", "same"))
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

        clipboard = "kept"
        val result = run("|one\ntwo\nthree", "2 5y")
        assertEquals(Register("one\ntwo\n", linewise = true), engine.state.registers[5])
        assertEquals(listOf("stored yank in register 5"), result.messages)
        assertEquals("kept", clipboard)
        assertEquals(Register(), engine.state.registers[0])

        run("|abc", "1 9x")
        assertEquals("a", clipboard)
    }

    @Test
    fun `register space y yanks the current line into that register only`() {
        clipboard = "kept"
        run("one\ntw|o\nthree", "2 y")
        assertEquals(Register("two\n", linewise = true), engine.state.registers[2])
        assertEquals("kept", clipboard)
        assertEquals("one\n|two\ntwo\nthree", run("one\ntw|o\nthree", "2p").text)
        assertEquals("one\n|keptthree", run("one\n|three", "p").text)

        run("|abc", "9 y")
        assertEquals("abc\n", clipboard)
    }

    @Test
    fun `register space x deletes a character into that register`() {
        assertEquals("|bc", run("|abc", "3 x").text)
        assertEquals(Register("a"), engine.state.registers[3])
        assertEquals(Register(), engine.state.registers[8])
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

    // ---- search ----

    @Test
    fun `slash searches forward and highlights all matches`() {
        val result = run("|foo bar baz bar", "/bar\n")
        assertEquals("foo |bar baz bar", result.text)
        assertEquals(Effect.Highlight(listOf(Match(4, 7), Match(12, 15)), 0), result.highlight)
        assertEquals("", engine.commandLine)
    }

    @Test
    fun `the command line shows what is typed and backspace edits it`() {
        run("|abc", "/ab")
        assertEquals("/ab", engine.commandLine)
        run("|abc", "x\b\b")
        assertEquals("/a", engine.commandLine)
        engine.escape()
        run("|abc", ",")
        assertEquals("~", engine.commandLine)
        engine.escape()
        run("|abc", "=")
        assertEquals("=", engine.commandLine)
    }

    @Test
    fun `space and digits are part of the search text`() {
        assertEquals("a |b 1 c b 1", run("|a b 1 c b 1", "/b 1\n").text)
        assertEquals(Mode.NAV, engine.state.mode)
    }

    @Test
    fun `n and N move between matches and stop at the ends`() {
        run("|foo bar baz bar", "/bar\n")
        assertEquals("foo bar baz |bar", run("foo |bar baz bar", "n").text)
        val end = run("foo bar baz |bar", "n")
        assertEquals("foo bar baz |bar", end.text)
        assertEquals(listOf("search reached end"), end.messages)
        assertEquals("foo |bar baz bar", run("foo bar baz |bar", "N").text)
        assertEquals(listOf("search reached start"), run("foo |bar baz bar", "N").messages)
    }

    @Test
    fun `backward and regex search`() {
        assertEquals("ab ab |ab cd", run("ab ab ab c|d", "\\ab\n").text)
        assertEquals("x |a12 b3", run("|x a12 b3", ",[a-z]\\d+\n").text)
        assertEquals("x a12 |b3", run("x |a12 b3", "n").text)
    }

    @Test
    fun `forward search without a later match goes to the last match like MIV`() {
        assertEquals("a b |a", run("a b |a", "/a\n").text)
        assertEquals("a b |a", run("a b a|", "/a\n").text)
    }

    @Test
    fun `search reports missing text and invalid regex`() {
        assertEquals(listOf("not found: zz"), run("|abc", "/zz\n").messages)
        assertEquals(listOf("invalid regex: ("), run("|abc", ",(\n").messages)
    }

    @Test
    fun `escape cancels the command line and hides matches`() {
        run("|abc", "/ab")
        assertEquals("|abc", run("|abc", "\u001b").text)
        assertEquals("", engine.commandLine)
        run("|ab ab", "/ab\n")
        assertEquals(Effect.Highlight(emptyList()), run("|ab", "\u001b").highlight)
        assertEquals(false, engine.handlesEnter)
    }

    @Test
    fun `enter works as usual when nothing is pending`() {
        assertEquals("a\n|b", run("a|b", "\n").text)
        assertEquals(null, engine.enter(TextView("ab", 1)) { null })
    }

    // ---- replace ----

    @Test
    fun `equals with two parts steps through the matches`() {
        val start = run("|foo x foo y foo", "=foo bar\n")
        assertEquals("|foo x foo y foo", start.text)
        assertEquals(Effect.Highlight(listOf(Match(0, 3), Match(6, 9), Match(12, 15)), 0), start.highlight)
        assertEquals(listOf("Enter replaces, n/N skips"), start.messages)

        // Enter replaces one match and moves on, n skips one.
        assertEquals("bar x |foo y foo", run("|foo x foo y foo", "\n").text)
        assertEquals("bar x foo y |foo", run("bar x |foo y foo", "n").text)
        assertEquals("bar x foo y |bar", run("bar x foo y |foo", "\n").text)
        assertEquals("bar x |foo y bar", run("bar x foo y |bar", "N").text)
    }

    @Test
    fun `equals with two parts starts at the match under the caret`() {
        assertEquals("a |foo foo", run("a |foo foo", "=foo x\n").text)
        assertEquals(listOf("not found: zz"), run("|abc", "=zz x\n").messages)
        assertEquals(false, engine.handlesEnter)
    }

    @Test
    fun `replacing every match turns Enter back to normal`() {
        run("|foo x foo", "/foo\n")
        val result = run("foo x |foo", "==bar\n")
        assertEquals("bar x bar", result.text.replace("|", ""))
        assertEquals(listOf("replaced 2 matches"), result.messages)
        assertEquals(Effect.Highlight(emptyList()), result.highlight)
        assertEquals(false, engine.handlesEnter)
    }

    @Test
    fun `equals with one part steps through the last search`() {
        run("|a.b x a.b", "/a.b\n")
        val start = run("|a.b x a.b", "=X\n")
        assertEquals("|a.b x a.b", start.text)
        assertEquals(listOf("Enter replaces, n/N skips"), start.messages)
        assertEquals("X x |a.b", run("|a.b x a.b", "\n").text)
    }

    @Test
    fun `double equals replaces every match of the last search`() {
        run("|a.b a.b", "/a.b\n")
        val result = run("a.b |a.b", "==X\n")
        assertEquals("X X", result.text.replace("|", ""))
        assertEquals(listOf("replaced 2 matches"), result.messages)
    }

    @Test
    fun `double equals with two parts replaces every literal match`() {
        assertEquals("bar x bar", run("|foo x foo", "==foo bar\n").text.replace("|", ""))
    }

    @Test
    fun `double equals alone replaces every match of the current rule`() {
        assertEquals(listOf("no replace rule"), run("|a", "==\n").messages)
        run("|foo foo", "=foo X\n")
        assertEquals("X X", run("|foo foo", "==\n").text.replace("|", ""))
    }

    @Test
    fun `regex replace expands groups`() {
        run("|ann@x bo@y", ",(\\w+)@(\\w+)\n")
        assertEquals("x:ann y:bo", run("|ann@x bo@y", "==$2:$1\n").text.replace("|", ""))
    }

    @Test
    fun `regex rules can be stepped through`() {
        run("|a1 b2 c3", ",([a-z])(\\d)\n")
        run("|a1 b2 c3", "=$2$1\n")
        assertEquals("1a |b2 c3", run("|a1 b2 c3", "\n").text)
        assertEquals("1a b2 |c3", run("1a |b2 c3", "n").text)
        assertEquals("1a b2 |3c", run("1a b2 |c3", "\n").text)
    }

    @Test
    fun `quoted parts may contain spaces`() {
        assertEquals("a |b-c", run("|a b c", "='b c' b-c\n\n").text)
    }

    @Test
    fun `replace needs a previous search or a valid rule`() {
        assertEquals(listOf("no previous search"), run("|abc", "=x\n").messages)
        assertEquals(listOf("invalid replace rule"), run("|abc", "='x y\n").messages)
    }

    @Test
    fun `enter and dot replace the current match and move to the next`() {
        engine.state.replaceRule = ReplaceRule("foo", "X", regex = false)
        run("|foo foo foo", "/foo\n")
        assertEquals(true, engine.handlesEnter)
        assertEquals("foo X |foo", run("foo |foo foo", "\n").text)
        // Without a later match the caret stays on the replaced text.
        assertEquals("foo X |X", run("foo X |foo", ".").text)
        assertEquals(listOf("no more matches"), run("foo X X|", ".").messages)
    }

    // ---- repeat ----

    @Test
    fun `dot repeats the last edit`() {
        assertEquals("|cdef", run("|abcdef", "x.").text)
        assertEquals("|ef", run("|abcdef", "2x.").text)
    }

    @Test
    fun `dot does not repeat motions, pastes or undo`() {
        clipboard = "Z"
        run("|abcd", "xpsu")
        assertEquals(Action.DELETE_CHAR, engine.state.lastCommand?.action)
        assertEquals("|cd", run("|bcd", ".").text)
    }

    @Test
    fun `dot repeats a search`() {
        run("|a b a b a", "/a\n")
        assertEquals("a b a b |a", run("a b |a b a", ".").text)
    }

    // ---- text objects ----

    @Test
    fun `text object yank uses register 0 and the clipboard`() {
        run("say \"hel|lo\" now", "\"y")
        assertEquals("hello", clipboard)
        assertEquals(Register("hello"), engine.state.registers[0])
    }

    @Test
    fun `text object with a register only writes that register`() {
        clipboard = "kept"
        run("f(a|b)", "( 3y")
        assertEquals(Register("ab"), engine.state.registers[3])
        assertEquals("kept", clipboard)
        assertEquals(Register(), engine.state.registers[0])
    }

    @Test
    fun `text object delete and paste`() {
        assertEquals("f(|) + 1", run("f(a, |b) + 1", "(x").text)
        assertEquals(Register("a, b"), engine.state.registers[8])
        clipboard = "NEW"
        assertEquals("[|NEW]", run("[o|ld]", "[p").text)
        assertEquals("{|NEW}", run("{|x}", "!p").text)
    }

    @Test
    fun `text object without surrounding delimiters does nothing`() {
        assertEquals("a|bc", run("a|bc", "(x").text)
    }

    // ---- phase 4: register viewer, yank flash, stats ----

    @Test
    fun `v shows the non-empty registers including the clipboard`() {
        assertEquals(listOf("no registers yet"), run("|a", "v").messages)
        engine.state.registers[2] = Register("two")
        clipboard = "clip\n"
        assertEquals(
            Effect.ShowRegisters(listOf(2 to Register("two"), 9 to Register("clip\n", linewise = true))),
            run("|a", "v").registersShown,
        )
    }

    @Test
    fun `yanks flash the yanked text`() {
        assertEquals(Effect.Flash(4, 7), run("one\ntw|o\nthree", "y").flash)
        assertEquals(Effect.Flash(0, 3), run("|foo bar", "Y").flash)
        assertEquals(Effect.Flash(3, 5), run("f(\"a|b\")", "\"y").flash)
    }

    @Test
    fun `commands are counted for the stats view`() {
        run("|abcdef", "x2xa")
        val stats = engine.state.stats.format()
        assertTrue(stats, stats.contains("DELETE_CHAR  2"))
        assertTrue(stats, stats.contains("LEFT         1"))
        assertTrue(stats, stats.contains("2x  1"))
    }

    // ---- custom keys ----

    @Test
    fun `custom keys drive commands and the default key stops working`() {
        engine.layout = KeyLayout(mapOf("LEFT" to 'h', "DELETE_CHAR" to 'z'))
        assertEquals(listOf(Effect.Ide(IdeOp.LEFT, 3)), run("a|b", "3h").ide)
        assertEquals(emptyList<Effect.Ide>(), run("a|b", "a").ide)
        assertEquals("a|", run("a|b", "z").text)
        assertEquals("", engine.commandLine)
    }

    @Test
    fun `text after r and on the command line is not remapped`() {
        engine.layout = KeyLayout(mapOf("LEFT" to 'h', "REPLACE_CHAR" to 't'))
        assertEquals("|hbc", run("|abc", "th").text)
        assertEquals("h ab |h", run("|h ab h", "/h\n").text)
        run("|x h", "/h")
        assertEquals("/h", engine.commandLine)
    }
}
