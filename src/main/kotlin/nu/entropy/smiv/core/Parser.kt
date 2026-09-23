package nu.entropy.smiv.core

/** Upper bound for repeat counts so a typo like `99999999w` cannot hang the editor. */
const val MAX_COUNT = 10_000

data class Command(
    val action: Action,
    /** Repeat count, or the line/percent/register number for goto and store commands. */
    val count: Int = 1,
    val explicitCount: Boolean = false,
    val sequence: String = "",
    /** Target (x, y, v) or source (p, P) register; null means the action's default. */
    val register: Int? = null,
    /** Replacement character for `r`, or the object key for text objects. */
    val char: Char? = null,
    /** Query for searches repeated with `.`. */
    val text: String? = null,
)

sealed interface ParseResult {
    data class Complete(val command: Command) : ParseResult
    data object Partial : ParseResult
    data object Invalid : ParseResult
}

/**
 * Parse the buffered NAV key sequence (port of MIV's `parser/index.ts`).
 *
 * Grammar:
 * - `[count]key`, `r<char>`, `[count]f<char>`, `[count]F<char>`
 * - `-` / `_` and `[1-9]-` / `[1-9]_` (10–90 % into the block from the top / bottom)
 * - `count ␠ register x|y` (for example `5 3x`), `register ␠ x|y` (for example `2 y`)
 * - `[register]p`, `[register]P`, `v`, `register v`
 * - `g`, `[line]g`, `m`, `[1-9]m`, `G`, `[n]G`
 * - text objects: `!y`, `"x`, `(p`, `"Y`, `(X` or with a register `" 3y`
 */
object Parser {
    fun parse(buffer: String): ParseResult {
        if (buffer.isEmpty()) return ParseResult.Invalid

        if (buffer[0] in Keys.TEXT_OBJECT_KEYS) return parseTextObject(buffer)

        val digits = buffer.takeWhile { it.isAsciiDigit() }
        val rest = buffer.substring(digits.length)
        if (rest.isEmpty()) return ParseResult.Partial

        if (rest[0] == Keys.REGISTER_SEPARATOR) {
            return if (digits.isEmpty()) ParseResult.Invalid else parseRegisterTargeted(buffer, digits, rest)
        }

        Keys.CHAR_ARGUMENT_KEYS[rest[0]]?.let { action ->
            return when (rest.length) {
                1 -> ParseResult.Partial
                2 -> {
                    val count = if (action.countable && digits.isNotEmpty()) parseNumber(digits).coerceAtMost(MAX_COUNT) else 1
                    complete(Command(action, count, digits.isNotEmpty(), buffer, char = rest[1]))
                }
                else -> ParseResult.Invalid
            }
        }

        if (rest.length != 1) return ParseResult.Invalid
        val key = rest[0]
        val number = digits.ifEmpty { null }?.let(::parseNumber)
        val register = if (digits.length == 1) digits[0] - '0' else null
        // `3m`, `5-`: a single digit 1–9 meaning 10–90 %.
        val percentDigit = register?.takeIf { it in 1..9 }

        return when (key) {
            Keys.PASTE_BEFORE, Keys.PASTE_AFTER -> {
                if (digits.length > 1) return ParseResult.Invalid
                val action = if (key == Keys.PASTE_BEFORE) Action.PASTE_BEFORE else Action.PASTE_AFTER
                complete(Command(action, sequence = buffer, register = register))
            }
            Keys.STORE_REGISTER -> {
                if (digits.isEmpty()) return complete(Command(Action.SHOW_REGISTERS, sequence = buffer))
                if (register == null || register == 0) return ParseResult.Invalid
                complete(Command(Action.STORE_REGISTER, sequence = buffer, register = register))
            }
            Keys.GOTO_LINE -> complete(Command(Action.GOTO_LINE, number ?: 1, number != null, buffer))
            Keys.DOC_MIDDLE -> when {
                number == null -> complete(Command(Action.GOTO_PERCENT, 50, sequence = buffer))
                percentDigit != null -> complete(Command(Action.GOTO_PERCENT, percentDigit * 10, true, buffer))
                else -> ParseResult.Invalid
            }
            Keys.BLOCK_FIRST_LINE, Keys.BLOCK_LAST_LINE -> {
                val action = if (key == Keys.BLOCK_FIRST_LINE) Action.BLOCK_FIRST_LINE else Action.BLOCK_LAST_LINE
                when {
                    number == null -> complete(Command(action, 0, sequence = buffer))
                    percentDigit != null -> complete(Command(action, percentDigit * 10, true, buffer))
                    else -> ParseResult.Invalid
                }
            }
            Keys.DOC_BOTTOM ->
                if (number == null) complete(Command(Action.DOC_END, sequence = buffer))
                else complete(Command(Action.GOTO_LINE_FROM_BOTTOM, number, true, buffer))
            else -> {
                val action = Keys.SINGLE_KEY_ACTIONS[key] ?: return ParseResult.Invalid
                if (number == null) return complete(Command(action, sequence = buffer))
                val count = if (action.countable) number.coerceAtMost(MAX_COUNT) else 1
                complete(Command(action, count, explicitCount = true, sequence = buffer))
            }
        }
    }

