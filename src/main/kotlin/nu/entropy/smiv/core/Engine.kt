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
    NEW_LINE_BELOW, NEW_LINE_ABOVE, JOIN_LINES, UNDO, REVERT_TO_SAVED,
    MOVE_LINE_DOWN, MOVE_LINE_UP, INDENT, OUTDENT, SET_ANCHOR, JUMP_TO_ANCHOR,
}

/** What a command asks the IDE to do, in order. Offsets refer to the text at that point. */
sealed interface Effect {
    data class Replace(val start: Int, val end: Int, val text: String) : Effect
    data class MoveCaret(val offset: Int) : Effect
    data class Ide(val op: IdeOp, val times: Int = 1) : Effect
    data class SetClipboard(val text: String) : Effect
    data class Message(val text: String) : Effect

    /** Highlight search [matches]; [current] is the index of the current match or -1. Empty clears. */
    data class Highlight(val matches: List<Match>, val current: Int = -1) : Effect

    /** Briefly highlight text that was just yanked. */
    data class Flash(val start: Int, val end: Int) : Effect

    /** Open the register viewer with the non-empty registers; choosing one pastes it. */
    data class ShowRegisters(val registers: List<Pair<Int, Register>>) : Effect
}

/**
 * NAV-mode command engine: key in, effects out. Contains no IntelliJ code so it
 * can be unit tested with a plain [TextView].
 *
 * Registers 0..8 live in [SmivState]. Register 9 *is* the system clipboard: it is
 * read through [clipboard] and written with [Effect.SetClipboard], so copies made
 * with Cmd+C or in other apps are always visible as register 9.
 *
 * A yank without a register goes to register 0 and the clipboard. A yank or delete
 * into an explicit register (`2 y`, `5 3x`) only touches that register.
 */
class Engine(val state: SmivState = SmivState()) {

    /** Custom NAV keys from the sMiv settings. */
    var layout: KeyLayout = KeyLayout.DEFAULT

    /** What the status bar shows: the command line (`/foo`) or the keys typed so far (`5 3`). */
    val commandLine: String get() = state.commandLine?.text ?: state.pending.toString()

    val isCommandLineActive: Boolean get() = state.commandLine != null

    /** Selection mode (`V`) is on. */
    val isSelecting: Boolean get() = state.selectAnchor != null

    /** Enter is taken by sMiv (instead of inserting a line) when this is true. */
    val handlesEnter: Boolean
        get() = state.mode == Mode.NAV &&
            (state.commandLine != null || state.pending.isNotEmpty() || (state.replaceRule != null && state.searchVisible))

