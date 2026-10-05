package si.moneo.ui.components

import si.moneo.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Naloži sliko iz datoteke v ozadju, pomanjšano na približno [maxSize] px (brez knjižnice za slike). */
@Composable
fun rememberFileBitmap(path: String?, maxSize: Int): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, path, maxSize) {
        value = if (path == null) null else withContext(Dispatchers.IO) { decodeSampled(path, maxSize) }
    }
    return bitmap
}

private fun decodeSampled(path: String, maxSize: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

@Composable
fun AttachmentThumbnail(path: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bmp = rememberFileBitmap(path, 300)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        bmp?.let { Image(it.asImageBitmap(), stringResource(R.string.receipt_image), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Celozaslonski ogled slike računa. */
@Composable
fun AttachmentViewer(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bmp = rememberFileBitmap(path, 2000)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            bmp?.let { Image(it.asImageBitmap(), stringResource(R.string.receipt_image), Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}
