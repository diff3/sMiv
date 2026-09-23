package nu.entropy.smiv

import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nu.entropy.smiv.core.Mode

/** Types keys into a real (headless) editor, through the same handlers as in PhpStorm. */
class SmivIdeTest : BasePlatformTestCase() {
    fun testLessThanCentresTheCaretLine() {
        myFixture.configureByText("a.txt", (1..300).joinToString("\n") { "line $it" })
        val editor = myFixture.editor
        EditorTestUtil.setEditorVisibleSize(editor, 80, 20)
        SmivService.get().toNav(editor)
        assertEquals(Mode.NAV, SmivService.get().mode)

        // Caret on line 100, away from the middle of the view.
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(100))
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        val before = editor.scrollingModel.verticalScrollOffset
        val textBefore = editor.document.text

        // The fixture's editor is not a MAIN_EDITOR, so feed the key to sMiv directly.
        SmivService.get().handleTyped(editor, '<', EditorUtil.getEditorDataContext(editor))

        assertEquals("< must not be inserted", textBefore, editor.document.text)
        val after = editor.scrollingModel.verticalScrollOffset
        val caretY = editor.visualPositionToXY(editor.caretModel.visualPosition).y
        val middle = after + editor.scrollingModel.visibleArea.height / 2
        assertTrue("view should scroll: before=$before after=$after", after != before)
        assertTrue("caret line should be near the middle: caretY=$caretY middle=$middle",
            Math.abs(caretY - middle) <= 2 * editor.lineHeight)
    }
}
