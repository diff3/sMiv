package nu.entropy.smiv

import nu.entropy.smiv.core.KeyLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FEATURES.md is the plugin description (see build.gradle.kts). README.md carries a copy, and
 * FEATURES.sv.md is the same list in Swedish; these tests keep the three in step.
 */
class FeaturesTest {
    private val english = File("FEATURES.md").readText().trimEnd()
    private val swedish = File("FEATURES.sv.md").readText().trimEnd()

    @Test
    fun `README carries FEATURES_md word for word`() {
        val readme = File("README.md").readText()
        val start = readme.indexOf("-->", readme.indexOf("<!-- FEATURES:START")) + "-->".length
        val end = readme.indexOf("<!-- FEATURES:END -->")
        assertEquals(english, readme.substring(start, end).trim())
    }

    @Test
    fun `the Swedish list has the same lines and markup as the English one`() {
        val tags = Regex("</?[a-z0-9]+")
        val englishLines = english.lines()
        val swedishLines = swedish.lines()
        assertEquals(englishLines.size, swedishLines.size)
        englishLines.zip(swedishLines).forEachIndexed { index, (en, sv) ->
            assertEquals("line ${index + 1}", tags.findAll(en).map { it.value }.toList(), tags.findAll(sv).map { it.value }.toList())
        }
    }

    @Test
    fun `every default NAV key is in the list`() {
        val keys = Regex("<code>(.*?)</code>").findAll(english)
            .flatMap { it.groupValues[1].unescapeHtml().split(' ') }
            .filter { it.isNotEmpty() }
            .toList()
        for (binding in KeyLayout.BINDINGS) {
            val key = binding.defaultKey
            assertTrue("${binding.id} ($key) is missing", keys.any { it.first() == key || it.last() == key })
        }
    }

    private fun String.unescapeHtml() = replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
