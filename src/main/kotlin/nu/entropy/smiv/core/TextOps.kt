package nu.entropy.smiv.core

/** Pure text helpers. Documents use `\n` as the only line separator. */
object TextOps {
    private const val WORD_SEPARATORS = "`~!@#$%^&*()-=+[{]}\\|;:'\",.<>/?"

    // ---- lines ----

    /** First non-space/tab offset on the line containing [offset] (line end if blank). */
    fun firstNonBlank(text: CharSequence, offset: Int): Int {
        var i = lineStart(text, offset)
        val end = lineEnd(text, offset)
        while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
        return i
    }

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

    /** Line table for commands that address lines by number. */
    class Lines(private val text: CharSequence) {
        private val starts: IntArray = buildList {
            add(0)
            for (i in text.indices) if (text[i] == '\n') add(i + 1)
        }.toIntArray()

        val count: Int get() = starts.size
        val last: Int get() = starts.size - 1

        fun start(line: Int): Int = starts[line]
        fun end(line: Int): Int = if (line < last) starts[line + 1] - 1 else text.length
        fun isBlank(line: Int): Boolean = (start(line) until end(line)).all { text[it].isWhitespace() }

        fun lineOf(offset: Int): Int {
            val index = starts.binarySearch(offset.coerceIn(0, text.length))
            return if (index >= 0) index else -index - 2
        }

        /** Offset on [line] at [column], clamped to the line's length. */
        fun offsetAt(line: Int, column: Int): Int = start(line) + column.coerceIn(0, end(line) - start(line))
    }

    // ---- words ----

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

    /** End offset after [count] `e` steps from [offset]. */
    fun wordsEnd(text: CharSequence, offset: Int, count: Int): Int {
        var end = offset
        repeat(count) { end = wordEndRight(text, end) }
        return end
    }

    /**
     * Range for `R` and `°`: from the start of the word at the caret (or the next
     * word) over [count] words. Null when there is no word to act on.
     */
    fun wordOperationRange(text: CharSequence, offset: Int, count: Int): IntRange? {
        val inWord = (offset < text.length && charClass(text[offset]) == 2) ||
            (offset > 0 && charClass(text[offset - 1]) == 2)
        val start = if (inWord) {
            var i = offset
            while (i > 0 && charClass(text[i - 1]) == 2) i--
            i
        } else {
            wordStartRight(text, offset).takeIf { it != offset && it < text.length } ?: return null
        }
        val end = wordsEnd(text, start, count)
        return if (end > start) start until end else null
    }

    /** The word (letters, digits, `_` …) at or right before [offset], for `*` / `#`. */
    fun wordAt(text: CharSequence, offset: Int): IntRange? {
        var start = offset.coerceIn(0, text.length)
        if ((start >= text.length || charClass(text[start]) != 2) && (start == 0 || charClass(text[start - 1]) != 2)) return null
        while (start > 0 && charClass(text[start - 1]) == 2) start--
        var end = start
        while (end < text.length && charClass(text[end]) == 2) end++
        return start until end
    }

    /** `f` / `F`: the [count]th [char] after (or before) [offset] on the same line. */
    fun findCharOnLine(text: CharSequence, offset: Int, char: Char, forward: Boolean, count: Int): Int? {
        val range = if (forward) (offset + 1) until lineEnd(text, offset) else (offset - 1) downTo lineStart(text, offset)
        return range.filter { text[it] == char }.getOrNull(count - 1)
    }

    fun toggleCase(value: CharSequence): String = buildString(value.length) {
        for (c in value) append(if (c.isUpperCase()) c.lowercaseChar() else c.uppercaseChar())
    }

    // ---- paragraphs (Alt+Q / Alt+E) ----

    /** Start of the next or previous paragraph; null when there is none forward. */
    fun paragraphTarget(text: CharSequence, offset: Int, forward: Boolean): Int? {
        val lines = Lines(text)
        var line = lines.lineOf(offset)

        if (forward) {
            if (!lines.isBlank(line)) while (line <= lines.last && !lines.isBlank(line)) line++
            while (line <= lines.last && lines.isBlank(line)) line++
            return if (line > lines.last) null else lines.start(line)
        }

        if (!lines.isBlank(line)) while (line >= 0 && !lines.isBlank(line)) line--
        while (line >= 0 && lines.isBlank(line)) line--
        if (line < 0) return 0
        while (line > 0 && !lines.isBlank(line - 1)) line--
        return lines.start(line)
    }

    // ---- revert to saved (`U`) ----

