package com.rainax.docscan

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob wrapper. These are Google's TEST ad unit ids - safe to click while developing.
 * Replace them with your own ids from the AdMob console before publishing
 * (and replace the app id in app/build.gradle.kts).
 */
object Ads {
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var lastInterstitial = 0L

    fun init(c: Context) {
        MobileAds.initialize(c)
        loadInterstitial(c)
        loadRewarded(c)
    }

    private fun loadInterstitial(c: Context) {
        InterstitialAd.load(c, INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
            })
    }

    private fun loadRewarded(c: Context) {
        RewardedAd.load(c, REWARDED, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null }
            })
    }

    /** Shows at most one interstitial per 60 seconds, never blocks the user if no ad is ready. */
    fun showInterstitial(a: Activity) {
        val ad = interstitial ?: return
        if (SystemClock.elapsedRealtime() - lastInterstitial < 60_000) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                lastInterstitial = SystemClock.elapsedRealtime()
                loadInterstitial(a)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                interstitial = null
                loadInterstitial(a)
            }
        }
        ad.show(a)
    }

    /** Runs [onReward] after the user watches the ad. If no ad is ready, the user gets the feature anyway. */
    fun showRewarded(a: Activity, onReward: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            loadRewarded(a)
            onReward()
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                loadRewarded(a)
                if (earned) onReward()
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                rewarded = null
                loadRewarded(a)
                onReward()
            }
        }
        ad.show(a) { earned = true }
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = Ads.BANNER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
