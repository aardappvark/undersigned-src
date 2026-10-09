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
 *   SRL1|snt|<package>|<store review uuid>|<b64 text>
 *                                 — a publisher's signed note on a dApp Store review of THEIR app (the store's own reply
 *                                   box is not on-chain and cannot be checked); shown only when the signer is a verified
 *                                   publisher of <package>. Withdrawn with rvk like any record.
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
    /** `bst|<review id>`: the review's own author pays to promote it, in the same transaction as the payment (see Weighting). */
    data class Boost(val recordId: String) : ReviewMemo()
    /** A shared seat on the signer's Pro term, named by the pair hash; only the paying wallet's signature makes it real. */
    data class Share(val tag: String) : ReviewMemo()
    data class Unshare(val tag: String) : ReviewMemo()
    data class Use(val packageName: String, val bucket: UseBucket) : ReviewMemo()
    data class Time(val packageName: String, val time: UseTime) : ReviewMemo()
    /**
     * `SRL1|act|<package>|m<0-6>|<recency>|i<0-9>` (10 Oct 2026, owner: "how long the reviewer has actually used the dapp
     * ... installed and uninstalled over time"): in how many of the last six months the phone recorded use, when it was last
     * used ([Recency]), and how many reinstalls Undersigned saw on this phone (9 = nine or more). Opt-in, from Android's
     * usage access, in the review's own transaction like [Time]; the device's report, read by no score or weight.
     */
    data class Activity(val packageName: String, val monthsActive: Int, val recency: Recency, val reinstalls: Int) : ReviewMemo()
    data class StoreNote(val packageName: String, val reviewId: String, val text: String) : ReviewMemo()
    data class Treasury(val packageName: String, val code: String) : ReviewMemo()
    data class Untreasury(val packageName: String) : ReviewMemo()
    /** The app's proven publisher lists a treasury it names, so the treasury wallet never has to sign (see Payments). */
    data class TreasuryFor(val packageName: String, val code: String, val treasury: String) : ReviewMemo()
    /** A moderator hides a record's TEXT (its rating and tags still count); see Moderation. */
    data class Moderate(val recordId: String, val reason: ModReason) : ReviewMemo()
    data class Unmoderate(val recordId: String) : ReviewMemo()
    enum class ModReason(val wire: String) { ILLEGAL("illegal"), PERSONAL("personal"), ABUSE("abuse"), IMPERSONATION("impersonation");
        companion object { fun fromWire(s: String) = entries.firstOrNull { it.wire == s } } }
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
        /** The store's review ids, lowercase (as the store returns them). */
        val STORE_REVIEW_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        private fun cleanText(t: String): String? = t.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT_CHARS && it.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES && it.none { c -> c == '|' || c == '\n' || c == '\r' } }
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
            is Boost -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|bst|${m.recordId}" }
            is Share -> { require(TAG32.matches(m.tag)) { "tag" }; "${Registry.PROTOCOL}|shr|${m.tag}" }
            is Unshare -> { require(TAG32.matches(m.tag)) { "tag" }; "${Registry.PROTOCOL}|shx|${m.tag}" }
            is Use -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; "${Registry.PROTOCOL}|use|${m.packageName}|${m.bucket.wire}" }
            is StoreNote -> {
                require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }
                require(STORE_REVIEW_ID.matches(m.reviewId)) { "store review id" }
                val text = requireNotNull(cleanText(m.text)) { "note text" }
                "${Registry.PROTOCOL}|snt|${m.packageName}|${m.reviewId}|" + Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
            }
            is Time -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; "${Registry.PROTOCOL}|tim|${m.packageName}|${m.time.wire}" }
            is Activity -> {
                require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }
                require(m.monthsActive in 0..Usage.ACTIVE_MONTHS_WINDOW && m.reinstalls in 0..Usage.MAX_REINSTALLS) { "bad activity" }
                "${Registry.PROTOCOL}|act|${m.packageName}|m${m.monthsActive}|${m.recency.wire}|i${m.reinstalls}"
            }
            is Treasury -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; require(Payments.validCode(m.code)) { "code" }; "${Registry.PROTOCOL}|trs|${m.packageName}|${m.code}" }
            is Moderate -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|mod|${m.recordId}|${m.reason.wire}" }
            is Unmoderate -> { require(SIGNATURE.matches(m.recordId)) { "record id" }; "${Registry.PROTOCOL}|umd|${m.recordId}" }
            is TreasuryFor -> { require(m.packageName.length <= MAX_PACKAGE_CHARS && PACKAGE.matches(m.packageName)) { "bad package" }; require(Payments.validCode(m.code)) { "code" }; require(Payments.isReference(m.treasury)) { "treasury address" }; "${Registry.PROTOCOL}|trd|${m.packageName}|${m.code}|${m.treasury}" }
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
                "bst" -> if (f.size == 3 && SIGNATURE.matches(f[2])) Boost(f[2]) else null
                "pro" -> if (f.size == 3) f[2].toIntOrNull()?.takeIf { it in 1..MAX_PRO_MONTHS }?.let { Pro(it) } else null
                "shr" -> if (f.size == 3 && TAG32.matches(f[2])) Share(f[2]) else null
                "shx" -> if (f.size == 3 && TAG32.matches(f[2])) Unshare(f[2]) else null
                "use" -> if (f.size == 4 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2])) UseBucket.fromWire(f[3])?.let { Use(f[2], it) } else null
                "snt" -> {
                    if (f.size != 5 || f[2].length > MAX_PACKAGE_CHARS || !PACKAGE.matches(f[2]) || !STORE_REVIEW_ID.matches(f[3]) || f[4].isEmpty()) return null
                    val text = Base64.getUrlDecoder().decode(f[4]).takeIf { it.size <= MAX_TEXT_BYTES }?.let { String(it, StandardCharsets.UTF_8) }?.let { cleanText(it) } ?: return null
                    StoreNote(f[2], f[3], text)
                }
                "tim" -> if (f.size == 4 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2])) UseTime.fromWire(f[3])?.let { Time(f[2], it) } else null
                "act" -> {
                    if (f.size != 6 || f[2].length > MAX_PACKAGE_CHARS || !PACKAGE.matches(f[2])) return null
                    val months = f[3].removePrefix("m").takeIf { f[3].startsWith("m") }?.toIntOrNull()?.takeIf { it in 0..Usage.ACTIVE_MONTHS_WINDOW } ?: return null
                    val re = f[5].removePrefix("i").takeIf { f[5].startsWith("i") }?.toIntOrNull()?.takeIf { it in 0..Usage.MAX_REINSTALLS } ?: return null
                    Recency.fromWire(f[4])?.let { Activity(f[2], months, it, re) }
                }
                "trs" -> if (f.size == 4 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2]) && Payments.validCode(f[3])) Treasury(f[2], f[3]) else null
                "mod" -> if (f.size == 4 && SIGNATURE.matches(f[2])) ModReason.fromWire(f[3])?.let { Moderate(f[2], it) } else null
                "umd" -> if (f.size == 3 && SIGNATURE.matches(f[2])) Unmoderate(f[2]) else null
                "trd" -> if (f.size == 5 && f[2].length <= MAX_PACKAGE_CHARS && PACKAGE.matches(f[2]) && Payments.validCode(f[3]) && Payments.isReference(f[4])) TreasuryFor(f[2], f[3], f[4]) else null
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
