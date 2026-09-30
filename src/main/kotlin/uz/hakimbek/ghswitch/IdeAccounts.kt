package uz.hakimbek.ghswitch

import com.google.gson.JsonParser
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountAuthData
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import kotlinx.coroutines.runBlocking
import java.awt.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** GitHub akkaunt profili (API dan). `email` topilmasa null. */
data class GitHubProfile(val login: String, val name: String?, val email: String?)

/**
 * IDE ning o'z GitHub akkauntlari (Settings → Version Control → GitHub) bilan ishlash.
 * Aynan shu akkauntlar Git → Clone oynasida repolari bilan ko'rinadi.
 */
object IdeGitHubAccounts {
    private val log = logger<IdeGitHubAccounts>()
    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }

    fun all(): List<GithubAccount> =
        service<GHAccountManager>().accountsState.value
            .filter { it.server.isGithubDotCom }
            .sortedBy { it.name.lowercase() }

    fun find(login: String?): GithubAccount? =
        if (login.isNullOrBlank()) null else all().firstOrNull { it.name.equals(login.trim(), ignoreCase = true) }

    /** Loyiha uchun IDE ning default GitHub akkauntini (PR, Share, Gist uchun) o'rnatadi. */
    fun setProjectDefault(project: Project, account: GithubAccount) {
        if (GHAccountsUtil.getDefaultAccount(project) != account) GHAccountsUtil.setDefaultAccount(project, account)
    }

    /** IDE ning standart GitHub login oynasini ochadi (brauzer yoki token orqali). EDT da chaqiring. */
    fun login(project: Project?, parent: Component?): GithubAccount? {
        // Kotlin default parametrli funksiyani `requestNewAccount$default` orqali chaqiradi, uning imzosi esa
        // 2025.1 va 2026.x da farq qiladi. 4 argumentli @JvmOverloads varianti ikkalasida ham bor — shuni chaqiramiz.
        val method = GHAccountsUtil::class.java.getMethod(
            "requestNewAccount",
            GithubServerPath::class.java, String::class.java, Project::class.java, Component::class.java,
        )
        val result = method.invoke(
            null, GithubServerPath.DEFAULT_SERVER, null,
            project ?: ProjectManager.getInstance().defaultProject, parent,
        ) as GHAccountAuthData?
        return result?.account
    }

    /** IDE saqlagan token bilan GitHub API dan ism va emailni oladi. Bloklaydi — fon oqimida chaqiring. */
    fun fetchProfile(account: GithubAccount): GitHubProfile {
        val fallback = GitHubProfile(account.name, null, null)
        val token = runCatching { runBlocking { service<GHAccountManager>().findCredentials(account) } }
            .getOrNull() ?: return fallback
        val user = get(account, "/user", token)?.asJsonObject ?: return fallback
        val login = user.get("login")?.takeIf { !it.isJsonNull }?.asString ?: account.name
        val name = user.get("name")?.takeIf { !it.isJsonNull }?.asString
        // /user/emails `user:email` ruxsatini talab qiladi — bo'lmasa ommaviy email, u ham bo'lmasa noreply
        val primary = get(account, "/user/emails", token)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.map { it.asJsonObject }
            ?.firstOrNull { it.get("primary")?.asBoolean == true }
            ?.get("email")?.asString
        val public = user.get("email")?.takeIf { !it.isJsonNull }?.asString
        val noreply = user.get("id")?.takeIf { !it.isJsonNull }?.asLong?.let { "$it+$login@users.noreply.github.com" }
        return GitHubProfile(login, name, primary ?: public ?: noreply)
    }

    private fun get(account: GithubAccount, path: String, token: String) = try {
        val request = HttpRequest.newBuilder(URI(account.server.toApiUrl() + path))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) JsonParser.parseString(response.body())
        else { log.info("GitHub API $path -> ${response.statusCode()}"); null }
    } catch (e: Exception) {
        log.info("GitHub API $path xatosi: ${e.message}")
        null
    }

    /**
     * Profillarni akkauntlar ro'yxatiga qo'shadi. Mavjud yozuv GitHub login, user.name yoki email bo'yicha
     * topilsa — faqat bo'sh GitHub login to'ldiriladi (foydalanuvchi qiymatlari o'zgarmaydi), aks holda yangi yozuv.
     * @return (yangi ro'yxat, qo'shilganlar soni, bog'langanlar soni)
     */
    fun merge(existing: List<GitAccount>, profiles: List<GitHubProfile>): Triple<List<GitAccount>, Int, Int> {
        val result = existing.map { it.copy() }.toMutableList()
        var added = 0
        var linked = 0
        for (p in profiles) {
            if (result.any { it.githubLogin.equals(p.login, ignoreCase = true) }) continue
            val match = result.firstOrNull {
                it.githubLogin.isBlank() && (
                    it.userName.equals(p.login, ignoreCase = true) ||
                        (p.email != null && it.email.equals(p.email, ignoreCase = true)))
            }
            if (match != null) {
                match.githubLogin = p.login
                linked++
            } else {
                result += GitAccount(label = p.login, userName = p.name ?: p.login, email = p.email.orEmpty(), githubLogin = p.login)
                added++
            }
        }
        return Triple(result, added, linked)
    }

    /** Tarmoqsiz bog'lash: GitHub login bo'sh, lekin user.name IDE akkaunt nomiga teng bo'lsa — to'ldiradi. */
    fun autoLink(state: AccountsState): Int {
        val (merged, _, linked) = merge(state.accounts, emptyList())
        var count = linked
        val ide = all()
        merged.filter { it.githubLogin.isBlank() }.forEach { acc ->
            ide.firstOrNull { it.name.equals(acc.userName, ignoreCase = true) }?.let { acc.githubLogin = it.name; count++ }
        }
        if (count > 0) state.accounts = merged
        return count
    }
}
