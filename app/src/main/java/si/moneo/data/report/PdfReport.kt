package si.moneo.data.report

import si.moneo.ui.theme.ensureContrast
import si.moneo.R
import si.moneo.ui.str
import si.moneo.ui.qty
import si.moneo.ui.fmtDate
import si.moneo.ui.fmtDayMonth
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import si.moneo.ui.CategorySpend
import si.moneo.ui.PeriodBucket
import si.moneo.ui.TransactionUi
import si.moneo.ui.formatCents
import si.moneo.ui.theme.ThemePrefs
import si.moneo.ui.theme.accentFor
import si.moneo.ui.theme.appColors
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/** Podatki za poročilo izbranega obdobja (iz Statistike). */
data class PeriodReport(
    val periodLabel: String,
    val accountLabel: String?,
    val bucket: PeriodBucket,
    val previous: PeriodBucket?,
    val history: List<PeriodBucket>,
    val expenses: List<CategorySpend>,
    val income: List<CategorySpend>,
    val budgets: Map<String, Long>,
    val biggest: List<TransactionUi>,
    val transactionCount: Int,
)

/**
 * Izriše poročilo v PDF (A4) z Android PdfDocument - brez zunanjih knjižnic.
 * Vrne datoteko v predpomnilniku (za deljenje prek FileProviderja).
 */
object PdfReport {
    private const val W = 595f // A4 v točkah
    private const val H = 842f
    private const val M = 40f

