package uz.hakimbek.ghswitch

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import javax.swing.JComponent

/** Settings → Tools → GitHub Account Switcher: akkauntlar jadvali. */
class AccountsConfigurable : Configurable {
    private val model = ListTableModel<GitAccount>(
        column("Nomi (label)", GitAccount::label) { a, v -> a.label = v },
        column("user.name", GitAccount::userName) { a, v -> a.userName = v },
        column("user.email", GitAccount::email) { a, v -> a.email = v },
        column("GitHub login", GitAccount::githubLogin) { a, v -> a.githubLogin = v },
        column("SSH kalit (ixtiyoriy)", GitAccount::sshKeyPath) { a, v -> a.sshKeyPath = v },
    )
    private val table = TableView(model)
    private val alsoGlobal = JBCheckBox("Almashtirganda global git configni ham yangilash (user.name / user.email)")

    override fun getDisplayName() = "GitHub Account Switcher"

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
            .createPanel()
        return FormBuilder.createFormBuilder()
            .addComponentFillVertically(tablePanel, 0)
            .addComponent(
                JBLabel(
                    "<html>Akkaunt tanlanganda u joriy repozitoriyning <b>lokal</b> configiga yoziladi. " +
                        "<b>GitHub login</b> HTTPS remote URLga qo'shiladi (https://login@github.com/…), shunda push aynan shu akkaunt bilan bo'ladi. " +
                        "SSH remote uchun shu akkauntning kalit yo'lini kiriting (masalan <code>~/.ssh/id_work</code>).</html>"
                ).apply { setAllowAutoWrapping(true); setCopyable(true) }
            )
            .addComponent(alsoGlobal)
            .panel
    }

    override fun isModified(): Boolean {
        val state = AccountsState.getInstance()
        return snapshot(model.items) != snapshot(state.accounts) || alsoGlobal.isSelected != state.alsoGlobal
    }

    override fun apply() {
        table.stopEditing()
        val state = AccountsState.getInstance()
        state.accounts = model.items.filter { it.email.isNotBlank() }
        state.alsoGlobal = alsoGlobal.isSelected
    }

    override fun reset() {
        val state = AccountsState.getInstance()
        model.items = state.accounts.map { it.copy() }
        alsoGlobal.isSelected = state.alsoGlobal
    }

    private fun snapshot(list: List<GitAccount>) =
        list.map { listOf(it.label, it.userName, it.email, it.githubLogin, it.sshKeyPath) }

    private fun column(
        name: String,
        getter: (GitAccount) -> String,
        setter: (GitAccount, String) -> Unit,
    ) = object : ColumnInfo<GitAccount, String>(name) {
        override fun valueOf(item: GitAccount) = getter(item)
        override fun isCellEditable(item: GitAccount) = true
        override fun setValue(item: GitAccount, value: String?) = setter(item, value.orEmpty().trim())
    }
}
