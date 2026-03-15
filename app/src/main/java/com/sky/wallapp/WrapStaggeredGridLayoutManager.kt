package com.sky.wallapp

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class WrapStaggeredGridLayoutManager : StaggeredGridLayoutManager {
    constructor(spanCount: Int, orientation: Int) : super(spanCount, orientation)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            // This is a known bug in RecyclerView when using StaggeredGridLayoutManager
            // Catching it prevents the app from crashing.
        }
    }
}
