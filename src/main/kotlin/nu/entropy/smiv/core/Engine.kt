package nu.entropy.smiv.core

/** Read-only view of the editor at the moment a key was typed. Offsets are char offsets. */
data class TextView(
    val text: CharSequence,
    val caret: Int,
    val selectionStart: Int = caret,
    val selectionEnd: Int = caret,
) {
    val hasSelection: Boolean get() = selectionStart != selectionEnd
}

/** Editor operations the IDE layer performs with its own actions (fold/soft-wrap/indent aware). */
enum class IdeOp {
    LEFT, RIGHT, UP, DOWN, PAGE_UP, PAGE_DOWN, LINE_START, LINE_END,
    NEW_LINE_BELOW, NEW_LINE_ABOVE, UNDO,
}

/** What a command asks the IDE to do, in order. Offsets refer to the text at that point. */
sealed interface Effect {
    data class Replace(val start: Int, val end: Int, val text: String) : Effect
    data class MoveCaret(val offset: Int) : Effect
    data class Ide(val op: IdeOp, val times: Int = 1) : Effect
    data class SetClipboard(val text: String) : Effect
    data class Message(val text: String) : Effect
}

/**
 * NAV-mode command engine: key in, effects out. Contains no IntelliJ code so it
 * can be unit tested with a plain [TextView].
 */
class Engine(val state: SmivState = SmivState()) {

    val commandLine: String get() = state.pending.toString()

    /** Handle one typed character in NAV mode. Returns no effects in INSERT mode. */
    fun type(char: Char, view: TextView, clipboard: () -> String?): List<Effect> {
        if (state.mode != Mode.NAV) return emptyList()

        if (char == Keys.INSERT_SPACE && state.pending.isEmpty()) {
            state.mode = Mode.INSERT
            return emptyList()
        }

        state.pending.append(char)
        return when (val result = Parser.parse(state.pending.toString())) {
            ParseResult.Partial -> emptyList()
            ParseResult.Invalid -> {
                state.pending.clear()
                emptyList()
            }
            is ParseResult.Complete -> {
                state.pending.clear()
                execute(result.command, view, clipboard)
            }
        }
    }

    /** ESC: drop any half-typed command and return to NAV. */
    fun escape() {
        state.pending.clear()
        state.mode = Mode.NAV
    }

    fun execute(command: Command, view: TextView, clipboard: () -> String?): List<Effect> {
        val count = command.count
        return when (command.action) {
            Action.LEFT -> listOf(Effect.Ide(IdeOp.LEFT, count))
            Action.RIGHT -> listOf(Effect.Ide(IdeOp.RIGHT, count))
            Action.UP -> listOf(Effect.Ide(IdeOp.UP, count))
            Action.DOWN -> listOf(Effect.Ide(IdeOp.DOWN, count))
            Action.PAGE_UP -> listOf(Effect.Ide(IdeOp.PAGE_UP, count))
            Action.PAGE_DOWN -> listOf(Effect.Ide(IdeOp.PAGE_DOWN, count))
            Action.LINE_START -> listOf(Effect.Ide(IdeOp.LINE_START, count))
            Action.LINE_END -> listOf(Effect.Ide(IdeOp.LINE_END, count))
            Action.WORD_LEFT -> moveBy(view, count, TextOps::wordLeft)
            Action.WORD_END_RIGHT -> moveBy(view, count, TextOps::wordEndRight)
            Action.WORD_END_LEFT -> moveBy(view, count, TextOps::wordEndLeft)
            Action.WORD_START_RIGHT -> moveBy(view, count, TextOps::wordStartRight)
            Action.DELETE_CHAR -> deleteChars(command, view)
            Action.DELETE_LINE -> deleteLines(count, view)
            Action.YANK_LINE -> yankLines(command, view)
            Action.PASTE_BEFORE -> paste(clipboard(), view, after = false)
            Action.PASTE_AFTER -> paste(clipboard(), view, after = true)
            Action.UNDO -> listOf(Effect.Ide(IdeOp.UNDO))
            Action.INSERT -> enterInsert()
            Action.OPEN_LINE_BELOW -> enterInsert(Effect.Ide(IdeOp.NEW_LINE_BELOW))
            Action.OPEN_LINE_ABOVE -> enterInsert(Effect.Ide(IdeOp.NEW_LINE_ABOVE))
        }
    }

    private fun enterInsert(vararg before: Effect): List<Effect> {
        state.mode = Mode.INSERT
        return before.toList()
    }

    private fun moveBy(view: TextView, count: Int, step: (CharSequence, Int) -> Int): List<Effect> {
        var offset = view.caret
        repeat(count) { offset = step(view.text, offset) }
        return listOf(Effect.MoveCaret(offset))
    }

    private fun deleteChars(command: Command, view: TextView): List<Effect> {
        val (start, end) = if (!command.explicitCount && view.hasSelection) {
            minOf(view.selectionStart, view.selectionEnd) to maxOf(view.selectionStart, view.selectionEnd)
        } else {
            view.caret to minOf(view.text.length, view.caret + command.count)
        }
        if (start >= end) return emptyList()

        state.store(SmivState.DELETE_REGISTER, view.text.substring(start, end), linewise = false)
        return listOf(
            Effect.Replace(start, end, ""),
            Effect.MoveCaret(start),
            Effect.Message("stored delete in register ${SmivState.DELETE_REGISTER}"),
        )
    }

