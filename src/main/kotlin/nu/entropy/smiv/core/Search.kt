package nu.entropy.smiv.core

import java.util.regex.PatternSyntaxException

/** A match as [start, end) offsets. Regex matches may be empty. */
data class Match(val start: Int, val end: Int)

data class SearchQuery(val pattern: String, val regex: Boolean, val ignoreCase: Boolean = false)

data class ReplaceRule(val search: String, val replace: String, val regex: Boolean, val ignoreCase: Boolean = false)

/** The command line shown in the status bar while typing a search or replace rule. */
enum class CommandLineKind(val prefix: Char) {
    SEARCH_FORWARD('/'),
    SEARCH_BACKWARD('\\'),
    SEARCH_REGEX('~'),
    REPLACE_RULE('='),
}

class CommandLine(val kind: CommandLineKind) {
    val buffer = StringBuilder()
    val text: String get() = "${kind.prefix}$buffer"
}

/** Search and replace helpers (port of MIV's searchController/replaceController). */
object Search {
    /** All matches, non-overlapping; null for an invalid regex. */
    fun findMatches(text: CharSequence, pattern: String, regex: Boolean, ignoreCase: Boolean = false): List<Match>? {
        if (pattern.isEmpty()) return emptyList()
        if (!regex) {
            val matches = mutableListOf<Match>()
            var index = text.indexOf(pattern, ignoreCase = ignoreCase)
            while (index >= 0) {
                matches += Match(index, index + pattern.length)
                index = text.indexOf(pattern, index + pattern.length, ignoreCase)
            }
            return matches
        }
        val compiled = compile(pattern, ignoreCase) ?: return null
        return compiled.findAll(text).map { Match(it.range.first, it.range.last + 1) }.toList()
    }

    /** Text with every match of [rule] replaced; null for an invalid regex. */
    fun replaceAll(text: CharSequence, rule: ReplaceRule): String? {
        if (!rule.regex) return text.toString().replace(rule.search, rule.replace, rule.ignoreCase)
        val compiled = compile(rule.search, rule.ignoreCase) ?: return null
        return compiled.replace(text, javaReplacement(rule.replace))
    }

    /** Replacement text for one [match] of [rule] in [text] (expands `$1` etc. for regex rules). */
    fun replacementFor(text: CharSequence, rule: ReplaceRule, match: Match): String {
        if (!rule.regex) return rule.replace
        val compiled = compile(rule.search, rule.ignoreCase) ?: return rule.replace
        val matcher = compiled.toPattern().matcher(text)
        if (!matcher.find(match.start)) return rule.replace
        val buffer = StringBuilder()
        matcher.appendReplacement(buffer, javaReplacement(rule.replace))
        // appendReplacement first copies the text before the match.
        return buffer.substring(matcher.start())
    }

    /**
     * Rules are written in JavaScript replacement syntax (as in MIV): `$1`, `$&`,
     * `$<name>`, `$$`. Convert to Java's syntax, where `\` is special too.
     */
    fun javaReplacement(js: String): String = buildString {
        var i = 0
        while (i < js.length) {
            val c = js[i]
            val next = js.getOrNull(i + 1)
            when {
                c == '\\' -> append("\\\\")
                c != '$' -> append(c)
                next == '$' -> { append("\\$"); i++ }
                next == '&' -> { append("$0"); i++ }
                next != null && next.isDigit() -> { append('$').append(next); i++ }
                next == '<' && js.indexOf('>', i + 2) > 0 -> {
                    val close = js.indexOf('>', i + 2)
                    append("\${").append(js, i + 2, close).append('}')
                    i = close
                }
                else -> append("\\$")
            }
            i++
        }
    }

    /**
     * Split `search replacement` (port of MIV's parseReplaceRuleParts). Parts are
     * separated by whitespace; `'...'` quotes a part with spaces. One or two parts.
     */
    fun parseReplaceRuleParts(input: String): List<String>? {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < input.length && parts.size < 2) {
            while (i < input.length && input[i].isWhitespace()) i++
            if (i >= input.length) break
            if (input[i] == '\'') {
                val close = input.indexOf('\'', i + 1)
                if (close < 0) return null
                parts += input.substring(i + 1, close)
                i = close + 1
                continue
            }
            val start = i
            while (i < input.length && !input[i].isWhitespace()) i++
            parts += input.substring(start, i)
        }
        while (i < input.length && input[i].isWhitespace()) i++
        return if (parts.size in 1..2 && i == input.length) parts else null
    }

    private fun compile(pattern: String, ignoreCase: Boolean = false): Regex? = try {
        if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
    } catch (_: PatternSyntaxException) {
        null
    }

    /**
     * Smart case: a search without upper-case letters ignores case. In a regex, letters
     * right after `\` (like `\D`) are escapes and do not count.
     */
    fun smartIgnoreCase(query: String, regex: Boolean): Boolean =
        query.indices.none { query[it].isUpperCase() && !(regex && it > 0 && query[it - 1] == '\\') }

    private const val WORD_SEPARATORS = "`~!@#$%^&*()\\-=+\\[{\\]}\\\\|;:'\",.<>/?"

    /**
     * Regex for the whole word [word] (`*` / `#`): not preceded or followed by another
     * word character (anything but whitespace and separators, as for `q` / `e`).
     */
    fun wholeWordPattern(word: String): String {
        val wordChar = "[^\\s$WORD_SEPARATORS]"
        return "(?<!$wordChar)${Regex.escape(word)}(?!$wordChar)"
    }
}
