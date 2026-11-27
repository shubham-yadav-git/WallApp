package com.sky.wallapp

import android.content.Context
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private const val MAX_CONCURRENT_MEASUREMENTS = 3

    private val ratios = ConcurrentHashMap<String, Double>()
    private val measurementPermits = Semaphore(MAX_CONCURRENT_MEASUREMENTS)

    fun ratioOf(image: Wallpaper): Double =
        ratios[image.layoutKey] ?: ImageUrls.knownRatio(image)?.also { ratios[image.layoutKey] = it } ?: FALLBACK_RATIO

    /** Measures every image in [images] whose ratio isn't known yet. */
    suspend fun measure(context: Context, images: List<Wallpaper>) = coroutineScope {
        val appContext = context.applicationContext
        images
            .filter { !ratios.containsKey(it.layoutKey) && ImageUrls.knownRatio(it) == null }
            .map { image ->
                async(Dispatchers.IO) {
                    val url = ImageUrls.tileUrl(image) ?: return@async
                    measurementPermits.withPermit {
                        val glide = Glide.with(appContext)
                        val future = glide.load(url).submit()
                        try {
                            val drawable = future.get(MEASURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                ratios[image.layoutKey] = drawable.intrinsicHeight.toDouble() / drawable.intrinsicWidth
                            } else {
                                ratios[image.layoutKey] = FALLBACK_RATIO
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: InterruptedException) {
                            ratios[image.layoutKey] = FALLBACK_RATIO
                            Thread.currentThread().interrupt()
                            currentCoroutineContext().ensureActive()
                        } catch (_: Exception) {
                            // Slow or broken: lock in the fallback so the tile never changes shape later
                            ratios[image.layoutKey] = FALLBACK_RATIO
                        } finally {
                            glide.clear(future)
                        }
                    }
                }
            }
            .awaitAll()
    }

    /** True once [image]'s ratio is known (measured or from its URL). */
    fun isKnown(image: Wallpaper) = ratios.containsKey(image.layoutKey) || ImageUrls.knownRatio(image) != null
}
