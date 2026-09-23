package nu.entropy.smiv

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean

/** Shows `sMiv NAV` / `sMiv INSERT` plus the command being typed or the last message. */
class SmivStatusBarWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
    override fun ID(): String = ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getText(): String = SmivService.get().statusText()
    override fun getTooltipText(): String = "sMiv mode and command line"
    override fun getAlignment(): Float = Component.LEFT_ALIGNMENT
    override fun install(statusBar: StatusBar) = Unit
    override fun dispose() = Unit

    companion object {
        const val ID = "nu.entropy.smiv.status"
    }
}

class SmivStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = SmivStatusBarWidget.ID
    override fun getDisplayName(): String = "sMiv"
    override fun createWidget(project: Project): StatusBarWidget = SmivStatusBarWidget()
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
