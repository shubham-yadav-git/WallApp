package com.sky.wallapp

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.gms.ads.nativead.NativeAd

/**
 * Static list of wallpapers rendered as masonry pins. When [feedAds] is given, its native ads
 * are interleaved as sponsored pins; click callbacks always receive indices into [items].
 */
class PinAdapter(
    private val items: List<Model>,
    private val onPinClick: (position: Int, model: Model) -> Unit,
    private val onFavoriteToggled: (model: Model, nowFavorite: Boolean) -> Unit,
    private val feedAds: FeedAds? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        data class Pin(val itemIndex: Int) : Row
        data class Ad(val ad: NativeAd) : Row
    }

    private var rows: List<Row> = buildRows()

    /** Re-reads [feedAds] after new ads arrive. */
    fun refreshAds() {
        rows = buildRows()
        notifyDataSetChanged()
    }

    private fun buildRows(): List<Row> {
        val ads = feedAds?.ads.orEmpty()
        var nextAd = 0
        return buildList {
            items.indices.forEach { index ->
                val isAdSlot = index >= FIRST_AD_POSITION && (index - FIRST_AD_POSITION) % AD_INTERVAL == 0
                if (isAdSlot && nextAd < ads.size) add(Row.Ad(ads[nextAd++]))
                add(Row.Pin(index))
            }
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Ad) VIEW_TYPE_AD else VIEW_TYPE_PIN

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_AD) FeedAds.PinHolder.inflate(parent) else ViewHolder.inflate(parent)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Ad -> (holder as FeedAds.PinHolder).bind(row.ad)
            is Row.Pin -> {
                val model = items[row.itemIndex]
                (holder as ViewHolder).bind(
                    model,
                    onClick = { onPinClick(row.itemIndex, model) },
                    onFavoriteToggled = { nowFavorite -> onFavoriteToggled(model, nowFavorite) }
                )
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    companion object {
        private const val VIEW_TYPE_PIN = 0
        private const val VIEW_TYPE_AD = 1

        /** First sponsored pin appears before the 5th wallpaper, then every 12 wallpapers. */
        private const val FIRST_AD_POSITION = 4
        private const val AD_INTERVAL = 12

        /** Two columns on phones, three on tablets / unfolded foldables. */
        fun spanCount(context: Context): Int =
            if (context.resources.configuration.screenWidthDp >= 600) 3 else 2

        fun newLayoutManager(context: Context): StaggeredGridLayoutManager =
            WrapStaggeredGridLayoutManager(spanCount(context), StaggeredGridLayoutManager.VERTICAL).apply {
                // Pin heights are deterministic, so keep items in their column instead of reshuffling on scroll.
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
            }
    }
}
