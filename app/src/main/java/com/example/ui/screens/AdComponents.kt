package com.example.ui.screens

import android.content.Context
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.example.BuildConfig

// Singleton AdManager to control frequency limits and prevent overlapping/consecutive showing
object AdManager {
    private var lastInterstitialShowTime: Long = 0
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 45000 // 45 seconds interval

    fun canShowInterstitial(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastInterstitialShowTime) >= MIN_INTERSTITIAL_INTERVAL_MS
    }

    fun recordInterstitialShown() {
        lastInterstitialShowTime = System.currentTimeMillis()
    }
}

@Composable
fun AdBannerView(
    modifier: Modifier = Modifier,
    isSecondary: Boolean = false
) {
    val context = LocalContext.current
    var isAdFailed by remember { mutableStateOf(false) }
    var isAdLoaded by remember { mutableStateOf(false) }

    val adUnitId = if (BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/6300978111" // Google Test Banner ID
    } else {
        if (isSecondary) {
            "ca-app-pub-7994338654022536/4823605316" // Banner Ads 2
        } else {
            "ca-app-pub-7994338654022536/7669260506" // Banner Ads 1
        }
    }

    if (isAdFailed) {
        // Collapsed if ad failed to load to keep UI fully usable and not display empty distracting area
        return
    }

    val displayMetrics = context.resources.displayMetrics
    val widthInDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdUnitId(adUnitId)
                    val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthInDp)
                    setAdSize(adSize)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                        }
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            isAdFailed = true
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            },
            update = { adView ->
                // Handled automatically by the AdView instance
            }
        )
    }
}

