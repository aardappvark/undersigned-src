package com.undersigned.app.ui

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Base64

/** Cross-screen intents: a package a notification asked us to open, or a reply prepared elsewhere (desktop MCP hand-off). */
object NavIntents {
    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    val pendingPackage = MutableStateFlow<String?>(null)
    /** A screen to open from a notification that is not about one app (e.g. "my-reviews" after a store scan). */
    val pendingScreen = MutableStateFlow<String?>(null)
    /** A package whose composer to open (undersigned://review?pkg=…). */
    val pendingReview = MutableStateFlow<String?>(null)
    /** (record id, prefilled text) */
    val pendingReply = MutableStateFlow<Pair<String, String>?>(null)
    /** kind ∈ vote|flag|dispute, record id, reason (flag) or text (dispute) */
    data class Action(val kind: String, val target: String, val arg: String)
    val pendingAction = MutableStateFlow<Action?>(null)

    fun consume(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("pkg")?.takeIf { it.isNotBlank() }?.let { pendingPackage.value = it }
        intent.getStringExtra("screen")?.takeIf { it.isNotBlank() }?.let { pendingScreen.value = it }
        val u = intent.data ?: return
        if (u.scheme != "undersigned") return
        // undersigned://app?pkg=… opens an app's page; undersigned://review?pkg=… its composer (the reviewer still chooses
        // the stars and signs). For publishers and other apps to link to; neither carries a rating.
        val pkg = u.getQueryParameter("pkg")?.takeIf { PACKAGE.matches(it) }
        when (u.host) { "app" -> { pkg?.let { pendingPackage.value = it }; return }; "review" -> { pkg?.let { pendingReview.value = it }; return } }
        val target = u.getQueryParameter("target") ?: return
        val text = u.getQueryParameter("text")?.let { runCatching { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }.getOrNull() } ?: ""
        when (u.host) {
            "reply" -> pendingReply.value = target to text
            "vote" -> pendingAction.value = Action("vote", target, "")
            "flag" -> pendingAction.value = Action("flag", target, u.getQueryParameter("reason") ?: "spam")
            "dispute" -> pendingAction.value = Action("dispute", target, text)
            "unvote", "unflag", "clear" -> pendingAction.value = Action(u.host!!, target, "")
        }
    }
}
