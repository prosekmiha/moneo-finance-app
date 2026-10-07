package si.moneo.ui.components

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import si.moneo.ui.fmtDate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.TransactionUi
import si.moneo.ui.TransferUi
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import si.moneo.ui.theme.asGraphic
import si.moneo.ui.theme.asText
import androidx.compose.ui.graphics.compositeOver

/** Snackbar, ki ga zagotovi MainActivity - za "Razveljavi" in potrditve. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Zaobljena kartica (osnovni gradnik dizajna). `elevated`: v svetlem načinu mehka senca,
 * v temnem tanka obroba, da se kartica loči od ozadja; za kartice znotraj kartic `false`.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.lg),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    elevated: Boolean = color != Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.98f) else Modifier)
            .softElevation(shape, elevated).clip(shape)
            .then(if (onClick != null) Modifier.clickable(interaction, LocalIndication.current, onClick = onClick) else Modifier),
        color = color,
        shape = shape,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Mehka senca (svetlo) oz. tanka obroba (temno) - skupna globina kartic in plavajočih elementov. */
@Composable
fun Modifier.softElevation(shape: Shape, enabled: Boolean = true, elevation: Dp = 6.dp): Modifier {
    if (!enabled) return this
    return if (Finance.colors.isDark) {
        border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), shape)
    } else {
        shadow(
            elevation, shape, clip = false,
            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.10f),
        )
    }
}

/**
 * Ikona kategorije izbrana po ključnih besedah naslova: slovenske in angleške so v kodi,
 * besede drugih jezikov pa v prevodih (icon_kw_*), da ikone delujejo v vseh jezikih.
 */
