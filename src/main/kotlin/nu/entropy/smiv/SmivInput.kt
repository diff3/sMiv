package nu.entropy.smiv

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx
import com.intellij.openapi.project.DumbAware
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
        if (SmivService.get().isActiveIn(editor)) SmivService.get().toNav(editor)
    }
}

/**
 * sMiv is a layer on top of the editor: in NAV only typed characters are taken over.
 * Enter works as usual, except that it commits the command line (`/foo`, `=bar`),
 * cancels a half-typed command, or applies an active replace rule to the current match.
 */
class SmivEnterHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    private fun handles(editor: Editor) = interceptsNav(editor) && SmivService.get().handlesEnter

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        handles(editor) || original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        if (handles(editor)) {
            SmivService.get().handleEnter(editor, dataContext ?: DataContext.EMPTY_CONTEXT)
        } else {
            original.execute(editor, caret, dataContext)
        }
    }
}

/**
 * Arrow keys in selection mode (`V`) extend the selection like `w a s d`; otherwise
 * they work as usual.
 */
class SmivArrowHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val service = SmivService.get()
        val anchor = service.engine.state.selectAnchor
        if (anchor == null || !interceptsNav(editor)) {
            original.execute(editor, caret, dataContext)
            return
        }
        // Move from the caret (with a selection the arrow would only collapse it), then select again.
        val primary = editor.caretModel.primaryCaret
        primary.removeSelection()
        original.execute(editor, primary, dataContext)
        SmivEffects.selectFrom(editor, anchor)
    }
}

/** Backspace edits the command line while one is open, otherwise it works as usual. */
class SmivBackspaceHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    private fun handles(editor: Editor) = interceptsNav(editor) && SmivService.get().isCommandLineActive

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        handles(editor) || original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        if (handles(editor)) SmivService.get().handleBackspace() else original.execute(editor, caret, dataContext)
    }
}

/** NAV-only shortcut actions. Disabled outside NAV so the key types normally (e.g. Option+Q → œ). */
abstract class SmivNavAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && interceptsNav(editor)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        if (interceptsNav(editor)) perform(editor, e)
    }

    protected abstract fun perform(editor: Editor, e: AnActionEvent)
}

/** Alt+Q */
class SmivParagraphBackwardAction : SmivNavAction() {
    override fun perform(editor: Editor, e: AnActionEvent) = SmivService.get().paragraph(editor, e.dataContext, forward = false)
}

/** Alt+E */
class SmivParagraphForwardAction : SmivNavAction() {
    override fun perform(editor: Editor, e: AnActionEvent) = SmivService.get().paragraph(editor, e.dataContext, forward = true)
}

/** Runs a platform action, like MIV's `miv.executeBuiltin`. */
abstract class SmivDelegateAction(private val actionId: String) : SmivNavAction() {
    override fun perform(editor: Editor, e: AnActionEvent) {
        SmivService.get().cancelPending()
        val action = ActionManager.getInstance().getAction(actionId) ?: return
        ActionManager.getInstance().tryToExecute(action, e.inputEvent, editor.contentComponent, e.place, true)
    }
}

/** Alt+Z: set the anchor. */
class SmivSetAnchorAction : SmivNavAction() {
    override fun perform(editor: Editor, e: AnActionEvent) = SmivService.get().setAnchor(editor)
}

/** Alt+X: jump to the anchor, then toggle between it and where you jumped from. */
class SmivJumpToAnchorAction : SmivNavAction() {
    override fun perform(editor: Editor, e: AnActionEvent) = SmivService.get().jumpToAnchor(editor)
}

/** Alt+A: navigate back. */
class SmivNavigateBackAction : SmivDelegateAction(IdeActions.ACTION_GOTO_BACK)

/** Alt+D: navigate forward. */
class SmivNavigateForwardAction : SmivDelegateAction(IdeActions.ACTION_GOTO_FORWARD)

/** Ctrl+F: the editor's find bar, as MIV binds it in NAV. */
class SmivFindAction : SmivDelegateAction(IdeActions.ACTION_FIND)
