package si.moneo.feature.voice

import si.moneo.R
import androidx.compose.ui.res.stringResource
import si.moneo.ui.fmtDate
import si.moneo.domain.voice.VoiceVocabulary
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import si.moneo.MoneoApp
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.domain.voice.DEFAULT_VOICE_EXAMPLES
import si.moneo.domain.voice.VoiceCommandParser
import si.moneo.domain.voice.VoiceExample
import si.moneo.domain.voice.voiceExamples
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import android.os.SystemClock
import si.moneo.ui.formatCents
import si.moneo.ui.theme.MoneoTheme
import si.moneo.ui.theme.ThemePrefs
import java.time.ZoneId
import java.util.Locale
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import androidx.compose.ui.text.style.TextAlign

/**
 * Pregleden "bottom-sheet" aktiviti za glasovni vnos:
 * posluša -> razčleni po fiksnih pravilih -> prikaže predogled -> potrdi/shrani.
 */
class VoiceInputActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(si.moneo.ui.AppLocale.wrap(newBase))
    }

    private var state by mutableStateOf(VoiceState())
    /** Primeri stavkov za uporabnikove najpogostejše kategorije. */
    private var examples by mutableStateOf(DEFAULT_VOICE_EXAMPLES)
    private var recognizer: SpeechRecognizer? = null

    data class VoiceState(
        val listening: Boolean = false,
        val heard: String = "",
        val parsed: VoiceCommandParser.Parsed? = null,
        val saved: Boolean = false,
        val error: String? = null,
    )

    /** Jezik aplikacije (iz nastavitev) - v njem posluša prepoznava govora, če je na voljo. */
    private val uiLocale: Locale get() = resources.configuration.locales[0]

    /** Jezik prepoznave: jezik aplikacije ali angleščina, če ga prepoznava ne podpira; null = še ni preverjen. */
    private var speechLocale by mutableStateOf<Locale?>(null)
    private var resolvingLanguage = false
    private val activeSpeechLocale: Locale get() = speechLocale ?: uiLocale
    private val usingFallback: Boolean get() = activeSpeechLocale.language != uiLocale.language
    private val isSlovenian: Boolean get() = activeSpeechLocale.language == "sl"

    /** Viri v jeziku prepoznave (besede za razčlenjevanje, primeri stavkov). */
    private fun speechContext(): Context =
        if (!usingFallback) this
        else createConfigurationContext(Configuration(resources.configuration).apply { setLocale(activeSpeechLocale) })

    private fun vocabulary() = speechContext().let { c ->
        VoiceVocabulary.of(
            c.getString(R.string.voice_words_income), c.getString(R.string.voice_words_expense),
            c.getString(R.string.voice_words_today), c.getString(R.string.voice_words_yesterday),
        )
    }

    /** Oznaka jezika za prepoznavo govora, vedno z regijo (npr. "de-DE"). */
    private fun recognizerLanguage(l: Locale = activeSpeechLocale): String {
        if (l.country.isNotEmpty()) return "${l.language}-${l.country}"
        val region = mapOf(
            "sl" to "SI", "en" to "US", "de" to "DE", "hr" to "HR", "sr" to "RS", "it" to "IT", "fr" to "FR",
            "es" to "ES", "pt" to "PT", "nl" to "NL", "pl" to "PL", "cs" to "CZ", "sk" to "SK", "hu" to "HU",
            "ro" to "RO", "bg" to "BG", "el" to "GR", "ru" to "RU", "uk" to "UA", "sv" to "SE", "da" to "DK",
            "nb" to "NO", "fi" to "FI", "et" to "EE", "lv" to "LV", "lt" to "LT",
        )[l.language]
        return if (region != null) "${l.language}-$region" else l.language
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        si.moneo.ui.L10n.init(this)
        val app = application as MoneoApp
        loadExamples(app)

        setContent {
            MoneoTheme(ThemePrefs.load(this), ThemePrefs.loadAccent(this)) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> if (granted) startListening(app) else state = state.copy(error = getString(R.string.mic_permission_needed)) }

                Surface {
                    Column(
                        // Pod statusno vrstico in izrezom za kamero + dodaten razmak nad naslovom
                        Modifier.fillMaxWidth().statusBarsPadding().displayCutoutPadding()
                            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.voice_entry), style = MaterialTheme.typography.titleLarge)
                        // V katerem jeziku posluša (in ali je bila uporabljena angleščina namesto jezika aplikacije)
                        if (speechLocale != null) {
                            Text(
                                if (usingFallback) {
                                    stringResource(
                                        R.string.voice_language_fallback,
                                        uiLocale.getDisplayLanguage(uiLocale), activeSpeechLocale.getDisplayLanguage(uiLocale),
                                    )
                                } else {
                                    stringResource(R.string.voice_language, activeSpeechLocale.getDisplayLanguage(uiLocale))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (usingFallback) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }

                        when {
                            state.listening -> {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.listening), style = MaterialTheme.typography.bodyMedium)
                                ExamplesCard(examples)
                            }
                            state.saved -> Text(stringResource(R.string.saved_check), style = MaterialTheme.typography.titleMedium)
                            else -> {
                                if (state.heard.isNotEmpty()) {
                                    Text(stringResource(R.string.quoted, state.heard), style = MaterialTheme.typography.bodyMedium)
                                }
                                state.parsed?.let { p ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(R.string.voice_type, stringResource(if (p.type.name == "INCOME") R.string.type_income else R.string.type_expense)))
                                            Text(stringResource(R.string.voice_amount, p.amountCents?.let { formatCents(it) } ?: "?"))
                                            Text(stringResource(R.string.voice_category, p.category?.title ?: stringResource(R.string.voice_none)))
                                            Text(stringResource(R.string.voice_account, p.account?.title ?: stringResource(R.string.voice_default)))
                                            Text(stringResource(R.string.voice_date, p.date.fmtDate()))
                                            if (p.comment.isNotBlank()) Text(stringResource(R.string.voice_note, p.comment))
                                        }
                                    }
                                }
                                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                // Ob napaki ali pred prvim poskusom pokaži, kako povedati
                                if (state.parsed?.isComplete != true) ExamplesCard(examples)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { finish() }) { Text(stringResource(R.string.close)) }
                            if (state.parsed?.isComplete == true && !state.saved) {
                                Button(onClick = { saveParsed(app) }) { Text(stringResource(R.string.save)) }
                            }
                            if (!state.listening && !state.saved) {
                                Button(onClick = {
                                    if (ContextCompat.checkSelfPermission(this@VoiceInputActivity, Manifest.permission.RECORD_AUDIO)
                                        == PackageManager.PERMISSION_GRANTED
                                    ) startListening(app) else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }) { Text(stringResource(if (state.parsed == null) R.string.start else R.string.retry)) }
                            }
                        }
                    }
                }
            }
        }

        // Samodejno začni poslušati ob odprtju (če je dovoljenje že odobreno) - z majhnim zamikom,
        // ko je okno že prikazano; takojšen zagon v onCreate prepoznava včasih zavrne
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                delay(300)
                startListening(app)
            }
        }
    }

    /** Števec poskusov: dogodki prejšnjega poskusa (zakasnele napake) se prezrejo. */
    private var session = 0
    private var silentRetries = 0

    /**
     * Začne poslušati. Ena instanca prepoznave se ponovno uporabi - uničenje in takojšnje ponovno
     * ustvarjanje Googlova prepoznava pogosto zavrne z napako "zasedeno" ali "prekinjeno".
     */
    private fun startListening(app: MoneoApp, isRetry: Boolean = false) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            state = state.copy(error = getString(R.string.speech_unavailable))
            return
        }
        if (!isRetry) silentRetries = 0
        state = VoiceState(listening = true)
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
        if (speechLocale == null) {
            // Najprej preveri, ali prepoznava podpira jezik aplikacije (sicer angleščina), nato začni poslušati
            if (!resolvingLanguage) {
                resolvingLanguage = true
                resolveSpeechLanguage(r) {
                    resolvingLanguage = false
                    if (!isDestroyed) startListening(app, isRetry)
                }
            }
            return
        }
        val id = ++session
        val startedAt = SystemClock.elapsedRealtime()
        var ready = false
        var done = false
        r.cancel()
        r.setRecognitionListener(object : RecognitionListener {
            private fun current() = id == session && !done

            override fun onReadyForSpeech(params: Bundle?) { if (id == session) ready = true }

            override fun onResults(results: Bundle) {
                if (!current()) return
                done = true
                val candidates = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                lifecycleScope.launch {
                    val categories = app.container.database.categoryDao().allActive()
                    val accounts = app.container.database.accountDao().all().filter { !it.deleted }
                    val parser = VoiceCommandParser(categories, accounts, vocabulary = vocabulary())
                    // Prepoznava vrne več različic; vzemi prvo, v kateri je znesek
                    val parsedAll = candidates.map { it to parser.parse(it) }
                    val best = parsedAll.firstOrNull { it.second.isComplete } ?: parsedAll.firstOrNull()
                    state = if (best == null) {
                        VoiceState(error = errorMessage(SpeechRecognizer.ERROR_NO_MATCH))
                    } else {
                        VoiceState(
                            heard = best.first,
                            parsed = best.second,
                            error = if (best.second.isComplete) null else getString(R.string.voice_no_amount, speechContext().getString(R.string.voice_simple_example)),
                        )
                    }
                }
            }

            override fun onError(error: Int) {
                // Zakasnela napaka prejšnjega poskusa ali lažna napaka po že vrnjenem rezultatu
                if (!current()) return
                val languageError = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                if (languageError && activeSpeechLocale.language != FALLBACK_LOCALE.language) {
                    // Jezik aplikacije ni na voljo - poslušaj v angleščini
                    done = true
                    useFallbackLanguage(app)
                    startListening(app, isRetry = true)
                    return
                }
                val early = !ready || SystemClock.elapsedRealtime() - startedAt < 1_000
                val transient = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                    (error == SpeechRecognizer.ERROR_NO_MATCH && early)
                if (transient && silentRetries < MAX_SILENT_RETRIES) {
                    // Kratkotrajna težava prepoznave - tiho poskusi znova
                    silentRetries++
                    done = true
                    lifecycleScope.launch {
                        delay(400L * silentRetries)
                        if (id == session) startListening(app, isRetry = true)
                    }
                    return
                }
                done = true
                state = VoiceState(error = errorMessage(error))
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(recognizerIntent(activeSpeechLocale))
    }

    private fun recognizerIntent(locale: Locale) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognizerLanguage(locale))
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    /**
     * Določi jezik prepoznave: jezik aplikacije, če ga prepoznava podpira, sicer angleščina.
     * Na Androidu 13+ se preveri vnaprej; na starejših (in če preverjanje ne uspe) se angleščina
     * uporabi šele ob napaki "jezik ni podprt" (glej onError).
     */
    private fun resolveSpeechLanguage(r: SpeechRecognizer, then: () -> Unit) {
        val app = uiLocale
        if (Build.VERSION.SDK_INT < 33 || app.language == FALLBACK_LOCALE.language) {
            speechLocale = app
            then()
            return
        }
        var answered = false
        fun answer(locale: Locale) {
            if (answered) return
            answered = true
            if (locale.language != app.language) useFallbackLanguage(application as MoneoApp) else speechLocale = locale
            then()
        }
        r.checkRecognitionSupport(recognizerIntent(app), mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                // Takoj uporabni: nameščeni na napravi ali prek spleta
                val available = support.installedOnDeviceLanguages + support.onlineLanguages
                val known = available + support.supportedOnDeviceLanguages + support.pendingOnDeviceLanguages
                fun lang(tag: String) = Locale.forLanguageTag(tag).language.let { if (it == "no") "nb" else it }
                val appLang = lang(app.toLanguageTag())
                // Prazen seznam = prepoznava jezikov ne sporoča; ne ugibaj, poskusi z jezikom aplikacije
                answer(if (known.isEmpty() || available.any { lang(it) == appLang }) app else FALLBACK_LOCALE)
            }

            override fun onError(error: Int) = answer(app)
        })
    }

    /** Preklopi prepoznavo na angleščino (besede in primeri stavkov tudi). */
    private fun useFallbackLanguage(app: MoneoApp) {
        speechLocale = FALLBACK_LOCALE
        loadExamples(app)
    }

    /** Primeri stavkov v jeziku prepoznave, prilagojeni najpogostejšim kategorijam. */
    private fun loadExamples(app: MoneoApp) {
        val c = speechContext()
        val slovenian = isSlovenian
        val locale = activeSpeechLocale
        lifecycleScope.launch(Dispatchers.IO) {
            val db = app.container.database
            examples = voiceExamples(
                db.transactionDao().all(),
                db.categoryDao().allActive(),
                db.accountDao().all().filter { !it.deleted },
                slovenian = slovenian,
                defaults = listOf(R.string.voice_default_example_1, R.string.voice_default_example_2, R.string.voice_default_example_3)
                    .map { VoiceExample(c.getString(it), "", emptyList()) },
                decimalSeparator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator,
            )
        }
    }

    private fun saveParsed(app: MoneoApp) {
        val p = state.parsed ?: return
        lifecycleScope.launch {
            app.container.repository.saveTransaction(
                TransactionEntity(
                    type = p.type,
                    amountCents = p.amountCents!!,
                    date = p.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    comment = p.comment,
                    categoryUid = p.category?.uid,
                    accountUid = p.account?.uid,
                    source = TransactionSource.VOICE,
                    confirmed = true,
                )
            )
            state = state.copy(saved = true)
        }
    }

    @Composable
    private fun ExamplesCard(list: List<VoiceExample>) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.try_for_example), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEach { ex ->
                    Column {
                        Text(stringResource(R.string.quoted, ex.phrase), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val info = buildList {
                            if (ex.categoryTitle.isNotEmpty()) add("→ ${ex.categoryTitle}")
                            if (ex.alternatives.isNotEmpty()) add(getString(R.string.also_works, ex.alternatives.joinToString(", ")))
                        }.joinToString(" · ")
                        if (info.isNotEmpty()) {
                            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    /** Razumljivo sporočilo za napako prepoznave govora. */
    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            getString(R.string.err_network)
        SpeechRecognizer.ERROR_AUDIO -> getString(R.string.err_audio)
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> getString(R.string.err_server)
        SpeechRecognizer.ERROR_CLIENT -> getString(R.string.err_client)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.err_timeout)
        SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.err_no_match, speechContext().getString(R.string.voice_simple_example))
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> getString(R.string.err_busy)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.err_permission)
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> getString(R.string.err_too_many)
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            getString(R.string.err_language, activeSpeechLocale.getDisplayLanguage(uiLocale))
        else -> getString(R.string.err_other, error)
    }

    override fun onDestroy() {
        session++
        recognizer?.destroy()
        super.onDestroy()
    }

    private companion object {
        /** Kolikokrat tiho ponovimo ob kratkotrajni napaki prepoznave. */
        const val MAX_SILENT_RETRIES = 2

        /** Jezik prepoznave, kadar jezik aplikacije ni na voljo. */
        val FALLBACK_LOCALE: Locale = Locale.US
    }
}
