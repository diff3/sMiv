package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolsTest {
    private val anchors = Anchors<Int> { a, b -> a == b }

    @Test
    fun `no anchor means no jump`() {
        assertEquals(null, anchors.jump(5))
    }

    @Test
    fun `jump goes to the anchor and toggles back to where it came from`() {
        anchors.set(10)
        assertEquals(10, anchors.jump(40))
        assertEquals(40, anchors.jump(10))
        assertEquals(10, anchors.jump(40))
    }

    @Test
    fun `on the anchor without a previous jump stays on the anchor`() {
        anchors.set(10)
        assertEquals(10, anchors.jump(10))
    }

    @Test
    fun `setting the anchor forgets the previous jump`() {
        anchors.set(10)
        anchors.jump(40)
        anchors.set(20)
        assertEquals(20, anchors.jump(20))
    }

    @Test
    fun `stats list the most used first`() {
        val stats = CommandStats()
        stats.record(Command(Action.DOWN, sequence = "s"))
        stats.record(Command(Action.DOWN, sequence = "3s"))
        stats.record(Command(Action.UNDO, sequence = "u"))
        assertEquals(
            listOf(
                "=== sMiv Command Usage ===", "", "DOWN  2", "UNDO  1", "",
                "=== Key Sequences ===", "", "3s  1", "s   1", "u   1",
            ).joinToString("\n"),
            stats.format(),
        )
        assertEquals("=== sMiv Command Usage ===\n\n(no data)\n\n=== Key Sequences ===\n\n(no data)", CommandStats().format())
    }

    @Test
    fun `register preview is one short line`() {
        assertEquals("a b c", registerPreview("  a\n b\tc  "))
        assertEquals("12345678901234567890...", registerPreview("1234567890123456789012345"))
    }
}
