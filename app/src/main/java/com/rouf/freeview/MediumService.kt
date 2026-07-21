package com.rouf.freeview

import java.net.URLEncoder

enum class MediumService(
    val id: String,
    val displayName: String,
    private val template: String,
    private val encodeUrl: Boolean,
) {
    READ_MEDIUM("readmedium", "Read-Medium", "https://readmedium.com/en/%s", false),
    FREEDIUM("freedium", "Freedium", "https://freedium-mirror.cfd/%s", false),
    ARCHIVE_TODAY("archive_today", "Archive.today", "https://archive.today/?url=%s&run=1", true),
    ARCHIVE_IS("archive_is", "Archive.is", "https://archive.is/?url=%s&run=1", true),
    PROXY_API("proxy", "Proxy API", "https://medium-parser.vercel.app/?url=%s", true);

    /** Builds the service URL for [mediumUrl], percent-encoding it when required. */
    fun buildUrl(mediumUrl: String): String {
        val value = if (encodeUrl) {
            // URLEncoder is pure-JVM (so buildUrl is unit-testable, unlike
            // android.net.Uri.encode). It emits '+' for spaces; convert those
            // to %20 for a valid URL component.
            URLEncoder.encode(mediumUrl, "UTF-8").replace("+", "%20")
        } else {
            mediumUrl
        }
        return template.format(value)
    }

    companion object {
        val DEFAULT = FREEDIUM

        /** Returns the service with this [id], or [DEFAULT] when unknown or null. */
        fun fromId(id: String?): MediumService =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
