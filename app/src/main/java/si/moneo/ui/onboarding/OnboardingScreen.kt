package si.moneo.ui.onboarding

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.NotificationAccessDialog
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.centsToInput
import si.moneo.ui.parseCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.ThemeMode

private const val STEPS = 4

/** Uvodni vodnik ob prvem zagonu: račun + začetno stanje, obvestila, tema. Vsak korak je mogoče preskočiti. */
@Composable
fun OnboardingScreen(
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNotificationsAsked: () -> Unit,
    onFinish: () -> Unit,
) {
    val pager = rememberPagerState { STEPS }
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val main = accounts.firstOrNull()

    var accountTitle by remember(main?.uid) { mutableStateOf(main?.title ?: str(R.string.main_account)) }
    var initial by remember(main?.uid) {
        mutableStateOf(main?.initialBalanceCents?.takeIf { it != 0L }?.let { centsToInput(it) } ?: "")
    }

    fun saveAccount() {
        val cents = parseCents(initial.replace("−", "-")) ?: 0
        // Privzeti račun ustvari SeedData ob prvem zagonu - če se še ni naložil, ga ne podvajamo
        val base = main ?: return
        vm.saveAccount(base.copy(title = accountTitle.trim().ifEmpty { str(R.string.main_account) }, initialBalanceCents = cents))
    }

    fun next() {
        if (pager.currentPage == 1) saveAccount()
        if (pager.currentPage == STEPS - 1) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    // Nazaj = prejšnji korak (na prvem koraku preskoči vodnik)
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onFinish()
    }
    // Surface nastavi barvo besedila (onBackground) - sicer je v temni temi besedilo črno
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).padding(start = Spacing.md)) {
                repeat(STEPS) { i ->
                    val active = i <= pager.currentPage
                    val w by animateDpAsState(if (i == pager.currentPage) 22.dp else 8.dp, label = "step")
                    val c by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "stepc")
                    Box(Modifier.padding(end = 6.dp).height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }
            TextButton(onClick = onFinish) { Text(stringResource(R.string.skip)) }
        }

        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = false) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> Welcome()
                    1 -> AccountStep(accountTitle, { accountTitle = it }, initial, { initial = it })
                    2 -> NotificationsStep(onNotificationsAsked)
                    else -> ThemeStep(themeMode, onThemeChange)
                }
            }
        }

        PillButton(
            stringResource(if (pager.currentPage == STEPS - 1) R.string.start else R.string.next),
            if (pager.currentPage == STEPS - 1) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            ::next,
            Modifier.fillMaxWidth().padding(Spacing.screen).height(56.dp),
            filled = true,
            enabled = pager.currentPage != 1 || parseCents(initial.replace("−", "-")) != null,
        )
    }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, tint: Color, title: String, text: String) {
    Box(
        Modifier.size(112.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(52.dp)) }
    Spacer(Modifier.height(Spacing.xl))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Spacing.sm))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Spacing.xl))
}

@Composable
private fun Welcome() {
    StepHeader(
        Icons.Rounded.Savings, Finance.colors.income,
        stringResource(R.string.welcome_title),
        stringResource(R.string.welcome_text),
    )
    listOf(
        stringResource(R.string.welcome_point_1),
        stringResource(R.string.welcome_point_2),
        stringResource(R.string.welcome_point_3),
    ).forEach { line ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.md))
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AccountStep(title: String, onTitle: (String) -> Unit, initial: String, onInitial: (String) -> Unit) {
    StepHeader(
        Icons.Rounded.AccountBalanceWallet, Finance.colors.warning,
        stringResource(R.string.your_main_account),
        stringResource(R.string.main_account_text),
    )
    OutlinedTextField(
        title, onTitle, label = { Text(stringResource(R.string.account_name)) }, singleLine = true,
        shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Spacing.md))
    OutlinedTextField(
        initial, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) onInitial(v) },
        label = { Text(stringResource(R.string.current_balance)) },
        placeholder = { Text(stringResource(R.string.balance_example)) },
        supportingText = { Text(stringResource(R.string.balance_not_income)) },
        isError = parseCents(initial.replace("−", "-")) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NotificationsStep(onAsked: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    StepHeader(
        Icons.Rounded.NotificationsActive, MaterialTheme.colorScheme.primary,
        stringResource(R.string.reminders_title),
        stringResource(R.string.reminders_text),
    )
    PermissionRow(
        stringResource(R.string.app_notifications), stringResource(R.string.app_notifications_sub), granted,
    ) {
        onAsked()
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Spacer(Modifier.height(Spacing.md))
    var showDisclosure by remember { mutableStateOf(false) }
    PermissionRow(stringResource(R.string.bank_notifications), stringResource(R.string.bank_notifications_sub), false) {
        showDisclosure = true
    }
    if (showDisclosure) NotificationAccessDialog(onDismiss = { showDisclosure = false })
}

@Composable
private fun PermissionRow(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick, enabled = !done, shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationsActive, if (done) Finance.colors.income else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(if (done) stringResource(R.string.allowed) else subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!done) Text(stringResource(R.string.allow), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ThemeStep(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    StepHeader(
        Icons.Rounded.Palette, Finance.colors.expense,
        stringResource(R.string.choose_look),
        stringResource(R.string.choose_look_text),
    )
    SegmentedTabs(
        ThemeMode.entries.toList(), themeMode, onThemeChange,
        label = { when (it) { ThemeMode.SYSTEM -> str(R.string.theme_system); ThemeMode.LIGHT -> str(R.string.theme_light); ThemeMode.DARK -> str(R.string.theme_dark) } },
    )
}
