package uz.hakimbek.ghswitch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class AccountWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = ID
    override fun getDisplayName() = "GitHub Account"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = AccountWidget(project)

    companion object {
        const val ID = "GitHubAccountSwitcher"
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
        return if (id.local) "GitHub: $label" else "GitHub: $label (global)"
    }

    override fun getTooltipText(): String {
        val id = switcher.current ?: return "Git repozitoriy topilmadi"
        val scope = if (id.local) "shu repozitoriy uchun o'rnatilgan" else "global configdan olinmoqda — akkaunt tanlang"
        return "Commit muallifi: ${id.name} <${id.email}> ($scope). Almashtirish uchun bosing."
    }

    override fun getPopup(): JBPopup = switcher.createPopup()

    override fun dispose() {}
}
