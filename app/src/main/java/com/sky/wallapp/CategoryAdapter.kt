package com.sky.wallapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView

class CategoryAdapter(
    private val categories: List<Category>,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = -1

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
        
        holder.nameTv.text = category.name
        
        val context = holder.itemView.context
        
        // Colors for selection state using Material 3 theme attributes or project colors
        val bgColor = if (isSelected) R.color.primaryContainer else R.color.surfaceVariant
        val contentColor = if (isSelected) R.color.onPrimaryContainer else R.color.onSurfaceVariant
        
        holder.card.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))
        holder.nameTv.setTextColor(ContextCompat.getColor(context, contentColor))
        holder.iconIv.setColorFilter(ContextCompat.getColor(context, contentColor))
        
        // Use the gallery icon as the default/placeholder for everything
        val defaultIcon = R.drawable.ic_action_category

        if (!category.icon.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(category.icon)
                .placeholder(defaultIcon)
                .error(defaultIcon)
                .into(holder.iconIv)
        } else {
            holder.iconIv.setImageResource(defaultIcon)
        }

        holder.card.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
            onCategoryClick(category)
        }
    }

    fun clearSelection() {
        val oldPosition = selectedPosition
        selectedPosition = -1
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
    }

    override fun getItemCount() = categories.size
}
