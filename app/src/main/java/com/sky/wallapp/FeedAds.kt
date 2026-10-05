package com.sky.wallapp

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.material.button.MaterialButton

/**
 * Loads a small batch of AdMob native ads to show as sponsored pins in the masonry feed.
 * Disabled when the ad unit ID is blank (release builds until a real native unit is configured).
 */
class FeedAds(context: Context, private val adUnitId: String) {

    private val appContext = context.applicationContext
    private val loadedAds = mutableListOf<NativeAd>()
    private var loadedAt = 0L
    private var isLoading = false
    private var destroyed = false

    /** Called on the main thread whenever the set of available ads changes. */
    var onAdsChanged: (() -> Unit)? = null

    val ads: List<NativeAd> get() = loadedAds

    val isEnabled: Boolean get() = adUnitId.isNotBlank()

    /** Loads ads if none are loaded yet or the current ones are close to expiring (~1 hour). */
    fun loadIfNeeded() {
        if (!isEnabled || destroyed || isLoading) return
        val isStale = SystemClock.elapsedRealtime() - loadedAt > AD_MAX_AGE_MS
        if (loadedAds.isNotEmpty() && !isStale) return

        isLoading = true
        val fresh = mutableListOf<NativeAd>()
        lateinit var loader: AdLoader
        loader = AdLoader.Builder(appContext, adUnitId)
            .forNativeAd { ad ->
                if (destroyed) {
                    ad.destroy()
                    return@forNativeAd
                }
                fresh += ad
                if (!loader.isLoading) publish(fresh)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Native ad failed to load: ${error.code} ${error.message}")
                    if (!loader.isLoading) publish(fresh)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT)
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
        loader.loadAds(AdRequest.Builder().build(), MAX_ADS)
    }

    private fun publish(fresh: List<NativeAd>) {
        isLoading = false
        if (fresh.isEmpty()) return
        loadedAds.forEach { it.destroy() }
        loadedAds.clear()
        loadedAds.addAll(fresh)
        loadedAt = SystemClock.elapsedRealtime()
        onAdsChanged?.invoke()
    }

    fun destroy() {
        destroyed = true
        onAdsChanged = null
        loadedAds.forEach { it.destroy() }
        loadedAds.clear()
    }

    /** Native ad rendered as a pin (`res/layout/item_native_ad.xml`). */
    class PinHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val adView = itemView as NativeAdView
        private val media: MediaView = itemView.findViewById(R.id.ad_media)
        private val headline: TextView = itemView.findViewById(R.id.ad_headline)
        private val advertiser: TextView = itemView.findViewById(R.id.ad_advertiser)
        private val cta: MaterialButton = itemView.findViewById(R.id.ad_cta)

        init {
            adView.mediaView = media
            adView.headlineView = headline
            adView.advertiserView = advertiser
            adView.callToActionView = cta
            media.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }

        fun bind(ad: NativeAd) {
            val context = itemView.context
            headline.text = ad.headline
            advertiser.text = listOfNotNull(
                context.getString(R.string.sponsored),
                ad.advertiser?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            cta.text = ad.callToAction
            cta.isVisible = !ad.callToAction.isNullOrBlank()

            // Match the creative's shape, within the same range as wallpaper pins.
            val aspect = ad.mediaContent?.aspectRatio?.takeIf { it > 0f }?.coerceIn(0.5f, 1.5f) ?: 0.8f
            val params = media.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = aspect.toString()
            media.layoutParams = params

            adView.setNativeAd(ad)
        }

        companion object {
            fun inflate(parent: ViewGroup): PinHolder =
                PinHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_native_ad, parent, false))
        }
    }

    companion object {
        private const val TAG = "FeedAds"
        private const val MAX_ADS = 5
        private const val AD_MAX_AGE_MS = 55L * 60 * 1000
    }
}
