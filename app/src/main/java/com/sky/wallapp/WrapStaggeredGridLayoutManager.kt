package com.sky.wallapp

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class WrapStaggeredGridLayoutManager : StaggeredGridLayoutManager {
    private var loggedLayoutRace = false

    constructor(spanCount: Int, orientation: Int) : super(spanCount, orientation)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            if (!loggedLayoutRace) {
                loggedLayoutRace = true
                Log.w(TAG, "Staggered grid layout race recovered", e)
            }
        }
    }

    private companion object {
        const val TAG = "WrapStaggeredGrid"
    }
}
