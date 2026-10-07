package si.moneo.ui.home

import si.moneo.ui.theme.underWhiteText
import si.moneo.ui.theme.asGraphic
import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import si.moneo.ui.qty
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import si.moneo.ui.fmt
import si.moneo.ui.components.softElevation
import si.moneo.ui.components.rememberStagger
import si.moneo.ui.components.staggerItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.ui.UpcomingKind
import si.moneo.ui.components.LocalSnackbar
import si.moneo.ui.favorites.FavoriteEditTarget
import si.moneo.ui.favorites.FavoriteEditorSheet
import si.moneo.ui.favorites.FavoritesRow
import si.moneo.ui.components.IconBadge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.AccountBalance
import si.moneo.ui.BudgetStatus
import si.moneo.ui.GoalUi
import si.moneo.ui.HomeUiState
import si.moneo.ui.MainViewModel
import si.moneo.ui.TransactionUi
import si.moneo.ui.TransferUi
import si.moneo.ui.HOME_PERIODS
import si.moneo.ui.DailyAverage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import si.moneo.ui.StatsPeriod
import si.moneo.ui.balanceTitle
import si.moneo.ui.homePeriodLabel
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.AccountFilterRow
import si.moneo.ui.components.TransferItem
import si.moneo.ui.components.AnimatedAmount
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.ProgressRing
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SlimProgress
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.Sparkline
import si.moneo.ui.components.TransactionItem
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.LightFinanceColors
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale


@Composable
fun HomeScreen(
    vm: MainViewModel,
    onEdit: (TransactionUi) -> Unit,
    onEditTransfer: (TransferUi) -> Unit,
    onSearch: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onNewGoal: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenYearReview: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by vm.home.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val overallBudget by vm.overallBudget.collectAsStateWithLifecycle()
    val layout by vm.homeLayout.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.onAppResumed()
        onPauseOrDispose {}
    }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var favoriteEditor by remember { mutableStateOf<FavoriteEditTarget?>(null) }
    val haptic = LocalHapticFeedback.current

    /** Tap na priljubljen vnos: takoj zapiši, z možnostjo razveljavitve. */
    fun addFavorite(f: FavoriteEntity) {
        scope.launch {
            val uid = vm.addFromFavorite(f)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val r = snackbar.showSnackbar(
                str(R.string.added_snackbar, f.title, formatCents(f.amountCents)), actionLabel = str(R.string.undo), duration = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) vm.delete(uid)
        }
    }

    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val accountFilter by vm.accountFilter.collectAsStateWithLifecycle()
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    // Tab Odhodki/Prihodki na kartici stanja določa tudi seznam kategorij spodaj (ob vrnitvi vedno odhodki)
    var listType by remember { mutableStateOf(TransactionType.EXPENSE) }
    // Vsote po kategorijah za izbrano obdobje, padajoče po znesku
    val groups = remember(state.transactions, listType) { categoryGroups(state.transactions, listType) }
    // Razprte kategorije se ob menjavi obdobja, tipa ali računa zaprejo
    var expanded by remember(state.period, state.start, listType, accountFilter) { mutableStateOf(emptySet<String>()) }
    var transfersOpen by remember(state.period, state.start, accountFilter) { mutableStateOf(false) }

    // Kartice ob odprtju zaslona "pridrsijo" ena za drugo
    val stagger = rememberStagger()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        stagger.reset()
        staggerItem(stagger, key = "header") { Header(onSearch) }
        staggerItem(stagger, key = "month") {
            Column(Modifier.padding(horizontal = Spacing.screen)) {
                SegmentedTabs(
                    options = HOME_PERIODS,
                    selected = state.period,
                    onSelect = vm::setHomePeriod,
                    label = { it.label },
                    modifier = Modifier.padding(top = Spacing.md),
                )
                PeriodSwitcher(state.period, state.start, state.isCurrent, vm::prevPeriod, vm::nextPeriod)
            }
        }
        if (accounts.size > 1) {
            staggerItem(stagger, key = "account-filter") {
                AccountFilterRow(accounts, accountFilter, { vm.accountFilter.value = it }, Modifier.padding(bottom = Spacing.md))
            }
        }
        staggerItem(stagger, key = "hero") {
            HeroPager(state, hidden, listType, onTypeChange = { listType = it }, onToggleHidden = { vm.setHideBalance(!hidden) })
        }

        if (state.unconfirmed.isNotEmpty()) {
            staggerItem(stagger, key = "unconfirmed") {
                UnconfirmedBanner(
                    count = state.unconfirmed.size,
                    onConfirmAll = { state.unconfirmed.forEach { vm.confirm(it.uid) } },
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 6.dp),
                )
            }
        }

        // Kartice v vrstnem redu, ki ga izbere uporabnik (Več -> Videz -> Domača stran)
        layout.filter { it.visible }.forEach { entry ->
            when (entry.section) {
                HomeSection.BUDGETS -> {
                    overallBudget?.takeIf { it.fraction >= 0.8f }?.let { b ->
                        staggerItem(stagger, key = "overall-budget") {
                            OverallBudgetCard(b, onClick = onOpenCategories, Modifier.padding(horizontal = Spacing.screen, vertical = 6.dp))
                        }
                    }
                    items(state.budgetAlerts.take(2), key = { "budget-" + it.category.uid }) { alert ->
                        BudgetAlertCard(alert, onClick = { onOpenCategory(alert.category.uid) }, Modifier.padding(horizontal = Spacing.screen, vertical = 6.dp))
                    }
                }
                HomeSection.FAVORITES -> staggerItem(stagger, key = "favorites") {
                    FavoritesRow(
                        favorites, categories,
                        onAdd = ::addFavorite,
                        onEdit = { favoriteEditor = FavoriteEditTarget(it) },
                        onNew = { favoriteEditor = FavoriteEditTarget(null) },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
                HomeSection.UPCOMING -> if (upcoming.isNotEmpty()) {
                    staggerItem(stagger, key = "upcoming") {
                        UpcomingCard(
                            upcoming, categories, hidden,
                            onOpen = { p ->
                                when (p.kind) {
                                    UpcomingKind.SUBSCRIPTION -> onOpenSubscriptions()
                                    UpcomingKind.DEBT_I_OWE, UpcomingKind.DEBT_OWED_TO_ME -> onOpenDebts()
                                    UpcomingKind.RECURRING -> onOpenRecurring()
                                }
                            },
                            modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg),
                        )
                    }
                }
                HomeSection.YEAR_REVIEW -> yearReviewPromoYear()?.let { year ->
                    staggerItem(stagger, key = "year-review") {
                        YearReviewPromo(year, onClick = { onOpenYearReview(year) }, Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg))
                    }
                }
                HomeSection.GOALS -> staggerItem(stagger, key = "goals") {
                    GoalsStrip(goals, onOpenGoals, onOpenGoal, onNewGoal, Modifier.padding(top = Spacing.lg))
                }
            }
        }

        item(key = "tx-header") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)
                    .monthSwipe(vm::prevPeriod, vm::nextPeriod, state.isCurrent),
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.outline),
                    )
                    SectionHeader(
                        stringResource(if (listType == TransactionType.EXPENSE) R.string.expenses_by_category else R.string.income_by_category),
                        Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
                        action = stringResource(R.string.search),
                        onAction = onSearch,
                    )
                }
            }
        }

        if (state.loaded && groups.isEmpty() && state.transfers.isEmpty()) {
            item(key = "empty") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().monthSwipe(vm::prevPeriod, vm::nextPeriod, state.isCurrent),
                ) {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        if (state.transactions.isEmpty()) emptyTitle(state.period, state.isCurrent)
                        else stringResource(if (listType == TransactionType.EXPENSE) R.string.no_expenses_in_period else R.string.no_income_in_period),
                        stringResource(R.string.empty_add_hint),
                    )
                }
            }
        }

        groups.forEach { g ->
            val open = g.key in expanded
            item(key = "cat-${g.key}") {
                CategoryGroupRow(
                    g, open, listType,
                    onClick = { expanded = if (open) expanded - g.key else expanded + g.key },
                    modifier = Modifier.animateItem(),
                )
            }
            // Vsi vnosi kategorije v izbranem obdobju (najnovejši najprej)
            if (open) {
                items(g.items, key = { "tx-" + it.uid }) { tx ->
                    SwipeableTransaction(
                        tx = tx,
                        onClick = { onEdit(tx) },
                        onConfirm = { vm.confirm(tx.uid) },
                        showDate = true,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        if (state.transfers.isNotEmpty()) {
            item(key = "transfers") {
                TransfersGroupRow(
                    state.transfers.size, state.transfers.sumOf { it.amountCents }, transfersOpen,
                    onClick = { transfersOpen = !transfersOpen },
                    modifier = Modifier.animateItem(),
                )
            }
            if (transfersOpen) {
                items(state.transfers.sortedByDescending { it.date }, key = { "tr-" + it.uid }) { t ->
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.animateItem()) {
                        TransferItem(t, onClick = { onEditTransfer(t) }, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }

        item(key = "sheet-end") {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().height(24.dp)) {}
        }
    }

    favoriteEditor?.let { target ->
        FavoriteEditorSheet(
            favorite = target.favorite,
            categories = categories,
            accounts = accounts,
            onDismiss = { favoriteEditor = null },
            onSave = { vm.saveFavorite(it); favoriteEditor = null },
            onDelete = target.favorite?.let { f -> { vm.deleteFavorite(f); favoriteEditor = null } },
        )
    }
}

@Composable
private fun Header(onSearch: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 11 -> stringResource(R.string.greeting_morning)
        hour < 18 -> stringResource(R.string.greeting_day)
        else -> stringResource(R.string.greeting_evening)
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = Spacing.screen, end = 8.dp, top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Finance.colors.heroGradient),
            contentAlignment = Alignment.Center,
        ) { Text("€", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text("$greeting 👋", style = MaterialTheme.typography.titleMedium)
            Text(
                LocalDate.now().fmt(R.string.fmt_day_full_no_year, "EEEE, d. MMMM").replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, onClick = onSearch, modifier = Modifier.size(44.dp).softElevation(CircleShape, elevation = 3.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, stringResource(R.string.search_a11y)) }
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun PeriodSwitcher(
    period: StatsPeriod,
    start: LocalDate,
    isCurrent: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val what = when (period) {
        StatsPeriod.DAY -> "dan"
        StatsPeriod.WEEK -> "teden"
        StatsPeriod.YEAR -> "leto"
        else -> "mesec"
    }
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.md).monthSwipe(onPrev, onNext, isCurrent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.prev_period))
                }
                AnimatedContent(
                    period to start,
                    transitionSpec = {
                        val dir = if (targetState.second > initialState.second) 1 else -1
                        (slideInHorizontally { it / 2 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 2 * dir } + fadeOut())
                    },
                    label = "period",
                ) { (p, st) ->
                    Text(
                        homePeriodLabel(p, st),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = !isCurrent, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_period))
                }
            }
        }
    }
}