    /** Handle one typed character in NAV mode. Returns no effects in INSERT mode. */
    fun type(typed: Char, view: TextView, clipboard: () -> String?): List<Effect> {
        if (state.mode != Mode.NAV) return emptyList()

        state.commandLine?.let {
            it.buffer.append(typed)
            return emptyList()
        }

        // The character after `r` is text, not a key.
        val isArgument = state.pending.lastOrNull() in Keys.CHAR_ARGUMENT_KEYS
        val char = if (isArgument) typed else layout.translate(typed)
        if (char == null) {
            state.pending.clear()
            return emptyList()
        }

        if (state.pending.isEmpty()) {
            if (char == Keys.INSERT_SPACE) {
                state.mode = Mode.INSERT
                return emptyList()
            }
            Keys.COMMAND_LINE_KEYS[char]?.let {
                state.commandLine = CommandLine(it)
                return emptyList()
            }
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
                run(result.command, view, clipboard)
            }
        }
    }

    /** Run [command] and remember it for `.` when it is repeatable. */
    private fun run(command: Command, view: TextView, clipboard: () -> String?): List<Effect> {
        state.stats.record(command)
        if (command.action in Keys.REPEATABLE) state.lastCommand = command
        val effects = execute(command, view, clipboard)
        // Motions extend the selection in selection mode; anything else ends it.
        if (command.action !in Keys.MOTIONS && command.action != Action.TOGGLE_SELECT) state.selectAnchor = null
        return effects
    }

    /**
     * Enter in NAV: commit the command line, cancel a half-typed command, or apply
     * the replace rule to the current match. Null when Enter should work as usual.
     */
    fun enter(view: TextView, clipboard: () -> String?): List<Effect>? {
        if (state.mode != Mode.NAV) return null
        state.commandLine?.let {
            state.commandLine = null
            return commitCommandLine(it, view, clipboard)
        }
        if (state.pending.isNotEmpty()) {
            state.pending.clear()
            return emptyList()
        }
        if (state.replaceRule != null && state.searchVisible) {
            return run(Command(Action.APPLY_REPLACE_RULE, sequence = "="), view, clipboard)
        }
        return null
    }

    /** Backspace edits the command line; false when there is none (Backspace works as usual). */
    fun backspace(): Boolean {
        val line = state.commandLine ?: return false
        if (line.buffer.isNotEmpty()) line.buffer.setLength(line.buffer.length - 1)
        return true
    }

    /** ESC: drop any half-typed command, command line or selection mode, hide search matches, return to NAV. */
    fun escape(): List<Effect> {
        state.pending.clear()
        state.commandLine = null
        state.selectAnchor = null
        state.mode = Mode.NAV
        if (!state.searchVisible) return emptyList()
        state.searchVisible = false
        return listOf(Effect.Highlight(emptyList()))
    }

    /** Drop a half-typed command. */
    fun cancelPending() {
        state.pending.clear()
        state.commandLine = null
    }

    private fun commitCommandLine(line: CommandLine, view: TextView, clipboard: () -> String?): List<Effect> {
        val input = line.buffer.toString()
        val action = when (line.kind) {
            CommandLineKind.SEARCH_FORWARD -> Action.SEARCH_FORWARD
            CommandLineKind.SEARCH_BACKWARD -> Action.SEARCH_BACKWARD
            CommandLineKind.SEARCH_REGEX -> Action.SEARCH_REGEX
            CommandLineKind.REPLACE_RULE -> return commitReplaceRule(input, view, clipboard)
        }
        if (input.isEmpty()) return emptyList()
        return run(Command(action, sequence = line.text, text = input), view, clipboard)
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
            Action.YANK_WORD -> yank(caret, TextOps.wordsEnd(text, caret, count), text, linewise = false, null)
            Action.PASTE_BEFORE -> paste(command, view, clipboard, after = false)
            Action.PASTE_AFTER -> paste(command, view, clipboard, after = true)
            Action.STORE_REGISTER -> storeClipboard(command.register ?: return emptyList(), clipboard)
            Action.SHOW_REGISTERS -> {
                val registers = (0..SmivState.CLIPBOARD_REGISTER)
                    .map { it to register(it, clipboard) }
                    .filter { (_, value) -> value.text.isNotEmpty() }
                if (registers.isEmpty()) listOf(Effect.Message("no registers yet")) else listOf(Effect.ShowRegisters(registers))
            }

            Action.REPLACE_CHAR -> replaceChar(command.char ?: return emptyList(), view)
            Action.REPLACE_WORD -> replaceWord(count, view)
            Action.TOGGLE_CASE_CHAR, Action.TOGGLE_CASE_WORD -> when {
                usesSelection(command, view) ->
                    toggleCase(selectionStart(view), selectionEnd(view), view, "toggled selection")
                command.action == Action.TOGGLE_CASE_CHAR ->
                    toggleCase(caret, minOf(text.length, caret + count), view, "toggled $count character${plural(count)}")
                else -> {
                    val range = TextOps.wordOperationRange(text, caret, count) ?: return emptyList()
                    toggleCase(range.first, range.last + 1, view, "toggled $count word${plural(count)}")
                }
            }
            Action.CHANGE_TO_LINE_END ->
                if (usesSelection(command, view)) changeSelection(view) else changeToLineEnd(count, view)
            Action.CHANGE_TO_LINE_START ->
                if (usesSelection(command, view)) changeSelection(view) else changeToLineStart(view)
            Action.BLOCK_FIRST_LINE, Action.BLOCK_LAST_LINE ->
                TextOps.blockLineTarget(text, caret, first = command.action == Action.BLOCK_FIRST_LINE, percent = command.count)
                    ?.let { listOf(Effect.MoveCaret(it)) }.orEmpty()
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

            Action.REPEAT -> state.lastCommand?.let { execute(it, view, clipboard) }.orEmpty()
            Action.SEARCH_FORWARD -> typedSearch(command.text.orEmpty(), view, forward = true, regex = false)
            Action.SEARCH_BACKWARD -> typedSearch(command.text.orEmpty(), view, forward = false, regex = false)
            Action.SEARCH_REGEX -> typedSearch(command.text.orEmpty(), view, forward = true, regex = true)
            Action.SEARCH_WORD_FORWARD -> searchWord(view, forward = true)
            Action.SEARCH_WORD_BACKWARD -> searchWord(view, forward = false)
            Action.SEARCH_NEXT -> searchAgain(view, forward = true)
            Action.SEARCH_PREVIOUS -> searchAgain(view, forward = false)
            Action.SEARCH_CHAR_FORWARD -> searchChar(view, forward = true)
            Action.SEARCH_CHAR_BACKWARD -> searchChar(view, forward = false)
            Action.APPLY_REPLACE_RULE -> applyReplaceRule(view)
            Action.TEXT_OBJECT_YANK, Action.TEXT_OBJECT_DELETE, Action.TEXT_OBJECT_PASTE,
            Action.TEXT_OBJECT_YANK_AROUND, Action.TEXT_OBJECT_DELETE_AROUND ->
                textObject(command, view, clipboard)

            Action.TOGGLE_SELECT -> toggleSelect(view)
            Action.MOVE_LINE_DOWN -> ide(IdeOp.MOVE_LINE_DOWN, count)
            Action.MOVE_LINE_UP -> ide(IdeOp.MOVE_LINE_UP, count)
            Action.INDENT -> ide(IdeOp.INDENT, count)
            Action.OUTDENT -> ide(IdeOp.OUTDENT, count)
            Action.LINE_PERCENT -> listOf(Effect.MoveCaret(TextOps.linePercentTarget(text, caret, count)))
            Action.SET_ANCHOR -> ide(IdeOp.SET_ANCHOR)
            Action.JUMP_TO_ANCHOR -> ide(IdeOp.JUMP_TO_ANCHOR)

            Action.UNDO -> ide(IdeOp.UNDO)
            Action.REVERT_TO_SAVED -> ide(IdeOp.REVERT_TO_SAVED)
            // `i` inserts after the character under the (block) caret; Space inserts before it.
            Action.INSERT -> enterInsert(Effect.MoveCaret(minOf(caret + 1, TextOps.lineEnd(text, caret))))
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

    /**
     * Yank [start, end) of [text]. No [register]: register 0 plus the clipboard. Explicit
     * register: only that register. Linewise yanks always end with a line break.
     */
    private fun yank(start: Int, end: Int, text: CharSequence, linewise: Boolean, register: Int?): List<Effect> {
        if (start >= end && !linewise) return emptyList()
        val value = text.substring(start, end) + if (linewise) "\n" else ""
        val target = register ?: SmivState.YANK_REGISTER
        val clipboard = if (register == null) listOf(Effect.SetClipboard(value)) else emptyList()
        return store(target, value, linewise) + clipboard +
            Effect.Flash(start, end) + Effect.Message("stored yank in register $target")
    }

    private fun yankLines(command: Command, view: TextView): List<Effect> {
        val text = view.text
        val register = command.register
        if (!command.explicitCount && view.hasSelection) {
            val start = minOf(view.selectionStart, view.selectionEnd)
            val end = maxOf(view.selectionStart, view.selectionEnd)
            return yank(start, end, text, linewise = false, register)
        }
        val start = TextOps.lineStart(text, view.caret)
        val end = TextOps.lineEnd(text, TextOps.lineStartAfter(text, view.caret, command.count - 1))
        return yank(start, end, text, linewise = true, register)
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
        val effects = when {
            // With a selection, the register replaces it.
            view.hasSelection -> listOf(
                Effect.Replace(selectionStart(view), selectionEnd(view), value.text),
                Effect.MoveCaret(selectionStart(view)),
            )
            value.linewise -> pasteLines(value.text, view, after)
            else -> pasteChars(value.text, view, after)
        }
        return effects + Effect.Message("pasted register $index")
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

    /**
     * `c`, like Vim's `C`: delete to the end of the line and [count] - 1 more
     * lines into register 8, then enter INSERT.
     */
    private fun changeToLineEnd(count: Int, view: TextView): List<Effect> {
        val end = TextOps.lineEnd(view.text, TextOps.lineStartAfter(view.text, view.caret, count - 1))
        state.mode = Mode.INSERT
        return deleteRange(view.caret, end, view.text)
    }

    /**
     * `C`: delete from the start of the line up to the caret into register 8, then
     * enter INSERT. Indentation is kept unless the caret is inside it.
     */
    private fun changeToLineStart(view: TextView): List<Effect> {
        val indentEnd = TextOps.firstNonBlank(view.text, view.caret)
        val start = if (view.caret > indentEnd) indentEnd else TextOps.lineStart(view.text, view.caret)
        state.mode = Mode.INSERT
        return deleteRange(start, view.caret, view.text)
    }

    // ---- search and replace (port of MIV's search/replace controllers) ----

    /**
     * `/`, `\`, `,`: jump to the first match after the caret (forward) or before it
     * (backward), falling back to the last/first match like MIV, and highlight all.
     */
    /** `/`, `\`, `,`: smart case, so a query without upper-case letters ignores case. */
    private fun typedSearch(query: String, view: TextView, forward: Boolean, regex: Boolean): List<Effect> =
        search(SearchQuery(query, regex, Search.smartIgnoreCase(query, regex)), view, forward)

    /** `*` / `#`: the whole word under the caret, case-sensitive, skipping the word itself. */
    private fun searchWord(view: TextView, forward: Boolean): List<Effect> {
        val word = TextOps.wordAt(view.text, view.caret) ?: return listOf(Effect.Message("no word under caret"))
        val query = SearchQuery(Search.wholeWordPattern(view.text.substring(word.first, word.last + 1)), regex = true)
        // Search from the start of the word so the word itself is skipped both ways.
        return search(query, view.copy(caret = word.first), forward)
    }

    /**
     * Jump to the first match after the caret (forward) or before it (backward) and
     * highlight all. Past the last / first match the search wraps around.
     */
    private fun search(query: SearchQuery, view: TextView, forward: Boolean, includeCaret: Boolean = false): List<Effect> {
        if (query.pattern.isEmpty()) return emptyList()
        val matches = Search.findMatches(view.text, query.pattern, query.regex, query.ignoreCase)
            ?: return listOf(Effect.Message("invalid regex: ${query.pattern}"))
        if (matches.isEmpty()) return listOf(Effect.Message("not found: ${query.pattern}"))

        state.lastSearch = query
        val firstAfter = if (includeCaret) view.caret else view.caret + 1
        return showNearest(matches, view, forward, firstAfter)
    }

    /** `n` / `N`: next or previous match of the last search, counted from the caret; wraps around. */
    private fun searchAgain(view: TextView, forward: Boolean): List<Effect> {
        val query = state.lastSearch ?: return emptyList()
        val matches = Search.findMatches(view.text, query.pattern, query.regex, query.ignoreCase) ?: return emptyList()
        if (matches.isEmpty()) return listOf(Effect.Message("not found: ${query.pattern}"))
        return showNearest(matches, view, forward, view.caret + 1)
    }

    private fun showNearest(matches: List<Match>, view: TextView, forward: Boolean, firstAfter: Int): List<Effect> {
        val index = if (forward) matches.indexOfFirst { it.start >= firstAfter } else matches.indexOfLast { it.start < view.caret }
        if (index >= 0) return showMatch(matches, index)
        val wrapped = if (forward) 0 else matches.lastIndex
        return showMatch(matches, wrapped) + Effect.Message("search wrapped")
    }

    private fun showMatch(matches: List<Match>, index: Int): List<Effect> {
        state.searchVisible = true
        return listOf(Effect.MoveCaret(matches[index].start), Effect.Highlight(matches, index))
    }

    /**
     * Enter on the command line after `=`. A single `=` steps through the matches
     * (Enter replaces one, `n` / `N` skip one); a double `==` replaces them all at once.
     * - `=replacement` / `==replacement` use the last search (literal or regex).
     * - `=search replacement` / `==search replacement` use a literal `search`.
     * - `=` alone replaces the current match, `==` alone every match of the current rule.
     */
    private fun commitReplaceRule(input: String, view: TextView, clipboard: () -> String?): List<Effect> {
        val replaceEverything = input.startsWith(CommandLineKind.REPLACE_RULE.prefix)
        val body = if (replaceEverything) input.substring(1) else input

        if (body.isBlank()) {
            if (!replaceEverything) return run(Command(Action.APPLY_REPLACE_RULE, sequence = "="), view, clipboard)
            return replaceAll(state.replaceRule ?: return listOf(Effect.Message("no replace rule")), view)
        }

        val parts = Search.parseReplaceRuleParts(body.trim()) ?: return listOf(Effect.Message("invalid replace rule"))
        val rule = if (parts.size == 2) {
            if (parts[0].isEmpty()) return listOf(Effect.Message("invalid replace rule"))
            ReplaceRule(parts[0], parts[1], regex = false)
        } else {
            val last = state.lastSearch ?: return listOf(Effect.Message("no previous search"))
            ReplaceRule(last.pattern, parts[0], last.regex, last.ignoreCase)
        }

        state.replaceRule = rule
        state.lastCommand = Command(Action.APPLY_REPLACE_RULE, sequence = "=")
        return if (replaceEverything) replaceAll(rule, view) else startStepping(rule, view)
    }

    /** Jump to the first match of [rule] from the caret and highlight all; Enter then replaces one at a time. */
    private fun startStepping(rule: ReplaceRule, view: TextView): List<Effect> {
        // Old matches must not stay active for the new rule when it finds nothing.
        state.searchVisible = false
        val effects = search(SearchQuery(rule.search, rule.regex, rule.ignoreCase), view, forward = true, includeCaret = true)
        if (!state.searchVisible) return listOf(Effect.Highlight(emptyList())) + effects
        return effects + Effect.Message("Enter replaces, n/N skips")
    }

    private fun replaceAll(rule: ReplaceRule, view: TextView): List<Effect> {
        val text = view.text
        val count = Search.findMatches(text, rule.search, rule.regex, rule.ignoreCase)?.size
        val replaced = Search.replaceAll(text, rule)
        if (count == null || replaced == null) return listOf(Effect.Message("invalid regex: ${rule.search}"))

        val remaining = Search.findMatches(replaced, rule.search, rule.regex, rule.ignoreCase).orEmpty()
        state.searchVisible = remaining.isNotEmpty()
        return listOfNotNull(
            TextOps.minimalReplacement(text, replaced),
            Effect.Highlight(remaining),
            Effect.Message("replaced $count matches"),
        )
    }

    /**
     * Enter / `.` with a replace rule: replace the match at (or after) the caret and
     * move to the next match.
     */
    private fun applyReplaceRule(view: TextView): List<Effect> {
        val rule = state.replaceRule ?: return emptyList()
        val text = view.text
        val matches = Search.findMatches(text, rule.search, rule.regex, rule.ignoreCase)
            ?: return listOf(Effect.Message("invalid regex: ${rule.search}"))
        val current = matches.firstOrNull { view.caret >= it.start && view.caret < it.end }
            ?: matches.firstOrNull { it.start >= view.caret }
            ?: return listOf(Effect.Highlight(matches), Effect.Message("no more matches"))

        val replacement = Search.replacementFor(text, rule, current)
        val updated = StringBuilder(text).replace(current.start, current.end, replacement)
        val remaining = Search.findMatches(updated, rule.search, rule.regex, rule.ignoreCase).orEmpty()
        val next = remaining.indexOfFirst { it.start >= current.start + replacement.length }
        state.searchVisible = remaining.isNotEmpty()
        return listOf(
            Effect.Replace(current.start, current.end, replacement),
            Effect.MoveCaret(if (next >= 0) remaining[next].start else current.start),
            Effect.Highlight(remaining, next),
        )
    }

    // ---- text objects ----

    /**
     * `"y`, `(x`, `!p` act inside the delimiters around the caret. Short forms use the
     * default registers (yank: 0 and the clipboard, delete: 8, paste: the clipboard);
     * `" 3y` uses only register 3.
     */
    private fun textObject(command: Command, view: TextView, clipboard: () -> String?): List<Effect> {
        val objectKey = command.char ?: return emptyList()
        val (open, close) = TextOps.findTextObjectBounds(view.text, view.caret, objectKey) ?: return emptyList()
        // `"Y` / `"X` include the delimiters.
        val around = command.action == Action.TEXT_OBJECT_YANK_AROUND || command.action == Action.TEXT_OBJECT_DELETE_AROUND
        val start = if (around) open else open + 1
        val end = if (around) close + 1 else close
        val selected = view.text.substring(start, end)

        return when (command.action) {
            Action.TEXT_OBJECT_YANK, Action.TEXT_OBJECT_YANK_AROUND -> {
                if (selected.isEmpty()) return emptyList()
                val registers = command.register?.let { listOf(it) }
                    ?: listOf(SmivState.YANK_REGISTER, SmivState.CLIPBOARD_REGISTER)
                registers.flatMap { store(it, selected, linewise = false) } +
                    Effect.Flash(start, end) + Effect.Message("stored yank in register ${registers.first()}")
            }
            Action.TEXT_OBJECT_DELETE, Action.TEXT_OBJECT_DELETE_AROUND -> {
                if (selected.isEmpty()) return emptyList()
                val register = command.register ?: SmivState.DELETE_REGISTER
                store(register, selected, linewise = false) + listOf(
                    Effect.Replace(start, end, ""),
                    Effect.MoveCaret(start),
                    Effect.Message("stored delete in register $register"),
                )
            }
            else -> {
                val register = command.register ?: SmivState.CLIPBOARD_REGISTER
                val value = register(register, clipboard)
                listOf(
                    Effect.Replace(start, close, value.text),
                    Effect.MoveCaret(start),
                    Effect.Message("pasted register $register"),
                )
            }
        }
    }

    // ---- selection mode, search character ----

    private fun usesSelection(command: Command, view: TextView) = !command.explicitCount && view.hasSelection
    private fun selectionStart(view: TextView) = minOf(view.selectionStart, view.selectionEnd)
    private fun selectionEnd(view: TextView) = maxOf(view.selectionStart, view.selectionEnd)

    /**
     * `V`: start selecting from the caret (or keep an existing selection), or stop and
     * drop the selection. While on, motions extend the selection and `x y c § p`
     * act on it.
     */
    private fun toggleSelect(view: TextView): List<Effect> {
        if (state.selectAnchor != null) {
            state.selectAnchor = null
            return listOf(Effect.MoveCaret(view.caret))
        }
        state.selectAnchor = when {
            !view.hasSelection -> view.caret
            view.caret == view.selectionEnd -> view.selectionStart
            else -> view.selectionEnd
        }
        return emptyList()
    }

    /** `c` / `C` with a selection: delete it into register 8 and enter INSERT. */
    private fun changeSelection(view: TextView): List<Effect> {
        state.mode = Mode.INSERT
        return deleteRange(selectionStart(view), selectionEnd(view), view.text)
    }

    /** `f` / `F`: search the character under the caret forward / backward (case-sensitive); `n` / `N` step. */
    private fun searchChar(view: TextView, forward: Boolean): List<Effect> {
        val char = view.text.getOrNull(view.caret)
        if (char == null || char == '\n') return listOf(Effect.Message("no character under caret"))
        return search(SearchQuery(char.toString(), regex = false), view, forward)
    }
}