@Composable
fun TestInterstitialAd(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    // If frequency limit is active, gracefully dismiss immediately and continue action
    if (!AdManager.canShowInterstitial()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    val context = LocalContext.current
    var isAdLoaded by remember { mutableStateOf(false) }
    var mInterstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var adFailedToLoad by remember { mutableStateOf(false) }

    val adUnitId = if (BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/1033173712" // Test Interstitial ID
    } else {
        "ca-app-pub-7994338654022536/3318951950" // Production Interstitial ID
    }

    LaunchedEffect(show) {
        if (show) {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mInterstitialAd = null
                        adFailedToLoad = true
                        // Gracefully proceed on failure to avoid blocking the user
                        onDismiss()
                    }

                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        mInterstitialAd = interstitialAd
                        isAdLoaded = true
                    }
                }
            )
        }
    }

    // If ad is loaded, try to show it using the current Activity
    if (isAdLoaded && mInterstitialAd != null) {
        val activity = context.findActivity()
        if (activity != null) {
            val ad = mInterstitialAd!!
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    AdManager.recordInterstitialShown()
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mInterstitialAd = null
                    onDismiss()
                }

                override fun onAdShowedFullScreenContent() {
                    // Called when ad is shown.
                }
            }
            // Show the ad
            LaunchedEffect(ad) {
                ad.show(activity)
            }
        } else {
            // Cannot find activity, dismiss gracefully
            LaunchedEffect(Unit) {
                onDismiss()
            }
        }
    } else if (!adFailedToLoad) {
        // While loading the ad, show a beautiful loading indicator dialog
        Dialog(
            onDismissRequest = { onDismiss() },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Loading Ad...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AdNativeAdvancedView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var adLoaded by remember { mutableStateOf(false) }
    var adFailed by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()

    val adUnitId = if (BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/2247696110" // Test Native Advanced ID
    } else {
        "ca-app-pub-7994338654022536/7996563560" // Production Native ID
    }

    DisposableEffect(Unit) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd = ad
                adLoaded = true
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    adFailed = true
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
        }
    }

    if (adFailed) {
        return
    }

    if (adLoaded && nativeAd != null) {
        val ad = nativeAd!!
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                factory = { ctx ->
                    val nativeAdView = NativeAdView(ctx)
                    
                    val root = android.widget.LinearLayout(ctx).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Row for Icon and Title/Headline
                    val headerRow = android.widget.LinearLayout(ctx).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // AD Tag
                    val adTag = android.widget.TextView(ctx).apply {
                        text = "AD"
                        textSize = 9f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(android.graphics.Color.parseColor("#4285F4")) // Google Ad Blue
                        setPadding(12, 4, 12, 4)
                        val params = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            rightMargin = 16
                        }
                        layoutParams = params
                    }
                    headerRow.addView(adTag)

                    // Icon view
                    val iconView = android.widget.ImageView(ctx).apply {
                        val size = (36 * ctx.resources.displayMetrics.density).toInt()
                        layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                            rightMargin = 16
                        }
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                    headerRow.addView(iconView)
                    nativeAdView.iconView = iconView

                    // Headline view
                    val headlineView = android.widget.TextView(ctx).apply {
                        textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    }
                    headerRow.addView(headlineView)
                    nativeAdView.headlineView = headlineView

                    root.addView(headerRow)

                    // Body text
                    val bodyView = android.widget.TextView(ctx).apply {
                        textSize = 12f
                        val params = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 8
                            bottomMargin = 8
                        }
                        layoutParams = params
                    }
                    root.addView(bodyView)
                    nativeAdView.bodyView = bodyView

                    // Media view
                    val mediaView = MediaView(ctx).apply {
                        val height = (110 * ctx.resources.displayMetrics.density).toInt()
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            height
                        ).apply {
                            bottomMargin = 8
                        }
                    }
                    root.addView(mediaView)
                    nativeAdView.mediaView = mediaView

                    // Call to Action button
                    val ctaView = android.widget.Button(ctx).apply {
                        textSize = 13f
                        val params = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            (38 * ctx.resources.displayMetrics.density).toInt()
                        )
                        layoutParams = params
                    }
                    root.addView(ctaView)
                    nativeAdView.callToActionView = ctaView

                    nativeAdView.addView(root)
                    nativeAdView
                },
                update = { nativeAdView ->
                    val textColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    val subColor = if (isDarkTheme) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY

                    (nativeAdView.headlineView as? android.widget.TextView)?.apply {
                        text = ad.headline
                        setTextColor(textColor)
                    }
                    (nativeAdView.bodyView as? android.widget.TextView)?.apply {
                        text = ad.body
                        setTextColor(subColor)
                    }
                    
                    if (ad.icon != null) {
                        nativeAdView.iconView?.visibility = android.view.View.VISIBLE
                        (nativeAdView.iconView as? android.widget.ImageView)?.setImageDrawable(ad.icon?.drawable)
                    } else {
                        nativeAdView.iconView?.visibility = android.view.View.GONE
                    }

                    if (ad.callToAction != null) {
                        nativeAdView.callToActionView?.visibility = android.view.View.VISIBLE
                        (nativeAdView.callToActionView as? android.widget.Button)?.apply {
                            text = ad.callToAction
                        }
                    } else {
                        nativeAdView.callToActionView?.visibility = android.view.View.GONE
                    }

                    nativeAdView.setNativeAd(ad)
                }
            )
        }
    } else {
        // Subtle loading shimmer / skeleton card that is clearly distinguishable
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sponsored Content",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// AppOpenAdManager to manage loading and foreground app open ads
class AppOpenAdManager(private val context: Context) {
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTime: Long = 0

    private val adUnitId: String
        get() = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/9257395921" // Test App Open ID
        } else {
            "ca-app-pub-7994338654022536/7503890346" // Production App Open ID
        }

    fun loadAd(onComplete: (Boolean) -> Unit = {}) {
        if (isLoadingAd || isAdAvailable()) {
            onComplete(isAdAvailable())
            return
        }
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                    onComplete(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    onComplete(false)
                }
            }
        )
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = System.currentTimeMillis() - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    fun showAdIfAvailable(activity: Activity, onDismiss: () -> Unit = {}) {
        if (!isAdAvailable()) {
            loadAd()
            onDismiss()
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                loadAd()
                onDismiss()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                loadAd()
                onDismiss()
            }

            override fun onAdShowedFullScreenContent() {
                // Ad presented successfully
            }
        }
        appOpenAd?.show(activity)
    }
}

// Helper extension function to find the Activity from Context
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
