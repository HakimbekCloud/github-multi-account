package uz.hakimbek.ghswitch

import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

/** Plagin tillari. Tanlangan til IDE tilidan mustaqil. */
enum class PluginLanguage(val code: String, val title: String) {
    EN("en", "English"),
    UZ("uz", "O'zbekcha"),
    RU("ru", "Русский");

    override fun toString() = title

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: EN
    }
}

/** `messages/GhSwitch*.properties` dan matn oladi; kalit topilmasa inglizcha asosiy fayldan. */
object Msg {
    private val bundles = ConcurrentHashMap<PluginLanguage, ResourceBundle>()

    private fun bundle(): ResourceBundle {
        val lang = runCatching { AccountsState.getInstance().language }.getOrDefault(PluginLanguage.EN)
        return bundles.getOrPut(lang) {
            ResourceBundle.getBundle(
                "messages.GhSwitch", Locale.forLanguageTag(lang.code), Msg::class.java.classLoader,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
            )
        }
    }

    /** `{0}`, `{1}` … o'rniga argumentlar qo'yiladi. MessageFormat ishlatilmaydi — o'zbekcha `'` belgisi buzilmasin. */
    operator fun invoke(key: String, vararg args: Any?): String {
        var text = bundle().let { if (it.containsKey(key)) it.getString(key) else key }
        args.forEachIndexed { i, arg -> text = text.replace("{$i}", arg.toString()) }
        return text
    }
}
