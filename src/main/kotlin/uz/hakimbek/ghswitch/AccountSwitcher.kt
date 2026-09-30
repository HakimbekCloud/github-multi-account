package uz.hakimbek.ghswitch

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.WindowManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class AccountSwitcher(private val project: Project) {
    private val git = GitConfig(project)
    private val refreshing = AtomicBoolean(false)

    /** Birinchi repozitoriydagi joriy identity (status bar uchun kesh). */
    @Volatile
    var current: Identity? = null
        private set

    val currentAccount: GitAccount? get() = AccountsState.getInstance().findByEmail(current?.email)

    /** Joriy identityni fon oqimida qayta o'qiydi va status barni yangilaydi. */
    fun refresh(onDone: (() -> Unit)? = null) {
        if (project.isDisposed || !refreshing.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                current = git.roots().firstOrNull()?.let { git.identity(it) }
            } finally {
                refreshing.set(false)
            }
            ApplicationManager.getApplication().invokeLater({
                updateWidget()
                onDone?.invoke()
            }, project.disposed)
        }
    }

    fun updateWidget() {
        WindowManager.getInstance().getStatusBar(project)?.updateWidget(AccountWidgetFactory.ID)
    }

    fun switchTo(account: GitAccount) {
        object : Task.Backgroundable(project, "GitHub akkaunt almashtirilmoqda…", false) {
            override fun run(indicator: ProgressIndicator) {
                val roots = git.roots()
                if (roots.isEmpty()) {
                    notify("Loyihada git repozitoriy topilmadi.", NotificationType.WARNING)
                    return
                }
                roots.forEach { apply(it, account) }
                if (AccountsState.getInstance().alsoGlobal) {
                    if (account.userName.isNotBlank()) git.set(roots.first(), "user.name", account.userName, global = true)
                    git.set(roots.first(), "user.email", account.email, global = true)
                }
                // Git4Idea remote URLlarni keshlaydi — yangilab qo'yamiz
                git.repositories().forEach { it.update() }
                current = git.identity(roots.first())
                notify(
                    "<b>${account.displayName}</b> faollashtirildi: ${account.userName} &lt;${account.email}&gt;" +
                        if (roots.size > 1) " (${roots.size} ta repozitoriy)" else "",
                    NotificationType.INFORMATION,
                )
            }

            override fun onThrowable(error: Throwable) {
                notify("Akkauntni almashtirib bo'lmadi: ${error.message}", NotificationType.ERROR)
            }

            override fun onFinished() = updateWidget()
        }.queue()
    }

    private fun apply(root: File, account: GitAccount) {
        if (account.userName.isNotBlank()) git.set(root, "user.name", account.userName)
        git.set(root, "user.email", account.email)
        git.set(root, "ghswitch.account", account.displayName)

        // HTTPS push: remote URLga login qo'shiladi, shunda git credential helper ham, IDE ham
        // aynan shu akkauntning tokenini oladi.
        val login = account.githubLogin.trim()
        if (login.isNotEmpty()) git.set(root, "credential.https://github.com.username", login)
        else git.unset(root, "credential.https://github.com.username")
        git.remoteUrls(root).forEach { (key, url) ->
            val match = GITHUB_HTTPS.matchEntire(url) ?: return@forEach
            val newUrl = "https://" + (if (login.isNotEmpty()) "$login@" else "") + "github.com/" + match.groupValues[1]
            if (newUrl != url) git.set(root, key, newUrl)
        }

        // SSH push: faqat o'zimiz qo'ygan core.sshCommand ni o'chiramiz, foydalanuvchinikiga tegmaymiz.
        val key = account.sshKeyPath.trim()
        if (key.isNotEmpty()) {
            val path = if (key.startsWith("~/")) System.getProperty("user.home") + key.substring(1) else key
            git.set(root, "core.sshCommand", "ssh -i '$path' -o IdentitiesOnly=yes")
            git.set(root, "ghswitch.managedSsh", "true")
        } else if (git.get(root, "ghswitch.managedSsh", "--local") == "true") {
            git.unset(root, "core.sshCommand")
            git.unset(root, "ghswitch.managedSsh")
        }
    }

    /** Joriy identityni akkauntlar ro'yxatiga saqlaydi. */
    fun saveCurrentAsAccount() {
        val id = current ?: return
        val email = id.email ?: return
        val state = AccountsState.getInstance()
        state.accounts = state.accounts + GitAccount(label = email.substringBefore('@'), userName = id.name.orEmpty(), email = email)
        openSettings()
    }

    fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, AccountsConfigurable::class.java)
        refresh()
    }

    fun createPopup(): ListPopup {
        val accounts = AccountsState.getInstance().accounts
        val active = currentAccount
        val items = buildList<Item> {
            accounts.forEach { add(Item.Account(it)) }
            val email = current?.email
            if (email != null && active == null) add(Item.SaveCurrent(email))
            add(Item.Manage)
        }
        val step = object : BaseListPopupStep<Item>("GitHub akkaunt", items) {
            override fun getTextFor(value: Item): String = when (value) {
                is Item.Account -> buildString {
                    append(if (value.account === active) "✓ " else "    ")
                    append(value.account.displayName)
                    append("  —  ").append(value.account.email)
                }
                is Item.SaveCurrent -> "Joriy identityni saqlash (${value.email})…"
                Item.Manage -> "Akkauntlarni boshqarish…"
            }

            override fun getSeparatorAbove(value: Item) =
                if (value !is Item.Account && items.firstOrNull { it !is Item.Account } === value && accounts.isNotEmpty())
                    com.intellij.openapi.ui.popup.ListSeparator() else null

            override fun onChosen(selectedValue: Item, finalChoice: Boolean): PopupStep<*>? =
                doFinalStep {
                    when (selectedValue) {
                        is Item.Account -> switchTo(selectedValue.account)
                        is Item.SaveCurrent -> saveCurrentAsAccount()
                        Item.Manage -> openSettings()
                    }
                }
        }
        return JBPopupFactory.getInstance().createListPopup(step)
    }

    fun notify(html: String, type: NotificationType) {
        val n = NotificationGroupManager.getInstance().getNotificationGroup("GitHub Account Switcher")
            .createNotification("GitHub Account Switcher", html, type)
        if (type != NotificationType.INFORMATION) {
            n.addAction(NotificationAction.createSimpleExpiring("Akkaunt tanlash…") {
                createPopup().showCenteredInCurrentWindow(project)
            })
        }
        n.notify(project)
    }

    private sealed interface Item {
        class Account(val account: GitAccount) : Item
        class SaveCurrent(val email: String) : Item
        data object Manage : Item
    }

    companion object {
        private val GITHUB_HTTPS = Regex("^https://(?:[^@/]+@)?github\\.com/(.+)$")

        fun getInstance(project: Project): AccountSwitcher = project.service()
    }
}
