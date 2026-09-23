package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLayoutTest {
    @Test
    fun `every key the parser knows can be remapped and defaults do not clash`() {
        val defaults = KeyLayout.BINDINGS.map { it.defaultKey }.toSet()
        val parserKeys = Keys.SINGLE_KEY_ACTIONS.keys + Keys.COMMAND_LINE_KEYS.keys + Keys.TEXT_OBJECT_KEYS.toSet() +
            Keys.CHAR_ARGUMENT_KEYS.keys +
            setOf(Keys.LINE_MIDDLE, Keys.DOC_MIDDLE_ALIAS, Keys.REPLACE_CHAR, Keys.PASTE_BEFORE, Keys.PASTE_AFTER, Keys.STORE_REGISTER, Keys.GOTO_LINE, Keys.DOC_MIDDLE, Keys.DOC_BOTTOM)
        assertEquals(parserKeys, defaults)
        assertNull(KeyLayout.validate(emptyMap()))
    }

    @Test
    fun `default layout keeps keys, digits and space`() {
        val layout = KeyLayout.DEFAULT
        assertEquals('a', layout.translate('a'))
        assertEquals('7', layout.translate('7'))
        assertEquals(' ', layout.translate(' '))
        assertNull(layout.translate('j'))
    }

    @Test
    fun `a custom key replaces the default one`() {
        val layout = KeyLayout(mapOf("LEFT" to 'h'))
        assertEquals('a', layout.translate('h'))
        assertNull(layout.translate('a'))
    }

    @Test
    fun `validation rejects clashes, digits and space`() {
        assertEquals("'d' is used by both LEFT and RIGHT", KeyLayout.validate(mapOf("LEFT" to 'd')))
        assertNull(KeyLayout.validate(mapOf("LEFT" to 'd', "RIGHT" to 'a')))
        assertEquals("LEFT: digits and Space cannot be used", KeyLayout.validate(mapOf("LEFT" to '1')))
        assertEquals("LEFT: digits and Space cannot be used", KeyLayout.validate(mapOf("LEFT" to ' ')))
    }
}
