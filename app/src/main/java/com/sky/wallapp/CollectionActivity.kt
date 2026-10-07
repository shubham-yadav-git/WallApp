package com.sky.wallapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.HapticFeedbackConstants
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sky.wallapp.databinding.ActivityCollectionBinding
import kotlinx.coroutines.launch

/** One collection: its wallpapers, rename, delete (with Undo back on Saved) and remove images (with Undo). */
class CollectionActivity : AppCompatActivity(), FeedListener {

    private lateinit var binding: ActivityCollectionBinding
    private lateinit var adapter: FeedAdapter
    private lateinit var collectionId: String
    private var items: List<Wallpaper> = emptyList()
    private var measuredFor: List<String>? = null

    /** The close-up's "category" / "View saved" results belong to the main screen: pass them on. */
    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        collectionId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        SavedSync.init(this)
        WallpaperRepository.start(this)

        adapter = FeedAdapter(this)
        binding.grid.layoutManager = FeedAdapter.newLayoutManager(this)
        binding.grid.adapter = adapter
        binding.grid.itemAnimator = null

        val bottomPadding = binding.grid.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.updatePadding(top = bars.top)
            binding.grid.updatePadding(bottom = bottomPadding + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRename.setOnClickListener { rename() }
        binding.btnDelete.setOnClickListener { delete() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { SavedRepository.state.collect { render() } }
                launch { WallpaperRepository.state.collect { render() } }
            }
        }
    }

    private fun collection() = SavedRepository.state.value.collections.firstOrNull { it.id == collectionId }

    private fun render() {
        val collection = collection()
        val repo = WallpaperRepository.state.value
        binding.btnRename.isVisible = collection != null
        binding.btnDelete.isVisible = collection != null

        if (collection == null) {
            binding.collectionTitle.setText(R.string.collection_not_found)
            binding.collectionCount.text = ""
            adapter.submitList(listOf(FeedRow.Empty(null, getString(R.string.collection_not_found), getString(R.string.back), 0)))
            return
        }
        binding.collectionTitle.text = collection.name
        val count = resources.getQuantityString(R.plurals.saved_items, collection.keys.size, collection.keys.size)
        binding.collectionCount.text =
            if (collection.keys.isEmpty()) count else "$count · ${getString(R.string.remove_from_collection_hint)}"

        if (collection.keys.isEmpty()) {
            adapter.submitList(
                listOf(
                    FeedRow.Empty(
                        getString(R.string.empty_collection_title), getString(R.string.empty_collection_text),
                        getString(R.string.browse_wallpapers), 0, R.drawable.ic_collections_24
                    )
                )
            )
            return
        }
        if (!repo.allLoaded) {
            adapter.submitList(listOf(FeedRow.Loading))
            return
        }

        items = collection.keys.mapNotNull { repo.byKey[it] }
        val keys = items.map { it.key }
        if (measuredFor == null || !keys.all { it in measuredFor!! }) {
            if (measuredFor == null) adapter.submitList(listOf(FeedRow.Loading))
            lifecycleScope.launch {
                RatioCache.measure(this@CollectionActivity, items)
                measuredFor = keys
                render()
            }
            if (measuredFor == null) return
        }
        val favorites = SavedRepository.state.value.favorites.mapTo(HashSet()) { it.key }
        adapter.submitList(items.mapIndexed { i, image -> FeedRow.Pin(image, i, image.key in favorites) })
    }

    override fun onEmptyAction(actionId: Int) = finish()

    override fun onPinClick(row: FeedRow.Pin) {
        detailLauncher.launch(ImageActivity.intent(this, items, row.index, "collection"))
    }

    override fun onPinLongClick(row: FeedRow.Pin) {
        val collection = collection() ?: return
        binding.grid.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val undo = SavedRepository.removeFromCollection(collection.id, row.image.key)
        Snackbar.make(binding.root, getString(R.string.removed_from_collection, collection.name), 6000)
            .setAction(R.string.undo) { undo() }
            .show()
    }

    private fun rename() {
        val collection = collection() ?: return
        val input = EditText(this).apply {
            setText(collection.name)
            setSelection(collection.name.length)
            filters = arrayOf(InputFilter.LengthFilter(SavedStore.NAME_MAX))
            hint = getString(R.string.collection_name)
            isSingleLine = true
        }
        val container = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_collection)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ -> SavedRepository.renameCollection(collection.id, input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun delete() {
        val collection = collection() ?: return
        pendingUndo = SavedRepository.deleteCollection(collection.id)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DELETED_NAME, collection.name))
        finish()
    }

    companion object {
        const val EXTRA_ID = "collection_id"
        const val EXTRA_DELETED_NAME = "deleted_name"

        /** Undo for the last deleted collection, offered by the Saved screen's snackbar. */
        var pendingUndo: (() -> Unit)? = null

        fun intent(context: Context, id: String) =
            Intent(context, CollectionActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
