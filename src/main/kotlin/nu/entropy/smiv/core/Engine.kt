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
    NEW_LINE_BELOW, NEW_LINE_ABOVE, JOIN_LINES, UNDO,
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
 *
 * Registers 0..8 live in [SmivState]. Register 9 *is* the system clipboard: it is
 * read through [clipboard] and written with [Effect.SetClipboard], so copies made
 * with Cmd+C or in other apps are always visible as register 9.
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

    /** Drop a half-typed command (Enter in NAV). */
    fun cancelPending() {
        state.pending.clear()
    }

    /** Alt+Q / Alt+E. */
    fun paragraph(view: TextView, forward: Boolean): List<Effect> {
        state.pending.clear()
        val target = TextOps.paragraphTarget(view.text, view.caret, forward) ?: return emptyList()
        return listOf(Effect.MoveCaret(target))
    }

    fun register(index: Int, clipboard: () -> String?): Register {
        if (index != SmivState.CLIPBOARD_REGISTER) return state.registers[index]
        val text = clipboard().orEmpty()
        return Register(text, linewise = text.endsWith("\n"))
    }

    /** Store into a register; register 9 becomes a clipboard write. */
    private fun store(index: Int, text: String, linewise: Boolean): List<Effect> {
        if (index == SmivState.CLIPBOARD_REGISTER) return listOf(Effect.SetClipboard(text))
        state.registers[index] = Register(text, linewise)
        return emptyList()
    }

    fun execute(command: Command, view: TextView, clipboard: () -> String?): List<Effect> {
        val count = command.count
        val text = view.text
        val caret = view.caret
        return when (command.action) {
            Action.LEFT -> ide(IdeOp.LEFT, count)
            Action.RIGHT -> ide(IdeOp.RIGHT, count)
            Action.UP -> ide(IdeOp.UP, count)
            Action.DOWN -> ide(IdeOp.DOWN, count)
            Action.PAGE_UP -> ide(IdeOp.PAGE_UP, count)
            Action.PAGE_DOWN -> ide(IdeOp.PAGE_DOWN, count)
            Action.LINE_START -> ide(IdeOp.LINE_START, count)
            Action.LINE_END -> ide(IdeOp.LINE_END, count)
            Action.WORD_LEFT -> moveBy(view, count, TextOps::wordLeft)
            Action.WORD_END_RIGHT -> moveBy(view, count, TextOps::wordEndRight)
            Action.WORD_END_LEFT -> moveBy(view, count, TextOps::wordEndLeft)
            Action.WORD_START_RIGHT -> moveBy(view, count, TextOps::wordStartRight)

            Action.DELETE_CHAR -> deleteChars(command, view)
            Action.DELETE_LINE -> deleteLines(count, view)
            Action.DELETE_WORD -> deleteRange(caret, TextOps.wordsEnd(text, caret, count), text)
            Action.DELETE_TO_LINE_END -> deleteRange(caret, TextOps.lineEnd(text, caret), text)
            Action.YANK_LINE -> yankLines(command, view)
            Action.YANK_WORD -> yank(text.substring(caret, TextOps.wordsEnd(text, caret, count)), linewise = false)
            Action.PASTE_BEFORE -> paste(command, view, clipboard, after = false)
            Action.PASTE_AFTER -> paste(command, view, clipboard, after = true)
            Action.STORE_REGISTER -> storeClipboard(command.register ?: return emptyList(), clipboard)

            Action.REPLACE_CHAR -> replaceChar(command.char ?: return emptyList(), view)
            Action.REPLACE_WORD -> replaceWord(count, view)
            Action.TOGGLE_CASE_CHAR -> {
                val end = minOf(text.length, caret + count)
                toggleCase(caret, end, view, "toggled $count character${plural(count)}")
            }
            Action.TOGGLE_CASE_WORD -> {
                val range = TextOps.wordOperationRange(text, caret, count) ?: return emptyList()
                toggleCase(range.first, range.last + 1, view, "toggled $count word${plural(count)}")
            }
            Action.CHANGE_TO_LINE_END -> enterInsert(
                Effect.Replace(caret, TextOps.lineEnd(text, caret), ""),
                Effect.MoveCaret(caret),
            )
            Action.CHANGE_LINE -> changeLine(view)
            Action.JOIN_LINES -> ide(IdeOp.JOIN_LINES)
            Action.JUMP_BRACKET_MATCH ->
                TextOps.findMatchingBracket(text, caret)?.let { listOf(Effect.MoveCaret(it)) }.orEmpty()

            Action.GOTO_LINE -> gotoLine(view) { count - 1 }
            Action.GOTO_LINE_FROM_BOTTOM -> gotoLine(view) { lines -> lines.count - count }
            Action.GOTO_PERCENT -> {
                val lines = TextOps.Lines(text)
                val line = Math.round(lines.last * count / 100.0).toInt()
                listOf(Effect.MoveCaret(lines.start(line)))
            }
            Action.DOC_END -> listOf(Effect.MoveCaret(text.length))

            Action.UNDO -> ide(IdeOp.UNDO)
            Action.INSERT -> enterInsert()
            Action.INSERT_LINE_START -> enterInsert(Effect.Ide(IdeOp.LINE_START))
            Action.INSERT_LINE_END -> enterInsert(Effect.Ide(IdeOp.LINE_END))
            Action.OPEN_LINE_BELOW -> enterInsert(Effect.Ide(IdeOp.NEW_LINE_BELOW))
            Action.OPEN_LINE_ABOVE -> enterInsert(Effect.Ide(IdeOp.NEW_LINE_ABOVE))
        }
    }

    private fun ide(op: IdeOp, times: Int = 1): List<Effect> = listOf(Effect.Ide(op, times))

    private fun plural(count: Int) = if (count == 1) "" else "s"

    private fun enterInsert(vararg before: Effect): List<Effect> {
        state.mode = Mode.INSERT
        return before.toList()
    }

    private fun moveBy(view: TextView, count: Int, step: (CharSequence, Int) -> Int): List<Effect> {
        var offset = view.caret
        repeat(count) { offset = step(view.text, offset) }
        return listOf(Effect.MoveCaret(offset))
    }

    /** Keep the caret's column on the target line, like MIV's `g` and `[n]G`. */
    private fun gotoLine(view: TextView, targetLine: (TextOps.Lines) -> Int): List<Effect> {
        val lines = TextOps.Lines(view.text)
        val column = view.caret - lines.start(lines.lineOf(view.caret))
        val line = targetLine(lines).coerceIn(0, lines.last)
        return listOf(Effect.MoveCaret(lines.offsetAt(line, column)))
    }

    private fun deleteRange(
        start: Int,
        end: Int,
        text: CharSequence,
        register: Int = SmivState.DELETE_REGISTER,
    ): List<Effect> {
        if (start >= end) return emptyList()
        return store(register, text.substring(start, end), linewise = false) + listOf(
            Effect.Replace(start, end, ""),
            Effect.MoveCaret(start),
            Effect.Message("stored delete in register $register"),
        )
    }

    private fun deleteChars(command: Command, view: TextView): List<Effect> {
        val register = command.register ?: SmivState.DELETE_REGISTER
        if (!command.explicitCount && view.hasSelection) {
            val start = minOf(view.selectionStart, view.selectionEnd)
            val end = maxOf(view.selectionStart, view.selectionEnd)
            return deleteRange(start, end, view.text, register)
        }
        return deleteRange(view.caret, minOf(view.text.length, view.caret + command.count), view.text, register)
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
        val register = SmivState.DELETE_REGISTER
        return store(register, text.substring(firstStart, lastEnd) + "\n", linewise = true) + listOf(
            Effect.Replace(rangeStart, rangeEnd, ""),
            Effect.MoveCaret(caret),
            Effect.Message("stored delete in register $register"),
        )
    }

    /** Yanks go to the target register and, as in MIV, always to the clipboard (register 9). */
    private fun yank(value: String, linewise: Boolean, register: Int = SmivState.YANK_REGISTER): List<Effect> {
        if (value.isEmpty()) return emptyList()
        val effects = store(register, value, linewise)
        val clipboard = if (register == SmivState.CLIPBOARD_REGISTER) emptyList() else listOf(Effect.SetClipboard(value))
        return effects + clipboard + Effect.Message("stored yank in register $register")
    }

    private fun yankLines(command: Command, view: TextView): List<Effect> {
        val text = view.text
        val register = command.register ?: SmivState.YANK_REGISTER
        if (!command.explicitCount && view.hasSelection) {
            val start = minOf(view.selectionStart, view.selectionEnd)
            val end = maxOf(view.selectionStart, view.selectionEnd)
            return yank(text.substring(start, end), linewise = false, register)
        }
        val start = TextOps.lineStart(text, view.caret)
        val end = TextOps.lineEnd(text, TextOps.lineStartAfter(text, view.caret, command.count - 1))
        // Always end linewise yanks with a break so they paste as lines, even from the last line.
        return yank(text.substring(start, end) + "\n", linewise = true, register)
    }

    private fun storeClipboard(register: Int, clipboard: () -> String?): List<Effect> {
        val value = clipboard()
        if (value.isNullOrEmpty()) return listOf(Effect.Message("clipboard empty"))
        return store(register, value, value.endsWith("\n")) +
            Effect.Message("stored clipboard in register $register")
    }

    private fun paste(command: Command, view: TextView, clipboard: () -> String?, after: Boolean): List<Effect> {
        val index = command.register ?: SmivState.CLIPBOARD_REGISTER
        val value = register(index, clipboard)
        if (value.text.isEmpty()) return emptyList()
        val effects = if (value.linewise) pasteLines(value.text, view, after) else pasteChars(value.text, view, after)
        return if (command.register == null) effects else effects + Effect.Message("pasted register $index")
    }

    private fun pasteChars(value: String, view: TextView, after: Boolean): List<Effect> {
        val caret = view.caret
        val insertAt = if (after) minOf(TextOps.lineEnd(view.text, caret), caret + 1) else caret
        return listOf(Effect.Replace(insertAt, insertAt, value), Effect.MoveCaret(insertAt))
    }

    private fun pasteLines(value: String, view: TextView, after: Boolean): List<Effect> {
        val text = view.text
        val lines = value.removeSuffix("\n")
        if (!after) {
            val lineStart = TextOps.lineStart(text, view.caret)
            return listOf(Effect.Replace(lineStart, lineStart, lines + "\n"), Effect.MoveCaret(lineStart))
        }

        val lineEnd = TextOps.lineEnd(text, view.caret)
        if (lineEnd < text.length) {
            val insertAt = lineEnd + 1
            return listOf(Effect.Replace(insertAt, insertAt, lines + "\n"), Effect.MoveCaret(insertAt))
        }
        // Last line has no break to paste after: add one in front instead.
        return listOf(Effect.Replace(lineEnd, lineEnd, "\n" + lines), Effect.MoveCaret(lineEnd + 1))
    }

    private fun replaceChar(char: Char, view: TextView): List<Effect> {
        val caret = view.caret
        if (caret >= TextOps.lineEnd(view.text, caret)) return emptyList()
        return listOf(Effect.Replace(caret, caret + 1, char.toString()), Effect.MoveCaret(caret))
    }

    /** `R`: delete [count] words from the word at the caret and enter INSERT. */
    private fun replaceWord(count: Int, view: TextView): List<Effect> {
        val range = TextOps.wordOperationRange(view.text, view.caret, count) ?: return emptyList()
        return enterInsert(
            Effect.Replace(range.first, range.last + 1, ""),
            Effect.MoveCaret(range.first),
            Effect.Message("changed $count word${plural(count)}"),
        )
    }

    /** `§` / `°`: the caret stays where it was. */
    private fun toggleCase(start: Int, end: Int, view: TextView, message: String): List<Effect> {
        if (start >= end) return emptyList()
        val original = view.text.subSequence(start, end)
        val toggled = TextOps.toggleCase(original)
        if (toggled == original.toString()) return emptyList()
        return listOf(Effect.Replace(start, end, toggled), Effect.MoveCaret(view.caret), Effect.Message(message))
    }

    /** `_`: clear the line but keep its indentation, then enter INSERT. */
    private fun changeLine(view: TextView): List<Effect> {
        val text = view.text
        val start = TextOps.lineStart(text, view.caret)
        val end = TextOps.lineEnd(text, view.caret)
        var contentStart = start
        while (contentStart < end && (text[contentStart] == ' ' || text[contentStart] == '\t')) contentStart++
        return enterInsert(Effect.Replace(contentStart, end, ""), Effect.MoveCaret(contentStart))
    }
}
