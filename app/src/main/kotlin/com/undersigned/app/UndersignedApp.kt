package com.undersigned.app

import android.app.Application
import com.undersigned.app.data.UserPreferences

class UndersignedApp : Application(), coil.ImageLoaderFactory {
    /**
     * dApp icons stay on the phone (owner, 7 Oct 2026: "should be loaded and saved on the device"): a 64 MB disk cache that
     * ignores the store CDN's cache headers, so an icon seen once shows instantly from then on, offline too.
     */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("dapp_icons")).maxSizeBytes(64L * 1024 * 1024).build() }
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()

    override fun onCreate() {
        super.onCreate()
        try { UserPreferences.init(this) } catch (_: Exception) { }
        // Fleet analytics: init, then the stored opt-out, both BEFORE anything can track() (workers and the Activity alike);
        // then retry whatever a previous process died before delivering (the payment-event case).
        try {
            com.undersigned.app.service.GeoAnalyticsService.init(this, getString(R.string.analytics_api_key))
            UserPreferences.applyAnalyticsPreference()
            com.undersigned.app.service.GeoAnalyticsService.flushPending()
        } catch (e: Exception) { android.util.Log.e("UndersignedApp", "analytics init failed: ${e.message}") }
        // The four alert channels exist from first launch, so the system settings list them before any alert is posted.
        try { com.undersigned.app.notify.NotifChannel.ensure(this) } catch (_: Exception) { }
        com.undersigned.app.util.NetStatus.watch(this)
        try {
            fun keyed(res: Int): String? = getString(res).trim().takeIf { it.isNotBlank() }?.let { "https://mainnet.helius-rpc.com/?api-key=$it" }
            val primary = keyed(R.string.helius_api_key)
            val lastResort = keyed(R.string.helius_api_key_3)
            AppConfig.Api.configure(primary, lastResort)
            android.util.Log.i("UndersignedApp", "rpc pool: ${AppConfig.Api.RPC_ENDPOINTS.size} endpoints (keyed primary=${primary != null}, last-resort=${lastResort != null})")
        } catch (_: Exception) { }
    }
}
