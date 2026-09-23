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
    PASTE_BEFORE, PASTE_AFTER, STORE_REGISTER, SHOW_REGISTERS,
    REPLACE_CHAR, REPLACE_WORD(true), TOGGLE_CASE_CHAR(true), TOGGLE_CASE_WORD(true),
    CHANGE_TO_LINE_END(true), CHANGE_TO_LINE_START, JOIN_LINES, JUMP_BRACKET_MATCH,
    BLOCK_FIRST_LINE, BLOCK_LAST_LINE,
    GOTO_LINE, GOTO_PERCENT, DOC_END, GOTO_LINE_FROM_BOTTOM,
    UNDO, REVERT_TO_SAVED, INSERT, INSERT_LINE_START, INSERT_LINE_END, OPEN_LINE_BELOW, OPEN_LINE_ABOVE,
    REPEAT, SEARCH_NEXT, SEARCH_PREVIOUS,
    // Built by the engine from the command line (`/foo`, `=bar`), not by the parser.
    SEARCH_FORWARD, SEARCH_BACKWARD, SEARCH_REGEX, APPLY_REPLACE_RULE,
    TEXT_OBJECT_YANK, TEXT_OBJECT_DELETE, TEXT_OBJECT_PASTE,
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

    /** Keys that open the command line when no command is pending. */
    val COMMAND_LINE_KEYS: Map<Char, CommandLineKind> = mapOf(
        '/' to CommandLineKind.SEARCH_FORWARD,
        '\\' to CommandLineKind.SEARCH_BACKWARD,
        ',' to CommandLineKind.SEARCH_REGEX,
        '=' to CommandLineKind.REPLACE_RULE,
    )

    /** Text objects: `!` picks the surrounding pair automatically. */
    const val TEXT_OBJECT_AUTO = '!'
    const val TEXT_OBJECT_KEYS = "!\"'`´([{"

    val TEXT_OBJECT_ACTIONS: Map<Char, Action> = mapOf(
        YANK_LINE to Action.TEXT_OBJECT_YANK,
        DELETE_CHAR to Action.TEXT_OBJECT_DELETE,
        PASTE_BEFORE to Action.TEXT_OBJECT_PASTE,
    )

    /** Commands that `.` repeats (MIV's REPEATABLE_EDIT_ACTIONS plus searches and replace). */
    val REPEATABLE: Set<Action> = setOf(
        Action.DELETE_CHAR, Action.DELETE_WORD, Action.DELETE_TO_LINE_END, Action.DELETE_LINE,
        Action.YANK_LINE, Action.YANK_WORD, Action.REPLACE_WORD, Action.REPLACE_CHAR,
        Action.TOGGLE_CASE_CHAR, Action.TOGGLE_CASE_WORD, Action.OPEN_LINE_BELOW, Action.OPEN_LINE_ABOVE,
        Action.CHANGE_TO_LINE_END, Action.CHANGE_TO_LINE_START, Action.JOIN_LINES,
        Action.TEXT_OBJECT_YANK, Action.TEXT_OBJECT_DELETE, Action.TEXT_OBJECT_PASTE,
        Action.SEARCH_FORWARD, Action.SEARCH_BACKWARD, Action.SEARCH_REGEX, Action.APPLY_REPLACE_RULE,
    )

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
        'c' to Action.CHANGE_TO_LINE_END,
        'C' to Action.CHANGE_TO_LINE_START,
        '-' to Action.BLOCK_FIRST_LINE,
        '_' to Action.BLOCK_LAST_LINE,
        '&' to Action.JOIN_LINES,
        '%' to Action.JUMP_BRACKET_MATCH,
        'u' to Action.UNDO,
        'U' to Action.REVERT_TO_SAVED,
        '.' to Action.REPEAT,
        'n' to Action.SEARCH_NEXT,
        'N' to Action.SEARCH_PREVIOUS,
        'i' to Action.INSERT,
        'I' to Action.INSERT_LINE_START,
        'k' to Action.INSERT_LINE_END,
        'o' to Action.OPEN_LINE_BELOW,
        'O' to Action.OPEN_LINE_ABOVE,
    )
}
