package com.sky.wallapp

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import androidx.annotation.StringRes
import androidx.core.content.edit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Download for…" sizes: this device's screen, common phone / desktop sizes, or the original.
 * The image is cropped (never stretched) to fill the size. The last choice is remembered on the
 * device and used by the main Download button. Port of the website's `DownloadMenu.js`.
 */
object DownloadSizes {

    /** [id] is also the `size` param of the `file_download` event; 0 × 0 for the original. */
    data class Option(val id: String, @StringRes val label: Int, val width: Int = 0, val height: Int = 0) {
        val isOriginal: Boolean get() = id == ORIGINAL
    }

    const val DEVICE = "device"
    const val ORIGINAL = "original"
    private const val MAX_SIDE = 3840
    private const val PREFS = "wallapp_prefs"
    private const val KEY = "wallapp:download-size"

    val SCREEN_SIZES = listOf(
        Option("phone", R.string.size_phone, 1080, 2400),
        Option("phone-qhd", R.string.size_phone_qhd, 1440, 3200),
        Option("tablet", R.string.size_tablet, 2048, 2732),
        Option("hd", R.string.size_desktop_hd, 1920, 1080),
        Option("qhd", R.string.size_desktop_qhd, 2560, 1440),
        Option("4k", R.string.size_desktop_4k, 3840, 2160)
    )

    private val original = Option(ORIGINAL, R.string.size_original)

    /** This screen in physical pixels, portrait, longest side capped at 4K. */
    fun deviceSize(activity: Activity): Option? {
        val (a, b) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
        val scale = min(1.0, MAX_SIDE.toDouble() / max(a, b).coerceAtLeast(1))
        val w = (min(a, b) * scale).roundToInt()
        val h = (max(a, b) * scale).roundToInt()
        return if (w > 0 && h > 0) Option(DEVICE, R.string.size_device, w, h) else null
    }

    /** Options for [image]: only the original when it can't be cropped; sizes that would upscale are left out. */
    fun options(activity: Activity, image: Wallpaper): List<Option> {
        if (!ImageUrls.canCrop(image)) return listOf(original)
        return (listOfNotNull(deviceSize(activity)) + SCREEN_SIZES)
            .filter { !ImageUrls.wouldUpscale(image, it.width, it.height) } + original
    }

    fun url(image: Wallpaper, option: Option): String? =
        if (option.isOriginal) ImageUrls.downloadUrl(image) else ImageUrls.sizedDownloadUrl(image, option.width, option.height)

    /** "1080 × 2400", or "Full size, uncropped" for the original. */
    fun detail(context: Context, option: Option): String =
        if (option.isOriginal) context.getString(R.string.size_original_detail)
        else context.getString(R.string.size_detail, option.width, option.height)

    private fun preferredId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: ORIGINAL

    fun setPreferred(context: Context, option: Option) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, option.id) }
    }

    /** The remembered option among [options], falling back to the original. */
    fun preferred(context: Context, options: List<Option>): Option {
        val id = preferredId(context)
        return options.firstOrNull { it.id == id } ?: options.last()
    }
}
