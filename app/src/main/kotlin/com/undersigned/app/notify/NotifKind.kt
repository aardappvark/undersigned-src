package com.undersigned.app.notify

import com.undersigned.app.R

/**
 * Every kind of alert, each with its own switch in Settings under the master "Alerts" switch (owner, 10 Oct 2026: "each
 * action to be toggled in settings ... for both dev and user"). All on by default; turning one off stops only that kind.
 */
enum class NotifKind(val key: String, val label: Int, val publisher: Boolean) {
    REPLY("reply", R.string.notif_kind_reply, false),
    FIXED("fixed", R.string.notif_kind_fixed, false),
    HELPFUL("helpful", R.string.notif_kind_helpful, false),
    FLAG("flag", R.string.notif_kind_flag, false),
    NOTE("note", R.string.notif_kind_note, false),
    FOLLOWED("followed", R.string.notif_kind_followed, false),
    UPDATES("updates", R.string.notif_kind_updates, false),
    SCORE("score", R.string.notif_kind_score, false),
    DIGEST("digest", R.string.notif_kind_digest, false),
    PUB_REVIEW("pub_review", R.string.notif_kind_pub_review, true),
    PUB_FOLLOWUP("pub_followup", R.string.notif_kind_pub_followup, true),
    PUB_REPLY("pub_reply", R.string.notif_kind_pub_reply, true),
    PROVEN("proven", R.string.notif_kind_proven, true),
}
