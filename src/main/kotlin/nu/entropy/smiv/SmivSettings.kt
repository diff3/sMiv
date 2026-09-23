package nu.entropy.smiv

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import nu.entropy.smiv.core.KeyLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

/** Custom NAV keys, stored as MIV token name → key. Only keys that differ from the default are kept. */
@Service(Service.Level.APP)
@State(name = "SmivSettings", storages = [Storage("smiv.xml")])
class SmivSettings : PersistentStateComponent<SmivSettings.KeyState> {
    class KeyState {
        var keys: MutableMap<String, String> = mutableMapOf()
    }

    private var state = KeyState()

    val overrides: Map<String, Char>
        get() = state.keys.mapNotNull { (id, key) -> key.singleOrNull()?.let { id to it } }.toMap()

    fun setOverrides(overrides: Map<String, Char>) {
        state.keys = overrides.mapValues { it.value.toString() }.toMutableMap()
        applyToEngine()
    }

    override fun getState(): KeyState = state

    override fun loadState(loaded: KeyState) {
        state = loaded
        applyToEngine()
    }

    /** Push the keys to a running sMiv; at startup [SmivService] reads them itself. */
    private fun applyToEngine() {
        ApplicationManager.getApplication().getServiceIfCreated(SmivService::class.java)?.engine?.layout = KeyLayout(overrides)
    }

    companion object {
        fun get(): SmivSettings = service()
    }
}

/** Settings → Tools → sMiv: a table of NAV keys (MIV's "Change Keybindings"). */
class SmivConfigurable : Configurable {
    private val bindings = KeyLayout.BINDINGS
    private var edited = mutableMapOf<String, Char>()

    private val tableModel = object : AbstractTableModel() {
        override fun getRowCount() = bindings.size
        override fun getColumnCount() = 3
        override fun getColumnName(column: Int) = listOf("Command", "Description", "Key")[column]
        override fun isCellEditable(row: Int, column: Int) = column == 2

        override fun getValueAt(row: Int, column: Int): Any = when (column) {
            0 -> bindings[row].id
            1 -> bindings[row].description
            else -> (edited[bindings[row].id] ?: bindings[row].defaultKey).toString()
        }

        override fun setValueAt(value: Any?, row: Int, column: Int) {
            val binding = bindings[row]
            val key = value?.toString()?.trim()?.singleOrNull() ?: return
            if (key == binding.defaultKey) edited.remove(binding.id) else edited[binding.id] = key
            fireTableCellUpdated(row, column)
        }
    }

    override fun getDisplayName() = "sMiv"

    override fun createComponent(): JComponent {
        val table = JBTable(tableModel).apply { columnModel.getColumn(2).maxWidth = JBUI.scale(60) }
        val restore = JButton("Restore Defaults").apply {
            addActionListener {
                edited.clear()
                tableModel.fireTableDataChanged()
            }
        }
        val hint = JLabel(
            "One character per command. A new key replaces the default one. Digits and Space are fixed. " +
                "Alt shortcuts are in Keymap under sMiv.",
        )
        return JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            add(hint, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(restore) }, BorderLayout.SOUTH)
        }
    }

    override fun isModified() = edited != SmivSettings.get().overrides

    override fun apply() {
        KeyLayout.validate(edited)?.let { throw ConfigurationException(it) }
        SmivSettings.get().setOverrides(edited)
    }

    override fun reset() {
        edited = SmivSettings.get().overrides.toMutableMap()
        tableModel.fireTableDataChanged()
    }
}
