package si.moneo.feature.widget

import si.moneo.ui.str
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import si.moneo.MoneoApp
import si.moneo.R
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.formatCents

/** Pripravi gumbe za mrežo v widgetu s priljubljenimi vnosi. */
class FavoritesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var favorites: List<FavoriteEntity> = emptyList()
        private var colors: WidgetColors? = null

        override fun onCreate() {}

        // Teče na vezni niti (ne na glavni), zato je sinhrono branje baze dovoljeno
        override fun onDataSetChanged() {
            val repo = (context.applicationContext as MoneoApp).container.repository
            favorites = runBlocking { repo.activeFavorites() }
            colors = widgetColors(context)
        }

        override fun getCount(): Int = favorites.size

        override fun getViewAt(position: Int): RemoteViews {
            val f = favorites.getOrNull(position) ?: return emptyItem()
            val c = colors ?: widgetColors(context)
            val income = f.type == TransactionType.INCOME
            return RemoteViews(context.packageName, R.layout.widget_favorite_item).apply {
                setTextViewText(R.id.fav_item_title, f.title)
                setTextViewText(R.id.fav_item_amount, (if (income) "+" else "") + formatCents(f.amountCents))
                tint(R.id.fav_item_bg, if (income) c.incomeBg else c.accentBg)
                setTextColor(R.id.fav_item_title, if (income) c.text else c.onAccentBg)
                setTextColor(R.id.fav_item_amount, if (income) c.income else c.onAccentBg)
                setContentDescription(R.id.fav_item, str(R.string.add_named, "${f.title} ${formatCents(f.amountCents)}"))
                setOnClickFillInIntent(
                    R.id.fav_item,
                    Intent().putExtra(FavoritesWidgetProvider.EXTRA_FAVORITE_UID, f.uid),
                )
            }
        }

        private fun emptyItem() = RemoteViews(context.packageName, R.layout.widget_favorite_item)

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = favorites.getOrNull(position)?.uid?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds(): Boolean = true
        override fun onDestroy() {}
    }
}
