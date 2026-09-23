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
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import nu.entropy.smiv.core.Effect
import nu.entropy.smiv.core.IdeOp
import nu.entropy.smiv.core.TextOps
import java.awt.Color
import java.awt.Point
import java.awt.datatransfer.StringSelection
import javax.swing.Timer

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
        IdeOp.MOVE_LINE_DOWN to IdeActions.ACTION_MOVE_LINE_DOWN_ACTION,
        IdeOp.MOVE_LINE_UP to IdeActions.ACTION_MOVE_LINE_UP_ACTION,
        IdeOp.INDENT to "EditorIndentLineOrSelection",
        IdeOp.OUTDENT to IdeActions.ACTION_EDITOR_UNINDENT_SELECTION,
    )

    private val WRITE_OPS = setOf(
        IdeOp.NEW_LINE_BELOW, IdeOp.NEW_LINE_ABOVE, IdeOp.JOIN_LINES,
        IdeOp.MOVE_LINE_DOWN, IdeOp.MOVE_LINE_UP, IdeOp.INDENT, IdeOp.OUTDENT,
    )

    /** Runs [effects] in order; returns status messages produced while doing so. */
    fun apply(editor: Editor, dataContext: DataContext, effects: List<Effect>): List<String> {
        val messages = mutableListOf<String>()
        for (effect in effects) {
            if (effect is Effect.Ide && effect.op == IdeOp.REVERT_TO_SAVED) {
                messages += revertToSaved(editor)
                continue
            }
            if (effect is Effect.Ide && effect.op == IdeOp.SET_ANCHOR) {
                SmivService.get().setAnchor(editor)
                messages += "anchor set"
                continue
            }
            if (effect is Effect.Ide && effect.op == IdeOp.JUMP_TO_ANCHOR) {
                SmivService.get().jumpToAnchor(editor)
                continue
            }
            when (effect) {
                is Effect.Replace -> replace(editor, effect)
                is Effect.MoveCaret -> moveCaret(editor, effect.offset)
                is Effect.Ide -> runIdeOp(editor, dataContext, effect)
                is Effect.SetClipboard -> CopyPasteManager.getInstance().setContents(StringSelection(effect.text))
                is Effect.Highlight -> highlight(editor, effect)
                is Effect.Flash -> flash(editor, effect)
                is Effect.ShowRegisters -> SmivPopups.showRegisters(editor, effect.registers)
                is Effect.Message -> Unit
            }
        }
        return messages
    }

    private const val MAX_HIGHLIGHTS = 10_000
    private const val FLASH_MS = 200
    private val FLASH_ATTRIBUTES = TextAttributes().apply { backgroundColor = JBColor(Color(180, 200, 255), Color(60, 80, 120)) }

    private var highlighted: Pair<Editor, List<RangeHighlighter>>? = null
    private var lastHighlight: Pair<Editor, Effect.Highlight>? = null

    /** Search highlighting can be switched off from the sMiv menu; matches are still searched. */
    var highlightEnabled = true
        private set

    fun toggleHighlight(): Boolean {
        highlightEnabled = !highlightEnabled
        val last = lastHighlight
        clearHighlights()
        if (highlightEnabled && last != null && !last.first.isDisposed) highlight(last.first, last.second)
        return highlightEnabled
    }

    /** Search matches in the editor's search colours; the current match like a selected result. */
    private fun highlight(editor: Editor, effect: Effect.Highlight) {
        clearHighlights()
        lastHighlight = if (effect.matches.isEmpty()) null else editor to effect
        if (effect.matches.isEmpty() || !highlightEnabled) return

        val markup = editor.markupModel
        val length = editor.document.textLength
        val highlighters = effect.matches.take(MAX_HIGHLIGHTS).mapIndexed { index, match ->
            val start = match.start.coerceIn(0, length)
            // Empty regex matches still get a visible one-character mark.
            val end = maxOf(match.end, match.start + 1).coerceIn(start, length)
            val key = if (index == effect.current) EditorColors.SEARCH_RESULT_ATTRIBUTES else EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES
            markup.addRangeHighlighter(key, start, end, HighlighterLayer.SELECTION - 1, HighlighterTargetArea.EXACT_RANGE)
        }
        highlighted = editor to highlighters
    }

    private fun flash(editor: Editor, effect: Effect.Flash) {
        val length = editor.document.textLength
        val start = effect.start.coerceIn(0, length)
        val end = effect.end.coerceIn(start, length)
        if (start == end) return
        val highlighter = editor.markupModel.addRangeHighlighter(
            start, end, HighlighterLayer.SELECTION - 1, FLASH_ATTRIBUTES, HighlighterTargetArea.EXACT_RANGE,
        )
        Timer(FLASH_MS) { if (!editor.isDisposed) editor.markupModel.removeHighlighter(highlighter) }
            .apply { isRepeats = false }
            .start()
    }

    fun clearHighlights() {
        highlighted?.let { (editor, highlighters) ->
            if (!editor.isDisposed) highlighters.forEach(editor.markupModel::removeHighlighter)
        }
        highlighted = null
    }

    /**
     * `U`: replace the text with the last saved version as one undoable change, so `u`
     * brings the edits back. Only the differing part is replaced, so the caret stays put.
     */
    private fun revertToSaved(editor: Editor): String {
        val project = editor.project ?: return "no saved version"
        val document = editor.document
        val fileDocumentManager = FileDocumentManager.getInstance()
        val file = fileDocumentManager.getFile(document) ?: return "no saved version"
        if (!fileDocumentManager.isDocumentUnsaved(document)) return "no unsaved changes"
        if (!document.isWritable) return "file is read-only"

        val change = TextOps.minimalReplacement(document.immutableCharSequence, LoadTextUtil.loadText(file))
            ?: return "no unsaved changes"
        WriteCommandAction.runWriteCommandAction(project, "sMiv: Revert to Saved", null, {
            document.replaceString(change.start, change.end, change.text)
        })
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        return "reverted to saved version"
    }

    private fun replace(editor: Editor, effect: Effect.Replace) {
        val project = editor.project ?: return
        val document = editor.document
        if (!document.isWritable) return
        WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME, null, {
            document.replaceString(effect.start, effect.end, effect.text)
        })
    }

    /**
     * `|`: put the caret line in the middle of the view with PhpStorm's own Scroll to
     * Center. Near the top of a file the view cannot scroll far enough to centre.
     */
    private fun centerCaretLine(editor: Editor, dataContext: DataContext) {
        EditorActionManager.getInstance().getActionHandler("EditorScrollToCenter")?.let {
            it.execute(editor, editor.caretModel.primaryCaret, dataContext)
            return
        }
        val scrolling = editor.scrollingModel
        val caretY = editor.visualPositionToXY(editor.caretModel.visualPosition).y
        val target = caretY - (scrolling.visibleArea.height - editor.lineHeight) / 2
        scrolling.disableAnimation()
        scrolling.scrollVertically(maxOf(0, target))
        scrolling.enableAnimation()
    }

    /** Selection mode: select from [anchor] to the caret after a motion. */
    fun selectFrom(editor: Editor, anchor: Int) {
        val caret = editor.caretModel.primaryCaret
        val start = anchor.coerceIn(0, editor.document.textLength)
        caret.setSelection(minOf(start, caret.offset), maxOf(start, caret.offset))
    }

    private fun moveCaret(editor: Editor, offset: Int) {
        val caret = editor.caretModel.primaryCaret
        caret.removeSelection()
        caret.moveToOffset(offset.coerceIn(0, editor.document.textLength))
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun runIdeOp(editor: Editor, dataContext: DataContext, effect: Effect.Ide) {
        if (effect.op == IdeOp.CENTER_LINE) {
            centerCaretLine(editor, dataContext)
            return
        }
        if (effect.op == IdeOp.UNDO) {
            // Typing may run inside the editor's own command, and undo is not allowed there.
            ApplicationManager.getApplication().invokeLater({
                if (!editor.isDisposed) repeat(effect.times) { undo(editor) }
            }, ModalityState.stateForComponent(editor.contentComponent))
            return
        }

        val actionId = ACTION_IDS[effect.op] ?: return
        val handler = EditorActionManager.getInstance().getActionHandler(actionId) ?: return
        val caret = editor.caretModel.primaryCaret
        val writes = effect.op in WRITE_OPS
        repeat(effect.times) {
            if (writes) {
                if (!editor.document.isWritable) return
                CommandProcessor.getInstance().executeCommand(editor.project, {
                    handler.execute(editor, caret, dataContext)
                }, COMMAND_NAME, null, editor.document)
            } else {
                // With a selection, IntelliJ's arrow actions only collapse it; move from the caret instead.
                caret.removeSelection()
                handler.execute(editor, caret, dataContext)
            }
        }
    }

    /**
     * `u`: undo the last change but keep the caret and the scroll position where they
     * are (adjusted for the undone text), instead of jumping to the change.
     */
    private fun undo(editor: Editor) {
        val project = editor.project ?: return
        val fileEditor = FileEditorManagerEx.getInstanceEx(project).allEditors
            .filterIsInstance<TextEditor>()
            .firstOrNull { it.editor == editor }
        val undoManager = UndoManager.getInstance(project)
        val document = editor.document
        val scrolling = editor.scrollingModel

        val topOffset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(Point(0, scrolling.verticalScrollOffset)))
        val topRemainder = scrolling.verticalScrollOffset - editor.offsetToXY(topOffset).y
        val horizontal = scrolling.horizontalScrollOffset
        // Greedy to the left: text re-inserted right at the caret ends up after it, so the caret sits on it.
        val caretMarker = document.createRangeMarker(editor.caretModel.offset, editor.caretModel.offset)
            .apply { isGreedyToLeft = true }
        val topMarker = document.createRangeMarker(topOffset, topOffset)

        try {
            // Undo until the text actually changes (skips undo steps that only restore the caret).
            val stamp = document.modificationStamp
            var attempts = 0
            while (attempts++ < 2 && document.modificationStamp == stamp && undoManager.isUndoAvailable(fileEditor)) {
                undoManager.undo(fileEditor)
            }

            val caret = editor.caretModel.primaryCaret
            caret.removeSelection()
            caret.moveToOffset(caretMarker.startOffset.coerceIn(0, document.textLength))
            scrolling.disableAnimation()
            scrolling.scrollVertically(editor.offsetToXY(topMarker.startOffset).y + topRemainder)
            scrolling.scrollHorizontally(horizontal)
            scrolling.enableAnimation()
        } finally {
            caretMarker.dispose()
            topMarker.dispose()
        }
    }
}
