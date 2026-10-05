package si.moneo

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import si.moneo.feature.widget.FavoritesWidgetProvider
import si.moneo.feature.widget.QuickAddWidgetProvider
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.tween
import si.moneo.ui.components.pressScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import si.moneo.ui.theme.Spacing
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import si.moneo.ui.components.LocalNavAnimScope
import si.moneo.ui.components.LocalSharedScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import si.moneo.data.db.entity.TransactionType
import si.moneo.feature.ocr.ReceiptScanActivity
import si.moneo.feature.voice.VoiceInputActivity
import si.moneo.ui.MainViewModel
import si.moneo.ui.TransactionUi
import si.moneo.ui.add.AddTransactionSheet
import si.moneo.ui.add.TransactionDetailSheet
import si.moneo.ui.category.AccountsScreen
import si.moneo.ui.category.CategoriesScreen
import si.moneo.ui.category.CategoryDetailScreen
import si.moneo.ui.components.LocalSnackbar
import si.moneo.ui.goals.GoalDetailScreen
import si.moneo.ui.goals.GoalEditorSheet
import si.moneo.ui.goals.GoalsScreen
import si.moneo.ui.home.HomeScreen
import si.moneo.ui.more.MoreScreen
import si.moneo.ui.onboarding.OnboardingScreen
import si.moneo.ui.search.SearchScreen
import si.moneo.ui.subscriptions.SubscriptionsScreen
import si.moneo.ui.plan.PlanScreen
import si.moneo.ui.recurring.RecurringScreen
import si.moneo.ui.debts.DebtsScreen
import si.moneo.ui.favorites.FavoritesScreen
import si.moneo.ui.tags.TagDetailScreen
import si.moneo.ui.tags.TagsScreen
import si.moneo.ui.year.YearReviewScreen
import si.moneo.ui.stats.StatsScreen
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.MoneoTheme
import si.moneo.ui.theme.ThemeAccent
import si.moneo.ui.theme.ThemeMode
import si.moneo.ui.theme.ThemePrefs

object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val GOALS = "goals"
    const val MORE = "more"
    const val GOAL = "goal/{uid}"
    const val CATEGORY = "category/{uid}"
    const val CATEGORIES = "categories"
    const val ACCOUNTS = "accounts"
    const val SEARCH = "search"
    const val SUBSCRIPTIONS = "subscriptions"
    const val PLAN = "plan"
    const val RECURRING = "recurring"
    const val FAVORITES = "favorites"
    const val DEBTS = "debts"
    const val TAGS = "tags"
    const val TAG = "tag/{name}"
    const val YEAR = "year/{year}"

    val topLevel = setOf(HOME, STATS, PLAN, MORE)

    fun goal(uid: String) = "goal/$uid"
    fun category(uid: String) = "category/$uid"
    fun tag(name: String) = "tag/" + android.net.Uri.encode(name)
    fun year(year: Int = java.time.LocalDate.now().year) = "year/$year"
}

/** Kaj ureja urejevalnik ciljev: null = zaprt, "" = nov cilj, sicer uid. */
private typealias GoalEditorTarget = String?

