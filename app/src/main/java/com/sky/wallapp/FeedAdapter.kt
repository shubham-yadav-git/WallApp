package com.sky.wallapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/** One row of a masonry screen. Pins, ads and collection cards take one column; the rest span. */
sealed class FeedRow(val id: String) {
    data class Heading(val title: String, val count: String?) : FeedRow("heading")
    data class Recent(val items: List<Wallpaper>, val showSeeAll: Boolean) : FeedRow("recent")
    data class Empty(
        val title: String?,
        val message: String,
        val action: String?,
        val actionId: Int,
        val iconRes: Int = 0
    ) : FeedRow("empty")
    data object Loading : FeedRow("loading")
    data class SyncBanner(val account: SavedSync.Account) : FeedRow("sync")
    data class SavedTabs(val selected: SavedTab, val favorites: Int, val collections: Int, val recent: Int) : FeedRow("tabs")
    data class SubAction(val label: String, val actionId: Int) : FeedRow("sub")
    data class Pin(val image: Wallpaper, val index: Int, val saved: Boolean) : FeedRow("pin:${image.key}")
    data class Ad(val slot: Int, val ad: NativeAd) : FeedRow("ad:$slot")
    data class CollectionCard(val collection: SavedStore.Collection, val covers: List<Wallpaper>) :
        FeedRow("collection:${collection.id}")
}

enum class SavedTab { FAVORITES, COLLECTIONS, RECENT }

/** Callbacks from feed rows; screens override what they use. */
interface FeedListener {
    fun onPinClick(row: FeedRow.Pin) {}
    fun onPinLongClick(row: FeedRow.Pin) {}
    fun onRecentClick(items: List<Wallpaper>, index: Int) {}
    fun onRecentSeeAll() {}
    fun onRecentClear() {}
    fun onEmptyAction(actionId: Int) {}
    fun onSubAction(actionId: Int) {}
    fun onSignIn() {}
    fun onSavedTab(tab: SavedTab) {}
    fun onCollectionClick(collection: SavedStore.Collection) {}
}

class FeedAdapter(private val listener: FeedListener) : ListAdapter<FeedRow, RecyclerView.ViewHolder>(DIFF) {