@Composable
private fun HeroPager(
    state: HomeUiState,
    hidden: Boolean,
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    onToggleHidden: () -> Unit,
) {
    val pages = 1 + state.accounts.size
    val pager = rememberPagerState { pages }
    Column {
        HorizontalPager(state = pager) { page ->
            // Navpični odmik, da pager ne odreže sence kartice
            Box(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                if (page == 0) BalanceCard(state, hidden, type, onTypeChange, onToggleHidden) else AccountCard(state.accounts[page - 1], hidden)
            }
        }
        if (pages > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (pager.currentPage == 0) stringResource(R.string.swipe_for_accounts) else state.accounts.getOrNull(pager.currentPage - 1)?.account?.title.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                repeat(pages) { i ->
                    val selected = pager.currentPage == i
                    val w by animateDpAsState(if (selected) 18.dp else 6.dp, label = "dot")
                    val c by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "dotc",
                    )
                    Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape).background(c))
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    state: HomeUiState,
    hidden: Boolean,
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    onToggleHidden: () -> Unit,
) {
    val colors = Finance.colors
    val isExpense = type == TransactionType.EXPENSE
    // Besedilo na belem zavihku: vedno temnejši (svetli) ton, tudi v temnem načinu - zaradi kontrasta
    val typeColor = if (isExpense) LightFinanceColors.expense else LightFinanceColors.income
    val onHero = Color.White
    val onHeroMuted = Color.White.copy(alpha = 0.9f)
    HeroSurface(colors.heroGradient) {
        // Stanje obdobja: odhodki ali prihodki, brez predznaka; izbrani zavihek bel z barvo tipa
        SegmentedTabs(
            options = listOf(TransactionType.EXPENSE, TransactionType.INCOME),
            selected = type,
            onSelect = onTypeChange,
            label = { str(if (it == TransactionType.EXPENSE) R.string.expenses else R.string.income_plural) },
            selectedColor = onHero,
            onSelectedColor = typeColor,
            trackColor = Color.White.copy(alpha = 0.16f),
            unselectedTextColor = onHeroMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(balanceTitle(state.period), style = MaterialTheme.typography.labelLarge, color = onHeroMuted, modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleHidden) {
                Icon(
                    if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (hidden) R.string.show_balance else R.string.hide_balance),
                    tint = onHeroMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedAmount(
            if (isExpense) state.expenseCents else state.incomeCents,
            style = MaterialTheme.typography.displayMedium,
            color = onHero,
            masked = hidden,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                if (state.trend.count { it != 0L } >= 3) {
                    Sparkline(state.trend, onHero, Modifier.width(120.dp).height(56.dp))
                    Text(trendLabel(state.period), style = MaterialTheme.typography.labelSmall, color = onHeroMuted)
                } else if (state.incomeCents > 0 && !hidden) {
                    // Premalo zgodovine za trend - pokaži, koliko prihodkov je že porabljenih
                    val spent = state.expenseCents.toFloat() / state.incomeCents
                    ProgressRing(
                        spent, size = 56.dp, thickness = 6.dp,
                        color = if (spent > 1f) colors.expense else if (spent > 0.8f) colors.warning else onHero,
                        track = Color.White.copy(alpha = 0.22f),
                    ) { Text("${(spent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = onHero) }
                    Text(stringResource(R.string.spent_label), style = MaterialTheme.typography.labelSmall, color = onHeroMuted)
                }
            }
        }
        DailyAverageRow(state.average, type, hidden)
    }
}

/**
 * Gradientna "hero" kartica z dekorativnima krogoma (stanje obdobja in strani računov).
 * V svetlem načinu ima obarvano senco, ki sledi barvi gradienta.
 */
@Composable
private fun HeroSurface(brush: Brush, glow: Color = Finance.colors.heroEnd, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Radius.xl)
    val shadow = if (Finance.colors.isDark) Modifier else Modifier.shadow(14.dp, shape, ambientColor = glow.copy(alpha = 0.35f), spotColor = glow.copy(alpha = 0.45f))
    Box(Modifier.fillMaxWidth().heightIn(min = 176.dp).then(shadow).clip(shape).background(brush)) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-50).dp).size(180.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 20.dp, y = 40.dp).size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
        Column(Modifier.padding(20.dp).fillMaxWidth(), content = content)
    }
}