class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(si.moneo.ui.AppLocale.wrap(newBase))
    }

    private val prefs by lazy { (application as MoneoApp).container.prefs }

    /** Zaklenjeno ob zagonu (če je zaklep vklopljen) in po [LOCK_AFTER_MS] v ozadju. */
    private var locked by mutableStateOf(false)
    private var backgroundedAt = 0L

    private val biometricAvailable: Boolean
        get() = BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Pokaže sistemski poziv za prstni odtis / obraz / PIN. */
    fun authenticate(title: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(getString(R.string.app_name))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    override fun onStart() {
        super.onStart()
        if (prefs.appLock && backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > LOCK_AFTER_MS) locked = true
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        si.moneo.ui.L10n.init(this)
        // Vstop iz widgeta / tile-a s prednastavljenim tipom -> takoj odpri vnos
        val entryType = if (savedInstanceState == null) intent?.getStringExtra(EXTRA_ENTRY_TYPE) else null
        if (savedInstanceState == null) locked = prefs.appLock && biometricAvailable
        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.load(this)) }
            var themeAccent by remember { mutableStateOf(ThemePrefs.loadAccent(this)) }
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Ikone sistemskih vrstic sledijo izbrani temi aplikacije (ne le sistemski)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            var appLock by remember { mutableStateOf(prefs.appLock) }
            MoneoTheme(themeMode, themeAccent) {
                App(
                    entryType = entryType,
                    themeMode = themeMode,
                    onThemeChange = {
                        themeMode = it
                        ThemePrefs.save(this, it)
                        refreshWidgets()
                    },
                    themeAccent = themeAccent,
                    onAccentChange = {
                        themeAccent = it
                        ThemePrefs.saveAccent(this, it)
                        refreshWidgets()
                    },
                    appLock = appLock,
                    appLockAvailable = biometricAvailable,
                    // Vklop/izklop zaklepa zahteva potrditev, da ga ne more izklopiti nekdo drug
                    onAppLockChange = { enable ->
                        authenticate(getString(if (enable) R.string.lock_enable else R.string.lock_disable)) {
                            prefs.appLock = enable
                            appLock = enable
                        }
                    },
                )
                AnimatedVisibility(locked, enter = fadeIn(), exit = fadeOut()) {
                    LockScreen(onUnlock = { authenticate(getString(R.string.unlock_app)) { locked = false } })
                }
            }
        }
    }

    /** Widgeti na domačem zaslonu naj sledijo izbrani temi. */
    private fun refreshWidgets() {
        lifecycleScope.launch {
            QuickAddWidgetProvider.refresh(this@MainActivity)
            FavoritesWidgetProvider.refresh(this@MainActivity)
        }
    }

    companion object {
        const val EXTRA_ENTRY_TYPE = "entry_type"

        fun intent(context: android.content.Context, type: String?): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_TYPE, type)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun App(
    entryType: String?,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    onAccentChange: (ThemeAccent) -> Unit,
    appLock: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
) {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var quickMenu by remember { mutableStateOf(false) }
    var goalEditor by rememberSaveable { mutableStateOf<GoalEditorTarget>(null) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun openAdd(type: TransactionType) {
        vm.startDraft(type)
        showAdd = true
    }

    // Pregled transakcije (uid) - tap na transakcijo najprej pokaže podrobnosti
    var detailUid by rememberSaveable { mutableStateOf<String?>(null) }
    fun openDetail(tx: TransactionUi) { detailUid = tx.uid }

    fun openEdit(tx: TransactionUi) {
        vm.editTransaction(tx)
        showAdd = true
    }

    // Widget prikazuje stanje - ob skrivanju/prikazu ga takoj osveži
    val context = LocalContext.current
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    LaunchedEffect(hidden) {
        QuickAddWidgetProvider.refresh(context)
        FavoritesWidgetProvider.refresh(context)
    }

    // Android 13+: dovoljenje za obvestila (povzetek, opomniki) - vprašamo le enkrat
    val prefs = (context.applicationContext as MoneoApp).container.prefs
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && prefs.onboardingDone && !prefs.askedNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.askedNotificationPermission = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(entryType) {
        when (entryType) {
            "income" -> openAdd(TransactionType.INCOME)
            "expense" -> openAdd(TransactionType.EXPENSE)
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = {
                    SnackbarHost(snackbar) {
                        Snackbar(it, shape = RoundedCornerShape(Radius.md), actionColor = Finance.colors.income)
                    }
                },
                bottomBar = {
                    AnimatedVisibility(
                        route in Routes.topLevel || route == null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        BottomBar(
                            current = route,
                            onNavigate = { nav.navigateTop(it) },
                            onAdd = { openAdd(TransactionType.EXPENSE) },
                            onAddLong = { quickMenu = true },
                            menuOpen = quickMenu,
                        )
                    }
                },
            ) { padding ->
                // Skupni prehodi (ikona kategorije / obroč cilja "zleti" v podrobnosti)
                SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    enterTransition = {
                        // Med zavihki "fade through" (rahel zoom), podzasloni zdrsnejo z desne
                        if (targetState.destination.route in Routes.topLevel) fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(280, delayMillis = 60), initialScale = 0.97f)
                        else slideInHorizontally { it / 4 } + fadeIn()
                    },
                    exitTransition = {
                        // Fade through: stari zavihek hitro izgine, preden se pokaže novi
                        if (targetState.destination.route in Routes.topLevel) fadeOut(tween(90)) else fadeOut()
                    },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = {
                        if (initialState.destination.route in Routes.topLevel) fadeOut()
                        else slideOutHorizontally { it / 4 } + fadeOut()
                    },
                ) {
                    screen(Routes.HOME) {
                        HomeScreen(
                            vm = vm,
                            onEdit = ::openDetail,
                            onEditTransfer = {
                                vm.startTransfer(it)
                                showAdd = true
                            },
                            onSearch = { nav.navigate(Routes.SEARCH) },
                            onOpenGoals = { nav.navigate(Routes.GOALS) },
                            onOpenGoal = { nav.navigate(Routes.goal(it)) },
                            onNewGoal = { goalEditor = "" },
                            onOpenCategory = { nav.navigate(Routes.category(it)) },
                            onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                            onOpenSubscriptions = { nav.navigate(Routes.SUBSCRIPTIONS) },
                            onOpenDebts = { nav.navigate(Routes.DEBTS) },
                            onOpenRecurring = { nav.navigate(Routes.RECURRING) },
                            onOpenYearReview = { nav.navigate(Routes.year(it)) },
                            contentPadding = padding,
                        )
                    }
                    screen(Routes.STATS) {
                        StatsScreen(
                            vm,
                            onOpenCategory = { nav.navigate(Routes.category(it)) },
                            onOpenYearReview = { nav.navigate(Routes.year()) },
                            onOpenTags = { nav.navigate(Routes.TAGS) },
                            contentPadding = padding,
                        )
                    }
                    screen(Routes.PLAN) {
                        PlanScreen(
                            vm,
                            onOpenGoals = { nav.navigate(Routes.GOALS) },
                            onNewGoal = { goalEditor = "" },
                            onOpenSubscriptions = { nav.navigate(Routes.SUBSCRIPTIONS) },
                            onOpenRecurring = { nav.navigate(Routes.RECURRING) },
                            onOpenDebts = { nav.navigate(Routes.DEBTS) },
                            onOpenBudgets = { nav.navigate(Routes.CATEGORIES) },
                            contentPadding = padding,
                        )
                    }
                    screen(Routes.GOALS) {
                        GoalsScreen(
                            vm, onOpenGoal = { nav.navigate(Routes.goal(it)) }, onNewGoal = { goalEditor = "" },
                            contentPadding = padding, onBack = { nav.popBackStack() },
                        )
                    }
                    screen(Routes.RECURRING) { RecurringScreen(vm, onBack = { nav.popBackStack() }) }
                    screen(Routes.MORE) {
                        MoreScreen(
                            vm, themeMode, onThemeChange,
                            themeAccent = themeAccent,
                            onAccentChange = onAccentChange,
                            appLock = appLock,
                            appLockAvailable = appLockAvailable,
                            onAppLockChange = onAppLockChange,
                            onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                            onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                            onOpenFavorites = { nav.navigate(Routes.FAVORITES) },
                            contentPadding = padding,
                        )
                    }
                    screen(Routes.GOAL) { entry ->
                        val uid = entry.arguments?.getString("uid").orEmpty()
                        GoalDetailScreen(vm, uid, onBack = { nav.popBackStack() }, onEdit = { goalEditor = uid })
                    }
                    screen(Routes.CATEGORY) { entry ->
                        CategoryDetailScreen(vm, entry.arguments?.getString("uid").orEmpty(), onBack = { nav.popBackStack() }, onEdit = ::openDetail)
                    }
                    screen(Routes.CATEGORIES) {
                        CategoriesScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.category(it)) })
                    }
                    screen(Routes.ACCOUNTS) {
                        AccountsScreen(
                            vm,
                            onBack = { nav.popBackStack() },
                            onTransfer = {
                                vm.startTransfer()
                                showAdd = true
                            },
                        )
                    }
                    screen(Routes.SEARCH) {
                        SearchScreen(vm, onBack = { nav.popBackStack() }, onEdit = ::openDetail)
                    }
                    screen(Routes.SUBSCRIPTIONS) { SubscriptionsScreen(vm, onBack = { nav.popBackStack() }) }
                    screen(Routes.FAVORITES) { FavoritesScreen(vm, onBack = { nav.popBackStack() }) }
                    screen(Routes.DEBTS) { DebtsScreen(vm, onBack = { nav.popBackStack() }) }
                    screen(Routes.TAGS) { TagsScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.tag(it)) }) }
                    screen(Routes.TAG) { entry ->
                        TagDetailScreen(vm, entry.arguments?.getString("name").orEmpty(), onBack = { nav.popBackStack() }, onOpenTransaction = ::openDetail)
                    }
                    screen(Routes.YEAR) { entry ->
                        YearReviewScreen(
                            vm, entry.arguments?.getString("year")?.toIntOrNull() ?: java.time.LocalDate.now().year,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                }
                }
            }

            // Pas pod statusno vrstico, da drseča vsebina ne prekriva ure in ikon
            Box(
                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
            )

            // Enkratni namig za hiter meni (dolg pritisk na +)
            var showHint by remember { mutableStateOf(!prefs.quickMenuHintShown) }
            fun hideHint() {
                showHint = false
                prefs.quickMenuHintShown = true
            }
            LaunchedEffect(quickMenu) { if (quickMenu) hideHint() }
            AnimatedVisibility(
                showHint && route == Routes.HOME && !quickMenu,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
            ) {
                QuickMenuHint(onDismiss = ::hideHint)
            }

            QuickAddMenu(
                visible = quickMenu,
                onDismiss = { quickMenu = false },
                onExpense = { quickMenu = false; openAdd(TransactionType.EXPENSE) },
                onIncome = { quickMenu = false; openAdd(TransactionType.INCOME) },
            )

            var onboarding by rememberSaveable { mutableStateOf(!prefs.onboardingDone) }
            AnimatedVisibility(onboarding, enter = fadeIn(), exit = fadeOut()) {
                OnboardingScreen(
                    vm = vm,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onNotificationsAsked = { prefs.askedNotificationPermission = true },
                    onFinish = {
                        prefs.onboardingDone = true
                        onboarding = false
                    },
                )
            }
        }

        if (showAdd) {
            AddTransactionSheet(
                vm = vm,
                sheetState = addSheetState,
                onDismiss = {
                    scope.launch { addSheetState.hide() }.invokeOnCompletion { showAdd = false }
                },
            )
        }

        detailUid?.let { uid ->
            val all by vm.allTransactions.collectAsStateWithLifecycle()
            val rules by vm.activeRules.collectAsStateWithLifecycle()
            val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
            val tx = all.firstOrNull { it.uid == uid }
            // izbrisana (npr. drugje) - zapri pregled; dokler se seznam nalaga, ne kaži ničesar
            LaunchedEffect(tx == null, all.isEmpty()) { if (tx == null && all.isNotEmpty()) detailUid = null }
            if (tx != null) {
                TransactionDetailSheet(
                    tx = tx,
                    ruleTitle = tx.recurringRuleUid?.let { r -> rules.firstOrNull { it.uid == r }?.title },
                    subscriptionTitle = tx.subscriptionUid?.let { s -> subscriptions.firstOrNull { it.uid == s }?.title },
                    onDismiss = { detailUid = null },
                    onEdit = {
                        detailUid = null
                        openEdit(tx)
                    },
                    onRepeat = {
                        detailUid = null
                        vm.repeatTransaction(tx)
                        showAdd = true
                    },
                    onDelete = {
                        detailUid = null
                        vm.delete(tx.uid)
                        scope.launch {
                            val r = snackbar.showSnackbar(str(R.string.deleted_named, tx.title), actionLabel = str(R.string.undo), duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) vm.restore(tx.uid)
                        }
                    },
                    onConfirm = { vm.confirm(tx.uid) },
                )
            }
        }

        goalEditor?.let { target ->
            val goals by vm.goals.collectAsStateWithLifecycle()
            val existing = goals.firstOrNull { it.goal.uid == target }?.goal
            GoalEditorSheet(
                goal = existing,
                onDismiss = { goalEditor = null },
                onSave = {
                    vm.saveGoal(it)
                    goalEditor = null
                },
                onDelete = existing?.let {
                    {
                        vm.deleteGoal(it.uid)
                        goalEditor = null
                        if (route == Routes.GOAL) nav.popBackStack()
                    }
                },
            )
        }
    }
}

