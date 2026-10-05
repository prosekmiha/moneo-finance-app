package si.moneo

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.moneo.ui.AppLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Prevodi: vzorci datumov, oblikovanje nizov in množinske oblike v vseh podprtih jezikih. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalizationTest {

    private val base: Context = ApplicationProvider.getApplicationContext()

    private fun context(tag: String): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    private val datePatterns = listOf(
        R.string.fmt_date, R.string.fmt_day_month, R.string.fmt_weekday_short, R.string.fmt_month_short,
        R.string.fmt_month_year, R.string.fmt_month_name, R.string.fmt_day_full, R.string.fmt_day_full_no_year,
        R.string.fmt_day_short_year, R.string.fmt_date_long, R.string.fmt_month_year_short,
    )

    @Test fun datePatternsAreValidInAllLanguages() {
        val date = LocalDate.of(2026, 10, 1)
        for (tag in AppLocale.SUPPORTED) {
            val c = context(tag)
            for (id in datePatterns) {
                val pattern = c.getString(id)
                val text = DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(tag)).format(date)
                assertTrue("$tag: $pattern", text.isNotBlank())
            }
        }
    }

    @Test fun formattedStringsAndPluralsWorkInAllLanguages() {
        for (tag in AppLocale.SUPPORTED) {
            val c = context(tag)
            assertTrue(tag, c.getString(R.string.percent, 42).contains("42"))
            assertTrue(tag, c.getString(R.string.amount_of, "1 €", "2 €").contains("2 €"))
            assertTrue(tag, c.getString(R.string.insight_trend_up, 15, c.getString(R.string.period_prev_month)).contains("15"))
            for (n in listOf(0, 1, 2, 3, 5, 11, 21, 22, 25, 101)) {
                assertTrue(tag, c.resources.getQuantityString(R.plurals.entries_count, n, n).contains("$n"))
                assertTrue(tag, c.resources.getQuantityString(R.plurals.in_days, n, n).contains("$n"))
            }
        }
    }

    @Test fun everyLanguageIsTranslated() {
        val english = context("en").getString(R.string.settings)
        assertEquals("Settings", english)
        for (tag in AppLocale.SUPPORTED.filter { it != "en" }) {
            assertNotEquals(tag, english, context(tag).getString(R.string.settings))
        }
    }

    @Test fun unsupportedLanguageFallsBackToEnglish() {
        assertEquals("Settings", context("ja").getString(R.string.settings))
    }
}
