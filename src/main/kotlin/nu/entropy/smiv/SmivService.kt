package nu.entropy.smiv

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import nu.entropy.smiv.core.Action
import nu.entropy.smiv.core.Anchors
import nu.entropy.smiv.core.Command
import nu.entropy.smiv.core.Effect
import nu.entropy.smiv.core.Engine
import nu.entropy.smiv.core.KeyLayout
import nu.entropy.smiv.core.Mode
import nu.entropy.smiv.core.TextView
import java.awt.datatransfer.DataFlavor

/** Application-wide sMiv runtime: one mode for all editors, as in MIV. */
@Service(Service.Level.APP)
class SmivService : Disposable {
    val engine = Engine().apply { layout = KeyLayout(SmivSettings.get().overrides) }

    /** False after the user turns sMiv off (IdeaVim warning); everything then passes through. */
    var enabled = true
        set(value) {
            field = value
            if (!value) {
                engine.escape()
                SmivEffects.clearHighlights()
            }
            refresh()
        }

    private var message: String? = null
    private val messageAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    val mode: Mode get() = engine.state.mode

    val handlesEnter: Boolean get() = engine.handlesEnter

    val isCommandLineActive: Boolean get() = engine.isCommandLineActive

    /** sMiv only drives real code editors, not consoles, commit messages or dialog fields. */
    fun isActiveIn(editor: Editor): Boolean =
        enabled && editor.project != null && editor.editorKind == EditorKind.MAIN_EDITOR

    fun statusText(): String {
        val base = if (engine.isSelecting) "sMiv SELECT" else "sMiv ${mode.name}"
        val extra = engine.commandLine.ifEmpty { message.orEmpty() }
        return if (extra.isEmpty()) base else "$base  $extra"
    }

    fun handleTyped(editor: Editor, char: Char, dataContext: DataContext) =
        run(editor, dataContext) { view -> engine.type(char, view, ::readClipboard) }

    fun paragraph(editor: Editor, dataContext: DataContext, forward: Boolean) =
        run(editor, dataContext) { view -> engine.paragraph(view, forward) }

    fun handleEnter(editor: Editor, dataContext: DataContext) =
        run(editor, dataContext) { view -> engine.enter(view, ::readClipboard).orEmpty() }

    fun handleBackspace() {
        engine.backspace()
        refresh()
    }

    /** Snapshot the primary caret, let the engine decide, then apply its effects. */
    private fun run(editor: Editor, dataContext: DataContext, command: (TextView) -> List<Effect>) {
        val caret = editor.caretModel.primaryCaret
        val view = TextView(editor.document.immutableCharSequence, caret.offset, caret.selectionStart, caret.selectionEnd)
        val effects = command(view)
        val messages = effects.filterIsInstance<Effect.Message>().map { it.text } +
            SmivEffects.apply(editor, dataContext, effects.filterNot { it is Effect.Message })
        engine.state.selectAnchor?.let { SmivEffects.selectFrom(editor, it) }
        messages.lastOrNull()?.let(::showMessage)
        refresh()
    }

    fun toNav(editor: Editor) = run(editor, DataContext.EMPTY_CONTEXT) { engine.escape() }

    /** Register viewer from `v` or the sMiv menu. */
    fun showRegisters(editor: Editor) = run(editor, DataContext.EMPTY_CONTEXT) { view ->
        engine.execute(Command(Action.SHOW_REGISTERS, sequence = "v"), view, ::readClipboard)
    }

    /** A register chosen in the register viewer is pasted before the caret. */
    fun pasteRegister(editor: Editor, register: Int) = run(editor, DataContext.EMPTY_CONTEXT) { view ->
        engine.execute(Command(Action.PASTE_BEFORE, sequence = "${register}p", register = register), view, ::readClipboard)
    }

    fun toggleSearchHighlight() {
        showMessage(if (SmivEffects.toggleHighlight()) "search highlight enabled" else "search highlight disabled")
        refresh()
    }

    /** A place for Alt+Z / Alt+X; the range marker follows edits to the document. */
    class AnchorPoint(val file: VirtualFile, val marker: RangeMarker)

    private val anchors = Anchors<AnchorPoint> { a, b ->
        a.file == b.file && a.marker.isValid && b.marker.isValid && a.marker.startOffset == b.marker.startOffset
    }

    private fun anchorPoint(editor: Editor): AnchorPoint? {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val offset = editor.caretModel.offset
        return AnchorPoint(file, editor.document.createRangeMarker(offset, offset))
    }

    /** `z` / Alt+Z */
    fun setAnchor(editor: Editor) {
        anchors.set(anchorPoint(editor) ?: return)
    }

    /** `Z` / Alt+X: to the anchor, or from the anchor back to where we jumped from (also across files). */
    fun jumpToAnchor(editor: Editor) {
        val project = editor.project ?: return
        val target = anchors.jump(anchorPoint(editor)) ?: return
        if (!target.marker.isValid || !target.file.isValid) return
        FileEditorManager.getInstance(project)
            .openTextEditor(OpenFileDescriptor(project, target.file, target.marker.startOffset), true)
    }

    fun cancelPending() {
        engine.cancelPending()
        refresh()
    }

    private fun readClipboard(): String? =
        CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)

    private fun showMessage(text: String) {
        message = text
        messageAlarm.cancelAllRequests()
        messageAlarm.addRequest({
            message = null
            refresh()
        }, MESSAGE_TIMEOUT_MS)
    }

    private val widgets = mutableSetOf<SmivStatusBarWidget>()

    fun addWidget(widget: SmivStatusBarWidget) {
        widgets += widget
    }

    fun removeWidget(widget: SmivStatusBarWidget) {
        widgets -= widget
    }

    /** Status bar colour: plain in NAV, highlighted in INSERT, selection mode and while a command is typed (as in MIV). */
    val statusHighlighted: Boolean get() = mode == Mode.INSERT || engine.isSelecting || engine.commandLine.isNotEmpty()

    /** Re-apply cursor shape to every editor and redraw the status bar widgets. */
    fun refresh() {
        EditorFactory.getInstance().allEditors.forEach(::applyCursor)
        widgets.forEach(SmivStatusBarWidget::update)
    }

    fun applyCursor(editor: Editor) {
        if (editor.editorKind != EditorKind.MAIN_EDITOR) return
        editor.settings.isBlockCursor = enabled && mode == Mode.NAV
    }

    override fun dispose() = Unit

    companion object {
        private const val MESSAGE_TIMEOUT_MS = 1000

        fun get(): SmivService = service()
    }
}

/** Gives editors opened later the block cursor too. */
class SmivEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        SmivService.get().applyCursor(event.editor)
    }
}
