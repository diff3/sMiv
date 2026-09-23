package nu.entropy.smiv.core

enum class Mode { NAV, INSERT }

data class Register(val text: String = "", val linewise: Boolean = false)

/** Session state shared by all editors, like MIV's single global state. */
class SmivState {
    var mode: Mode = Mode.NAV

    /** Keys typed so far for the current NAV command, shown in the status bar. */
    val pending = StringBuilder()

    /** Registers 0..9. MVP writes 0 (yank) and 8 (delete); reading them comes in phase 2. */
    val registers: Array<Register> = Array(10) { Register() }

    fun store(register: Int, text: String, linewise: Boolean) {
        registers[register] = Register(text, linewise)
    }

    companion object {
        const val YANK_REGISTER = 0
        const val DELETE_REGISTER = 8
    }
}
