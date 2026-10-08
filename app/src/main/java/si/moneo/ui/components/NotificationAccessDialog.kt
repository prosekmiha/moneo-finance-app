package si.moneo.ui.components

import si.moneo.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Razkritje pred dostopom do obvestil (zahteva Google Play): pove, kaj aplikacija bere in da
 * vsebina ostane na napravi. Sistemske nastavitve se odprejo šele po potrditvi.
 */
@Composable
fun NotificationAccessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
        title = { Text(stringResource(R.string.bank_notifications)) },
        text = {
            Text(stringResource(R.string.notif_access_text), Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text(stringResource(R.string.open_settings)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
