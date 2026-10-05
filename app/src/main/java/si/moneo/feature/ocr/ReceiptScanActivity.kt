package si.moneo.feature.ocr

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import si.moneo.MoneoApp
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.domain.receipt.OcrLine
import si.moneo.domain.receipt.ReceiptParser
import si.moneo.domain.receipt.ReceiptResult
import si.moneo.ui.components.DateField
import si.moneo.ui.components.DropdownField
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.centsToInput
import si.moneo.ui.formatCents
import si.moneo.ui.parseCents
import si.moneo.ui.theme.MoneoTheme
import si.moneo.ui.theme.ThemePrefs
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR računov: slikaš račun (ali izbereš iz galerije) -> ML Kit prepozna besedilo (na napravi) ->
 * [ReceiptParser] najde skupni znesek, datum računa in trgovino -> predlog kategorije ->
 * uporabnik preveri/popravi in shrani (skupaj s pomanjšano sliko računa).
 */
class ReceiptScanActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(si.moneo.ui.AppLocale.wrap(newBase))
    }

    private var status by mutableStateOf(str(R.string.receipt_start))
    private var busy by mutableStateOf(false)
    private var imageUri by mutableStateOf<Uri?>(null)
    private var preview by mutableStateOf<Bitmap?>(null)

    // Rezultat prepoznave in obrazec za popravke
    private var result by mutableStateOf<ReceiptResult?>(null)
    private var amountText by mutableStateOf("")
    private var date by mutableStateOf(LocalDate.now())
    private var comment by mutableStateOf("")
    private var categoryUid by mutableStateOf<String?>(null)
    private var accountUid by mutableStateOf<String?>(null)
    private var categories by mutableStateOf<List<CategoryEntity>>(emptyList())
    private var accounts by mutableStateOf<List<AccountEntity>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        si.moneo.ui.L10n.init(this)
        val app = application as MoneoApp
        lifecycleScope.launch(Dispatchers.IO) {
            categories = app.container.database.categoryDao().allActive().filter { it.type == TransactionType.EXPENSE }
            accounts = app.container.database.accountDao().all().filter { !it.deleted }
        }

        setContent {
            MoneoTheme(ThemePrefs.load(this), ThemePrefs.loadAccent(this)) {
                val photoUri = remember { mutableStateOf<Uri?>(null) }
                val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                    val uri = photoUri.value
                    if (ok && uri != null) recognize(uri) else status = getString(R.string.photo_cancelled)
                }
                val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) recognize(uri)
                }

                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .statusBarsPadding().navigationBarsPadding().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.receipt_scanner), style = MaterialTheme.typography.headlineSmall)

                        Surface(
                            shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(if (result == null) 320.dp else 200.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val bmp = preview
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), stringResource(R.string.receipt_image), Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.xl)), contentScale = ContentScale.Crop)
                                } else {
                                    IconBadge(Icons.Rounded.DocumentScanner, MaterialTheme.colorScheme.primary, size = 88.dp)
                                }
                                if (busy) CircularProgressIndicator()
                            }
                        }

                        Text(status, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (result != null && !busy) ReviewForm(onSave = { save(app) })

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            PillButton(stringResource(R.string.take_photo), Icons.Rounded.PhotoCamera, {
                                val file = File(cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(this@ReceiptScanActivity, "$packageName.fileprovider", file)
                                photoUri.value = uri
                                takePicture.launch(uri)
                            }, Modifier.weight(1f), filled = result == null, enabled = !busy)
                            PillButton(stringResource(R.string.gallery), Icons.Rounded.PhotoLibrary, {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, Modifier.weight(1f), enabled = !busy)
                        }
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.close)) }
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
        }
    }

    /** Pregled in popravek pred shranjevanjem: znesek (z drugimi zneski z računa), datum, trgovina, kategorija, račun. */
    @OptIn(ExperimentalLayoutApi::class)
    @androidx.compose.runtime.Composable
    private fun ReviewForm(onSave: () -> Unit) {
        val r = result ?: return
        val cents = parseCents(amountText) ?: 0
        SoftCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.check_and_fix), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    amountText, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) amountText = v },
                    label = { Text(stringResource(R.string.amount_eur)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth(),
                )
                if (r.candidates.isNotEmpty()) {
                    Text(stringResource(R.string.other_amounts), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOfNotNull(r.totalCents) + r.candidates).forEach { c ->
                            val selected = c == cents
                            Surface(
                                onClick = { amountText = centsToInput(c) },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(formatCents(c), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                DateField(stringResource(R.string.receipt_date) + if (r.date == null) stringResource(R.string.not_read) else "", date, { if (it != null) date = it }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    comment, { comment = it }, label = { Text(stringResource(R.string.shop_note)) }, singleLine = true,
                    shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(
                        stringResource(R.string.category),
                        listOf<Pair<String?, String>>(null to stringResource(R.string.none)) + categories.map { it.uid to it.title },
                        categoryUid, { categoryUid = it }, Modifier.weight(1f),
                    )
                    DropdownField(
                        stringResource(R.string.account),
                        listOf<Pair<String?, String>>(null to stringResource(R.string.default_short)) + accounts.map { it.uid to it.title },
                        accountUid, { accountUid = it }, Modifier.weight(1f),
                    )
                }
                PillButton(stringResource(R.string.save), Icons.Rounded.Check, onSave, Modifier.fillMaxWidth().height(56.dp), filled = true, enabled = cents > 0)
            }
        }
    }

    private fun recognize(uri: Uri) {
        busy = true
        imageUri = uri
        result = null
        status = getString(R.string.recognizing_text)
        val app = application as MoneoApp
        lifecycleScope.launch {
            try {
                preview = withContext(Dispatchers.IO) { decodeScaled(uri, 1000) }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val text = recognizer.process(InputImage.fromFilePath(this@ReceiptScanActivity, uri)).await()
                // Vrstice s položajem -> vrstice računa (oznaka in znesek v isti vrstici)
                val lines = text.textBlocks.flatMap { b ->
                    b.lines.mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.left, it.top, it.right, it.bottom) } }
                }
                val rows = if (lines.isNotEmpty()) ReceiptParser.rows(lines) else text.text.lines()
                val parsed = withContext(Dispatchers.Default) { ReceiptParser.parse(rows) }

                result = parsed
                amountText = parsed.totalCents?.let(::centsToInput).orEmpty()
                date = parsed.date ?: LocalDate.now()
                comment = parsed.merchant.orEmpty()
                // Kategorija: pretekli vnosi pri tej trgovini, sicer ključne besede (Mercator -> Groceries ...)
                val repo = app.container.repository
                categoryUid = parsed.merchant?.let { repo.suggestCategoryForComment(it, TransactionType.EXPENSE) }
                    ?: repo.suggestCategoryForComment(rows.take(4).joinToString(" "), TransactionType.EXPENSE)
                status = when {
                    parsed.totalCents == null -> getString(R.string.total_not_found)
                    else -> getString(R.string.found_check)
                }
            } catch (e: Exception) {
                status = getString(R.string.recognition_error, e.message.orEmpty())
            } finally {
                busy = false
            }
        }
    }

    private fun save(app: MoneoApp) {
        val amount = parseCents(amountText)?.takeIf { it > 0 } ?: return
        lifecycleScope.launch {
            val uid = UUID.randomUUID().toString()
            // Pomanjšana kopija slike v notranjem pomnilniku (cache se lahko kadar koli pobriše)
            val path = withContext(Dispatchers.IO) {
                imageUri?.let { uri ->
                    runCatching {
                        val dir = File(filesDir, "receipts").apply { mkdirs() }
                        val out = File(dir, "$uid.jpg")
                        decodeScaled(uri, 1600)?.let { bmp ->
                            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                            out.absolutePath
                        }
                    }.getOrNull()
                }
            }
            app.container.repository.saveTransaction(
                TransactionEntity(
                    uid = uid,
                    type = TransactionType.EXPENSE,
                    amountCents = amount,
                    date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    comment = comment.trim().ifBlank { getString(R.string.receipt) },
                    categoryUid = categoryUid,
                    accountUid = accountUid,
                    source = TransactionSource.OCR,
                    // Uporabnik je podatke že pregledal v obrazcu
                    confirmed = true,
                    attachmentPath = path,
                ),
            )
            finish()
        }
    }

    private fun decodeScaled(uri: Uri, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // Fotoaparat sliko pogosto shrani "postrani" in zasuk zapiše le v EXIF
        val degrees = runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { exception -> cont.resumeWithException(exception) }
        addOnCanceledListener { cont.cancel() }
    }