private data class NavItem(val route: String, val icon: ImageVector, @androidx.annotation.StringRes val labelRes: Int) {
    val label: String get() = str(labelRes)
}

private val navItems = listOf(
    NavItem(Routes.HOME, Icons.Rounded.Home, R.string.nav_home),
    NavItem(Routes.STATS, Icons.Rounded.BarChart, R.string.nav_stats),
    NavItem(Routes.PLAN, Icons.Rounded.EditCalendar, R.string.nav_plan),
    NavItem(Routes.MORE, Icons.Rounded.GridView, R.string.nav_more),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBar(
    current: String?,
    onNavigate: (String) -> Unit,
    onAdd: () -> Unit,
    onAddLong: () -> Unit,
    menuOpen: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            navItems.take(2).forEach { NavButton(it, current == it.route) { onNavigate(it.route) } }
            val rotation by animateFloatAsState(if (menuOpen) 45f else 0f, spring(dampingRatio = 0.6f), label = "rot")
            val glow = Finance.colors.heroStart
            val addInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier.size(54.dp)
                    .pressScale(addInteraction, 0.9f)
                    .shadow(10.dp, CircleShape, ambientColor = glow, spotColor = glow)
                    .clip(CircleShape)
                    .background(Finance.colors.heroGradient)
                    .combinedClickable(
                        interactionSource = addInteraction,
                        indication = LocalIndication.current,
                        onClick = onAdd,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddLong()
                        },
                        onClickLabel = stringResource(R.string.add_transaction),
                        onLongClickLabel = stringResource(R.string.quick_menu),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.add), tint = Color.White, modifier = Modifier.size(30.dp).rotate(rotation))
            }
            navItems.drop(2).forEach { NavButton(it, current == it.route) { onNavigate(it.route) } }
        }
    }
}

