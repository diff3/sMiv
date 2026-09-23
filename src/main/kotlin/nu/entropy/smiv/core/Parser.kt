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
    /** Replacement character for `r`. */
    val char: Char? = null,
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
 * - `[count]key`, `[count]r<char>`
 * - `count ␠ register x|y` (for example `5 3x`)
 * - `[register]p`, `[register]P`, `register v`
 * - `g`, `[line]g`, `m`, `[1-9]m`, `G`, `[n]G`
 */
object Parser {
    fun parse(buffer: String): ParseResult {
        if (buffer.isEmpty()) return ParseResult.Invalid

        val digits = buffer.takeWhile { it.isAsciiDigit() }
        val rest = buffer.substring(digits.length)
        if (rest.isEmpty()) return ParseResult.Partial

        if (rest[0] == Keys.REGISTER_SEPARATOR) {
            return if (digits.isEmpty()) ParseResult.Invalid else parseRegisterTargeted(buffer, digits, rest)
        }

        if (rest[0] == Keys.REPLACE_CHAR) {
            return when (rest.length) {
                1 -> ParseResult.Partial
                2 -> complete(Command(Action.REPLACE_CHAR, sequence = buffer, char = rest[1]))
                else -> ParseResult.Invalid
            }
        }

        if (rest.length != 1) return ParseResult.Invalid
        val key = rest[0]
        val number = digits.ifEmpty { null }?.let(::parseNumber)
        val register = if (digits.length == 1) digits[0] - '0' else null

        return when (key) {
            Keys.PASTE_BEFORE, Keys.PASTE_AFTER -> {
                if (digits.length > 1) return ParseResult.Invalid
                val action = if (key == Keys.PASTE_BEFORE) Action.PASTE_BEFORE else Action.PASTE_AFTER
                complete(Command(action, sequence = buffer, register = register))
            }
            Keys.STORE_REGISTER -> {
                if (register == null || register == 0) return ParseResult.Invalid
                complete(Command(Action.STORE_REGISTER, sequence = buffer, register = register))
            }
            Keys.GOTO_LINE -> complete(Command(Action.GOTO_LINE, number ?: 1, number != null, buffer))
            Keys.DOC_MIDDLE -> when {
                number == null -> complete(Command(Action.GOTO_PERCENT, 50, sequence = buffer))
                digits.length == 1 && number in 1..9 ->
                    complete(Command(Action.GOTO_PERCENT, number * 10, true, buffer))
                else -> ParseResult.Invalid
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

    /** `count ␠ register x|y`, with partial states while it is being typed. */
    private fun parseRegisterTargeted(buffer: String, digits: String, rest: String): ParseResult {
        if (rest.length == 1) return ParseResult.Partial
        val register = rest[1]
        if (!register.isAsciiDigit()) return ParseResult.Invalid
        if (rest.length == 2) return ParseResult.Partial
        if (rest.length != 3) return ParseResult.Invalid

        val action = when (rest[2]) {
            Keys.DELETE_CHAR -> Action.DELETE_CHAR
            Keys.YANK_LINE -> Action.YANK_LINE
            else -> return ParseResult.Invalid
        }
        val count = parseNumber(digits).coerceAtMost(MAX_COUNT)
        return complete(Command(action, count, explicitCount = true, sequence = buffer, register = register - '0'))
    }

    private fun complete(command: Command) = ParseResult.Complete(command)

    /** Positive number from digits; `0` becomes 1 and overlong input is capped. */
    private fun parseNumber(digits: String): Int {
        val value = digits.trimStart('0').take(9).toIntOrNull() ?: 0
        return value.coerceAtLeast(1)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
