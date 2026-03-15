package com.sky.wallapp

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var imageView: ImageView = itemView.findViewById(R.id.imageView)
    var textView: TextView = itemView.findViewById(R.id.title_tv)
    var cardViewParent: CardView = itemView.findViewById(R.id.parent_card_layout)
}
