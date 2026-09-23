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
    TOGGLE_SELECT, SEARCH_CHAR_FORWARD, SEARCH_CHAR_BACKWARD,
    SEARCH_WORD_FORWARD, SEARCH_WORD_BACKWARD,
    MOVE_LINE_DOWN(true), MOVE_LINE_UP(true), INDENT(true), OUTDENT(true), LINE_PERCENT,
    SET_ANCHOR, JUMP_TO_ANCHOR,
    // Built by the engine from the command line (`/foo`, `=bar`), not by the parser.
    SEARCH_FORWARD, SEARCH_BACKWARD, SEARCH_REGEX, APPLY_REPLACE_RULE,
    TEXT_OBJECT_YANK, TEXT_OBJECT_DELETE, TEXT_OBJECT_PASTE,
    TEXT_OBJECT_YANK_AROUND, TEXT_OBJECT_DELETE_AROUND,
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
    const val LINE_MIDDLE = '<'
    const val DOC_MIDDLE_ALIAS = '>'
    const val BLOCK_FIRST_LINE = '-'
    const val BLOCK_LAST_LINE = '_'

    /** Keys whose next typed character is an argument (`rX`), not a key. */
    val CHAR_ARGUMENT_KEYS: Map<Char, Action> = mapOf(
        REPLACE_CHAR to Action.REPLACE_CHAR,
    )

    /** Commands that move the caret; in selection mode (`V`) they extend the selection. */
    val MOTIONS: Set<Action> = setOf(
        Action.LEFT, Action.RIGHT, Action.UP, Action.DOWN, Action.PAGE_UP, Action.PAGE_DOWN,
        Action.LINE_START, Action.LINE_END, Action.WORD_LEFT, Action.WORD_END_RIGHT,
        Action.WORD_END_LEFT, Action.WORD_START_RIGHT, Action.BLOCK_FIRST_LINE, Action.BLOCK_LAST_LINE,
        Action.JUMP_BRACKET_MATCH, Action.GOTO_LINE, Action.GOTO_PERCENT, Action.DOC_END,
        Action.GOTO_LINE_FROM_BOTTOM, Action.SEARCH_NEXT, Action.SEARCH_PREVIOUS, Action.SEARCH_FORWARD,
        Action.SEARCH_BACKWARD, Action.SEARCH_REGEX, Action.SEARCH_WORD_FORWARD, Action.SEARCH_WORD_BACKWARD,
        Action.SEARCH_CHAR_FORWARD, Action.SEARCH_CHAR_BACKWARD, Action.LINE_PERCENT,
    )

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

    /** `"y` / `"x` / `"p` act inside the delimiters; `"Y` / `"X` include them. */
    val TEXT_OBJECT_ACTIONS: Map<Char, Action> = mapOf(
        YANK_LINE to Action.TEXT_OBJECT_YANK,
        DELETE_CHAR to Action.TEXT_OBJECT_DELETE,
        PASTE_BEFORE to Action.TEXT_OBJECT_PASTE,
        'Y' to Action.TEXT_OBJECT_YANK_AROUND,
        'X' to Action.TEXT_OBJECT_DELETE_AROUND,
    )

    /** Commands that `.` repeats (MIV's REPEATABLE_EDIT_ACTIONS plus searches and replace). */
    val REPEATABLE: Set<Action> = setOf(
        Action.DELETE_CHAR, Action.DELETE_WORD, Action.DELETE_TO_LINE_END, Action.DELETE_LINE,
        Action.YANK_LINE, Action.YANK_WORD, Action.REPLACE_WORD, Action.REPLACE_CHAR,
        Action.TOGGLE_CASE_CHAR, Action.TOGGLE_CASE_WORD, Action.OPEN_LINE_BELOW, Action.OPEN_LINE_ABOVE,
        Action.CHANGE_TO_LINE_END, Action.CHANGE_TO_LINE_START, Action.JOIN_LINES,
        Action.TEXT_OBJECT_YANK, Action.TEXT_OBJECT_DELETE, Action.TEXT_OBJECT_PASTE,
        Action.TEXT_OBJECT_YANK_AROUND, Action.TEXT_OBJECT_DELETE_AROUND,
        Action.MOVE_LINE_DOWN, Action.MOVE_LINE_UP, Action.INDENT, Action.OUTDENT,
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
        'V' to Action.TOGGLE_SELECT,
        'f' to Action.SEARCH_CHAR_FORWARD,
        'F' to Action.SEARCH_CHAR_BACKWARD,
        '*' to Action.SEARCH_WORD_FORWARD,
        '#' to Action.SEARCH_WORD_BACKWARD,
        'J' to Action.MOVE_LINE_DOWN,
        'K' to Action.MOVE_LINE_UP,
        'L' to Action.INDENT,
        'H' to Action.OUTDENT,
        'Z' to Action.SET_ANCHOR,
        'z' to Action.JUMP_TO_ANCHOR,
        'i' to Action.INSERT,
        'I' to Action.INSERT_LINE_START,
        'k' to Action.INSERT_LINE_END,
        'o' to Action.OPEN_LINE_BELOW,
        'O' to Action.OPEN_LINE_ABOVE,
    )
}

