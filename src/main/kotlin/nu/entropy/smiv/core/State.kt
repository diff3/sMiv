package nu.entropy.smiv.core

enum class Mode { NAV, INSERT }

data class Register(val text: String = "", val linewise: Boolean = false)

/** Session state shared by all editors, like MIV's single global state. */
class SmivState {
    var mode: Mode = Mode.NAV

    /** Keys typed so far for the current NAV command, shown in the status bar. */
    val pending = StringBuilder()

    /** Registers 0..8. Register 9 is the system clipboard itself, see [Engine]. */
    val registers: Array<Register> = Array(CLIPBOARD_REGISTER) { Register() }

    companion object {
        const val YANK_REGISTER = 0
        const val DELETE_REGISTER = 8
        const val CLIPBOARD_REGISTER = 9
    }
}
