package by.mlastovsky.kosht.ui.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import by.mlastovsky.kosht.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An adaptive banner, sized to the screen so it never crowds the content above
 * it. Premium hides it entirely — the composable emits nothing, not an empty
 * gap. In previews and tests there is no ad, only the space it would take.
 */
@Composable
fun BannerAd(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible || LocalInspectionMode.current) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AdsInitializer.ensureStarted(context)
        ready = true
    }
    if (!ready) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { viewContext ->
            AdView(viewContext).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        viewContext,
                        widthDp
                    )
                )
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

private object AdsInitializer {

    @Volatile
    private var started = false

    suspend fun ensureStarted(context: Context) {
        if (started) return
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            synchronized(this@AdsInitializer) {
                if (started) return@withContext
                MobileAds.initialize(appContext)
                started = true
            }
        }
    }
}