    /** Long-pressing a pin calls [FeedListener.onPinLongClick] only when enabled. */
    var longPressEnabled = true

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is FeedRow.Heading -> TYPE_HEADING
        is FeedRow.Recent -> TYPE_RECENT
        is FeedRow.Empty -> TYPE_EMPTY
        FeedRow.Loading -> TYPE_LOADING
        is FeedRow.SyncBanner -> TYPE_SYNC
        is FeedRow.SavedTabs -> TYPE_TABS
        is FeedRow.SubAction -> TYPE_SUB
        is FeedRow.Pin -> TYPE_PIN
        is FeedRow.Ad -> TYPE_AD
        is FeedRow.CollectionCard -> TYPE_COLLECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        fun inflate(layout: Int) = inflater.inflate(layout, parent, false)
        val holder = when (viewType) {
            TYPE_PIN -> ViewHolder.inflate(parent)
            TYPE_AD -> FeedAds.PinHolder.inflate(parent)
            TYPE_COLLECTION -> CollectionHolder(inflate(R.layout.item_collection_card))
            TYPE_HEADING -> HeadingHolder(inflate(R.layout.item_feed_heading))
            TYPE_RECENT -> RecentHolder(inflate(R.layout.item_recent_strip))
            TYPE_EMPTY -> EmptyHolder(inflate(R.layout.item_feed_empty))
            TYPE_SYNC -> SyncHolder(inflate(R.layout.item_sync_banner))
            TYPE_TABS -> TabsHolder(inflate(R.layout.item_saved_tabs))
            TYPE_SUB -> SubActionHolder(inflate(R.layout.item_sub_action))
            else -> object : RecyclerView.ViewHolder(inflate(R.layout.item_feed_loading)) {}
        }
        val spansOneColumn = viewType == TYPE_PIN || viewType == TYPE_AD || viewType == TYPE_COLLECTION
        holder.itemView.layoutParams = StaggeredGridLayoutManager.LayoutParams(holder.itemView.layoutParams).apply {
            isFullSpan = !spansOneColumn
        }
        return holder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is FeedRow.Pin -> (holder as ViewHolder).bind(
                row.image,
                row.saved,
                onClick = { listener.onPinClick(row) },
                onLongClick = if (longPressEnabled) ({ listener.onPinLongClick(row) }) else null
            )
            is FeedRow.Ad -> (holder as FeedAds.PinHolder).bind(row.ad)
            is FeedRow.CollectionCard -> (holder as CollectionHolder).bind(row)
            is FeedRow.Heading -> (holder as HeadingHolder).bind(row)
            is FeedRow.Recent -> (holder as RecentHolder).bind(row)
            is FeedRow.Empty -> (holder as EmptyHolder).bind(row)
            is FeedRow.SyncBanner -> (holder as SyncHolder).bind(row)
            is FeedRow.SavedTabs -> (holder as TabsHolder).bind(row)
            is FeedRow.SubAction -> (holder as SubActionHolder).bind(row)
            FeedRow.Loading -> Unit
        }
    }

    // ── Holders ─────────────────────────────────────────────────────────────────

    private class HeadingHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.heading_title)
        private val count: TextView = view.findViewById(R.id.heading_count)
        fun bind(row: FeedRow.Heading) {
            title.text = row.title
            count.text = row.count
            count.isVisible = row.count != null
        }
    }

    private inner class EmptyHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.empty_icon)
        private val title: TextView = view.findViewById(R.id.empty_title)
        private val message: TextView = view.findViewById(R.id.empty_message)
        private val action: MaterialButton = view.findViewById(R.id.empty_action)
        fun bind(row: FeedRow.Empty) {
            icon.isVisible = row.iconRes != 0
            if (row.iconRes != 0) icon.setImageResource(row.iconRes)
            title.text = row.title
            title.isVisible = row.title != null
            message.text = row.message
            action.text = row.action
            action.isVisible = row.action != null
            action.setOnClickListener { listener.onEmptyAction(row.actionId) }
        }
    }

    private inner class SubActionHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val button: MaterialButton = view.findViewById(R.id.sub_action)
        fun bind(row: FeedRow.SubAction) {
            button.text = row.label
            button.setOnClickListener { listener.onSubAction(row.actionId) }
        }
    }

    private inner class SyncHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val signedOut: View = view.findViewById(R.id.banner_signed_out)
        private val note: TextView = view.findViewById(R.id.banner_note)
        init {
            view.findViewById<View>(R.id.banner_google).setOnClickListener { listener.onSignIn() }
        }
        fun bind(row: FeedRow.SyncBanner) {
            val user = row.account.user
            signedOut.isVisible = row.account.known && user == null
            note.isVisible = user != null
            if (user != null) {
                note.text = syncText(note.context, row.account.status, user.email)
            }
        }
    }

    private inner class TabsHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val group: ChipGroup = view.findViewById(R.id.saved_tabs)
        private val favorites: Chip = view.findViewById(R.id.tab_favorites)
        private val collections: Chip = view.findViewById(R.id.tab_collections)
        private val recent: Chip = view.findViewById(R.id.tab_recent)
        private var binding = false
        init {
            group.setOnCheckedStateChangeListener { _, ids ->
                if (binding) return@setOnCheckedStateChangeListener
                when (ids.firstOrNull()) {
                    R.id.tab_favorites -> listener.onSavedTab(SavedTab.FAVORITES)
                    R.id.tab_collections -> listener.onSavedTab(SavedTab.COLLECTIONS)
                    R.id.tab_recent -> listener.onSavedTab(SavedTab.RECENT)
                }
            }
        }
        fun bind(row: FeedRow.SavedTabs) {
            val context = itemView.context
            fun label(res: Int, count: Int) = context.getString(res) + if (count > 0) "  $count" else ""
            binding = true
            favorites.text = label(R.string.tab_favourites, row.favorites)
            collections.text = label(R.string.tab_collections, row.collections)
            recent.text = label(R.string.tab_recent, row.recent)
            group.check(
                when (row.selected) {
                    SavedTab.FAVORITES -> R.id.tab_favorites
                    SavedTab.COLLECTIONS -> R.id.tab_collections
                    SavedTab.RECENT -> R.id.tab_recent
                }
            )
            binding = false
        }
    }

    private inner class CollectionHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val covers = listOf<ImageView>(
            view.findViewById(R.id.cover_0), view.findViewById(R.id.cover_1), view.findViewById(R.id.cover_2)
        )
        private val name: TextView = view.findViewById(R.id.collection_name)
        private val count: TextView = view.findViewById(R.id.collection_count)
        fun bind(row: FeedRow.CollectionCard) {
            val c = row.collection
            covers.forEachIndexed { i, view ->
                view.setBackgroundColor(PinUtils.placeholderColor("${c.id}$i"))
                val image = row.covers.getOrNull(i)
                if (image == null) {
                    Glide.with(view).clear(view)
                    view.setImageDrawable(null)
                } else {
                    Glide.with(view)
                        .load(image.mainUrl?.let { ImageUrls.sizedUrl(it, if (i == 0) 474 else 236) })
                        .into(view)
                }
            }
            name.text = c.name
            count.text = itemView.resources.getQuantityString(R.plurals.saved_items, c.keys.size, c.keys.size)
            itemView.setOnClickListener { listener.onCollectionClick(c) }
        }
    }

    private inner class RecentHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val seeAll: View = view.findViewById(R.id.recent_see_all)
        private val list: RecyclerView = view.findViewById(R.id.recent_list)
        private val thumbs = RecentThumbAdapter()
        init {
            list.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            list.adapter = thumbs
            seeAll.setOnClickListener { listener.onRecentSeeAll() }
            view.findViewById<View>(R.id.recent_clear).setOnClickListener { listener.onRecentClear() }
        }
        fun bind(row: FeedRow.Recent) {
            seeAll.isVisible = row.showSeeAll
            thumbs.submit(row.items) { index -> listener.onRecentClick(row.items, index) }
        }
    }

    private class RecentThumbAdapter : RecyclerView.Adapter<RecentThumbAdapter.Holder>() {
        private var items: List<Wallpaper> = emptyList()
        private var onClick: (Int) -> Unit = {}

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.thumb)
        }

        fun submit(newItems: List<Wallpaper>, click: (Int) -> Unit) {
            onClick = click
            if (newItems.map { it.key } != items.map { it.key }) {
                items = newItems
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_thumb, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val image = items[position]
            holder.thumb.setBackgroundColor(PinUtils.placeholderColor(image.key))
            holder.thumb.contentDescription = image.displayTitle
            Glide.with(holder.thumb).load(image.mainUrl?.let { ImageUrls.sizedUrl(it, 236) }).into(holder.thumb)
            holder.itemView.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) onClick(index)
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        private const val TYPE_PIN = 0
        private const val TYPE_AD = 1
        private const val TYPE_COLLECTION = 2
        private const val TYPE_HEADING = 3
        private const val TYPE_RECENT = 4
        private const val TYPE_EMPTY = 5
        private const val TYPE_LOADING = 6
        private const val TYPE_SYNC = 7
        private const val TYPE_TABS = 8
        private const val TYPE_SUB = 9

        private val DIFF = object : DiffUtil.ItemCallback<FeedRow>() {
            override fun areItemsTheSame(oldItem: FeedRow, newItem: FeedRow) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: FeedRow, newItem: FeedRow) = oldItem == newItem
        }

        /** Columns like the website: 2 on phones, 3 from 600dp, 4 from 840dp. */
        fun spanCount(context: Context): Int {
            val width = context.resources.configuration.screenWidthDp
            return when {
                width >= 840 -> 4
                width >= 600 -> 3
                else -> 2
            }
        }

        /**
         * Staggered grid that keeps items in the column they were placed in (new items go to the
         * shortest column), so nothing re-flows as more load.
         */
        fun newLayoutManager(context: Context, insideScrollView: Boolean = false): StaggeredGridLayoutManager =
            if (insideScrollView) {
                // GAP_HANDLING_NONE measures to 0 height inside a NestedScrollView
                StaggeredGridLayoutManager(spanCount(context), StaggeredGridLayoutManager.VERTICAL)
            } else {
                WrapStaggeredGridLayoutManager(spanCount(context), StaggeredGridLayoutManager.VERTICAL).apply {
                    gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
                }
            }

        fun syncText(context: Context, status: SavedSync.Status, email: String?): String = when (status) {
            SavedSync.Status.ERROR -> context.getString(R.string.sync_error)
            SavedSync.Status.SYNCING -> context.getString(R.string.sync_syncing)
            else -> if (email != null) context.getString(R.string.synced_with, email) else context.getString(R.string.sync_synced)
        }
    }
}
