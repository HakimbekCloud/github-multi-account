package uz.hakimbek.ghswitch

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** Bitta GitHub akkaunt. XML serializatsiya uchun barcha maydonlar `var` va bo'sh konstruktor bor. */
class GitAccount(
    var label: String = "",
    var userName: String = "",
    var email: String = "",
    /** GitHub login — HTTPS push qaysi akkaunt bilan bo'lishini belgilaydi. */
    var githubLogin: String = "",
    /** Ixtiyoriy: SSH remote uchun shaxsiy kalit yo'li (masalan ~/.ssh/id_work). */
    var sshKeyPath: String = "",
) {
    val displayName: String get() = label.ifBlank { githubLogin.ifBlank { email } }

    fun copy() = GitAccount(label, userName, email, githubLogin, sshKeyPath)
}

@Service(Service.Level.APP)
@State(name = "GitHubAccountSwitcher", storages = [Storage("githubAccountSwitcher.xml")])
class AccountsState : PersistentStateComponent<AccountsState.Data> {
    class Data {
        var accounts: MutableList<GitAccount> = mutableListOf()
        /** Almashtirganda global git configni ham yangilash. */
        var alsoGlobal: Boolean = false
    }

    private var data = Data()

    var accounts: List<GitAccount>
        get() = data.accounts
        set(value) { data.accounts = value.map { it.copy() }.toMutableList() }

    var alsoGlobal: Boolean
        get() = data.alsoGlobal
        set(value) { data.alsoGlobal = value }

    fun findByEmail(email: String?): GitAccount? =
        if (email.isNullOrBlank()) null else accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }

    override fun getState(): Data = data
    override fun loadState(state: Data) { data = state }

    companion object {
        fun getInstance(): AccountsState = service()
    }
}
