package com.undersigned.app

import android.app.Application
import com.undersigned.app.data.UserPreferences

class UndersignedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try { UserPreferences.init(this) } catch (_: Exception) { }
        try {
            fun keyed(res: Int): String? = getString(res).trim().takeIf { it.isNotBlank() }?.let { "https://mainnet.helius-rpc.com/?api-key=$it" }
            val primary = keyed(R.string.helius_api_key)
            val lastResort = keyed(R.string.helius_api_key_3)
            AppConfig.Api.configure(primary, lastResort)
            android.util.Log.i("UndersignedApp", "rpc pool: ${AppConfig.Api.RPC_ENDPOINTS.size} endpoints (keyed primary=${primary != null}, last-resort=${lastResort != null})")
        } catch (_: Exception) { }
    }
}
