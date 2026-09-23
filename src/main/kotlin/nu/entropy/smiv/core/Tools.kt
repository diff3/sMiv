package nu.entropy.smiv.core

/**
 * Alt+Z / Alt+X (port of MIV's anchor commands). [same] decides whether two positions
 * are the same place.
 */
class Anchors<T>(private val same: (T, T) -> Boolean) {
    var anchor: T? = null
        private set
    private var jumpedFrom: T? = null

    /** Alt+Z: set the anchor and forget where we jumped from. */
    fun set(current: T) {
        anchor = current
        jumpedFrom = null
    }

    /**
     * Alt+X: where to go from [current]. Away from the anchor it goes to the anchor
     * (remembering [current]); on the anchor it goes back to where we jumped from.
     */
    fun jump(current: T?): T? {
        val target = anchor ?: return null
        val atAnchor = current != null && same(current, target)
        if (atAnchor) return jumpedFrom ?: target
        if (current != null) jumpedFrom = current
        return target
    }
}

/** Usage counts per action and key sequence, shown from the sMiv menu (MIV's command stats). */
class CommandStats {
    private val actions = mutableMapOf<String, Int>()
    private val sequences = mutableMapOf<String, Int>()

    fun record(command: Command) {
        actions.merge(command.action.name, 1, Int::plus)
        sequences.merge(command.sequence, 1, Int::plus)
    }

    fun format(): String =
        (section("sMiv Command Usage", actions) + "" + section("Key Sequences", sequences)).joinToString("\n")

    private fun section(title: String, counts: Map<String, Int>): List<String> {
        val lines = mutableListOf("=== $title ===", "")
        if (counts.isEmpty()) return lines + "(no data)"
        val width = counts.keys.maxOf { it.length }
        counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .forEach { (key, count) -> lines += key.padEnd(width + 2) + count }
        return lines
    }
}

/** One line of register text for the register viewer, cut to [max] characters (MIV's toPreview). */
fun registerPreview(text: String, max: Int = 20): String {
    val singleLine = text.replace(Regex("\\s+"), " ").trim()
    return if (singleLine.length <= max) singleLine else singleLine.take(max) + "..."
}
