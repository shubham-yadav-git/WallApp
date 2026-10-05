package com.sky.wallapp

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView

/** Masonry "pin" cell (`res/layout/row.xml`). */
class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val imageView: ImageView = itemView.findViewById(R.id.imageView)
    val favoriteButton: ImageButton = itemView.findViewById(R.id.favorite_btn)
    val textView: TextView = itemView.findViewById(R.id.title_tv)
    val cardViewParent: MaterialCardView = itemView.findViewById(R.id.parent_card_layout)

    /**
     * Binds [model] as a pin. [onFavoriteToggled] is called with the new favorite state
     * after [FavoritesStore] has been updated.
     */
    fun bind(model: Model, onClick: () -> Unit, onFavoriteToggled: (Boolean) -> Unit) {
        textView.text = model.title
        textView.isVisible = !model.title.isNullOrBlank()

        val params = imageView.layoutParams as ConstraintLayout.LayoutParams
        val ratio = aspectRatioFor(model)
        if (params.dimensionRatio != ratio) {
            params.dimensionRatio = ratio
            imageView.layoutParams = params
        }

        Glide.with(imageView)
            .load(model.displayUrl)
            .transition(DrawableTransitionOptions.withCrossFade(150))
            .into(imageView)

        cardViewParent.setOnClickListener { onClick() }
        bindFavorite(model, onFavoriteToggled)
    }

    private fun bindFavorite(model: Model, onFavoriteToggled: (Boolean) -> Unit) {
        val context = itemView.context
        if (model.image.isNullOrBlank()) {
            favoriteButton.isEnabled = false
            favoriteButton.alpha = 0.4f
            renderFavorite(false)
            favoriteButton.setOnClickListener(null)
            return
        }

        favoriteButton.isEnabled = true
        favoriteButton.alpha = 1f
        renderFavorite(FavoritesStore.isFavorite(context, model.image))

        favoriteButton.setOnClickListener {
            val nowFavorite = FavoritesStore.toggleFavorite(context, model.title, model.image)
            favoriteButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            renderFavorite(nowFavorite)

            // Micro animation for better touch feedback on heart toggle.
            favoriteButton.animate().cancel()
            favoriteButton.animate()
                .scaleX(0.75f)
                .scaleY(0.75f)
                .setDuration(70)
                .withEndAction {
                    favoriteButton.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                }
                .start()

            onFavoriteToggled(nowFavorite)
        }
    }

    private fun renderFavorite(isFavorite: Boolean) {
        favoriteButton.isSelected = isFavorite
        favoriteButton.isActivated = isFavorite
        favoriteButton.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_24 else R.drawable.ic_favorite_border_24
        )
        favoriteButton.contentDescription = itemView.context.getString(
            if (isFavorite) R.string.favorited else R.string.favorite
        )
    }

    companion object {
        /** Portrait ratios that give the masonry feed its staggered rhythm. */
        private val PIN_RATIOS = listOf("2:3", "3:5", "9:16", "3:4", "4:5")

        fun inflate(parent: ViewGroup): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.row, parent, false))

        /** Stable per wallpaper, so a pin keeps its height across rebinds and screens. */
        fun aspectRatioFor(model: Model): String {
            val key = model.image ?: model.title ?: return PIN_RATIOS.first()
            return PIN_RATIOS[key.hashCode().mod(PIN_RATIOS.size)]
        }
    }
}
