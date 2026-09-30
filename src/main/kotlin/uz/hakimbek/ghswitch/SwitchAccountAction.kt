package uz.hakimbek.ghswitch

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class SwitchAccountAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val switcher = AccountSwitcher.getInstance(project)
        // Popupni yangi o'qilgan identity bilan ko'rsatamiz (✓ belgisi to'g'ri bo'lishi uchun)
        switcher.refresh { switcher.createPopup().showCenteredInCurrentWindow(project) }
    }
}