    /** `count ␠ register x|y` or `register ␠ x|y`, with partial states while it is being typed. */
    private fun parseRegisterTargeted(buffer: String, digits: String, rest: String): ParseResult {
        if (rest.length == 1) return ParseResult.Partial
        val register = rest[1]
        if (!register.isAsciiDigit()) {
            // `2 y`: the single digit is the register and the count is 1.
            val action = registerTargetAction(register)
            if (action == null || rest.length != 2 || digits.length != 1) return ParseResult.Invalid
            return complete(Command(action, 1, explicitCount = true, sequence = buffer, register = digits[0] - '0'))
        }
        if (rest.length == 2) return ParseResult.Partial
        if (rest.length != 3) return ParseResult.Invalid

        val action = registerTargetAction(rest[2]) ?: return ParseResult.Invalid
        val count = parseNumber(digits).coerceAtMost(MAX_COUNT)
        return complete(Command(action, count, explicitCount = true, sequence = buffer, register = register - '0'))
    }

    /**
     * `"y` uses the default registers; `" 3y` names one (port of MIV's
     * parseTextObjectCommand). `!` only has the short form, as in MIV.
     */
    private fun parseTextObject(buffer: String): ParseResult {
        val objectKey = buffer[0]
        if (buffer.length == 1) return ParseResult.Partial

        Keys.TEXT_OBJECT_ACTIONS[buffer[1]]?.let { action ->
            return if (buffer.length == 2) complete(Command(action, sequence = buffer, char = objectKey)) else ParseResult.Invalid
        }
        if (objectKey == Keys.TEXT_OBJECT_AUTO || buffer[1] != Keys.REGISTER_SEPARATOR) return ParseResult.Invalid
        if (buffer.length == 2) return ParseResult.Partial
        if (!buffer[2].isAsciiDigit()) return ParseResult.Invalid
        if (buffer.length == 3) return ParseResult.Partial
        if (buffer.length != 4) return ParseResult.Invalid

        val action = Keys.TEXT_OBJECT_ACTIONS[buffer[3]] ?: return ParseResult.Invalid
        return complete(Command(action, sequence = buffer, register = buffer[2] - '0', char = objectKey))
    }

    private fun registerTargetAction(key: Char): Action? = when (key) {
        Keys.DELETE_CHAR -> Action.DELETE_CHAR
        Keys.YANK_LINE -> Action.YANK_LINE
        else -> null
    }

    private fun complete(command: Command) = ParseResult.Complete(command)

    /** Positive number from digits; `0` becomes 1 and overlong input is capped. */
    private fun parseNumber(digits: String): Int {
        val value = digits.trimStart('0').take(9).toIntOrNull() ?: 0
        return value.coerceAtLeast(1)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