/** One remappable NAV key. [id] uses MIV's token names (keymaps/default.json). */
data class KeyBinding(val id: String, val defaultKey: Char, val description: String)

/**
 * The NAV keys the user can move (MIV lets you rebind them in VS Code's keyboard
 * shortcuts). A custom key replaces the default one, so the default key does nothing
 * afterwards. Digits and Space are fixed: they are counts, registers and INSERT.
 */
class KeyLayout(overrides: Map<String, Char> = emptyMap()) {
    /** Typed key → the default key the parser understands. */
    private val translation: Map<Char, Char> =
        BINDINGS.associate { (overrides[it.id] ?: it.defaultKey) to it.defaultKey }

    /** The key to parse for a typed [char], or null when it is not bound to anything. */
    fun translate(char: Char): Char? = if (char.isFixed()) char else translation[char]

    companion object {
        // BINDINGS must be initialised before DEFAULT, which is built from it.
        val BINDINGS: List<KeyBinding> = listOf(
            KeyBinding("LEFT", 'a', "Move left"),
            KeyBinding("RIGHT", 'd', "Move right"),
            KeyBinding("UP", 'w', "Move up"),
            KeyBinding("DOWN", 's', "Move down"),
            KeyBinding("PAGE_UP", 'W', "Page up"),
            KeyBinding("PAGE_DOWN", 'S', "Page down"),
            KeyBinding("LINE_START", 'A', "Line start"),
            KeyBinding("LINE_END", 'D', "Line end"),
            KeyBinding("WORD_LEFT", 'q', "Start of previous word"),
            KeyBinding("WORD_RIGHT", 'e', "End of next word"),
            KeyBinding("WORD_END_LEFT", 'Q', "End of previous word"),
            KeyBinding("WORD_END_RIGHT", 'E', "Start of next word"),
            KeyBinding("BLOCK_FIRST_LINE", '-', "First line in block"),
            KeyBinding("BLOCK_LAST_LINE", '_', "Last line in block"),
            KeyBinding("JUMP_BRACKET_MATCH", '%', "Matching bracket"),
            KeyBinding("GOTO_LINE", 'g', "Go to line"),
            KeyBinding("DOC_MIDDLE", 'm', "Go to document percent"),
            KeyBinding("DOC_BOTTOM", 'G', "Go to document bottom"),
            KeyBinding("INSERT", 'i', "Enter INSERT"),
            KeyBinding("INSERT_LINE_START", 'I', "Line start + INSERT"),
            KeyBinding("INSERT_LINE_END", 'k', "Line end + INSERT"),
            KeyBinding("OPEN_LINE_BELOW", 'o', "Open line below"),
            KeyBinding("OPEN_LINE_ABOVE", 'O', "Open line above"),
            KeyBinding("DELETE_CHAR", 'x', "Delete character"),
            KeyBinding("DELETE_WORD", 'X', "Delete word"),
            KeyBinding("DELETE_LINE", 'b', "Delete line"),
            KeyBinding("DELETE_TO_LINE_END", 'B', "Delete to line end"),
            KeyBinding("CHANGE_TO_LINE_END", 'c', "Change to line end"),
            KeyBinding("CHANGE_TO_LINE_START", 'C', "Change from line start"),
            KeyBinding("REPLACE_CHAR", 'r', "Replace character"),
            KeyBinding("REPLACE_WORD", 'R', "Change word"),
            KeyBinding("TOGGLE_CASE_CHAR", '§', "Toggle case of character"),
            KeyBinding("TOGGLE_CASE_WORD", '°', "Toggle case of word"),
            KeyBinding("JOIN_LINE_WITH_NEXT", '&', "Join lines"),
            KeyBinding("YANK_LINE", 'y', "Yank line"),
            KeyBinding("YANK_WORD", 'Y', "Yank word"),
            KeyBinding("PASTE_AFTER", 'p', "Paste before the caret"),
            KeyBinding("PASTE_BEFORE", 'P', "Paste after the caret"),
            KeyBinding("SHOW_REGISTERS", 'v', "Register viewer / store clipboard"),
            KeyBinding("UNDO", 'u', "Undo"),
            KeyBinding("REVERT_TO_SAVED", 'U', "Revert to saved version"),
            KeyBinding("REPEAT_ALIAS", '.', "Repeat"),
            KeyBinding("TOGGLE_SELECT", 'V', "Selection mode"),
            KeyBinding("SEARCH_CHAR_FORWARD", 'f', "Search character under caret forward"),
            KeyBinding("SEARCH_CHAR_BACKWARD", 'F', "Search character under caret backward"),
            KeyBinding("SEARCH_WORD_FORWARD", '*', "Search word under caret forward"),
            KeyBinding("SEARCH_WORD_BACKWARD", '#', "Search word under caret backward"),
            KeyBinding("MOVE_LINE_DOWN", 'J', "Move line down"),
            KeyBinding("MOVE_LINE_UP", 'K', "Move line up"),
            KeyBinding("INDENT_LINES", 'L', "Indent"),
            KeyBinding("OUTDENT_LINES", 'H', "Outdent"),
            KeyBinding("LINE_MIDDLE", '<', "Go to line percent (middle)"),
            KeyBinding("DOC_MIDDLE_ALIAS", '>', "Go to document percent (middle)"),
            KeyBinding("SET_ANCHOR", 'Z', "Set anchor"),
            KeyBinding("JUMP_TO_ANCHOR", 'z', "Jump to anchor and back"),
            KeyBinding("SEARCH_FORWARD", '/', "Search forward"),
            KeyBinding("SEARCH_BACKWARD", '\\', "Search backward"),
            KeyBinding("SEARCH_REGEX", ',', "Regex search"),
            KeyBinding("SEARCH_NEXT", 'n', "Next match"),
            KeyBinding("SEARCH_PREVIOUS", 'N', "Previous match"),
            KeyBinding("REPLACE_MATCHES", '=', "Replace"),
            KeyBinding("TEXT_OBJECT_AUTO", '!', "Text object: automatic"),
            KeyBinding("TEXT_OBJECT_DOUBLE_QUOTE", '"', "Text object: \" \""),
            KeyBinding("TEXT_OBJECT_SINGLE_QUOTE", '\'', "Text object: ' '"),
            KeyBinding("TEXT_OBJECT_BACKTICK", '`', "Text object: ` `"),
            KeyBinding("TEXT_OBJECT_ACUTE", '´', "Text object: ´ ´"),
            KeyBinding("TEXT_OBJECT_PAREN", '(', "Text object: ( )"),
            KeyBinding("TEXT_OBJECT_BRACKET", '[', "Text object: [ ]"),
            KeyBinding("TEXT_OBJECT_BRACE", '{', "Text object: { }"),
        )

        val DEFAULT = KeyLayout()

        private fun Char.isFixed() = this in '0'..'9' || this == ' '

        /** Why [overrides] cannot be used, or null when they are fine. */
        fun validate(overrides: Map<String, Char>): String? {
            val used = mutableMapOf<Char, String>()
            for (binding in BINDINGS) {
                val key = overrides[binding.id] ?: binding.defaultKey
                if (key.isFixed()) return "${binding.id}: digits and Space cannot be used"
                used.put(key, binding.id)?.let { return "'$key' is used by both $it and ${binding.id}" }
            }
            return null
        }
    }
}
