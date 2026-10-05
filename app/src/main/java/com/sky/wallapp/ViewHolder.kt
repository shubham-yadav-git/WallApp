package com.sky.wallapp

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import android.graphics.drawable.Drawable

/** Masonry pin (`res/layout/row.xml`): image at its natural ratio, placeholder colour, saved badge. */
class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val imageView: ImageView = itemView.findViewById(R.id.imageView)
    private val savedBadge: ImageView = itemView.findViewById(R.id.saved_badge)
    val cardViewParent: MaterialCardView = itemView.findViewById(R.id.parent_card_layout)

    fun bind(image: Wallpaper, saved: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
        val placeholder = PinUtils.placeholderColor(image.key)
        cardViewParent.setCardBackgroundColor(placeholder)

        val params = imageView.layoutParams as ConstraintLayout.LayoutParams
        val ratio = "1:${RatioCache.ratioOf(image)}"
        if (params.dimensionRatio != ratio) {
            params.dimensionRatio = ratio
            imageView.layoutParams = params
        }

        loadWithFallbacks(imageView, image, ImageUrls.tileUrl(image, gridWidth(itemView)))
        imageView.contentDescription = image.displayTitle
        savedBadge.isVisible = saved

        cardViewParent.setOnClickListener { onClick() }
        if (onLongClick != null) {
            cardViewParent.setOnLongClickListener { onLongClick(); true }
        } else {
            cardViewParent.setOnLongClickListener(null)
        }
    }

    companion object {
        fun inflate(parent: ViewGroup): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.row, parent, false))

        /** 236px tiles on small / low-density screens, 474px otherwise (like the website's srcset). */
        fun gridWidth(view: View): Int {
            val columnPx = view.resources.displayMetrics.widthPixels / 2
            return if (columnPx <= ImageUrls.GRID_WIDTH_SMALL * 1.25) ImageUrls.GRID_WIDTH_SMALL else ImageUrls.GRID_WIDTH
        }

        /** Loads [primary], then the record's fallback sources in order if it fails. */
        fun loadWithFallbacks(target: ImageView, image: Wallpaper, primary: String?) {
            val requests = (listOfNotNull(primary) + ImageUrls.fallbackSources(image)).distinct()
            if (requests.isEmpty()) {
                Glide.with(target).clear(target)
                target.setImageDrawable(null)
                return
            }
            val glide = Glide.with(target)
            var chain: RequestBuilder<Drawable>? = null
            for (url in requests.asReversed()) {
                val request = glide.load(url)
                chain = if (chain == null) request else request.error(chain)
            }
            chain!!
                .placeholder(ColorDrawable(android.graphics.Color.TRANSPARENT))
                .transition(DrawableTransitionOptions.withCrossFade(180))
                .into(target)
        }
    }
}
