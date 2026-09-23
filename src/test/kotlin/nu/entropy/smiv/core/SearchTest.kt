package nu.entropy.smiv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    @Test
    fun `literal matches do not overlap`() {
        assertEquals(listOf(Match(0, 2), Match(2, 4)), Search.findMatches("aaaa", "aa", regex = false))
    }

    @Test
    fun `invalid regex is reported as null`() {
        assertEquals(null, Search.findMatches("abc", "(", regex = true))
    }

    @Test
    fun `javascript replacement syntax is converted`() {
        assertEquals("$2-$1", Search.javaReplacement("$2-$1"))
        assertEquals("$0!", Search.javaReplacement("$&!"))
        assertEquals("\\$5", Search.javaReplacement("$$5"))
        assertEquals("a\\\\b", Search.javaReplacement("a\\b"))
        assertEquals("\${year}", Search.javaReplacement("$<year>"))
    }

    @Test
    fun `replacement for one regex match expands groups in context`() {
        val rule = ReplaceRule("(\\w)(\\d)", "$2$1", regex = true)
        assertEquals("1a", Search.replacementFor("x a1 b2", rule, Match(2, 4)))
    }

    @Test
    fun `replace rule parts`() {
        assertEquals(listOf("foo", "bar"), Search.parseReplaceRuleParts("foo bar"))
        assertEquals(listOf("a b", "c"), Search.parseReplaceRuleParts("'a b' c"))
        assertEquals(listOf("x"), Search.parseReplaceRuleParts("x"))
        assertEquals(null, Search.parseReplaceRuleParts("a b c"))
        assertEquals(null, Search.parseReplaceRuleParts("'open"))
    }

    @Test
    fun `text object bounds match MIV`() {
        val text = "{\"hello\"}"
        assertEquals(1 to 7, TextOps.findTextObjectBounds(text, 2, '!'))
        assertEquals(0 to 8, TextOps.findTextObjectBounds(text, 0, '!'))
        assertEquals(0 to 8, TextOps.findTextObjectBounds(text, 8, '!'))
        assertEquals(1 to 7, TextOps.findTextObjectBounds(text, 3, '"'))
        assertEquals(null, TextOps.findTextObjectBounds("abc", 1, '('))
        // The escaped quote is skipped when looking for the closing one.
        assertEquals(0 to 4, TextOps.findTextObjectBounds("\"a\\\"\"", 2, '"'))
    }
}
