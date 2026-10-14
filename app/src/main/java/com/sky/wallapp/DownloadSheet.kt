package com.sky.wallapp

import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.checkbox.MaterialCheckBox

/** "Download for" sheet: picks (and remembers) a size, then downloads at it. */
object DownloadSheet {

    fun show(activity: AppCompatActivity, image: Wallpaper, onPick: (DownloadSizes.Option) -> Unit) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_download, null)
        dialog.setContentView(view)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val sizes = view.findViewById<LinearLayout>(R.id.sizes)
        val options = DownloadSizes.options(activity, image)
        val current = DownloadSizes.preferred(activity, options)
        options.forEach { option ->
            val row = LayoutInflater.from(activity).inflate(R.layout.item_save_target, sizes, false)
            row.findViewById<ImageView>(R.id.target_icon).isVisible = false
            row.findViewById<TextView>(R.id.target_name).setText(option.label)
            row.findViewById<TextView>(R.id.target_count).apply {
                text = DownloadSizes.detail(activity, option)
                isVisible = true
            }
            row.findViewById<MaterialCheckBox>(R.id.target_check).isChecked = option == current
            row.setOnClickListener {
                DownloadSizes.setPreferred(activity, option)
                dialog.dismiss()
                onPick(option)
            }
            sizes.addView(row)
        }
        dialog.show()
    }
}
