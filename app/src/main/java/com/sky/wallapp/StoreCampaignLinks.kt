package com.sky.wallapp

import java.net.URLEncoder

/** Play Store links used by app-owned sharing campaigns. */
object StoreCampaignLinks {

    const val APP_SHARE_SOURCE = "wallapp"
    const val APP_SHARE_MEDIUM = "app_share"
    const val APP_SHARE_CAMPAIGN = "friend_referral"

    private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details"

    fun appShareUrl(packageName: String): String {
        val referrer = "utm_source=$APP_SHARE_SOURCE" +
            "&utm_medium=$APP_SHARE_MEDIUM" +
            "&utm_campaign=$APP_SHARE_CAMPAIGN"
        return "$PLAY_STORE_URL?id=$packageName&referrer=${URLEncoder.encode(referrer, "UTF-8")}"
    }
}