fun categoryIconFor(title: String?, type: TransactionType? = null): ImageVector? {
    val t = title?.lowercase() ?: return null
    fun has(@androidx.annotation.StringRes kw: Int, vararg k: String): Boolean {
        val localized = if (si.moneo.ui.L10n.ready) str(kw).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() } else emptyList()
        return k.any { t.contains(it) } || localized.any { t.contains(it) }
    }
    return when {
        has(R.string.icon_kw_restaurant, "restavr", "kosilo", "večerj", "hitra hrana", "pica", "burger", "restaurant", "lunch", "dinner", "pizza", "fast food") -> Icons.Rounded.Restaurant
        has(R.string.icon_kw_cafe, "kava", "kavarn", "bar", "pijač", "pivo", "coffee", "cafe", "drink", "beer") -> Icons.Rounded.LocalCafe
        has(R.string.icon_kw_groceries, "hrana", "malic", "živil", "trgovin", "market", "spar", "mercator", "lidl", "hofer", "food", "grocer", "snack") -> Icons.Rounded.ShoppingBasket
        has(R.string.icon_kw_fuel, "goriv", "bencin", "dizel", "petrol", "fuel", "gas", "diesel") -> Icons.Rounded.LocalGasStation
        has(R.string.icon_kw_car, "avto", "parkir", "cestnin", "vinjet", "servis", "car", "parking", "toll") -> Icons.Rounded.DirectionsCar
        has(R.string.icon_kw_transport, "prevoz", "bus", "vlak", "taxi", "taksi", "transport", "train", "ticket") -> Icons.Rounded.DirectionsBus
        has(R.string.icon_kw_home, "stanovan", "najemn", "dom", "hiša", "home", "rent", "house", "flat") -> Icons.Rounded.Home
        has(R.string.icon_kw_utilities, "elektri", "ogrevan", "komunal", "položnic", "voda", "electric", "heating", "utilit", "water") -> Icons.Rounded.Bolt
        has(R.string.icon_kw_phone, "telefon", "internet", "mobil", "phone") -> Icons.Rounded.PhoneAndroid
        has(R.string.icon_kw_health, "zdrav", "lekarn", "zobo", "health", "pharma", "doctor", "dentist") -> Icons.Rounded.LocalHospital
        has(R.string.icon_kw_sport, "šport", "fitnes", "rekreac", "sport", "gym", "fitness") -> Icons.Rounded.FitnessCenter
        has(R.string.icon_kw_fun, "zabav", "kino", "koncert", "igre", "hobi", "prosti čas", "fun", "cinema", "concert", "game", "hobby", "leisure", "entertain") -> Icons.Rounded.Celebration
        has(R.string.icon_kw_clothes, "oblač", "oblek", "čevlj", "moda", "cloth", "shoe", "fashion") -> Icons.Rounded.Checkroom
        has(R.string.icon_kw_shopping, "nakup", "shopping") -> Icons.Rounded.ShoppingBag
        has(R.string.icon_kw_gift, "daril", "gift", "present") -> Icons.Rounded.CardGiftcard
        has(R.string.icon_kw_travel, "potovan", "dopust", "hotel", "letal", "travel", "holiday", "vacation", "flight") -> Icons.Rounded.Flight
        has(R.string.icon_kw_education, "izobra", "šola", "tečaj", "knjig", "študij", "educat", "school", "course", "book", "stud") -> Icons.Rounded.School
        has(R.string.icon_kw_kids, "otro", "vrtec", "kid", "child", "baby") -> Icons.Rounded.ChildCare
        has(R.string.icon_kw_pets, "ljubljen", "žival", "pes", "mačk", "pet", "dog", "cat ") -> Icons.Rounded.Pets
        has(R.string.icon_kw_subscriptions, "naročnin", "netflix", "spotify", "streaming", "subscription") -> Icons.Rounded.Subscriptions
        has(R.string.icon_kw_insurance, "zavarov", "insurance") -> Icons.Rounded.Shield
        has(R.string.icon_kw_bills, "račun", "davk", "pristojb", "bill", "tax", "fee") -> Icons.Rounded.Receipt
        has(R.string.icon_kw_salary, "plač", "dohod", "honorar", "salary", "wage", "income", "pay") -> Icons.Rounded.Payments
        has(R.string.icon_kw_work, "delo", "služb", "work", "job") -> Icons.Rounded.Work
        has(R.string.icon_kw_investments, "obrest", "investic", "dividend", "delnic", "interest", "invest", "stock") -> Icons.AutoMirrored.Rounded.TrendingUp
        has(R.string.icon_kw_savings, "varčev", "prihran", "saving") -> Icons.Rounded.Savings
        has(R.string.icon_kw_bonus, "bonus", "nagrad", "regres", "reward", "award") -> Icons.Rounded.EmojiEvents
        has(R.string.icon_kw_sale, "prodaj", "sale", "sold") -> Icons.Rounded.Sell
        has(R.string.icon_kw_other, "ostal", "drug", "razno", "other", "misc") -> Icons.Rounded.Category
        else -> if (type == TransactionType.INCOME) Icons.Rounded.Payments else null
    }
}

/** Krog s pastelnim ozadjem in ikono (ali začetnico) kategorije. */
@Composable
fun CategoryIcon(
    title: String?,
    color: Int? = null,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    type: TransactionType? = null,
    emoji: String? = null,
) {
    val accent = accentFor(title ?: "?", color)
    val circle = accent.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surface)
    Box(
        modifier.size(size).clip(CircleShape).background(circle),
        contentAlignment = Alignment.Center,
    ) {
        val icon = if (emoji == null) categoryIconFor(title, type) else null
        when {
            emoji != null -> Text(emoji, style = MaterialTheme.typography.titleMedium)
            icon != null -> Icon(icon, null, tint = accent.asGraphic(on = circle), modifier = Modifier.size(size * 0.5f))
            else -> Text(
                (title?.firstOrNull() ?: '?').uppercase(),
                color = accent.asText(on = circle),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Majhen krog z ikono (za nastavitve, nasvete ...). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val circle = tint.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surface)
    Box(
        modifier.size(size).clip(CircleShape).background(circle),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint.asGraphic(on = circle), modifier = Modifier.size(size * 0.5f)) }
}

fun signedAmount(tx: TransactionUi): String =
    (if (tx.type == TransactionType.EXPENSE) "−" else "+") + formatCents(tx.amountCents)

