package si.moneo.ui.category

import si.moneo.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.AccountEntity
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.AnimatedAmount
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor

@Composable
fun AccountsScreen(vm: MainViewModel, onBack: () -> Unit, onTransfer: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val balances = home.accounts
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    // Račun v urejanju (null = urejevalnik zaprt)
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.accounts), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Column(Modifier.padding(bottom = Spacing.sm)) {
                    Text(stringResource(R.string.total_balance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedAmount(balances.sumOf { it.balanceCents }, style = MaterialTheme.typography.displaySmall, masked = hidden)
                }
            }
            // Prenos sredstev je mogoč šele, ko obstajata vsaj dva računa
            if (balances.size >= 2) {
                item {
                    PillButton(stringResource(R.string.new_transfer), Icons.Rounded.SwapHoriz, onTransfer, Modifier.fillMaxWidth())
                }
            }
            items(balances, key = { it.account.uid }) { b ->
                val base = accentFor(b.account.title, b.account.color)
                Box(
                    Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.7f))))
                        .clickable { editing = b.account },
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(b.account.title, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(if (hidden) "•••• €" else formatCents(b.balanceCents, b.account.currencyCode), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.weight(1f))
                        Row {
                            Text("•••• ${b.account.currencyCode}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.edit), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                PillButton(stringResource(R.string.new_account), Icons.Rounded.Add, { creating = true }, Modifier.fillMaxWidth().padding(top = Spacing.sm), filled = true)
            }
        }
    }

    val titles = balances.map { it.account.title }.toSet()
    if (creating) {
        AccountEditorSheet(
            account = null,
            existingTitles = titles,
            onDismiss = { creating = false },
            onSave = { new ->
                vm.saveAccount(new.copy(position = (balances.maxOfOrNull { it.account.position } ?: 0) + 1))
                creating = false
            },
        )
    }
    editing?.let { acc ->
        AccountEditorSheet(
            account = acc,
            existingTitles = titles,
            currentBalanceCents = balances.firstOrNull { it.account.uid == acc.uid }?.balanceCents,
            onDismiss = { editing = null },
            onSave = {
                vm.saveAccount(it)
                editing = null
            },
            // Zadnjega računa ne dovolimo izbrisati - transakcije potrebujejo privzeti račun
            onDelete = if (balances.size > 1) ({
                vm.deleteAccount(acc)
                editing = null
            }) else null,
        )
    }
}
