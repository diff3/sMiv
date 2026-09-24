package nu.entropy.smiv

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.testFramework.LightVirtualFile
import nu.entropy.smiv.core.Register
import nu.entropy.smiv.core.registerPreview
import java.awt.Component

/** Register viewer, the sMiv menu and the text views it opens (MIV's QuickPicks and output channel). */
object SmivPopups {
    /**
     * `V`: list the non-empty registers. Typing a digit or choosing a row pastes that
     * register before the caret.
     */
    fun showRegisters(editor: Editor, registers: List<Pair<Int, Register>>) {
        val step = object : BaseListPopupStep<Pair<Int, Register>>("Registers", registers) {
            override fun getTextFor(value: Pair<Int, Register>) = "${value.first}   ${registerPreview(value.second.text)}"

            // Speed search on the register digit only, so typing `3` picks register 3.
            override fun isSpeedSearchEnabled() = true
            override fun getIndexedString(value: Pair<Int, Register>) = value.first.toString()
            override fun isAutoSelectionEnabled() = true

            override fun onChosen(selectedValue: Pair<Int, Register>, finalChoice: Boolean): PopupStep<*>? =
                doFinalStep { SmivService.get().pasteRegister(editor, selectedValue.first) }
        }
        // Stays open until a register is chosen or Esc is pressed.
        JBPopupFactory.getInstance().createListPopup(step).showInBestPositionFor(editor)
    }

    private class MenuItem(val label: String, val run: () -> Unit)

    /** Clicking the status bar widget. */
    fun showMenu(project: Project, component: Component) {
        val items = listOf(
            MenuItem("Registers") { showRegistersFor(project) },
            MenuItem("Command Stats") { showStats(project) },
            MenuItem("Toggle Search Highlight") { SmivService.get().toggleSearchHighlight() },
            MenuItem("Change Keybindings") { ShowSettingsUtil.getInstance().showSettingsDialog(project, SmivConfigurable::class.java) },
            MenuItem("Open KEYMAP") { showKeymap(project) },
        )
        val step = object : BaseListPopupStep<MenuItem>("sMiv", items) {
            override fun getTextFor(value: MenuItem) = value.label
            override fun onChosen(selectedValue: MenuItem, finalChoice: Boolean): PopupStep<*>? =
                doFinalStep { selectedValue.run() }
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(component)
    }

    private fun showRegistersFor(project: Project) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        SmivService.get().showRegisters(editor)
    }

    private var statsFile: LightVirtualFile? = null

    /** Command usage in a read-only tab; the previous stats tab is replaced, like MIV's output channel. */
    private fun showStats(project: Project) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        statsFile?.let(fileEditorManager::closeFile)
        val text = SmivService.get().engine.state.stats.format()
        val file = LightVirtualFile("sMiv Stats.txt", text).apply { isWritable = false }
        statsFile = file
        fileEditorManager.openFile(file, true)
    }

    private fun showKeymap(project: Project) {
        val text = SmivPopups::class.java.getResource("/nu/entropy/smiv/KEYMAP.md")?.readText() ?: return
        val file = LightVirtualFile("sMiv KEYMAP.md", text).apply { isWritable = false }
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}
