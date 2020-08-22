package com.sky.wallapp;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;


public class ViewHolder extends RecyclerView.ViewHolder {
    View mView;
    public ImageView imageView;
    public TextView textView;
    public CardView cardViewParent;

    public ViewHolder(@NonNull final View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.imageView);
        textView = itemView.findViewById(R.id.title_tv);
        cardViewParent=itemView.findViewById(R.id.parent_card_layout);

    }
}
