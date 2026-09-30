package uz.hakimbek.ghswitch

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFrame

/** Loyiha ochilganda: repo global identity bilan ishlayotgan bo'lsa ogohlantiradi. */
class IdentityStartupCheck : ProjectActivity {
    override suspend fun execute(project: Project) {
        // user.name IDE dagi GitHub akkaunt nomiga teng bo'lsa, GitHub loginni o'zi to'ldiradi
        IdeGitHubAccounts.autoLink(AccountsState.getInstance())
        val switcher = AccountSwitcher.getInstance(project)
        switcher.refresh {
            val id = switcher.current ?: return@refresh
            if (id.local || AccountsState.getInstance().accounts.size < 2) return@refresh
            switcher.notify(
                Msg("startup.globalIdentity", id.name, id.email),
                NotificationType.WARNING,
            )
        }
    }
}

/** IDE ga qaytganda (masalan terminalda `git config` o'zgartirilgan bo'lsa) status barni yangilaydi. */
class FrameActivationListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
        ProjectManager.getInstance().openProjects.forEach { AccountSwitcher.getInstance(it).refresh() }
    }
}