    /**
     * The smallest single replacement that turns [current] into [target] (common
     * prefix and suffix are kept), or null when they are equal.
     */
    fun minimalReplacement(current: CharSequence, target: CharSequence): Effect.Replace? {
        val maxShared = minOf(current.length, target.length)
        var prefix = 0
        while (prefix < maxShared && current[prefix] == target[prefix]) prefix++
        if (prefix == current.length && prefix == target.length) return null
        var suffix = 0
        while (suffix < maxShared - prefix &&
            current[current.length - 1 - suffix] == target[target.length - 1 - suffix]
        ) suffix++
        return Effect.Replace(prefix, current.length - suffix, target.substring(prefix, target.length - suffix))
    }

    // ---- block lines (`-` / `_`) ----

    private const val BLOCK_OPENERS = "([{"
    private const val BLOCK_CLOSERS = ")]}"

    /**
     * Innermost `()`, `[]` or `{}` around [offset] as (open, close) offsets. The caret
     * may sit on either bracket. Brackets inside strings or comments are not skipped.
     */
    fun enclosingBlock(text: CharSequence, offset: Int): Pair<Int, Int>? {
        var open = -1
        if (offset < text.length && text[offset] in BLOCK_OPENERS) {
            open = offset
        } else {
            var depth = 0
            for (i in (offset - 1) downTo 0) {
                val c = text[i]
                if (c in BLOCK_CLOSERS) depth++
                if (c in BLOCK_OPENERS && depth-- == 0) {
                    open = i
                    break
                }
            }
        }
        if (open < 0) return null

        var depth = 0
        for (i in (open + 1) until text.length) {
            val c = text[i]
            if (c in BLOCK_OPENERS) depth++
            if (c in BLOCK_CLOSERS && depth-- == 0) return open to i
        }
        return null
    }

    /**
     * `-` / `_`: first non-blank of the first ([first]) or last line inside the
     * enclosing block, or [percent] of the way in from the top (`-`) or bottom (`_`).
     * When the block has no lines of its own, the first or last character inside the brackets.
     */
    fun blockLineTarget(text: CharSequence, offset: Int, first: Boolean, percent: Int = 0): Int? {
        val (open, close) = enclosingBlock(text, offset) ?: return null
        val openLineEnd = lineEnd(text, open)
        val closeLineStart = lineStart(text, close)
        val hasInnerLines = openLineEnd + 1 < closeLineStart
        if (!hasInnerLines) return if (first || close == open + 1) open + 1 else close - 1

        val innerLineStarts = buildList {
            var start = openLineEnd + 1
            while (start < closeLineStart) {
                add(start)
                start = lineEnd(text, start) + 1
            }
        }
        val fromTop = Math.round((innerLineStarts.size - 1) * percent / 100.0).toInt()
        val index = if (first) fromTop else innerLineStarts.lastIndex - fromTop
        return firstNonBlank(text, innerLineStarts[index])
    }

    // ---- text objects (port of MIV's editActions.ts, no nesting) ----

    private val TEXT_OBJECT_PAIRS = mapOf(
        '"' to '"', '\'' to '\'', '`' to '`', '´' to '´', '(' to ')', '[' to ']', '{' to '}', '<' to '>',
    )
    private val TEXT_OBJECT_CLOSERS = mapOf(')' to '(', ']' to '[', '}' to '{', '>' to '<')

    /**
     * Delimiter offsets (open, close) for text object [objectKey] around [offset]:
     * scan left to the nearest opening delimiter, then right to the first closing one.
     * `!` first tries the delimiter under the caret, then any pair.
     */
    fun findTextObjectBounds(text: CharSequence, offset: Int, objectKey: Char): Pair<Int, Int>? {
        if (objectKey == Keys.TEXT_OBJECT_AUTO) autoBoundsAtCaret(text, offset)?.let { return it }

        for (openOffset in (offset - 1) downTo 0) {
            val close = textObjectCloseAt(text, openOffset, objectKey) ?: continue
            val closeOffset = firstUnescaped(text, offset until text.length, close, symmetric = close == text[openOffset])
                ?: continue
            return openOffset to closeOffset
        }
        return null
    }

