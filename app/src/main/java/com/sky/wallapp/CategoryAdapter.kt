package com.sky.wallapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CategoryAdapter(
    private val categories: List<Category>,
    private val firebaseDatabase: FirebaseDatabase,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = -1
    /** Cache: category path → first image URL (null = tried, nothing found) */
    private val firstImageCache = mutableMapOf<String, String?>()

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTv: TextView = view.findViewById(R.id.category_name)
        val iconIv: ImageView = view.findViewById(R.id.category_icon)
        val card: MaterialCardView = view.findViewById(R.id.category_card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_compact, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        val isSelected = position == selectedPosition
        val context = holder.itemView.context

        holder.nameTv.text = category.name

        // Selection: highlight with a colored stroke and raised elevation
        val strokePx = (3 * context.resources.displayMetrics.density).toInt()
        holder.card.strokeWidth = if (isSelected) strokePx else 0
        holder.card.strokeColor = if (isSelected)
            ContextCompat.getColor(context, R.color.primary) else Color.TRANSPARENT
        holder.card.cardElevation = if (isSelected)
            context.resources.displayMetrics.density * 6 else context.resources.displayMetrics.density * 3

        val defaultIcon = R.drawable.ic_action_category

        when {
            // Load first photo from the category's Firebase path as thumbnail
            !category.path.isNullOrEmpty() -> {
                val path = category.path!!

                fun loadImageIntoHolder(imageUrl: String?) {
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(context)
                            .load(imageUrl)
                            .placeholder(defaultIcon)
                            .error(defaultIcon)
                            .centerCrop()
                            .into(holder.iconIv)
                    } else {
                        holder.iconIv.setImageResource(defaultIcon)
                    }
                }

                if (firstImageCache.containsKey(path)) {
                    // Already fetched — use cached result immediately
                    loadImageIntoHolder(firstImageCache[path])
                } else {
                    // Show placeholder while fetching
                    holder.iconIv.setImageResource(defaultIcon)

                    firebaseDatabase.getReference(path)
                        .limitToFirst(1)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val imageUrl = snapshot.children
                                    .firstOrNull()
                                    ?.getValue(Model::class.java)
                                    ?.image

                                firstImageCache[path] = imageUrl

                                // Only update if this ViewHolder is still attached
                                if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                    loadImageIntoHolder(imageUrl)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                firstImageCache[path] = null
                            }
                        })
                }
            }

            // 3. Nothing available — fall back to default tinted vector icon
            else -> holder.iconIv.setImageResource(defaultIcon)
        }

        holder.card.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            if (oldPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(oldPosition)
            }
            if (selectedPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(selectedPosition)
            }
            onCategoryClick(category)
        }
    }

    fun clearSelection() {
        val oldPosition = selectedPosition
        selectedPosition = -1
        if (oldPosition != -1) notifyItemChanged(oldPosition)
    }

    override fun getItemCount() = categories.size
}
