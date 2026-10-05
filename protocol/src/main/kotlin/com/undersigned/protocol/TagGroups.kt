package com.undersigned.protocol

/**
 * How the 24 review tags are presented: by theme, with opposite pairs that cannot both be chosen. Presentation only:
 * no record, rule or score reads this, and a record carrying both halves of a pair (signed before this existed, or by
 * another client) is read exactly as before. Shared so the app, the web composer and the importer group the same way.
 */
object TagGroups {
    enum class Group(val key: String) { USE("use"), WALLET("wallet"), MONEY("money"), TRUST("trust"), BUILD("build") }

    val groups: List<Pair<Group, List<ReviewMemo.Tag>>> = listOf(
        Group.USE to listOf(ReviewMemo.Tag.WORKS, ReviewMemo.Tag.CRASHES, ReviewMemo.Tag.FAST, ReviewMemo.Tag.SLOW, ReviewMemo.Tag.BATTERY_DRAIN, ReviewMemo.Tag.OFFLINE_OK),
        Group.WALLET to listOf(ReviewMemo.Tag.WALLET_CONNECTS, ReviewMemo.Tag.WALLET_FAILS),
        Group.MONEY to listOf(ReviewMemo.Tag.FAIR_PRICE, ReviewMemo.Tag.OVERPRICED, ReviewMemo.Tag.ADS, ReviewMemo.Tag.PAYWALL, ReviewMemo.Tag.NEEDS_ACCOUNT),
        Group.TRUST to listOf(ReviewMemo.Tag.CLEAR_PERMISSIONS, ReviewMemo.Tag.EXCESS_PERMISSIONS, ReviewMemo.Tag.MISLEADING_LISTING, ReviewMemo.Tag.SPAM_REVIEWS, ReviewMemo.Tag.SUSPECTED_SCAM),
        Group.BUILD to listOf(ReviewMemo.Tag.ACTIVE_DEV, ReviewMemo.Tag.ABANDONED, ReviewMemo.Tag.GOOD_ONBOARDING, ReviewMemo.Tag.CONFUSING, ReviewMemo.Tag.SEEKER_NATIVE, ReviewMemo.Tag.WEBVIEW_WRAPPER),
    )

    /** Pairs that say opposite things about the same quality; choosing one releases the other. */
    val opposites: Map<ReviewMemo.Tag, ReviewMemo.Tag> = listOf(
        ReviewMemo.Tag.WORKS to ReviewMemo.Tag.CRASHES, ReviewMemo.Tag.FAST to ReviewMemo.Tag.SLOW,
        ReviewMemo.Tag.WALLET_CONNECTS to ReviewMemo.Tag.WALLET_FAILS, ReviewMemo.Tag.FAIR_PRICE to ReviewMemo.Tag.OVERPRICED,
        ReviewMemo.Tag.CLEAR_PERMISSIONS to ReviewMemo.Tag.EXCESS_PERMISSIONS, ReviewMemo.Tag.ACTIVE_DEV to ReviewMemo.Tag.ABANDONED,
        ReviewMemo.Tag.GOOD_ONBOARDING to ReviewMemo.Tag.CONFUSING, ReviewMemo.Tag.SEEKER_NATIVE to ReviewMemo.Tag.WEBVIEW_WRAPPER,
    ).flatMap { (a, b) -> listOf(a to b, b to a) }.toMap()

    /**
     * The selection after tapping [t]: tapping a chosen tag removes it; tapping a new one adds it and drops its opposite;
     * a full selection (3) ignores a new tag unless it replaces its own opposite.
     */
    fun toggle(selected: List<ReviewMemo.Tag>, t: ReviewMemo.Tag): List<ReviewMemo.Tag> {
        if (t in selected) return selected - t
        val without = selected - setOfNotNull(opposites[t])
        return if (without.size < ReviewMemo.MAX_TAGS) without + t else selected
    }
}
