package com.rouf.freeview

/**
 * Pure-JVM URL helpers shared by the history and bookmark lists/stores.
 *
 * These use only string operations (never android.net.Uri or java.net.URI, which throw on
 * malformed input and aren't available in plain JVM unit tests). Every function is total -
 * it returns a sensible fallback rather than throwing - so it is safe to call from the main
 * article screen and is directly unit-testable (see UrlTextTest).
 */

/** A best-effort readable title from a Medium slug: drops the trailing id and spaces the words. */
fun deriveArticleTitle(url: String): String {
    val segment = lastPathSegment(url)?.takeIf { it.isNotBlank() } ?: return url
    val parts = segment.split('-')
    val words = if (parts.size > 1) parts.dropLast(1) else parts
    val title = words.joinToString(" ").trim()
    return if (title.isBlank()) url else title.replaceFirstChar { it.uppercase() }
}

/**
 * True when [url] should appear for the list search [query]: a blank query matches everything;
 * otherwise the query (case-insensitive) must be a substring of the URL or of its derived title.
 * Needs no stored title - both sides are computed from the URL.
 */
fun matchesQuery(url: String, query: String): Boolean {
    if (query.isBlank()) return true
    return url.contains(query, ignoreCase = true) ||
        deriveArticleTitle(url).contains(query, ignoreCase = true)
}

/**
 * True when a link tapped on the reader page ([currentUrl]) navigates to a different site
 * ([targetUrl]) — i.e. it leaves the reader service and should open in a browser. A missing current
 * host (e.g. the initial load, before any page) is treated as "does not leave", and hosts that differ
 * only by subdomain (e.g. www.) count as the same site.
 */
fun leavesReaderDomain(currentUrl: String?, targetUrl: String): Boolean {
    val readerHost = hostOfUrl(currentUrl) ?: return false
    val targetHost = hostOfUrl(targetUrl) ?: return true
    if (readerHost == targetHost) return false
    return !(targetHost.endsWith(".$readerHost") || readerHost.endsWith(".$targetHost"))
}

/**
 * Lenient host extraction (scheme://HOST[:port][/path]). Java's URI is too strict for reader URLs
 * that embed a full Medium URL in the path, so parse by hand and strip any user-info and port. The
 * user-info strip also defuses the `medium.com@evil.com` trick — the real host is `evil.com`.
 */
private fun hostOfUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val afterScheme = url.substringAfter("://", "")
    if (afterScheme.isEmpty()) return null
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':')
    return host.ifBlank { null }?.lowercase()
}

/**
 * A canonical key for de-duplicating bookmarks: folds the scheme to https, lowercases the
 * host, and drops the query and fragment (a Medium article's identity is its path, while the
 * query is tracking noise like ?source=/?sk=). The original URL is what gets stored; this is
 * only the comparison key.
 */
fun normalizeBookmarkUrl(url: String): String = runCatching {
    val raw = url.trim()
    if (raw.isEmpty()) return@runCatching ""
    val noQuery = raw.substringBefore('#').substringBefore('?')
    val schemeIdx = noQuery.indexOf("://")
    val afterScheme = if (schemeIdx >= 0) noQuery.substring(schemeIdx + 3) else noQuery
    val slash = afterScheme.indexOf('/')
    val host = (if (slash >= 0) afterScheme.substring(0, slash) else afterScheme).lowercase()
    val path = (if (slash >= 0) afterScheme.substring(slash) else "").trimEnd('/')
    "https://$host$path"
}.getOrDefault(url.trim())

/** The last non-empty path segment, or null when there is no path (host-only or scheme-less). */
private fun lastPathSegment(url: String): String? = runCatching {
    val noQuery = url.substringBefore('#').substringBefore('?')
    val afterScheme = noQuery.substringAfter("://", "")
    if (afterScheme.isEmpty()) return@runCatching null
    val slash = afterScheme.indexOf('/')
    if (slash < 0) return@runCatching null
    afterScheme.substring(slash).trim('/').substringAfterLast('/').takeIf { it.isNotEmpty() }
}.getOrNull()