@Composable
fun TransactionItem(
    tx: TransactionUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    showDate: Boolean = false,
) {
    val colors = Finance.colors
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(tx.categoryTitle ?: tx.comment.ifBlank { null }, tx.categoryColor, type = tx.type)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(tx.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = buildList {
                if (showDate) add(tx.date.fmtDate())
                if (tx.comment.isNotBlank() && tx.categoryTitle != null) add(tx.comment)
                if (tx.comment.isBlank() || tx.categoryTitle == null) tx.accountTitle?.let { add(it) }
                sourceLabel(tx.source)?.let { add(it) }
                tx.tags.forEach { add("#$it") }
            }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedAmount(tx),
                color = if (tx.type == TransactionType.INCOME) colors.income else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!tx.confirmed) {
                Text(
                    stringResource(R.string.unconfirmed),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                    modifier = Modifier.clip(CircleShape).background(colors.warningBg).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

fun sourceLabel(source: TransactionSource): String? = when (source) {
    TransactionSource.MANUAL -> null
    TransactionSource.VOICE -> str(R.string.source_voice)
    TransactionSource.NOTIFICATION -> str(R.string.source_notification)
    TransactionSource.OCR -> str(R.string.source_ocr)
    TransactionSource.RECURRING -> str(R.string.source_recurring)
    TransactionSource.IMPORT -> null
    TransactionSource.SUBSCRIPTION -> str(R.string.source_subscription)
}

/** Zaobljen svetel gumb z zeleno ikono (Top up / Withdraw iz dizajna). */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val bg = if (filled) tint else MaterialTheme.colorScheme.surface
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else tint
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) bg else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(52.dp).pressScale(interaction),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = if (enabled) fg else MaterialTheme.colorScheme.onSurfaceVariant
            if (icon != null) {
                Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(text, color = c, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Segmentni izbirnik z drsečim temnim indikatorjem (Week / Month / Quarter). */
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    selectedColor: Color = Finance.colors.selectedTab,
    onSelectedColor: Color = Finance.colors.onSelectedTab,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Radius.sm))
            .background(trackColor).padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        val indicatorColor by animateColorAsState(selectedColor, label = "segc")
        Box(
            Modifier.offset(x = offset).width(segment).fillMaxHeight()
                .clip(RoundedCornerShape(Radius.xs)).background(indicatorColor),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, option ->
                val textColor by animateColorAsState(
                    if (i == index) onSelectedColor else unselectedTextColor, label = "segt",
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Radius.xs))
                        .selectable(selected = i == index, role = androidx.compose.ui.semantics.Role.Tab) {
                            if (i != index) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), color = textColor, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Ploščica "Money in / Money out" s puščico. */
@Composable
fun MoneyInOutTile(
    label: String,
    cents: Long,
    income: Boolean,
    modifier: Modifier = Modifier,
    deltaPct: Int? = null,
) {
    val colors = Finance.colors
    val accent = if (income) colors.income else colors.expense
    SoftCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (income) colors.incomeBg else colors.expenseBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (income) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedAmount(cents, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
            }
        }
        if (deltaPct != null) {
            Spacer(Modifier.height(6.dp))
            DeltaChip(deltaPct, goodWhenUp = income)
        }
    }
}

/** "↑ 12 %" - zeleno, če je sprememba dobra (več prihodkov / manj stroškov). */
@Composable
fun DeltaChip(pct: Int, goodWhenUp: Boolean, modifier: Modifier = Modifier) {
    val colors = Finance.colors
    val good = if (goodWhenUp) pct >= 0 else pct <= 0
    val c = if (pct == 0) MaterialTheme.colorScheme.onSurfaceVariant else if (good) colors.income else colors.expense
    Text(
        "${if (pct > 0) "↑" else if (pct < 0) "↓" else "="} " + stringResource(R.string.percent, kotlin.math.abs(pct)),
        style = MaterialTheme.typography.labelSmall,
        color = c,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(CircleShape).background(c.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyIllustration(icon)
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Vrhnja vrstica podzaslonov: puščica nazaj, sredinski naslov, opcijska akcija desno. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = Spacing.md)) {
        if (onBack != null) {
            // Krog kot gumb za iskanje na Domov - enoten videz ikonskih gumbov v glavah
            Surface(
                onClick = onBack, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.CenterStart).size(44.dp).softElevation(CircleShape, elevation = 3.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            }
        }
        Text(
            title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        )
        Row(Modifier.align(Alignment.CenterEnd), content = actions)
    }
}

