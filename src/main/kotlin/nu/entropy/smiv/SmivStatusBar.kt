package nu.entropy.smiv

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class SmivStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = SmivStatusBarWidget.ID
    override fun getDisplayName(): String = "sMiv"
    override fun createWidget(project: Project): StatusBarWidget = SmivStatusBarWidget(project)
}