    private fun autoBoundsAtCaret(text: CharSequence, offset: Int): Pair<Int, Int>? {
        if (offset !in text.indices) return null
        val char = text[offset]
        val open = if (char in TEXT_OBJECT_PAIRS) char else TEXT_OBJECT_CLOSERS[char] ?: return null
        val close = TEXT_OBJECT_PAIRS.getValue(open)
        val symmetric = open == close

        if (symmetric) {
            if (isEscaped(text, offset)) return null
            firstUnescaped(text, (offset + 1) until text.length, close, true)?.let { return offset to it }
            firstUnescaped(text, (offset - 1) downTo 0, open, true)?.let { return it to offset }
            return null
        }
        if (char == open) firstUnescaped(text, (offset + 1) until text.length, close, false)?.let { return offset to it }
        if (char == close) firstUnescaped(text, (offset - 1) downTo 0, open, false)?.let { return it to offset }
        return null
    }

    /** Closing delimiter when [offset] holds an opening delimiter usable for [objectKey]. */
    private fun textObjectCloseAt(text: CharSequence, offset: Int, objectKey: Char): Char? {
        val open = text[offset]
        if (objectKey != Keys.TEXT_OBJECT_AUTO && open != objectKey) return null
        val close = TEXT_OBJECT_PAIRS[open] ?: return null
        if (open == close && isEscaped(text, offset)) return null
        return close
    }

    private fun firstUnescaped(text: CharSequence, indices: IntProgression, char: Char, symmetric: Boolean): Int? =
        indices.firstOrNull { text[it] == char && !(symmetric && isEscaped(text, it)) }

    // ---- bracket matching (`%`, port of MIV's motionActions.ts) ----

    private val OPEN_TO_CLOSE = linkedMapOf(
        '"' to '"', '\'' to '\'', '`' to '`', '´' to '´', '(' to ')', '[' to ']', '{' to '}', '<' to '>',
    )
    private val CLOSE_TO_OPEN = OPEN_TO_CLOSE.entries.associate { (open, close) -> close to open }

    private fun isEscaped(text: CharSequence, index: Int): Boolean {
        var backslashes = 0
        var i = index - 1
        while (i >= 0 && text[i] == '\\') {
            backslashes++
            i--
        }
        return backslashes % 2 == 1
    }

    fun findMatchingBracket(text: CharSequence, offset: Int): Int? {
        if (offset < text.length) matchOnBracket(text, offset)?.let { return it }
        if (offset > 0) matchOnBracket(text, offset - 1)?.let { return it }
        return enclosingClose(text, offset)
    }

    private fun matchOnBracket(text: CharSequence, offset: Int): Int? {
        val char = text[offset]
        OPEN_TO_CLOSE[char]?.let { close ->
            return if (close == char) symmetricPartner(text, offset, char) else scanForward(text, offset, char, close)
        }
        CLOSE_TO_OPEN[char]?.let { open -> return scanBackward(text, offset, open, char) }
        return null
    }

    /** Quote-like delimiters pair up in document order. */
    private fun symmetricPartner(text: CharSequence, offset: Int, char: Char): Int? {
        val positions = text.indices.filter { text[it] == char && !isEscaped(text, it) }
        val index = positions.indexOf(offset)
        if (index < 0) return null
        return if (index % 2 == 0) positions.getOrNull(index + 1) else positions[index - 1]
    }

    private fun scanForward(text: CharSequence, start: Int, open: Char, close: Char): Int? {
        if (open == close) {
            return ((start + 1) until text.length).firstOrNull { text[it] == close && !isEscaped(text, it) }
        }
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                open -> depth++
                close -> if (--depth == 0) return i
            }
        }
        return null
    }

    private fun scanBackward(text: CharSequence, start: Int, open: Char, close: Char): Int? {
        if (open == close) {
            return ((start - 1) downTo 0).firstOrNull { text[it] == open && !isEscaped(text, it) }
        }
        var depth = 0
        for (i in start downTo 0) {
            when (text[i]) {
                close -> depth++
                open -> if (--depth == 0) return i
            }
        }
        return null
    }

    /** Not on a bracket: jump to the close of the innermost pair around the cursor. */
    private fun enclosingClose(text: CharSequence, offset: Int): Int? {
        var bestOpen = -1
        var bestClose = -1
        for ((open, close) in OPEN_TO_CLOSE) {
            for (openOffset in (offset - 1) downTo 0) {
                if (text[openOffset] != open) continue
                if (open == close && isEscaped(text, openOffset)) continue
                val closeOffset = scanForward(text, openOffset, open, close) ?: continue
                if (!(openOffset < offset && offset < closeOffset)) continue
                if (openOffset > bestOpen) {
                    bestOpen = openOffset
                    bestClose = closeOffset
                }
                break
            }
        }
        return bestClose.takeIf { it >= 0 }
    }
}
