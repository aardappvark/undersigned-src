package com.undersigned.protocol

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Wire format (UTF-8, pipe-delimited, no JSON so the memo stays small and deterministic):
 *
 *   SRL1|rev|<package>|<rating 1-5>|<tags a,b,c>|<versionCode>|<b64 text or empty>
 *   SRL1|rvk|<signature of the review tx being withdrawn>
 *
 * Only these two record types exist in v1. Anything else, any unknown tag, any oversize field
 * or any field that fails validation is IGNORED by readers, never "partially" accepted.
 *
 * What is deliberately NOT here: reviewer identity (it is the transaction signer — read from
 * the chain, never trusted from the payload), timestamps (block time), stake weights (read
 * live at ranking time), and any money field. Text is optional, short, and gated behind a
 * second consent in the UI because the chain is permanent — see docs/LEGAL_GATES.md §UGC.
 */
sealed class ReviewMemo {
    data class Review(
        val packageName: String,
        val rating: Int,
        val tags: List<Tag>,
        val versionCode: Long,
        val text: String?,
    ) : ReviewMemo()

    data class Revocation(val reviewSignature: String) : ReviewMemo()

    /** Fixed vocabulary: rankable, translatable, and never defamatory free prose. */
    enum class Tag(val wire: String) {
        WORKS("works"), CRASHES("crashes"), WALLET_CONNECTS("wallet-ok"), WALLET_FAILS("wallet-fails"),
        FAST("fast"), SLOW("slow"), BATTERY_DRAIN("battery"), ADS("ads"), PAYWALL("paywall"),
        FAIR_PRICE("fair-price"), OVERPRICED("overpriced"), CLEAR_PERMISSIONS("perms-ok"),
        EXCESS_PERMISSIONS("perms-excess"), SPAM_REVIEWS("spam-reviews"), MISLEADING_LISTING("misleading"),
        SUSPECTED_SCAM("suspected-scam"), ABANDONED("abandoned"), ACTIVE_DEV("active-dev"),
        GOOD_ONBOARDING("onboarding-ok"), CONFUSING("confusing"), OFFLINE_OK("offline-ok"),
        NEEDS_ACCOUNT("needs-account"), SEEKER_NATIVE("seeker-native"), WEBVIEW_WRAPPER("webview");
        companion object { private val byWire = entries.associateBy { it.wire }; fun fromWire(s: String) = byWire[s] }
    }

    companion object {
        const val MAX_MEMO_BYTES = 566          // SPL Memo hard cap
        const val MAX_TEXT_CHARS = 140
        /** UTF-8 bytes of the text BEFORE base64. Worst-case header (100-char package, 3 longest tags,
         *  19-digit versionCode) is ~180 B; 280 raw bytes → 374 B base64url; total stays under 566. */
        const val MAX_TEXT_BYTES = 280
        const val MAX_PACKAGE_CHARS = 100
        const val MAX_TAGS = 3
        private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        private val SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{86,88}$")

        fun encode(m: ReviewMemo): String = when (m) {
            is Review -> {
                require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }
                require(m.rating in 1..5) { "rating 1..5" }
                require(m.tags.size in 1..MAX_TAGS && m.tags.toSet().size == m.tags.size) { "1..$MAX_TAGS distinct tags" }
                require(m.versionCode > 0) { "versionCode" }
                val text = m.text?.trim()?.takeIf { it.isNotEmpty() }
                require(text == null || text.length <= MAX_TEXT_CHARS) { "text ≤ $MAX_TEXT_CHARS chars" }
                require(text == null || text.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) { "text ≤ $MAX_TEXT_BYTES bytes" }
                require(text == null || text.none { it == '|' || it == '\n' || it == '\r' }) { "text chars" }
                val b64 = text?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(StandardCharsets.UTF_8)) } ?: ""
                "${Registry.PROTOCOL}|rev|${m.packageName}|${m.rating}|${m.tags.joinToString(",") { it.wire }}|${m.versionCode}|$b64"
            }
            is Revocation -> { require(SIGNATURE.matches(m.reviewSignature)) { "signature" }; "${Registry.PROTOCOL}|rvk|${m.reviewSignature}" }
        }.also { require(it.toByteArray(StandardCharsets.UTF_8).size <= MAX_MEMO_BYTES) { "memo > $MAX_MEMO_BYTES bytes" } }

        /** Lenient on input only in the sense that garbage returns null; never throws on untrusted data. */
        fun decode(raw: String): ReviewMemo? = runCatching {
            if (raw.toByteArray(StandardCharsets.UTF_8).size > MAX_MEMO_BYTES) return null
            val f = raw.split('|')
            if (f.getOrNull(0) != Registry.PROTOCOL) return null
            when (f.getOrNull(1)) {
                "rev" -> {
                    if (f.size != 7) return null
                    val pkg = f[2].takeIf { it.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(it) } ?: return null
                    val rating = f[3].toIntOrNull()?.takeIf { it in 1..5 } ?: return null
                    val tags = f[4].split(',').map { Tag.fromWire(it) ?: return null }
                    if (tags.isEmpty() || tags.size > MAX_TAGS || tags.toSet().size != tags.size) return null
                    val vc = f[5].toLongOrNull()?.takeIf { it > 0 } ?: return null
                    val text = if (f[6].isEmpty()) null else Base64.getUrlDecoder().decode(f[6])
                        .takeIf { it.size <= MAX_TEXT_BYTES }?.let { String(it, StandardCharsets.UTF_8) }
                        ?.takeIf { it.length <= MAX_TEXT_CHARS && it.none { c -> c == '\n' || c == '\r' } } ?: return null
                    Review(pkg, rating, tags, vc, text)
                }
                "rvk" -> if (f.size == 3 && SIGNATURE.matches(f[2])) Revocation(f[2]) else null
                else -> null
            }
        }.getOrNull()
    }
}
