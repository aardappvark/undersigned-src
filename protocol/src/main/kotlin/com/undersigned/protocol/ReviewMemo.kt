package com.undersigned.protocol

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Wire format (UTF-8, pipe-delimited, no JSON so the memo stays small and deterministic):
 *
 *   SRL1|rev|<package>|<rating 1-5>|<tags a,b,c>|<versionCode>|<b64 text or empty>
 *   SRL1|rvk|<signature of the review tx being withdrawn>
 *   SRL1|rep|<signature of the review being answered>|<b64 text>
 *   SRL1|flg|<record id>|<reason>  — "this record looks unnatural (suspected spam) / abusive / off-topic / misleading"; from Genesis wallets only
 *   SRL1|up|<record id>            — "helpful"; one per wallet per record, attestation-weighted; orders reviews, never hides
 *   SRL1|unv|<record id>           — withdraw my helpful vote     (latest of up/unv per wallet wins)
 *   SRL1|unf|<record id>           — withdraw my flag             (latest of flg/unf per wallet wins)
 *   SRL1|clr|<record id>           — "looks genuine": a clear vote that offsets flags, one per wallet, attested only
 *   SRL1|dsp|<record id>|<b64 text> — the AUTHOR disputes consensus hiding of their own record; raises the bar to 2× and shows the text
 *   SRL1|clm|<package>            — "I publish this app"; readers verify it against the website the
 *                                   store lists for the package (/.well-known/undersigned.txt names the wallet)
 *   SRL1|pro|<months>             — LEGACY, read but never written. A Pro licence used to ride in the payment
 *                                   transaction as this record. That put a pipe-delimited payload where the fleet
 *                                   convention wants a three-letter code, and since getSignaturesForAddress
 *                                   summarises a transaction with only its FIRST memo, the treasury scan saw
 *                                   "SRL1|pro|12" instead of "UNS" and could not attribute the payment.
 *                                   A licence is now the payment itself — treasury credit >= the floor, with the
 *                                   app's memo and reference — and the term comes from the amount, as the fleet
 *                                   tells tiers apart everywhere else. Records written before 20 Sep 2026 are
 *                                   still decoded here so the wallets that bought one keep it.
 *   SRL1|shr|<tag>                — the paying wallet shares its Pro term with another wallet of its own; the pair is
 *                                   named by hash (see ProSharing.tag), so the ledger never publishes the link
 *   SRL1|shx|<tag>                — takes that grant back
 *   SRL1|use|<package>|<bucket>   — rides in a REVIEW's own transaction: how long the reviewed app had been installed on
 *                                   the reviewer's phone, rounded (see Usage). The device's report, never proof.
 *   SRL1|tim|<package>|<bucket>   — rides in a REVIEW's own transaction: the reviewed app's foreground time on the
 *                                   reviewer's phone, rounded (see UseTime). Opt-in; the device's report, never proof.
 *   SRL1|trs|<package>|<code>     — signed BY a treasury wallet: "payments to me whose memo starts <code> are for
 *                                   <package>". Counted only when the store-listed website also names the wallet
 *                                   (see Payments).
 *   SRL1|trx|<package>            — the treasury withdraws that (latest of trs/trx per wallet and package wins)
 *
 * Three record types. A reply is a signed public comment under a review — anyone may post one
 * (the publisher answering, another owner disagreeing); replies never affect ranking. Anything else, any unknown tag, any oversize field
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

    data class Reply(val reviewSignature: String, val text: String) : ReviewMemo()

    data class Claim(val packageName: String) : ReviewMemo()

    data class Flag(val recordId: String, val reason: FlagReason) : ReviewMemo()
    data class Upvote(val recordId: String) : ReviewMemo()
    data class Unvote(val recordId: String) : ReviewMemo()
    data class Unflag(val recordId: String) : ReviewMemo()
    data class Clear(val recordId: String) : ReviewMemo()
    data class Dispute(val recordId: String, val text: String) : ReviewMemo()
    /** A licence term in months. Ignored by every ranking and reputation rule; the app reads it beside the payment it rode in on. */
    data class Pro(val months: Int) : ReviewMemo()
    /** A shared seat on the signer's Pro term, named by the pair hash; only the paying wallet's signature makes it real. */
    data class Share(val tag: String) : ReviewMemo()
    data class Unshare(val tag: String) : ReviewMemo()
    data class Use(val packageName: String, val bucket: UseBucket) : ReviewMemo()
    data class Time(val packageName: String, val time: UseTime) : ReviewMemo()
    data class Treasury(val packageName: String, val code: String) : ReviewMemo()
    data class Untreasury(val packageName: String) : ReviewMemo()
    enum class FlagReason(val wire: String) { SPAM("spam"), ABUSE("abuse"), OFF_TOPIC("offtopic"), MISLEADING("misleading");
        companion object { fun fromWire(s: String) = entries.firstOrNull { it.wire == s } } }

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
        const val MAX_PRO_MONTHS = 120
        private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        /** A record id: the transaction signature, plus ":<n>" for the n-th memo of a batched (import) transaction. */
        private val SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{86,88}(:[1-9][0-9]?)?$")
        private val TAG32 = Regex("^[0-9a-f]{32}$")
        fun recordId(txSignature: String, memoIndex: Int): String = if (memoIndex == 0) txSignature else "$txSignature:$memoIndex"

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
            is Upvote -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|up|${m.recordId}" }
            is Unvote -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|unv|${m.recordId}" }
            is Unflag -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|unf|${m.recordId}" }
            is Clear -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|clr|${m.recordId}" }
            is Dispute -> {
                require(SIGNATURE.matches(m.recordId)) { "record id" }
                val text = m.text.trim()
                require(text.isNotEmpty() && text.length <= MAX_TEXT_CHARS && text.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES && text.none { it == '|' || it == '\n' || it == '\r' }) { "dispute text" }
                "${Registry.PROTOCOL}|dsp|${m.recordId}|" + Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
            }
            is Flag -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|flg|${m.recordId}|${m.reason.wire}" }
            is Claim -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; "${Registry.PROTOCOL}|clm|${m.packageName}" }
            // Legacy wire, and the form the on-device cache stores a licence in. It must NEVER reach a payment:
            // that is enforced where it matters, in TxBuilder (which takes no memo record) and by the test that
            // reads the built payment bytes back. Refusing to encode here instead looked safer and was not —
            // LedgerCache serialises every entry through this codec, so it crashed the app on launch for the one
            // wallet that owns a licence (20 Sep 2026).
            is Pro -> { require(m.months in 1..MAX_PRO_MONTHS) { "months" }; "${Registry.PROTOCOL}|pro|${m.months}" }
            is Share -> { require(TAG32.matches(m.tag)) { "tag" }; "${Registry.PROTOCOL}|shr|${m.tag}" }
            is Unshare -> { require(TAG32.matches(m.tag)) { "tag" }; "${Registry.PROTOCOL}|shx|${m.tag}" }
            is Use -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; "${Registry.PROTOCOL}|use|${m.packageName}|${m.bucket.wire}" }
            is Time -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; "${Registry.PROTOCOL}|tim|${m.packageName}|${m.time.wire}" }
            is Treasury -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; require(Payments.validCode(m.code)) { "code" }; "${Registry.PROTOCOL}|trs|${m.packageName}|${m.code}" }
            is Untreasury -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; "${Registry.PROTOCOL}|trx|${m.packageName}" }
            is Reply -> {
                require(SIGNATURE.matches(m.reviewSignature)) { "signature" }
                val text = m.text.trim()
                require(text.isNotEmpty() && text.length <= MAX_TEXT_CHARS && text.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) { "reply text" }
                require(text.none { it == '|' || it == '\n' || it == '\r' }) { "text chars" }
                "${Registry.PROTOCOL}|rep|${m.reviewSignature}|" + Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
            }
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
                "up" -> if (f.size == 3 && SIGNATURE.matches(f[2])) Upvote(f[2]) else null
                "unv" -> if (f.size == 3 && SIGNATURE.matches(f[2])) Unvote(f[2]) else null
                "unf" -> if (f.size == 3 && SIGNATURE.matches(f[2])) Unflag(f[2]) else null
                "clr" -> if (f.size == 3 && SIGNATURE.matches(f[2])) Clear(f[2]) else null
                "dsp" -> {
                    if (f.size != 4 || !SIGNATURE.matches(f[2]) || f[3].isEmpty()) return null
                    val text = Base64.getUrlDecoder().decode(f[3]).takeIf { it.size <= MAX_TEXT_BYTES }?.let { String(it, StandardCharsets.UTF_8) }
                        ?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT_CHARS && it.none { c -> c == '\n' || c == '\r' } } ?: return null
                    Dispute(f[2], text)
                }
                "flg" -> if (f.size == 4 && SIGNATURE.matches(f[2])) FlagReason.fromWire(f[3])?.let { Flag(f[2], it) } else null
                "clm" -> if (f.size == 3 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2])) Claim(f[2]) else null
                "pro" -> if (f.size == 3) f[2].toIntOrNull()?.takeIf { it in 1..MAX_PRO_MONTHS }?.let { Pro(it) } else null
                "shr" -> if (f.size == 3 && TAG32.matches(f[2])) Share(f[2]) else null
                "shx" -> if (f.size == 3 && TAG32.matches(f[2])) Unshare(f[2]) else null
                "use" -> if (f.size == 4 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2])) UseBucket.fromWire(f[3])?.let { Use(f[2], it) } else null
                "tim" -> if (f.size == 4 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2])) UseTime.fromWire(f[3])?.let { Time(f[2], it) } else null
                "trs" -> if (f.size == 4 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2]) && Payments.validCode(f[3])) Treasury(f[2], f[3]) else null
                "trx" -> if (f.size == 3 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2])) Untreasury(f[2]) else null
                "rep" -> {
                    if (f.size != 4 || !SIGNATURE.matches(f[2]) || f[3].isEmpty()) return null
                    val text = Base64.getUrlDecoder().decode(f[3]).takeIf { it.size <= MAX_TEXT_BYTES }?.let { String(it, StandardCharsets.UTF_8) }
                        ?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT_CHARS && it.none { c -> c == '\n' || c == '\r' } } ?: return null
                    Reply(f[2], text)
                }
                else -> null
            }
        }.getOrNull()
    }
}
