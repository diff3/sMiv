package nu.entropy.smiv.core

/**
 * NAV-mode key layout (port of MIV's `config/constants.ts` + `keymaps/default.json`).
 *
 * sMiv receives typed characters (not physical key codes), so keys are chars.
 * `countable` actions repeat [Command.count] times; others ignore a count.
 */
enum class Action(val countable: Boolean = false) {
    LEFT(true), RIGHT(true), UP(true), DOWN(true),
    PAGE_UP(true), PAGE_DOWN(true), LINE_START(true), LINE_END(true),
    WORD_LEFT(true), WORD_END_RIGHT(true), WORD_END_LEFT(true), WORD_START_RIGHT(true),
    DELETE_CHAR(true), DELETE_LINE(true), DELETE_WORD(true), DELETE_TO_LINE_END,
    YANK_LINE(true), YANK_WORD(true),
    PASTE_BEFORE, PASTE_AFTER, STORE_REGISTER,
    REPLACE_CHAR, REPLACE_WORD(true), TOGGLE_CASE_CHAR(true), TOGGLE_CASE_WORD(true),
    CHANGE_TO_LINE_END, CHANGE_LINE, JOIN_LINES, JUMP_BRACKET_MATCH,
    GOTO_LINE, GOTO_PERCENT, DOC_END, GOTO_LINE_FROM_BOTTOM,
    UNDO, INSERT, INSERT_LINE_START, INSERT_LINE_END, OPEN_LINE_BELOW, OPEN_LINE_ABOVE,
}

object Keys {
    const val INSERT_SPACE = ' '
    const val REGISTER_SEPARATOR = ' '
    const val REPLACE_CHAR = 'r'
    const val PASTE_BEFORE = 'p'
    const val PASTE_AFTER = 'P'
    const val STORE_REGISTER = 'v'
    const val GOTO_LINE = 'g'
    const val DOC_MIDDLE = 'm'
    const val DOC_BOTTOM = 'G'
    const val DELETE_CHAR = 'x'
    const val YANK_LINE = 'y'

    val SINGLE_KEY_ACTIONS: Map<Char, Action> = mapOf(
        'a' to Action.LEFT,
        'd' to Action.RIGHT,
        'w' to Action.UP,
        's' to Action.DOWN,
        'W' to Action.PAGE_UP,
        'S' to Action.PAGE_DOWN,
        'A' to Action.LINE_START,
        'D' to Action.LINE_END,
        'q' to Action.WORD_LEFT,
        'e' to Action.WORD_END_RIGHT,
        'Q' to Action.WORD_END_LEFT,
        'E' to Action.WORD_START_RIGHT,
        DELETE_CHAR to Action.DELETE_CHAR,
        'X' to Action.DELETE_WORD,
        'b' to Action.DELETE_LINE,
        'B' to Action.DELETE_TO_LINE_END,
        YANK_LINE to Action.YANK_LINE,
        'Y' to Action.YANK_WORD,
        'R' to Action.REPLACE_WORD,
        '§' to Action.TOGGLE_CASE_CHAR,
        '°' to Action.TOGGLE_CASE_WORD,
        '-' to Action.CHANGE_TO_LINE_END,
        '_' to Action.CHANGE_LINE,
        '&' to Action.JOIN_LINES,
        '%' to Action.JUMP_BRACKET_MATCH,
        'u' to Action.UNDO,
        'i' to Action.INSERT,
        'I' to Action.INSERT_LINE_START,
        'k' to Action.INSERT_LINE_END,
        'o' to Action.OPEN_LINE_BELOW,
        'O' to Action.OPEN_LINE_ABOVE,
    )
}
