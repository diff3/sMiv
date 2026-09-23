package nu.entropy.smiv

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ide.CopyPasteManager
import nu.entropy.smiv.core.Effect
import nu.entropy.smiv.core.IdeOp
import java.awt.datatransfer.StringSelection

/** Runs engine effects against a real editor. NAV commands only touch the primary caret. */
object SmivEffects {
    private const val COMMAND_NAME = "sMiv"

    private val ACTION_IDS = mapOf(
        IdeOp.LEFT to IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT,
        IdeOp.RIGHT to IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT,
        IdeOp.UP to IdeActions.ACTION_EDITOR_MOVE_CARET_UP,
        IdeOp.DOWN to IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN,
        IdeOp.PAGE_UP to IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP,
        IdeOp.PAGE_DOWN to IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN,
        IdeOp.LINE_START to IdeActions.ACTION_EDITOR_MOVE_LINE_START,
        IdeOp.LINE_END to IdeActions.ACTION_EDITOR_MOVE_LINE_END,
        IdeOp.NEW_LINE_BELOW to IdeActions.ACTION_EDITOR_START_NEW_LINE,
        IdeOp.NEW_LINE_ABOVE to "EditorStartNewLineBefore",
        IdeOp.JOIN_LINES to IdeActions.ACTION_EDITOR_JOIN_LINES,
    )

    private val WRITE_OPS = setOf(IdeOp.NEW_LINE_BELOW, IdeOp.NEW_LINE_ABOVE, IdeOp.JOIN_LINES)

    fun apply(editor: Editor, dataContext: DataContext, effects: List<Effect>) {
        for (effect in effects) {
            when (effect) {
                is Effect.Replace -> replace(editor, effect)
                is Effect.MoveCaret -> moveCaret(editor, effect.offset)
                is Effect.Ide -> runIdeOp(editor, dataContext, effect)
                is Effect.SetClipboard -> CopyPasteManager.getInstance().setContents(StringSelection(effect.text))
                is Effect.Message -> Unit
            }
        }
    }

    private fun replace(editor: Editor, effect: Effect.Replace) {
        val project = editor.project ?: return
        val document = editor.document
        if (!document.isWritable) return
        WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME, null, {
            document.replaceString(effect.start, effect.end, effect.text)
        })
    }

    private fun moveCaret(editor: Editor, offset: Int) {
        val caret = editor.caretModel.primaryCaret
        caret.removeSelection()
        caret.moveToOffset(offset.coerceIn(0, editor.document.textLength))
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun runIdeOp(editor: Editor, dataContext: DataContext, effect: Effect.Ide) {
        if (effect.op == IdeOp.UNDO) {
            // Typing may run inside the editor's own command, and undo is not allowed there.
            ApplicationManager.getApplication().invokeLater({
                if (!editor.isDisposed) repeat(effect.times) { undo(editor) }
            }, ModalityState.stateForComponent(editor.contentComponent))
            return
        }

        val actionId = ACTION_IDS[effect.op] ?: return
        val handler = EditorActionManager.getInstance().getActionHandler(actionId)
        val caret = editor.caretModel.primaryCaret
        val writes = effect.op in WRITE_OPS
        repeat(effect.times) {
            if (writes) {
                if (!editor.document.isWritable) return
                CommandProcessor.getInstance().executeCommand(editor.project, {
                    handler.execute(editor, caret, dataContext)
                }, COMMAND_NAME, null, editor.document)
            } else {
                handler.execute(editor, caret, dataContext)
            }
        }
    }

    private fun undo(editor: Editor) {
        val project = editor.project ?: return
        val fileEditor = FileEditorManagerEx.getInstanceEx(project).allEditors
            .filterIsInstance<TextEditor>()
            .firstOrNull { it.editor == editor }
        val undoManager = UndoManager.getInstance(project)
        if (undoManager.isUndoAvailable(fileEditor)) undoManager.undo(fileEditor)
    }
}
