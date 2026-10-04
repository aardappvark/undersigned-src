package com.undersigned.api

import java.util.concurrent.ConcurrentHashMap

/**
 * Labels in the reader's language, read from the app's own string resources (bundled at build time under /i18n), so a
 * tag reads the same on a page as on a phone. Only the vocabulary the pages share with the app is taken (tags, install
 * age, usage time, flag reasons); the rest of a page is English for now.
 */
object I18n {
    val LOCALES = setOf("ar", "bn", "cs", "de", "el", "es", "fil", "fr", "hi", "hu", "in", "it", "ja", "ko", "nl", "pl", "pt-rBR", "ro", "ru", "sv", "th", "tr", "uk", "ur", "vi", "zh-rCN", "zh-rTW")
    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    /** `?lang=` or an Accept-Language header → the app's resource folder name, or "en". */
    fun pick(header: String?): String {
        for (part in (header ?: "").split(',')) {
            val tag = part.substringBefore(';').trim().lowercase(); if (tag.isEmpty()) continue
            val bits = tag.split('-', '_'); val l = bits[0]; val r = bits.getOrNull(1)
            if (l == "en") return "en"
            val cand = when (l) { "pt" -> "pt-rBR"; "zh" -> if (r in setOf("tw", "hk", "mo", "hant")) "zh-rTW" else "zh-rCN"; "id" -> "in"; "tl" -> "fil"; else -> l }
            if (cand in LOCALES) return cand
        }
        return "en"
    }

    private fun strings(lang: String): Map<String, String> = cache.getOrPut(lang) {
        val text = I18n::class.java.getResource(if (lang == "en") "/i18n/values/strings.xml" else "/i18n/values-$lang/strings.xml")?.readText() ?: return@getOrPut emptyMap()
        Regex("<string name=\"((?:tag|use|time|flag)_[a-z0-9_]+)\">(.*?)</string>").findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">") }
    }

    fun str(lang: String, key: String): String? = strings(lang)[key] ?: strings("en")[key]
    fun tag(lang: String, wire: String): String = str(lang, "tag_" + wire.replace('-', '_')) ?: wire.replace('-', ' ').replaceFirstChar { it.uppercase() }
    fun use(lang: String, bucket: String): String = str(lang, "use_$bucket") ?: bucket
    fun time(lang: String, bucket: String): String = str(lang, "time_$bucket") ?: bucket
    fun flag(lang: String, reason: String): String = str(lang, "flag_$reason") ?: reason
    fun htmlLang(lang: String): String = when (lang) { "pt-rBR" -> "pt-BR"; "zh-rCN" -> "zh-CN"; "zh-rTW" -> "zh-TW"; "in" -> "id"; else -> lang }
}
