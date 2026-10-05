package si.moneo.feature.quickadd

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import si.moneo.feature.voice.VoiceInputActivity

/**
 * Quick Settings tile - dostopen tudi z zaklenjenega zaslona.
 * Uporabnik ga doda ročno: hitre nastavitve -> uredi -> "Glasovni vnos".
 */
class QuickAddTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, VoiceInputActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (isLocked) {
            // Na zaklenjenem zaslonu zahtevamo odklep, nato odpremo glasovni vnos
            unlockAndRun {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
            }
        } else {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
    }
}
