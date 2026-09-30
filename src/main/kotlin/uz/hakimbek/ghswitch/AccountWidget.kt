package uz.hakimbek.ghswitch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class AccountWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = ID
    override fun getDisplayName() = Msg("widget.name")
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = AccountWidget(project)

    companion object {
        const val ID = "GitHubMultiAccount"
    }
}

/** Status bardagi "GitHub: <akkaunt>" yozuvi; bosilganda akkauntlar ro'yxati ochiladi. */
class AccountWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {
    private val switcher get() = AccountSwitcher.getInstance(project)

    override fun ID() = AccountWidgetFactory.ID
    override fun getPresentation() = this

    override fun install(statusBar: StatusBar) = switcher.refresh()

    override fun getSelectedValue(): String {
        val id = switcher.current ?: return "GitHub: —"
        val label = switcher.currentAccount?.displayName ?: id.email ?: "—"
        return if (id.local) "GitHub: $label" else "GitHub: $label " + Msg("widget.global")
    }

    override fun getTooltipText(): String {
        val id = switcher.current ?: return Msg("widget.tooltip.noRepo")
        val scope = Msg(if (id.local) "widget.scope.local" else "widget.scope.global")
        return Msg("widget.tooltip", id.name, id.email, scope)
    }

    override fun getPopup(): JBPopup = switcher.createPopup()

    override fun dispose() {}
}