    fun write(context: Context, r: PeriodReport): File {
        val (scheme, finance) = appColors(ThemePrefs.loadAccent(context), dark = false, context = context)
        val accent = scheme.primary.toArgb()
        val income = finance.income.toArgb()
        val expense = finance.expense.toArgb()
        val text = 0xFF111418.toInt()
        val muted = 0xFF6B7280.toInt()
        val line = 0xFFE5E7EB.toInt()

        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun paint(size: Float, color: Int = text, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
        }
        fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

        fun footer() {
            c.drawText(str(R.string.app_name) + " · ${r.periodLabel}", M, H - 20f, paint(8f, muted))
            c.drawText(str(R.string.pdf_page, pageNo), W - M, H - 20f, paint(8f, muted, align = Paint.Align.RIGHT))
        }
        fun newPage() {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            c = page.canvas
            y = M
        }
        /** Poskrbi za prostor [h]; sicer nova stran. */
        fun need(h: Float) { if (y + h > H - 50f) newPage() }
        fun section(title: String) {
            need(40f)
            y += 18f
            c.drawText(title, M, y, paint(13f, text, bold = true))
            y += 10f
            c.drawLine(M, y, W - M, y, fill(line))
            y += 14f
        }

        newPage()

        // Glava
        c.drawRect(0f, 0f, W, 6f, fill(accent))
        y += 14f
        c.drawText(str(R.string.pdf_title), M, y, paint(22f, text, bold = true))
        y += 20f
        c.drawText(r.periodLabel + (r.accountLabel?.let { str(R.string.pdf_account, it) } ?: ""), M, y, paint(12f, muted))
        c.drawText(str(R.string.pdf_created, LocalDate.now().fmtDate()), W - M, y, paint(9f, muted, align = Paint.Align.RIGHT))
        y += 24f

        // Povzetek v štirih okvirjih
        val b = r.bucket
        val net = b.incomeCents - b.expenseCents
        val rate = if (b.incomeCents > 0) ((net.toDouble() / b.incomeCents) * 100).roundToInt() else null
        val boxes = listOf(
            Triple(str(R.string.income_plural), formatCents(b.incomeCents), income),
            Triple(str(R.string.expenses), formatCents(b.expenseCents), expense),
            Triple(str(R.string.difference), formatCents(net), if (net >= 0) income else expense),
            Triple(str(R.string.savings), rate?.let { str(R.string.percent, it) } ?: "—", accent),
        )
        val bw = (W - 2 * M - 3 * 10f) / 4
        boxes.forEachIndexed { i, (label, value, color) ->
            val x = M + i * (bw + 10f)
            c.drawRoundRect(RectF(x, y, x + bw, y + 58f), 10f, 10f, fill(0xFFF5F6F7.toInt()))
            c.drawText(label, x + 10f, y + 20f, paint(9f, muted))
            c.drawText(value, x + 10f, y + 42f, paint(13f, color, bold = true))
        }
        y += 72f
        r.previous?.let { p ->
            fun delta(cur: Long, prev: Long) = if (prev <= 0) "—" else (if (cur >= prev) "+" else "") + str(R.string.percent, (((cur - prev).toDouble() / prev) * 100).roundToInt())
            c.drawText(
                str(
                    R.string.pdf_compared, p.longLabel, delta(b.incomeCents, p.incomeCents), delta(b.expenseCents, p.expenseCents),
                    qty(R.plurals.entries_count, r.transactionCount, r.transactionCount),
                ),
                M, y, paint(9f, muted),
            )
            y += 8f
        }

        // Stolpčni graf zadnjih obdobij
        if (r.history.isNotEmpty()) {
            section(str(R.string.pdf_history))
            val chartH = 120f
            need(chartH + 30f)
            val max = r.history.maxOf { maxOf(it.incomeCents, it.expenseCents) }.coerceAtLeast(1)
            val slot = (W - 2 * M) / r.history.size
            val barW = minOf(18f, slot / 3)
            r.history.forEachIndexed { i, h ->
                val cx = M + slot * i + slot / 2
                val base = y + chartH
                val ih = chartH * h.incomeCents / max
                val eh = chartH * h.expenseCents / max
                c.drawRoundRect(RectF(cx - barW - 1f, base - ih, cx - 1f, base), 3f, 3f, fill(income))
                c.drawRoundRect(RectF(cx + 1f, base - eh, cx + barW + 1f, base), 3f, 3f, fill(expense))
                val selected = h.start == b.start
                c.drawText(h.label, cx, base + 14f, paint(8f, if (selected) text else muted, bold = selected, align = Paint.Align.CENTER))
            }
            c.drawLine(M, y + chartH, W - M, y + chartH, fill(line))
            y += chartH + 26f
        }

        fun categoryTable(list: List<CategorySpend>, total: Long, budgets: Map<String, Long>) {
            list.forEach { s ->
                need(26f)
                // Pika na beli strani: vsaj 3:1 (WCAG za grafične elemente)
                val color = accentFor(s.category.title, s.category.color).ensureContrast(androidx.compose.ui.graphics.Color.White, 3f).toArgb()
                c.drawCircle(M + 5f, y - 4f, 4f, fill(color))
                c.drawText(s.category.title, M + 16f, y, paint(10f, text))
                c.drawText(formatCents(s.totalCents), W - M, y, paint(10f, text, bold = true, align = Paint.Align.RIGHT))
                val pct = if (total > 0) (s.totalCents * 100f / total).roundToInt() else 0
                val budget = budgets[s.category.uid]
                val info = if (budget != null) {
                    str(R.string.pdf_budget_info, str(R.string.percent, pct), formatCents(budget), str(R.string.percent, (s.totalCents * 100f / budget).roundToInt()))
                } else {
                    str(R.string.percent, pct) + " · " + qty(R.plurals.entries_count, s.count, s.count)
                }
                c.drawText(info, W - M - 90f, y, paint(8f, muted, align = Paint.Align.RIGHT))
                y += 6f
                val barMax = W - 2 * M - 16f
                c.drawRoundRect(RectF(M + 16f, y, M + 16f + barMax, y + 4f), 2f, 2f, fill(line))
                c.drawRoundRect(RectF(M + 16f, y, M + 16f + barMax * s.share.coerceIn(0f, 1f), y + 4f), 2f, 2f, fill(color))
                y += 16f
            }
        }

        if (r.expenses.isNotEmpty()) {
            section(str(R.string.expenses_by_category))
            categoryTable(r.expenses, b.expenseCents, r.budgets)
        }
        if (r.income.isNotEmpty()) {
            section(str(R.string.income_by_category))
            categoryTable(r.income, b.incomeCents, emptyMap())
        }
        if (r.biggest.isNotEmpty()) {
            section(str(R.string.biggest_expenses))
            r.biggest.forEach { tx ->
                need(18f)
                c.drawText(tx.date.fmtDayMonth(), M, y, paint(10f, muted))
                val title = tx.title + if (tx.comment.isNotBlank() && tx.categoryTitle != null) " · ${tx.comment}" else ""
                c.drawText(title.take(70), M + 50f, y, paint(10f, text))
                c.drawText(formatCents(tx.amountCents), W - M, y, paint(10f, text, bold = true, align = Paint.Align.RIGHT))
                y += 16f
            }
        }

        footer()
        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safe = r.periodLabel.replace(Regex("""[^\p{L}\p{N}]+"""), "-").trim('-')
        val file = File(dir, "Moneo-$safe.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