@Composable
private fun NavButton(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, label = "navtint",
    )
    val labelColor by animateColorAsState(if (selected) scheme.onSurface else scheme.onSurfaceVariant, label = "navlabel")
    // Kapsula za ikono izbranega zavihka "zraste" iz sredine
    val pillWidth by animateDpAsState(if (selected) 52.dp else 28.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "pill")
    val pillColor by animateColorAsState(if (selected) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f), label = "pillc")
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.width(64.dp).pressScale(interaction, 0.9f).clip(RoundedCornerShape(Radius.md))
            .clickable(interaction, LocalIndication.current, onClickLabel = item.label) {
                if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(30.dp).width(pillWidth).clip(CircleShape).background(pillColor), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.label, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** Hiter meni, ki se pokaže ob dolgem pritisku na "+". */
@Composable
private fun QuickAddMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onExpense: () -> Unit,
    onIncome: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = Finance.colors
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        val actions = listOf(
            Triple(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.entry_expense), colors.expense) to onExpense,
            Triple(Icons.Rounded.AddCircleOutline, stringResource(R.string.entry_income), colors.income) to onIncome,
            Triple(Icons.Rounded.Mic, stringResource(R.string.quick_voice), MaterialTheme.colorScheme.primary) to {
                onDismiss(); context.startActivity(Intent(context, VoiceInputActivity::class.java))
            },
            Triple(Icons.Rounded.DocumentScanner, stringResource(R.string.quick_receipt), colors.warning) to {
                onDismiss(); context.startActivity(Intent(context, ReceiptScanActivity::class.java))
            },
        )
        // Pahljača nad gumbom "+"
        val offsets = listOf(-132 to -84, -48 to -150, 48 to -150, 132 to -84)
        actions.forEachIndexed { i, (meta, action) ->
            val (icon, label, tint) = meta
            AnimatedVisibility(
                visible,
                enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 400f - i * 40f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.offset(x = offsets[i].first.dp, y = offsets[i].second.dp - 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = action, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp)) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    // Samodejno pokaži poziv ob prikazu zaklenjenega zaslona
    LaunchedEffect(Unit) { onUnlock() }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxSize()
            // prestrezi dotike, da zaslon spodaj ni uporaben
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(Finance.colors.heroGradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_locked), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Surface(onClick = onUnlock, shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.unlock), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    }
}

private const val LOCK_AFTER_MS = 30_000L
private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
private fun QuickMenuHint(onDismiss: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(Radius.md),
            color = Finance.colors.selectedTab,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fab_hint), color = Finance.colors.onSelectedTab, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Close, stringResource(R.string.close_hint), tint = Finance.colors.onSelectedTab, modifier = Modifier.size(16.dp))
            }
        }
        // "repek" oblačka, ki kaže na gumb +
        Box(Modifier.size(12.dp).offset(y = (-6).dp).rotate(45f).background(Finance.colors.selectedTab))
    }
}

/** Cilj navigacije, ki zaslonu posreduje animacijski obseg za skupne prehode. */
private fun NavGraphBuilder.screen(route: String, content: @Composable (NavBackStackEntry) -> Unit) =
    composable(route) { entry -> CompositionLocalProvider(LocalNavAnimScope provides this) { content(entry) } }
