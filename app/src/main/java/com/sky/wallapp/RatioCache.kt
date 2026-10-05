package com.sky.wallapp

import android.content.Context
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Height / width per wallpaper key. Ratios are known up front for Pexels URLs and records with
 * width/height; others are measured by loading the tile image once (which also warms Glide's
 * cache) before the tile is placed — so masonry tiles never change size after appearing.
 */
object RatioCache {

    const val FALLBACK_RATIO = 1.4
    private const val MEASURE_TIMEOUT_MS = 2_500L

    private val ratios = ConcurrentHashMap<String, Double>()

    fun ratioOf(image: Wallpaper): Double =
        ratios[image.key] ?: ImageUrls.knownRatio(image)?.also { ratios[image.key] = it } ?: FALLBACK_RATIO

    /** Measures every image in [images] whose ratio isn't known yet. */
    suspend fun measure(context: Context, images: List<Wallpaper>) = coroutineScope {
        val appContext = context.applicationContext
        images
            .filter { !ratios.containsKey(it.key) && ImageUrls.knownRatio(it) == null }
            .map { image ->
                async(Dispatchers.IO) {
                    val url = ImageUrls.tileUrl(image) ?: return@async
                    // Future.get with a timeout: a blocking get() can't be cancelled by coroutines
                    val future = Glide.with(appContext).load(url).submit()
                    runCatching {
                        val drawable = future.get(MEASURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                            ratios[image.key] = drawable.intrinsicHeight.toDouble() / drawable.intrinsicWidth
                        }
                    }.onFailure {
                        // Slow or broken: lock in the fallback so the tile never changes shape later
                        ratios[image.key] = FALLBACK_RATIO
                        Glide.with(appContext).clear(future)
                    }
                }
            }
            .awaitAll()
    }

    /** True once [image]'s ratio is known (measured or from its URL). */
    fun isKnown(image: Wallpaper) = ratios.containsKey(image.key) || ImageUrls.knownRatio(image) != null
}
