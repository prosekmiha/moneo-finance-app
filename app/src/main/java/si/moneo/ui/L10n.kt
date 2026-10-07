package si.moneo.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import si.moneo.MainActivity
import java.util.Locale

/**
 * Besedila izven Compose (analize, obvestila, workerji, ViewModel).
 * Na zaslonih uporabljaj stringResource(); [str] uporablja kontekst v trenutnem jeziku aplikacije.
 * Brez inicializacije (enotni testi na JVM) vrne ključ in argumente, da testi ne potrebujejo Androida.
 */
object L10n {
    @Volatile private var context: Context? = null

    /** true, ko so viri naloženi (v enotnih testih na JVM false). */
    val ready: Boolean get() = context != null

    /** Kliče se ob zagonu aplikacije in vsake aktivnosti (jezik aktivnosti je merodajen). */
    fun init(context: Context) {
        val app = context.applicationContext
        // Kontekst z nastavitvami (jezikom) aktivnosti, a brez sklica nanjo (brez puščanja pomnilnika)
        this.context = if (context is Activity) app.createConfigurationContext(context.resources.configuration) else app
    }

    // Brez argumentov niz ne gre skozi String.format (nizi s formatted="false" lahko vsebujejo "%")
    fun string(@StringRes id: Int, args: Array<out Any>): String =
        context?.let { if (args.isEmpty()) it.getString(id) else it.getString(id, *args) } ?: fallback(id, args)

    fun plural(@PluralsRes id: Int, count: Int, args: Array<out Any>): String =
        context?.resources?.getQuantityString(id, count, *args) ?: fallback(id, args)

    private fun fallback(id: Int, args: Array<out Any>) = (listOf("#$id") + args.map { it.toString() }).joinToString(" ")
}

fun str(@StringRes id: Int, vararg args: Any): String = L10n.string(id, args)

/** Množinska oblika (slovenščina ima ednino, dvojino, množino 3-4 in 5+). */
fun qty(@PluralsRes id: Int, count: Int, vararg args: Any): String = L10n.plural(id, count, args)

/**
 * Jezik aplikacije: privzeto jezik telefona (če ni podprt, angleščina - privzeti viri),
 * ročno pa kateri koli podprt jezik. Na Androidu 13+ prek sistemske izbire jezika aplikacije,
 * na starejših z lastnim kontekstom (attachBaseContext) in ponovnim zagonom.
 */
object AppLocale {
    /** Podprti jeziki (oznake BCP 47), vrstni red za izbirnik. */
    val SUPPORTED = listOf(
        "en", "sl", "de", "hr", "sr-Latn", "it", "fr", "es", "pt", "nl", "pl", "cs", "sk", "hu",
        "ro", "bg", "el", "ru", "uk", "sv", "da", "nb", "fi", "et", "lv", "lt",
    )

    private const val FILE = "ui_prefs"
    private const val KEY = "app_language"

    /** Ročno izbran jezik ali null (= kot telefon). */
    fun selected(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        }

    /** Ime jezika v tem jeziku ("Deutsch", "Slovenščina" ...). */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Za Android < 13: kontekst v izbranem jeziku (kliče se v attachBaseContext). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Nastavi jezik (null = kot telefon) in ga uveljavi. */
    fun set(activity: Activity, tag: String?) {
        activity.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            // Sistem sam ponovno ustvari aktivnosti in jezik prikaže tudi v sistemskih nastavitvah aplikacije
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            // Starejši Android: ponovni zagon, da jezik prevzamejo tudi aplikacija, workerji in widgeti
            activity.startActivity(
                Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            activity.finish()
            Runtime.getRuntime().exit(0)
        }
    }
}
