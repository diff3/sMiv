package nu.entropy.smiv.core

/** Upper bound for counts so a typo like `99999999w` cannot hang the editor. */
const val MAX_COUNT = 10_000

data class Command(
    val action: Action,
    val count: Int = 1,
    val explicitCount: Boolean = false,
    val sequence: String = "",
)

sealed interface ParseResult {
    data class Complete(val command: Command) : ParseResult
    data object Partial : ParseResult
    data object Invalid : ParseResult
}

/**
 * Parse the buffered NAV key sequence (subset of MIV's `parser/index.ts`).
 *
 * Grammar: `[count]key`. A count applies to countable actions; other actions
 * ignore it, except `p`/`P` where a leading digit means a register in MIV.
 */
object Parser {
    fun parse(buffer: String): ParseResult {
        if (buffer.isEmpty()) return ParseResult.Invalid

        val digits = buffer.takeWhile { it.isAsciiDigit() }
        val rest = buffer.substring(digits.length)
        if (rest.isEmpty()) return ParseResult.Partial
        if (rest.length != 1) return ParseResult.Invalid

        val key = rest[0]
        val action = Keys.SINGLE_KEY_ACTIONS[key] ?: return ParseResult.Invalid
        if (digits.isEmpty()) return ParseResult.Complete(Command(action, sequence = buffer))
        if (key in Keys.REGISTER_RESERVED) return ParseResult.Invalid

        val count = if (action.countable) parseCount(digits) else 1
        return ParseResult.Complete(Command(action, count, explicitCount = true, sequence = buffer))
    }

    private fun parseCount(digits: String): Int {
        val value = digits.trimStart('0').take(6).toIntOrNull() ?: 0
        return value.coerceIn(1, MAX_COUNT)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