/** Vrstica v nastavitvah: ikona, naslov, podnaslov, desno chevron ali poljubna vsebina. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    // Zadnji parameter: trailing lambda `SettingsRow(...) { }` je klik, ne sestavljiva vsebina.
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tanek zaobljen progress bar. */
@Composable
fun SlimProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val animated = rememberGrowFrom0(fraction.coerceIn(0f, 1f))
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** Vrstica prenosa med računi (nevtralna barva - ni prihodek ne strošek). */
@Composable
fun TransferItem(t: TransferUi, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val blue = Finance.colors.transfer
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Rounded.SwapHoriz, blue, size = 44.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.transfer), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull("${t.fromTitle ?: "?"} → ${t.toTitle ?: "?"}", t.comment.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(formatCents(t.amountCents), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = blue)
    }
}

/** Čipi za filtriranje po računu ("Vsi" + posamezni računi). Skrit, če je račun en sam. */
@Composable
fun AccountFilterRow(
    accounts: List<si.moneo.data.db.entity.AccountEntity>,
    selectedUid: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.screen),
) {
    if (accounts.size < 2) return
    androidx.compose.foundation.lazy.LazyRow(
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { FilterPill(stringResource(R.string.all_accounts), selectedUid == null) { onSelect(null) } }
        items(accounts.size) { i ->
            val acc = accounts[i]
            FilterPill(acc.title, selectedUid == acc.uid, dot = accentFor(acc.title, acc.color)) {
                onSelect(if (selectedUid == acc.uid) null else acc.uid)
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val colors = Finance.colors
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.selectedTab else MaterialTheme.colorScheme.surface,
        modifier = Modifier.pressScale(interaction, 0.94f),
        interactionSource = interaction,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                val pill = if (selected) colors.selectedTab else MaterialTheme.colorScheme.surface
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot.asGraphic(on = pill)))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onSelectedTab else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Ilustracija praznega stanja: mehki koncentrični krogi, plavajoči "kovanci" in ikona v sredini. */
@Composable
private fun EmptyIllustration(icon: ImageVector) {
    val primary = MaterialTheme.colorScheme.primary
    val colors = Finance.colors
    val float = rememberInfiniteTransition(label = "float")
    val dy by float.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "dy",
    )
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(150.dp)) {
            val c = center
            drawCircle(primary.copy(alpha = 0.05f), radius = size.minDimension / 2f, center = c)
            drawCircle(primary.copy(alpha = 0.08f), radius = size.minDimension / 2.8f, center = c)
            // "kovanci" okoli
            val r = 9.dp.toPx()
            drawCircle(colors.warning.copy(alpha = 0.85f), r, androidx.compose.ui.geometry.Offset(c.x - 52.dp.toPx(), c.y - 38.dp.toPx() + dy))
            drawCircle(colors.income.copy(alpha = 0.7f), r * 0.7f, androidx.compose.ui.geometry.Offset(c.x + 56.dp.toPx(), c.y - 22.dp.toPx() - dy))
            drawCircle(colors.expense.copy(alpha = 0.6f), r * 0.55f, androidx.compose.ui.geometry.Offset(c.x + 40.dp.toPx(), c.y + 50.dp.toPx() + dy))
            drawCircle(primary.copy(alpha = 0.5f), r * 0.45f, androidx.compose.ui.geometry.Offset(c.x - 44.dp.toPx(), c.y + 46.dp.toPx() - dy))
        }
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = primary, modifier = Modifier.size(32.dp)) }
        }
    }
}
