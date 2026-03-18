package com.sky.wallapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CategoryAdapter(
    private val categories: List<Category>,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

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
        holder.nameTv.text = category.name
        
        // You can set different icons based on category name if available
        // holder.iconIv.setImageResource(...) 

        holder.card.setOnClickListener { onCategoryClick(category) }
    }

    override fun getItemCount() = categories.size
}
