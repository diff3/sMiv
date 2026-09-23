package nu.entropy.smiv.core

/**
 * NAV-mode key layout (port of MIV's `config/constants.ts` + `keymaps/default.json`).
 *
 * sMiv receives typed characters (not physical key codes), so keys are chars.
 */
enum class Action(val countable: Boolean = false) {
    LEFT(true), RIGHT(true), UP(true), DOWN(true),
    PAGE_UP(true), PAGE_DOWN(true), LINE_START(true), LINE_END(true),
    WORD_LEFT(true), WORD_END_RIGHT(true), WORD_END_LEFT(true), WORD_START_RIGHT(true),
    DELETE_CHAR(true), DELETE_LINE(true), YANK_LINE(true),
    PASTE_BEFORE, PASTE_AFTER,
    UNDO, INSERT, OPEN_LINE_BELOW, OPEN_LINE_ABOVE,
}

object Keys {
    const val INSERT_SPACE = ' '

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
        'x' to Action.DELETE_CHAR,
        'b' to Action.DELETE_LINE,
        'y' to Action.YANK_LINE,
        // MIV swaps the Vim meaning: `p` pastes before the cursor, `P` after.
        'p' to Action.PASTE_BEFORE,
        'P' to Action.PASTE_AFTER,
        'u' to Action.UNDO,
        'i' to Action.INSERT,
        'o' to Action.OPEN_LINE_BELOW,
        'O' to Action.OPEN_LINE_ABOVE,
    )

    /** `[1-9]p` / `[1-9]P` are register pastes in MIV; reserved until registers are ported. */
    val REGISTER_RESERVED: Set<Char> = setOf('p', 'P')
}
