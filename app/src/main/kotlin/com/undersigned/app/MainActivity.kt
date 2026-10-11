package com.undersigned.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.undersigned.app.ui.NavIntents
import com.undersigned.app.ui.UndersignedNavHost
import android.content.Intent
import com.undersigned.app.ui.screens.BlockedRegionScreen
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.ui.theme.UndersignedTheme
import com.undersigned.app.util.GeoBlocker
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); NavIntents.consume(intent) }
    private var activityResultSender: ActivityResultSender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super.onCreate: the splash theme hands over to Theme.Undersigned here, mark on black, no animation.
        try { installSplashScreen() } catch (_: Exception) { }
        super.onCreate(savedInstanceState)
        try { activityResultSender = ActivityResultSender(this) } catch (e: Exception) { Log.e("MainActivity", "ActivityResultSender init failed: ${e.message}") }
        // Always light icons: the app is always dark, and the default (auto) drew dark icons on black whenever the phone
        // was in light mode, so the clock vanished (UI review 6 Oct 2026, emulator in light mode).
        try { enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)) } catch (_: Exception) { }
        NavIntents.consume(intent)
        // init + opt-out ran in UndersignedApp.onCreate; only the screen event belongs here. Region check once, outside
        // composition, so geo_blocked is counted once per launch rather than per recomposition.
        com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.APP_OPEN)
        val geoBlocker = try { GeoBlocker(this) } catch (_: Exception) { null }
        val blocked = geoBlocker?.isBlocked() == true
        if (blocked) com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.GEO_BLOCKED)
        setContent {
            UndersignedTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = RbColors.background) {
                    if (blocked && geoBlocker != null) BlockedRegionScreen(detectedRegion = geoBlocker.getDetectedRegion())
                    else UndersignedNavHost(activityResultSender = activityResultSender)
                }
            }
        }
    }
}
