plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "uz.hakimbek"
version = "1.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Interfeysning default metodlari uchun ko'prik metod yaratilmasin (Verifier "deprecated override" demasin)
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    intellijPlatform {
        local(providers.gradleProperty("localIdePath"))
        bundledPlugin("Git4Idea")
        bundledPlugin("org.jetbrains.plugins.github")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
        changeNotes = """
            <h3>1.2.0</h3>
            <ul>
              <li>Renamed to GitHub Multi-Account; new plugin icon</li>
              <li>Privacy section in the description</li>
            </ul>
            <h3>1.1.0</h3>
            <ul>
              <li>Interface languages: English (default), O'zbekcha, Русский</li>
            </ul>
            <h3>1.0.1</h3>
            <ul>
              <li>Integration with IDE GitHub accounts: import, log in, IDE status column, project default account on switch</li>
            </ul>
            <h3>1.0.0</h3>
            <ul>
              <li>First release: status bar switcher, local user.name / user.email, HTTPS login in remote URLs, SSH key per account</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            // Lokal o'rnatilgan IDE lar (yuklab olinmaydi)
            listOf("/Applications/PyCharm.app", "/Applications/IntelliJ IDEA.app", "/Applications/WebStorm.app")
                .map(::file).filter { it.exists() }.forEach { local(it) }
            // Eng eski qo'llab-quvvatlanadigan versiya (sinceBuild 251): ./gradlew verifyPlugin -PverifyOldest
            if (providers.gradleProperty("verifyOldest").isPresent) {
                create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            }
        }
    }
    publishing {
        // Birinchi versiya plugins.jetbrains.com da qo'lda yuklanadi; keyingilari: PUBLISH_TOKEN=... ./gradlew publishPlugin
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
