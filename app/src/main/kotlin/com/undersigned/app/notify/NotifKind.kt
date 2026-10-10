package com.undersigned.app.notify

import com.undersigned.app.R

/**
 * Every kind of alert, each with its own switch in Settings under the master "Alerts" switch (owner, 10 Oct 2026: "each
 * action to be toggled in settings ... for both dev and user"). All on by default; turning one off stops only that kind.
 */
/**
 * [channel] is the Android notification channel the kind posts to (1.1.6): the system settings can mute a group, the
 * switch here still decides per kind. One exception: a review burst is gated by FOLLOWED but posts to [NotifChannel.STORE].
 */
enum class NotifKind(val key: String, val label: Int, val publisher: Boolean, val channel: NotifChannel) {
    REPLY("reply", R.string.notif_kind_reply, false, NotifChannel.REVIEWS),
    FIXED("fixed", R.string.notif_kind_fixed, false, NotifChannel.REVIEWS),
    HELPFUL("helpful", R.string.notif_kind_helpful, false, NotifChannel.REVIEWS),
    FLAG("flag", R.string.notif_kind_flag, false, NotifChannel.PUBLISHER),
    NOTE("note", R.string.notif_kind_note, false, NotifChannel.REVIEWS),
    FOLLOWED("followed", R.string.notif_kind_followed, false, NotifChannel.REVIEWS),
    UPDATES("updates", R.string.notif_kind_updates, false, NotifChannel.STORE),
    SCORE("score", R.string.notif_kind_score, false, NotifChannel.STORE),
    DIGEST("digest", R.string.notif_kind_digest, false, NotifChannel.STORE),
    PUB_REVIEW("pub_review", R.string.notif_kind_pub_review, true, NotifChannel.PUBLISHER),
    PUB_FOLLOWUP("pub_followup", R.string.notif_kind_pub_followup, true, NotifChannel.PUBLISHER),
    PUB_REPLY("pub_reply", R.string.notif_kind_pub_reply, true, NotifChannel.PUBLISHER),
    PROVEN("proven", R.string.notif_kind_proven, true, NotifChannel.PUBLISHER),
}
