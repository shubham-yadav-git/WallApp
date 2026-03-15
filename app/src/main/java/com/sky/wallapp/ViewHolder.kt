package com.sky.wallapp

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var imageView: ImageView = itemView.findViewById(R.id.imageView)
    var textView: TextView = itemView.findViewById(R.id.title_tv)
    var cardViewParent: MaterialCardView = itemView.findViewById(R.id.parent_card_layout)
}