/** "Povprečno na dan: ↓ 18,40 €" - povprečje izbranega tipa za izbrano obdobje (na hero kartici). */
@Composable
private fun DailyAverageRow(avg: DailyAverage, type: TransactionType, hidden: Boolean) {
    val muted = Color.White.copy(alpha = 0.9f)
    Spacer(Modifier.height(Spacing.md))
    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
    Spacer(Modifier.height(Spacing.sm))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.avg_per_day), style = MaterialTheme.typography.labelMedium, color = muted)
            if (avg.note.isNotEmpty()) {
                Text(avg.note, style = MaterialTheme.typography.labelSmall, color = muted.copy(alpha = 0.8f))
            }
        }
        if (type == TransactionType.EXPENSE) {
            AverageChip(Icons.Rounded.ArrowDropDown, Color.White, avg.expenseCents, hidden, stringResource(R.string.avg_expense_a11y))
        } else {
            AverageChip(Icons.Rounded.ArrowDropUp, Color.White, avg.incomeCents, hidden, stringResource(R.string.avg_income_a11y))
        }
    }
}

@Composable
private fun AverageChip(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, cents: Long, hidden: Boolean, description: String) {
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = 0.16f)).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            if (hidden) "•••• €" else formatCents(cents),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun AccountCard(balance: AccountBalance, hidden: Boolean) {
    val base = accentFor(balance.account.title, balance.account.color).underWhiteText()
    HeroSurface(Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f).compositeOverDark())), glow = base) {
        Text(balance.account.title, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        AnimatedAmount(balance.balanceCents, style = MaterialTheme.typography.displayMedium, color = Color.White, masked = hidden, currency = balance.account.currencyCode)
        Spacer(Modifier.height(Spacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("•••• ${balance.account.currencyCode}", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.account_balance), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun Color.compositeOverDark(): Color = Color(red * 0.7f, green * 0.7f, blue * 0.7f, 1f)

@Composable
private fun UnconfirmedBanner(count: Int, onConfirmAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Finance.colors
    SoftCard(modifier.fillMaxWidth(), color = colors.warningBg, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = colors.warning)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.pending_confirmation, count, count), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pending_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(onClick = onConfirmAll, shape = CircleShape, color = colors.warning) {
                Text(stringResource(R.string.confirm_all), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** Slovenska dvojina/množina. */
fun plural(n: Int, one: String, two: String, few: String, many: String): String = when {
    n % 100 == 1 -> one
    n % 100 == 2 -> two
    n % 100 in 3..4 -> few
    else -> many
}

@Composable
private fun BudgetAlertCard(alert: BudgetStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Finance.colors
    val over = alert.fraction >= 1f
    val c = if (over) colors.expense else colors.warning
    SoftCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(alert.category.title, alert.category.color, size = 40.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = c, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (over) stringResource(R.string.budget_exceeded_cat, alert.category.title)
                        else stringResource(R.string.budget_used_cat, alert.category.title, (alert.fraction * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                SlimProgress(alert.fraction, c)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.amount_of, formatCents(alert.spentCents), formatCents(alert.budgetCents)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GoalsStrip(
    goals: List<GoalUi>,
    onOpenGoals: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onNewGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (goals.isEmpty()) {
        SoftCard(modifier.fillMaxWidth().padding(horizontal = Spacing.screen), onClick = onNewGoal, contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Savings, Finance.colors.income, size = 44.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.start_saving), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.start_saving_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        return
    }
    val saved = goals.sumOf { it.savedCents }
    val target = goals.sumOf { it.goal.targetCents }
    Column(modifier) {
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), contentPadding = PaddingValues(vertical = Spacing.lg)) {
            SectionHeader(stringResource(R.string.savings_goals), Modifier.padding(horizontal = Spacing.lg), action = stringResource(R.string.all), onAction = onOpenGoals)
            Row(Modifier.padding(horizontal = Spacing.lg), verticalAlignment = Alignment.Bottom) {
                Text(formatCents(saved), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (target > 0) {
                    Text(
                        stringResource(R.string.of_amount, formatCents(target)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(goals, key = { it.goal.uid }) { g ->
                    Column(
                        Modifier.width(64.dp).clip(RoundedCornerShape(Radius.md)).clickable { onOpenGoal(g.goal.uid) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProgressRing(
                            g.progress,
                            size = 60.dp,
                            thickness = 4.dp,
                            color = if (g.reached) Finance.colors.income else accentFor(g.goal.title, g.goal.color).asGraphic(),
                        ) {
                            Text(g.goal.emoji, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(g.goal.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                item(key = "add-goal") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                        Surface(onClick = onNewGoal, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(60.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.new_goal), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.new_goal), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransaction(
    tx: TransactionUi,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val colors = Finance.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onConfirm(); false }
                else -> true
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        // Poteg desno potrdi nepotrjeno transakcijo; brisanja s potegom ni (le v podrobnostih)
        enableDismissFromStartToEnd = !tx.confirmed,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(colors.income).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color.White)
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TransactionItem(tx, onClick = onClick, modifier = Modifier.padding(horizontal = 4.dp), showDate = showDate)
        }
    }
}

/** Poteg levo/desno po elementu zamenja mesec (desno = prejšnji). */
private fun Modifier.monthSwipe(onPrev: () -> Unit, onNext: () -> Unit, isCurrent: Boolean): Modifier = pointerInput(isCurrent) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            val threshold = 60.dp.toPx()
            if (total > threshold) onPrev()
            else if (total < -threshold && !isCurrent) onNext()
        },
    ) { _, dx -> total += dx }
}

private fun trendLabel(p: StatsPeriod) = when (p) {
    StatsPeriod.DAY -> str(R.string.trend_6_days)
    StatsPeriod.WEEK -> str(R.string.trend_6_weeks)
    StatsPeriod.MONTH -> str(R.string.trend_6_months)
    StatsPeriod.QUARTER -> str(R.string.trend_6_quarters)
    StatsPeriod.YEAR -> str(R.string.trend_6_years)
}

private fun emptyTitle(p: StatsPeriod, current: Boolean) = when {
    p == StatsPeriod.DAY && current -> str(R.string.empty_today)
    p == StatsPeriod.WEEK && current -> str(R.string.empty_this_week)
    p == StatsPeriod.MONTH && current -> str(R.string.empty_this_month)
    p == StatsPeriod.YEAR && current -> str(R.string.empty_this_year)
    else -> str(R.string.empty_period)
}

/** Vsota ene kategorije v izbranem obdobju. */
private data class CategoryGroup(
    val key: String,
    val title: String,
    val color: Int?,
    val type: TransactionType,
    val totalCents: Long,
    val share: Float,
    val items: List<TransactionUi>,
) {
    val unconfirmed: Int get() = items.count { !it.confirmed }
}

/** Transakcije izbranega tipa, združene po kategorijah in razvrščene padajoče po znesku. */
private fun categoryGroups(txs: List<TransactionUi>, type: TransactionType): List<CategoryGroup> {
    val ofType = txs.filter { it.type == type }
    // Vsote kot na kartici stanja: samo potrjene transakcije
    val grand = ofType.filter { it.confirmed }.sumOf { it.amountCents }.coerceAtLeast(1)
    // Transakcije izbrisane kategorije (brez naziva) gredo v isto skupino kot tiste brez kategorije
    return ofType.groupBy { if (it.categoryTitle == null) "" else it.categoryUid ?: "" }
        .map { (key, list) ->
            val total = list.filter { it.confirmed }.sumOf { it.amountCents }
            CategoryGroup(
                key = key.ifEmpty { "none" },
                title = list.first().categoryTitle ?: str(R.string.no_category),
                color = list.first().categoryColor,
                type = type,
                totalCents = total,
                share = total.toFloat() / grand,
                items = list.sortedWith(compareByDescending<TransactionUi> { it.date }.thenByDescending { it.amountCents }),
            )
        }
        .sortedByDescending { it.totalCents }
}

@Composable
private fun CategoryGroupRow(g: CategoryGroup, open: Boolean, type: TransactionType, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = accentFor(g.title, g.color)
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (open) accent.copy(alpha = 0.06f) else Color.Transparent)
                .clickable(onClickLabel = stringResource(if (open) R.string.hide_entries else R.string.show_entries), onClick = onClick)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(if (g.key == "none") null else g.title, g.color, type = type, size = 40.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        (if (type == TransactionType.INCOME) "+" else "−") + formatCents(g.totalCents),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                        color = if (type == TransactionType.INCOME) Finance.colors.income else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                SlimProgress(g.share, accent.asGraphic())
                Spacer(Modifier.height(4.dp))
                Text(
                    buildList {
                        add(qty(R.plurals.entries_count, g.items.size, g.items.size))
                        add(str(R.string.percent, (g.share * 100).toInt()))
                        if (g.unconfirmed > 0) add(qty(R.plurals.unconfirmed_count, g.unconfirmed, g.unconfirmed))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (g.unconfirmed > 0) Finance.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ExpandMore, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).rotate(rotation),
            )
        }
    }
}

@Composable
private fun TransfersGroupRow(count: Int, totalCents: Long, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.transfers_between_accounts), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.transfers_subtitle, pluralStringResource(R.plurals.transfers_count, count, count)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatCents(totalCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).rotate(rotation))
        }
    }
}
