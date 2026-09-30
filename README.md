# GitHub Multi-Account

A JetBrains IDE plugin (IntelliJ IDEA, PyCharm, WebStorm and others, 2025.1+) for working with several
GitHub accounts without committing or pushing as the wrong person.

Click the **GitHub: …** widget in the status bar, pick an account, and the current repository switches to it.

## What switching does

Everything goes into the repository's **local** git config, so other projects are not affected.

| Setting | Effect |
|---|---|
| `user.name`, `user.email` | Commits are authored by the selected account |
| `remote.*.url` → `https://<login>@github.com/…` | HTTPS pushes authenticate as that account (git credential helpers and the IDE both pick the right token) |
| `credential.https://github.com.username` | Same as above, for credential helpers |
| `core.sshCommand = ssh -i <key> -o IdentitiesOnly=yes` | SSH pushes use the account's key (only if a key is set) |
| IDE default GitHub account for the project | Pull requests, Share on GitHub and Gists use that account |

## Features

- Status bar widget with the current commit identity. The same popup is available from **Git | Switch GitHub Account…**
  and <kbd>Ctrl+Alt+Shift+G</kbd>.
- Warning when a repository has no account selected and falls back to the global git identity.
- **Settings | Tools | GitHub Multi-Account**:
  - an accounts table (label, `user.name`, `user.email`, GitHub login, optional SSH key);
  - **Import from IDE accounts** reads the accounts from *Settings | Version Control | GitHub*, fetching name and email from the GitHub API;
  - **Log in to GitHub** adds a new IDE account. Its repositories then appear in **Git | Clone**;
  - optionally also update the global git identity when switching;
  - interface language: English, O'zbekcha, Русский.

## Privacy

The plugin stores only the account list you enter, in the IDE settings (`githubAccountSwitcher.xml`).
When you import accounts, it uses the token already saved by the IDE's GitHub integration to call
`https://api.github.com/user` and `/user/emails`, only to read the name and email. Nothing is sent anywhere else.

## Building

Requires JDK 21. The plugin compiles against a locally installed IDE, set in `gradle.properties`:

```properties
localIdePath=/Applications/PyCharm.app
```

```bash
./gradlew buildPlugin                  # build/distributions/github-multi-account-<version>.zip
./gradlew verifyPlugin                 # Plugin Verifier against the local IDEs
./gradlew verifyPlugin -PverifyOldest  # also against IntelliJ IDEA 2025.1 (downloads ~1 GB)
PUBLISH_TOKEN=... ./gradlew publishPlugin
```

Install the zip with **Settings | Plugins | ⚙ | Install Plugin from Disk…**.

## License

[MIT](LICENSE)

---

### O'zbekcha

Bir nechta GitHub akkaunt bilan ishlash uchun plagin. Status bardagi **GitHub: …** yozuvini bosib akkauntni tanlaysiz.
Shundan keyin joriy repozitoriyda commit muallifi ham, push qiladigan akkaunt ham o'sha akkauntga o'tadi.
Sozlamalar: **Settings | Tools | GitHub Multi-Account**. U yerda tilni *O'zbekcha* ga o'zgartirsa bo'ladi.

### Русский

Плагин для работы с несколькими аккаунтами GitHub. Нажмите на **GitHub: …** в строке состояния и выберите аккаунт.
После этого текущий репозиторий коммитит и пушит от имени выбранного аккаунта.
Настройки: **Settings | Tools | GitHub Multi-Account**. Там же можно переключить язык на *Русский*.
