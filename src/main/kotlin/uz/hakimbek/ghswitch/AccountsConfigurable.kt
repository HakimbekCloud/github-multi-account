package uz.hakimbek.ghswitch

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import javax.swing.DefaultCellEditor
import javax.swing.JComponent
import javax.swing.table.TableCellEditor

/** Settings → Tools → GitHub Multi-Account: akkauntlar jadvali. */
class AccountsConfigurable : Configurable {
    private val model = ListTableModel<GitAccount>(
        column(Msg("settings.col.label"), GitAccount::label) { a, v -> a.label = v },
        column("user.name", GitAccount::userName) { a, v -> a.userName = v },
        column("user.email", GitAccount::email) { a, v -> a.email = v },
        loginColumn(),
        column(Msg("settings.col.ssh"), GitAccount::sshKeyPath) { a, v -> a.sshKeyPath = v },
        ideColumn(),
    )
    private val table = TableView(model)
    private val alsoGlobal = JBCheckBox(Msg("settings.alsoGlobal"))
    private val language = ComboBox(PluginLanguage.entries.toTypedArray())
    private val status = JBLabel()

    override fun getDisplayName() = PLUGIN_NAME

    override fun createComponent(): JComponent {
        table.setShowGrid(false)
        val tablePanel = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                table.stopEditing()
                model.addRow(GitAccount())
                val row = model.rowCount - 1
                table.selectionModel.setSelectionInterval(row, row)
                table.editCellAt(row, 0)
            }
            .addExtraAction(action(Msg("settings.import"), AllIcons.ToolbarDecorator.Import) { importFromIde() })
            .addExtraAction(action(Msg("settings.login"), AllIcons.General.User) { loginAndImport() })
            .createPanel()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(Msg("settings.language"), language)
            .addComponentToRightColumn(JBLabel(Msg("settings.language.note")).apply { componentStyle = UIUtil.ComponentStyle.SMALL })
            .addComponentFillVertically(tablePanel, 0)
            .addComponent(status)
            .addComponent(
                JBLabel(Msg("settings.help")).apply { setAllowAutoWrapping(true); setCopyable(true) }
            )
            .addComponent(alsoGlobal)
            .panel
    }

    /** IDE dagi barcha GitHub akkauntlarni API orqali o'qib, ro'yxatga qo'shadi yoki mavjudlariga bog'laydi. */
    private fun importFromIde() {
        table.stopEditing()
        val ide = IdeGitHubAccounts.all()
        if (ide.isEmpty()) {
            Messages.showInfoMessage(table, Msg("settings.import.none"), displayName)
            return
        }
        val profiles = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<GitHubProfile>, Exception>(
            { ide.map { IdeGitHubAccounts.fetchProfile(it) } }, Msg("settings.progress.profiles"), true, null,
        )
        mergeIntoTable(profiles)
    }

    private fun loginAndImport() {
        table.stopEditing()
        val account = IdeGitHubAccounts.login(null, table) ?: return
        val profile = ProgressManager.getInstance().runProcessWithProgressSynchronously<GitHubProfile, Exception>(
            { IdeGitHubAccounts.fetchProfile(account) }, Msg("settings.progress.profile"), true, null,
        )
        mergeIntoTable(listOf(profile))
    }

    private fun mergeIntoTable(profiles: List<GitHubProfile>) {
        val (merged, added, linked) = IdeGitHubAccounts.merge(model.items, profiles)
        model.items = merged
        status.text = when {
            added == 0 && linked == 0 -> Msg("settings.merge.none")
            else -> Msg("settings.merge.done", added, linked)
        }
        if (merged.any { it.email.isBlank() }) status.text += " " + Msg("settings.merge.noEmail")
    }

    override fun isModified(): Boolean {
        val state = AccountsState.getInstance()
        return snapshot(model.items) != snapshot(state.accounts) || alsoGlobal.isSelected != state.alsoGlobal ||
            language.item != state.language
    }

    override fun apply() {
        table.stopEditing()
        val rows = model.items.filterNot { it.email.isBlank() && it.userName.isBlank() && it.githubLogin.isBlank() }
        rows.firstOrNull { it.email.isBlank() }?.let {
            throw ConfigurationException(Msg("settings.error.noEmail", it.displayName))
        }
        val state = AccountsState.getInstance()
        state.accounts = rows
        state.alsoGlobal = alsoGlobal.isSelected
        if (state.language != language.item) {
            state.language = language.item
            // Status bar va menyu matnlari darhol yangi tilda ko'rinsin
            ProjectManager.getInstance().openProjects.forEach { AccountSwitcher.getInstance(it).updateWidget() }
        }
    }

    override fun reset() {
        val state = AccountsState.getInstance()
        model.items = state.accounts.map { it.copy() }
        alsoGlobal.isSelected = state.alsoGlobal
        language.item = state.language
        status.text = ""
    }

    private fun snapshot(list: List<GitAccount>) =
        list.map { listOf(it.label, it.userName, it.email, it.githubLogin, it.sshKeyPath) }

    private fun action(text: String, icon: javax.swing.Icon, run: () -> Unit) = object : DumbAwareAction(text, null, icon) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = run()
    }

    private fun column(
        name: String,
        getter: (GitAccount) -> String,
        setter: (GitAccount, String) -> Unit,
    ) = object : ColumnInfo<GitAccount, String>(name) {
        override fun valueOf(item: GitAccount) = getter(item)
        override fun isCellEditable(item: GitAccount) = true
        override fun setValue(item: GitAccount, value: String?) = setter(item, value.orEmpty().trim())
    }

    /** GitHub login: IDE akkauntlaridan tanlash yoki qo'lda yozish. */
    private fun loginColumn() = object : ColumnInfo<GitAccount, String>(Msg("settings.col.login")) {
        override fun valueOf(item: GitAccount) = item.githubLogin
        override fun isCellEditable(item: GitAccount) = true
        override fun setValue(item: GitAccount, value: String?) { item.githubLogin = value.orEmpty().trim() }
        override fun getEditor(item: GitAccount): TableCellEditor {
            val combo = ComboBox(IdeGitHubAccounts.all().map { it.name }.toTypedArray()).apply { isEditable = true }
            return DefaultCellEditor(combo)
        }
    }

    private fun ideColumn() = object : ColumnInfo<GitAccount, String>(Msg("settings.col.ide")) {
        override fun valueOf(item: GitAccount) = when {
            item.githubLogin.isBlank() -> "—"
            IdeGitHubAccounts.find(item.githubLogin) != null -> Msg("settings.ide.linked")
            else -> Msg("settings.ide.missing")
        }
        override fun getMaxStringValue() = Msg("settings.ide.missing") + "  "
    }
}
