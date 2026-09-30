package uz.hakimbek.ghswitch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.config.GitExecutableManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File

/** Repozitoriyda amalda ishlatiladigan identity. `local` — lokal configda o'rnatilganmi yoki globaldan kelyaptimi. */
data class Identity(val name: String?, val email: String?, val local: Boolean)

class GitCommandException(message: String) : RuntimeException(message)

/** `git config` ustidagi yupqa qatlam. Barcha metodlar bloklaydi — EDT dan chaqirmang. */
class GitConfig(private val project: Project) {
    private val log = logger<GitConfig>()

    private val git: String by lazy {
        runCatching { GitExecutableManager.getInstance().getPathToGit(project) }.getOrNull() ?: "git"
    }

    fun repositories(): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories

    /** Loyihaning repozitoriy ildizlari; Git4Idea hali aniqlamagan bo'lsa loyiha papkasi. */
    fun roots(): List<File> {
        val repos = repositories().map { File(it.root.path) }
        if (repos.isNotEmpty()) return repos
        val base = project.basePath ?: return emptyList()
        val top = run(File(base), "rev-parse", "--show-toplevel", check = false)?.trim()
        return if (top.isNullOrEmpty()) emptyList() else listOf(File(top))
    }

    fun identity(root: File): Identity {
        val localEmail = get(root, "user.email", "--local")
        return Identity(get(root, "user.name"), get(root, "user.email"), local = localEmail != null)
    }

    fun get(root: File, key: String, vararg scope: String): String? =
        run(root, "config", *scope, "--get", key, check = false)?.trim()?.ifEmpty { null }

    fun set(root: File, key: String, value: String, global: Boolean = false) {
        run(root, "config", if (global) "--global" else "--local", key, value)
    }

    fun unset(root: File, key: String) {
        // exit code 5 = kalit yo'q, bu xato emas
        run(root, "config", "--local", "--unset-all", key, check = false)
    }

    /** `remote.<name>.url` va `remote.<name>.pushurl` qiymatlari. */
    fun remoteUrls(root: File): Map<String, String> {
        val out = run(root, "config", "--local", "--get-regexp", "^remote\\..*\\.(url|pushurl)$", check = false) ?: return emptyMap()
        return out.lines().filter { it.isNotBlank() }.associate { line ->
            val (key, value) = line.split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            key to value
        }
    }

    private fun run(root: File, vararg args: String, check: Boolean = true): String? {
        val cmd = GeneralCommandLine(git, *args).withWorkDirectory(root).withCharset(Charsets.UTF_8)
        val out = ExecUtil.execAndGetOutput(cmd, 10_000)
        if (out.exitCode != 0) {
            if (check) throw GitCommandException("git ${args.joinToString(" ")}: ${out.stderr.trim()}")
            log.debug("git ${args.joinToString(" ")} -> ${out.exitCode}: ${out.stderr}")
            return null
        }
        return out.stdout
    }
}
