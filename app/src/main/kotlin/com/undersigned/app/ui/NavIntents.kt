package com.undersigned.app.ui

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Base64

/** Cross-screen intents: a package a notification asked us to open, or a reply prepared elsewhere (desktop MCP hand-off). */
object NavIntents {
    val pendingPackage = MutableStateFlow<String?>(null)
    /** (record id, prefilled text) */
    val pendingReply = MutableStateFlow<Pair<String, String>?>(null)
    /** kind ∈ vote|flag|dispute, record id, reason (flag) or text (dispute) */
    data class Action(val kind: String, val target: String, val arg: String)
    val pendingAction = MutableStateFlow<Action?>(null)

    fun consume(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("pkg")?.let { pendingPackage.value = it }
        val u = intent.data ?: return
        if (u.scheme != "undersigned") return
        val target = u.getQueryParameter("target") ?: return
        val text = u.getQueryParameter("text")?.let { runCatching { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }.getOrNull() } ?: ""
        when (u.host) {
            "reply" -> pendingReply.value = target to text
            "vote" -> pendingAction.value = Action("vote", target, "")
            "flag" -> pendingAction.value = Action("flag", target, u.getQueryParameter("reason") ?: "spam")
            "dispute" -> pendingAction.value = Action("dispute", target, text)
        }
    }
}
