package com.undersigned.app.util

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone

/**
 * GeoBlocker - On-device sanctions region screening.
 * Uses SIM, carrier, and locale signals to detect restricted jurisdictions.
 * All screening is performed entirely on-device - no location data is transmitted.
 */
class GeoBlocker(private val context: Context) {

    // Sanctioned/restricted jurisdiction ISO country codes
    private val restrictedCountries = setOf(
        "RU", // Russia
        "BY", // Belarus
        "KP", // North Korea
        "IR", // Iran
        "SY", // Syria
        "CU", // Cuba
        "VE", // Venezuela
        "MM", // Myanmar
        "SD", // Sudan
        "SS", // South Sudan
        "SO", // Somalia
        "LY", // Libya
        "CF", // Central African Republic
        "CD", // Democratic Republic of Congo
        "YE", // Yemen
        "ZW", // Zimbabwe
        "NI"  // Nicaragua
    )

    // Timezone → country. A mapped country only blocks if it is also in
    // restrictedCountries above, so entries here that are not on the list are inert.
    private val SANCTIONED_TIMEZONE_MAP = mapOf(
        "Europe/Moscow" to "RU", "Europe/Kaliningrad" to "RU",
        "Europe/Volgograd" to "RU", "Europe/Samara" to "RU",
        "Asia/Yekaterinburg" to "RU", "Asia/Omsk" to "RU",
        "Asia/Novosibirsk" to "RU", "Asia/Krasnoyarsk" to "RU",
        "Asia/Irkutsk" to "RU", "Asia/Yakutsk" to "RU",
        "Asia/Vladivostok" to "RU", "Asia/Magadan" to "RU",
        "Asia/Kamchatka" to "RU", "Asia/Anadyr" to "RU",
        "Europe/Astrakhan" to "RU", "Europe/Kirov" to "RU",
        "Europe/Saratov" to "RU", "Europe/Ulyanovsk" to "RU",
        "Asia/Barnaul" to "RU", "Asia/Chita" to "RU",
        "Asia/Khandyga" to "RU", "Asia/Novokuznetsk" to "RU",
        "Asia/Sakhalin" to "RU", "Asia/Srednekolymsk" to "RU",
        "Asia/Tomsk" to "RU", "Asia/Ust-Nera" to "RU",
        "Europe/Simferopol" to "RU",
        "Europe/Minsk" to "BY",
        "Asia/Pyongyang" to "KP",
        "Asia/Tehran" to "IR",
        "Asia/Damascus" to "SY",
        "America/Havana" to "CU",
        "Asia/Yangon" to "MM", "Asia/Rangoon" to "MM",
        "America/Caracas" to "VE",
        "Africa/Khartoum" to "SD",
        "Africa/Juba" to "SS",
        "Africa/Mogadishu" to "SO",
        "Asia/Aden" to "YE",
        "Africa/Tripoli" to "LY",
        "Africa/Harare" to "ZW",
        "Africa/Kinshasa" to "CD", "Africa/Lubumbashi" to "CD",
        "Africa/Bangui" to "CF",
        "America/Managua" to "NI"
    )

    private var detectedCountry: String? = null
    private var detectedSource: String = ""

    init {
        try {
            detectRegion()
        } catch (e: Exception) {
            // If detection fails, allow access (fail-open for usability)
            detectedCountry = null
        }
    }

    private fun detectRegion() {
        val tm = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (e: Exception) {
            null
        }

        // Priority 1: SIM card country (most reliable - physical SIM)
        val simCountry = tm?.simCountryIso?.uppercase()
        if (!simCountry.isNullOrBlank() && simCountry.length == 2) {
            detectedCountry = simCountry
            detectedSource = "SIM card"
            if (simCountry in restrictedCountries) return
        }

        // Priority 2: Network carrier country
        val networkCountry = tm?.networkCountryIso?.uppercase()
        if (!networkCountry.isNullOrBlank() && networkCountry.length == 2) {
            if (detectedCountry == null) {
                detectedCountry = networkCountry
                detectedSource = "network carrier"
            }
            if (networkCountry in restrictedCountries) {
                detectedCountry = networkCountry
                detectedSource = "network carrier"
                return
            }
        }

        val hasTelephonySignal = !simCountry.isNullOrBlank() || !networkCountry.isNullOrBlank()

        // Priority 3: Device locale — LAST RESORT ONLY.
        // Locale is a UI-language preference, not a location. A Turkish-speaking
        // family in Germany, or a Russian-speaking one in Berlin, is not in a
        // restricted jurisdiction — language says nothing about where someone lives
        // or is a citizen. It is therefore consulted only when telephony gave us no
        // country at all (a Wi-Fi-only tablet), and must never contradict hardware
        // evidence. The timezone below is always checked.
        if (!hasTelephonySignal) {
            val localeCountry = Locale.getDefault().country.uppercase()
            if (localeCountry.isNotBlank() && localeCountry.length == 2) {
                if (detectedCountry == null) {
                    detectedCountry = localeCountry
                    detectedSource = "device locale"
                }
                if (localeCountry in restrictedCountries) {
                    detectedCountry = localeCountry
                    detectedSource = "device locale"
                    return
                }
            }
        }

        // Priority 4: Device timezone. Set automatically from the network on a
        // normal device, so unlike the locale it does track physical location —
        // this is what catches a user physically inside a restricted jurisdiction
        // who is holding a foreign SIM.
        val tzCountry = SANCTIONED_TIMEZONE_MAP[TimeZone.getDefault().id]
        if (tzCountry != null && tzCountry in restrictedCountries) {
            detectedCountry = tzCountry
            detectedSource = "device timezone"
            return
        }
    }

    fun isBlocked(): Boolean {
        return detectedCountry?.uppercase() in restrictedCountries
    }

    fun getDetectedRegion(): String {
        val country = detectedCountry ?: return "Unknown"
        val name = getCountryName(country)
        return "$name ($country) — detected via $detectedSource"
    }

    private fun getCountryName(code: String): String {
        return when (code) {
            "RU" -> "Russia"
            "BY" -> "Belarus"
            "KP" -> "North Korea"
            "IR" -> "Iran"
            "SY" -> "Syria"
            "CU" -> "Cuba"
            "VE" -> "Venezuela"
            "MM" -> "Myanmar"
            "SD" -> "Sudan"
            "SS" -> "South Sudan"
            "SO" -> "Somalia"
            "LY" -> "Libya"
            "CF" -> "Central African Republic"
            "CD" -> "DR Congo"
            "YE" -> "Yemen"
            "ZW" -> "Zimbabwe"
            "NI" -> "Nicaragua"
            else -> try {
                Locale("", code).displayCountry
            } catch (e: Exception) {
                code
            }
        }
    }
}
