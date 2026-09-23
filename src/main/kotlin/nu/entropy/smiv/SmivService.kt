package nu.entropy.smiv

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm
import nu.entropy.smiv.core.Effect
import nu.entropy.smiv.core.Engine
import nu.entropy.smiv.core.Mode
import nu.entropy.smiv.core.TextView
import java.awt.datatransfer.DataFlavor

/** Application-wide sMiv runtime: one mode for all editors, as in MIV. */
@Service(Service.Level.APP)
class SmivService : Disposable {
    val engine = Engine()

    /** False after the user turns sMiv off (IdeaVim warning); everything then passes through. */
    var enabled = true
        set(value) {
            field = value
            if (!value) engine.escape()
            refresh()
        }

    private var message: String? = null
    private val messageAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    val mode: Mode get() = engine.state.mode

    /** sMiv only drives real code editors, not consoles, commit messages or dialog fields. */
    fun isActiveIn(editor: Editor): Boolean =
        enabled && editor.project != null && editor.editorKind == EditorKind.MAIN_EDITOR

    fun statusText(): String {
        val base = "sMiv ${mode.name}"
        val extra = engine.commandLine.ifEmpty { message.orEmpty() }
        return if (extra.isEmpty()) base else "$base  $extra"
    }

    fun handleTyped(editor: Editor, char: Char, dataContext: DataContext) =
        run(editor, dataContext) { view -> engine.type(char, view, ::readClipboard) }

    fun paragraph(editor: Editor, dataContext: DataContext, forward: Boolean) =
        run(editor, dataContext) { view -> engine.paragraph(view, forward) }

    /** Snapshot the primary caret, let the engine decide, then apply its effects. */
    private fun run(editor: Editor, dataContext: DataContext, command: (TextView) -> List<Effect>) {
        val caret = editor.caretModel.primaryCaret
        val view = TextView(editor.document.immutableCharSequence, caret.offset, caret.selectionStart, caret.selectionEnd)
        val effects = command(view)
        SmivEffects.apply(editor, dataContext, effects.filterNot { it is Effect.Message })
        effects.filterIsInstance<Effect.Message>().lastOrNull()?.let { showMessage(it.text) }
        refresh()
    }

    fun toNav() {
        engine.escape()
        refresh()
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

    /** Re-apply cursor shape to every editor and redraw the status bar widgets. */
    fun refresh() {
        EditorFactory.getInstance().allEditors.forEach(::applyCursor)
        for (project in ProjectManager.getInstance().openProjects) {
            WindowManager.getInstance().getStatusBar(project)?.updateWidget(SmivStatusBarWidget.ID)
        }
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