    private fun deleteLines(count: Int, view: TextView): List<Effect> {
        val text = view.text
        val firstStart = TextOps.lineStart(text, view.caret)
        val lastEnd = TextOps.lineEnd(text, TextOps.lineStartAfter(text, view.caret, count - 1))
        val deletesToDocumentEnd = lastEnd == text.length

        // Take the line break after the block, or before it when the block ends the document.
        val rangeStart = if (deletesToDocumentEnd && firstStart > 0) firstStart - 1 else firstStart
        val rangeEnd = if (deletesToDocumentEnd) lastEnd else lastEnd + 1
        if (rangeStart >= rangeEnd) return emptyList()

        val caret = if (rangeStart < firstStart) TextOps.lineStart(text, rangeStart) else rangeStart
        state.store(SmivState.DELETE_REGISTER, text.substring(firstStart, lastEnd) + "\n", linewise = true)
        return listOf(
            Effect.Replace(rangeStart, rangeEnd, ""),
            Effect.MoveCaret(caret),
            Effect.Message("stored delete in register ${SmivState.DELETE_REGISTER}"),
        )
    }

    private fun yankLines(command: Command, view: TextView): List<Effect> {
        val text = view.text
        val (yanked, linewise) = if (!command.explicitCount && view.hasSelection) {
            val start = minOf(view.selectionStart, view.selectionEnd)
            val end = maxOf(view.selectionStart, view.selectionEnd)
            text.substring(start, end) to false
        } else {
            val start = TextOps.lineStart(text, view.caret)
            val end = TextOps.lineEnd(text, TextOps.lineStartAfter(text, view.caret, command.count - 1))
            // Always end linewise yanks with a break so `p`/`P` recognise them, even on the last line.
            text.substring(start, end) + "\n" to true
        }

        state.store(SmivState.YANK_REGISTER, yanked, linewise)
        return listOf(
            Effect.SetClipboard(yanked),
            Effect.Message("stored yank in register ${SmivState.YANK_REGISTER}"),
        )
    }

    private fun paste(value: String?, view: TextView, after: Boolean): List<Effect> {
        if (value.isNullOrEmpty()) return emptyList()
        val text = view.text
        val caret = view.caret

        if (!value.endsWith("\n")) {
            val insertAt = if (after) minOf(TextOps.lineEnd(text, caret), caret + 1) else caret
            return listOf(Effect.Replace(insertAt, insertAt, value), Effect.MoveCaret(insertAt))
        }

        if (!after) {
            val lineStart = TextOps.lineStart(text, caret)
            return listOf(Effect.Replace(lineStart, lineStart, value), Effect.MoveCaret(lineStart))
        }

        val lineEnd = TextOps.lineEnd(text, caret)
        if (lineEnd < text.length) {
            val insertAt = lineEnd + 1
            return listOf(Effect.Replace(insertAt, insertAt, value), Effect.MoveCaret(insertAt))
        }
        // Last line has no break to paste after: add one in front instead.
        return listOf(
            Effect.Replace(lineEnd, lineEnd, "\n" + value.dropLast(1)),
            Effect.MoveCaret(lineEnd + 1),
        )
    }
}

/** Pure text helpers. Documents use `\n` as the only line separator. */
object TextOps {
    private const val WORD_SEPARATORS = "`~!@#$%^&*()-=+[{]}\\|;:'\",.<>/?"

    fun lineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    fun lineEnd(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    /** Start of the line [lines] lines below the one containing [offset], clamped to the last line. */
    fun lineStartAfter(text: CharSequence, offset: Int, lines: Int): Int {
        var start = lineStart(text, offset)
        repeat(lines) {
            val end = lineEnd(text, start)
            if (end >= text.length) return start
            start = end + 1
        }
        return start
    }

    // Word classes mirror VS Code's defaults: whitespace, separators, and everything else.
    private fun charClass(c: Char): Int = when {
        c.isWhitespace() -> 0
        c in WORD_SEPARATORS -> 1
        else -> 2
    }

    private fun isWordStart(text: CharSequence, p: Int): Boolean =
        p < text.length && charClass(text[p]) != 0 && (p == 0 || charClass(text[p - 1]) != charClass(text[p]))

    private fun isWordEnd(text: CharSequence, p: Int): Boolean =
        p > 0 && charClass(text[p - 1]) != 0 && (p == text.length || charClass(text[p]) != charClass(text[p - 1]))

    /** `q`: start of the previous word. */
    fun wordLeft(text: CharSequence, offset: Int): Int =
        ((offset - 1) downTo 0).firstOrNull { isWordStart(text, it) } ?: 0

    /** `e`: end of the next word. */
    fun wordEndRight(text: CharSequence, offset: Int): Int =
        ((offset + 1)..text.length).firstOrNull { isWordEnd(text, it) } ?: text.length

    /** `Q`: end of the previous word. */
    fun wordEndLeft(text: CharSequence, offset: Int): Int =
        ((offset - 1) downTo 0).firstOrNull { isWordEnd(text, it) } ?: 0

    /** `E`: start of the next word. */
    fun wordStartRight(text: CharSequence, offset: Int): Int =
        ((offset + 1)..text.length).firstOrNull { isWordStart(text, it) } ?: text.length
}
