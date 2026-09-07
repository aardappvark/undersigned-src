package com.undersigned.app.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Versioned Terms/Privacy/EULA consent, ideally backed by a Sign-In-With-Solana signature (fleet
 * standard, ported from the AarDappvark Toolkit). Signing initiates no transaction and moves no funds.
 */
class TermsAcceptanceService(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "undersigned_legal_compliance"
        const val CURRENT_TOS_VERSION = "1.0.0"
        const val CURRENT_PRIVACY_VERSION = "1.0.0"
        const val CURRENT_EULA_VERSION = "1.0.0"
        private const val KEY_SIG = "consent_signature"; private const val KEY_MSG = "consent_message"; private const val KEY_TS = "consent_timestamp"
        private const val KEY_WALLET = "consent_wallet_address"; private const val KEY_TOS = "consent_tos_version"; private const val KEY_PRIV = "consent_privacy_version"
        private const val KEY_EULA = "consent_eula_version"; private const val KEY_HASH = "consent_message_hash"
        private const val APP_NAME = "Undersigned"
        private const val APP_DOMAIN = "aardappvark.github.io"
    }

    fun generateConsentMessage(walletAddress: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        return """
I accept the Undersigned Terms of Service (v$CURRENT_TOS_VERSION), Privacy Policy (v$CURRENT_PRIVACY_VERSION), and End User License Agreement (v$CURRENT_EULA_VERSION).

I confirm that on $date (UTC) I:
- am at least 18 years of age;
- am not located in a sanctioned/blocked jurisdiction and am not on any OFAC, EU, UK, UN, or Australian sanctions list;
- understand Undersigned is a non-custodial reader and composer of signed reviews: it never holds my keys or funds, every review is a transaction I approve in my own wallet, and a published review is written to a public blockchain permanently;
- understand that reviews shown are the opinions of individual Seeker owners and that Undersigned is not affiliated with Solana Mobile;
- consent to my wallet address and this signed acceptance being stored locally as proof of consent.

Signing this message does not initiate any blockchain transaction or transfer of funds.
Wallet: $walletAddress
Domain: $APP_DOMAIN
Application: $APP_NAME
        """.trimIndent()
    }

    private fun hash(m: String) = MessageDigest.getInstance("SHA-256").digest(m.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun recordConsentWithSignature(walletAddress: String, signature: String, signedMessage: String) {
        prefs.edit().putString(KEY_WALLET, walletAddress).putString(KEY_MSG, signedMessage).putString(KEY_SIG, signature).putString(KEY_HASH, hash(signedMessage))
            .putLong(KEY_TS, System.currentTimeMillis()).putString(KEY_TOS, CURRENT_TOS_VERSION).putString(KEY_PRIV, CURRENT_PRIVACY_VERSION).putString(KEY_EULA, CURRENT_EULA_VERSION).apply()
    }
    fun recordConsentWithoutSignature() {
        val ts = System.currentTimeMillis(); val m = generateConsentMessage("(no wallet)")
        prefs.edit().putString(KEY_WALLET, "(no wallet)").putString(KEY_MSG, m).putString(KEY_SIG, "UNSIGNED_CONSENT_$ts").putString(KEY_HASH, hash(m))
            .putLong(KEY_TS, ts).putString(KEY_TOS, CURRENT_TOS_VERSION).putString(KEY_PRIV, CURRENT_PRIVACY_VERSION).putString(KEY_EULA, CURRENT_EULA_VERSION).apply()
    }
    fun hasValidConsent(): Boolean = prefs.getString(KEY_SIG, null) != null && prefs.getString(KEY_TOS, null) == CURRENT_TOS_VERSION &&
        prefs.getString(KEY_PRIV, null) == CURRENT_PRIVACY_VERSION && prefs.getString(KEY_EULA, null) == CURRENT_EULA_VERSION

    fun exportConsentRecordAsJson(): String {
        val sig = prefs.getString(KEY_SIG, null) ?: return "{}"
        val ts = prefs.getLong(KEY_TS, 0L)
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ts))
        return """
{
    "export_type": "GDPR_DATA_EXPORT",
    "application": "$APP_NAME",
    "consent_record": {
        "wallet_address": "${prefs.getString(KEY_WALLET, "")}",
        "consent_timestamp": $ts,
        "consent_date_utc": "$date",
        "terms_of_service_version": "${prefs.getString(KEY_TOS, "")}",
        "privacy_policy_version": "${prefs.getString(KEY_PRIV, "")}",
        "eula_version": "${prefs.getString(KEY_EULA, "")}",
        "signature": "$sig",
        "message_hash": "${prefs.getString(KEY_HASH, "")}"
    },
    "signed_message": ${org.json.JSONObject.quote(prefs.getString(KEY_MSG, "") ?: "")},
    "verification_instructions": "Verify the signature against signed_message using the wallet address with Ed25519 signature verification."
}
        """.trimIndent()
    }
    fun deleteAllConsentData(): Boolean = try { prefs.edit().clear().apply(); true } catch (e: Exception) { false }
}
