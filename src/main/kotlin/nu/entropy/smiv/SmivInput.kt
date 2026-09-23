package nu.entropy.smiv

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx
import nu.entropy.smiv.core.Mode

private fun interceptsNav(editor: Editor): Boolean {
    val service = SmivService.get()
    return service.isActiveIn(editor) && service.mode == Mode.NAV
}

/** Raw typed handler: in NAV every typed char is a command, in INSERT it types normally. */
class SmivTypedHandler(private val original: TypedActionHandler) : TypedActionHandlerEx {
    override fun beforeExecute(editor: Editor, c: Char, context: DataContext, plan: ActionPlan) {
        if (!interceptsNav(editor)) (original as? TypedActionHandlerEx)?.beforeExecute(editor, c, context, plan)
    }

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        if (interceptsNav(editor)) {
            SmivService.get().handleTyped(editor, charTyped, dataContext)
        } else {
            original.execute(editor, charTyped, dataContext)
        }
    }
}

/**
 * ESC: let PhpStorm close lookups, popups, the search bar and extra carets first,
 * then switch to NAV.
 */
class SmivEscapeHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        SmivService.get().isActiveIn(editor) || original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        if (original.isEnabled(editor, caret, dataContext)) original.execute(editor, caret, dataContext)
        if (SmivService.get().isActiveIn(editor)) SmivService.get().toNav()
    }
}

/** Enter does nothing in NAV (MIV behaviour); lookups handle Enter before this runs. */
class SmivEnterHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        interceptsNav(editor) || original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        if (!interceptsNav(editor)) original.execute(editor, caret, dataContext)
    }
}
