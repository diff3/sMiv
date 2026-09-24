package nu.entropy.smiv

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.util.concurrent.atomic.AtomicBoolean

class SmivStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = SmivStatusBarWidget.ID
    override fun getDisplayName(): String = "sMiv"
    override fun createWidget(project: Project): StatusBarWidget = SmivStatusBarWidget(project)
}

/** Applies the block cursor and status bar once a project is open. */
class SmivStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SmivService.get().refresh()
    }
}

/**
 * Warns once per session that IdeaVim is enabled too; both want the same keys.
 * Only registered when IdeaVim is enabled (optional dependency in plugin.xml).
 */
class SmivIdeaVimWarning : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!warned.compareAndSet(false, true)) return

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

    companion object {
        private val warned = AtomicBoolean(false)
    }
}
