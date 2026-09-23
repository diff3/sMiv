package nu.entropy.smiv

import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nu.entropy.smiv.core.Mode

/** Types keys into a real (headless) editor, through the same handlers as in PhpStorm. */
class SmivIdeTest : BasePlatformTestCase() {
    private fun type(keys: String) {
        val editor = myFixture.editor
        // The fixture's editor is not a MAIN_EDITOR, so feed the keys to sMiv directly.
        for (key in keys) SmivService.get().handleTyped(editor, key, EditorUtil.getEditorDataContext(editor))
    }

    fun testLessThanGoesToTheMiddleOfTheLineAndGreaterThanToTheMiddleOfTheDocument() {
        myFixture.configureByText("a.txt", (0..100).joinToString("\n") { if (it == 10) "    abcdef" else "line $it" })
        val editor = myFixture.editor
        SmivService.get().toNav(editor)
        assertEquals(Mode.NAV, SmivService.get().mode)
        val textBefore = editor.document.text

        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(10))
        type("<")
        assertEquals("< must not be inserted", textBefore, editor.document.text)
        assertEquals(editor.document.getLineStartOffset(10) + 7, editor.caretModel.offset)

        type(">")
        assertEquals(50, editor.caretModel.logicalPosition.line)
    }
}
