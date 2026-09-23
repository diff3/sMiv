package nu.entropy.smiv

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

/**
 * Shows `sMiv NAV` / `sMiv INSERT` plus the command being typed or the last message.
 * Highlighted in INSERT and while a command is typed; a click opens the sMiv menu.
 */
class SmivStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
    private val label = JBLabel().apply {
        border = JBUI.Borders.empty(0, 6)
        toolTipText = "sMiv mode and command line. Click for the sMiv menu."
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = SmivPopups.showMenu(project, this@apply)
        })
    }

    init {
        SmivService.get().addWidget(this)
        update()
    }

    override fun ID(): String = ID
    override fun getComponent(): JComponent = label
    override fun install(statusBar: StatusBar) = Unit

    fun update() {
        val service = SmivService.get()
        label.text = service.statusText()
        label.isOpaque = service.statusHighlighted
        label.background = HIGHLIGHT
        label.repaint()
    }

    override fun dispose() = SmivService.get().removeWidget(this)

    companion object {
        const val ID = "nu.entropy.smiv.status"
        private val HIGHLIGHT = JBColor.namedColor("Banner.warningBackground", JBColor(0xFFF4DB, 0x3D3223))
    }
}

class SmivStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = SmivStatusBarWidget.ID
    override fun getDisplayName(): String = "sMiv"
    override fun createWidget(project: Project): StatusBarWidget = SmivStatusBarWidget(project)
}

/** Warns once per session when IdeaVim is enabled too; both want the same keys. */
class SmivStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SmivService.get().refresh()
        if (!isIdeaVimEnabled() || !warned.compareAndSet(false, true)) return

        NotificationGroupManager.getInstance().getNotificationGroup("sMiv")
            .createNotification(
                "sMiv and IdeaVim are both enabled",
                "Both plugins handle typing and Escape, so keys may behave unpredictably.",
                NotificationType.WARNING,
            )
            .addAction(NotificationAction.createSimpleExpiring("Turn off sMiv for this session") {
                SmivService.get().enabled = false
            })
            .notify(project)
    }

    private fun isIdeaVimEnabled(): Boolean {
        val id = PluginId.getId("IdeaVIM")
        return PluginManagerCore.getPlugin(id) != null && !PluginManagerCore.isDisabled(id)
    }

    companion object {
        private val warned = AtomicBoolean(false)
    }
}
