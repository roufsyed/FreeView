package com.rouf.freeview

import android.net.Uri

/** Medium's canonical apex host; used only to prefer a Medium URL among several shared links. */
const val MEDIUM_HOST = "medium.com"

/** Upper bound on an accepted inbound URL, so an oversized attacker Uri can't be stored or loaded. */
const val MAX_URL_LENGTH = 2048

/** True when [uri]'s scheme is http or https (case-insensitive). */
fun isHttpUrl(uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
    "http", "https" -> true
    else -> false
}

/**
 * Gate for an inbound VIEW / deep-link [uri].
 *
 * FreeView opens links from ANY host - Medium articles live on medium.com but also on many custom
 * publication domains (ai.plainenglish.io, towardsdatascience.com, …) that can't be enumerated - so,
 * exactly like a pasted link, there is NO host restriction. The gate is purely a scheme + length check:
 * the scheme MUST be http/https (rejecting javascript:/file:/content:/intent:/data:) and the URL must be
 * within [MAX_URL_LENGTH]. This matters because MainActivity is exported: an explicit component intent
 * bypasses the manifest, so this code check is the real filter for dangerous schemes.
 *
 * The execution boundary remains [MediumService.buildUrl], which wraps the URL into a fixed https host,
 * so even a value that reached here could never be executed as a scheme by the WebView.
 */
fun isAcceptableViewUri(uri: Uri): Boolean =
    isHttpUrl(uri) && uri.toString().length <= MAX_URL_LENGTH
