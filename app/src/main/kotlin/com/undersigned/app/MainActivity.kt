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
        try { enableEdgeToEdge() } catch (_: Exception) { }
        NavIntents.consume(intent)
        setContent {
            UndersignedTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = RbColors.background) {
                    val geoBlocker = try { GeoBlocker(this) } catch (_: Exception) { null }
                    if (geoBlocker?.isBlocked() == true) BlockedRegionScreen(detectedRegion = geoBlocker.getDetectedRegion())
                    else UndersignedNavHost(activityResultSender = activityResultSender)
                }
            }
        }
    }
}
