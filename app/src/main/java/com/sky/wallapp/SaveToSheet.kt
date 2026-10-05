package com.sky.wallapp

import android.text.InputType
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch

/** "Save to…" sheet: Favourites, each collection, and an inline "New collection" (website SaveMenu). */
object SaveToSheet {

    fun show(
        activity: AppCompatActivity,
        image: Wallpaper,
        onToggleFavorite: () -> Unit,
        notify: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_save_to, null)
        dialog.setContentView(view)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val targets = view.findViewById<LinearLayout>(R.id.targets)
        val newCollection = view.findViewById<MaterialButton>(R.id.new_collection)
        val createRow = view.findViewById<LinearLayout>(R.id.create_row)
        val nameInput = view.findViewById<EditText>(R.id.collection_name_input)
        val createButton = view.findViewById<MaterialButton>(R.id.create_button)
        nameInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        fun addTarget(name: String, count: String?, iconRes: Int, checked: Boolean, onClick: () -> Unit) {
            val row = LayoutInflater.from(activity).inflate(R.layout.item_save_target, targets, false)
            row.findViewById<TextView>(R.id.target_name).text = name
            row.findViewById<TextView>(R.id.target_count).apply {
                text = count
                isVisible = count != null
            }
            row.findViewById<ImageView>(R.id.target_icon).apply {
                isVisible = iconRes != 0
                if (iconRes != 0) setImageResource(iconRes)
            }
            row.findViewById<MaterialCheckBox>(R.id.target_check).isChecked = checked
            row.setOnClickListener { onClick() }
            targets.addView(row)
        }

        fun render(state: SavedStore.State) {
            targets.removeAllViews()
            val favorite = SavedStore.isFavorite(state, image.key)
            addTarget(
                activity.getString(R.string.tab_favourites), null,
                if (favorite) R.drawable.ic_favorite_24 else R.drawable.ic_favorite_border_24,
                favorite, onToggleFavorite
            )
            state.collections.forEach { c ->
                val inIt = image.key in c.keys
                addTarget(c.name, activity.resources.getQuantityString(R.plurals.saved_items, c.keys.size, c.keys.size), 0, inIt) {
                    SavedRepository.toggleInCollection(c.id, image.key)
                    notify(activity.getString(if (inIt) R.string.removed_from_collection else R.string.saved_to_collection, c.name))
                }
            }
        }

        val job = activity.lifecycleScope.launch { SavedRepository.state.collect { render(it) } }
        dialog.setOnDismissListener { job.cancel() }

        newCollection.setOnClickListener {
            newCollection.isVisible = false
            createRow.isVisible = true
            nameInput.requestFocus()
            dialog.window?.let { WindowCompat.getInsetsController(it, nameInput).show(WindowInsetsCompat.Type.ime()) }
        }
        nameInput.doAfterTextChanged { createButton.isEnabled = !it.isNullOrBlank() }

        fun create() {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) return
            SavedRepository.createCollection(name, image.key)
            notify(activity.getString(R.string.saved_to_collection, name.replace(Regex("\\s+"), " ").take(SavedStore.NAME_MAX)))
            nameInput.setText("")
            nameInput.clearFocus()
            dialog.window?.let { WindowCompat.getInsetsController(it, nameInput).hide(WindowInsetsCompat.Type.ime()) }
            createRow.isVisible = false
            newCollection.isVisible = true
        }
        createButton.setOnClickListener { create() }
        nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                create()
                true
            } else {
                false
            }
        }

        dialog.show()
    }
}
