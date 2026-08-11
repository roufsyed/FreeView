package com.rouf.freeview

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import com.rouf.freeview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_URL = "com.rouf.freeview.extra.OPEN_URL"

        private const val TAG = "MainActivity"
        private const val URL_REGEX = "(https?://[\\w.-]+(?:/[\\w./?=&%\\-_~#@!$'()*+,;:]*)?)"
    }

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { AppPreferences(this) }
    private val history by lazy { HistoryStore(this) }
    private val bookmarks by lazy { BookmarkStore(this) }
    private val viewModel: MainViewModel by viewModels()

    /** True while the current navigation hit onReceivedError; blocks a premature ARTICLE promotion. */
    private var loadErrored = false

    /** Set when leaving for an in-app screen, so the next resume skips offering the clipboard. */
    private var skipClipboardCheck = false

    /** Set on a real resume; the deferred clipboard offer runs once the window regains focus. */
    private var pendingClipboardCheck = false

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Swap the brand-colored launch/splash theme for the real app theme before the window is built.
        setTheme(R.style.Theme_Freediumcfd)
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Draw edge-to-edge; pad the root for the system bars so the collapsing
        // app bar and the WebView lay out within the safe area.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding.webView.configureWebViewSettings()
        setupUrlInput()
        setupClipboardBanner()

        if (savedInstanceState == null) {
            Log.d(TAG, "Fresh start; handling launch intent")
            handleIntent(intent)
        } else {
            Log.d(TAG, "Recreated; restoring state from ViewModel")
            restoreState()
        }
    }

    /** Re-applies state kept in [viewModel] after a configuration change (e.g. rotation). */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun restoreState() {
        val url = viewModel.currentMediumUrl
        if (url != null) {
            setupWebView(prefs.selectedService.buildUrl(url))
        } else {
            showWelcomePage()
        }
        renderClipboardBanner() // restore the banner after a configuration change
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // The bookmark star is only offered on a loaded article; its icon reflects saved state.
        val canBookmark = viewModel.pageState == MainViewModel.PageState.ARTICLE &&
                viewModel.currentMediumUrl != null
        menu.findItem(R.id.action_toggle_bookmark)?.apply {
            isVisible = canBookmark
            if (canBookmark) {
                val saved = viewModel.isCurrentBookmarked
                setIcon(if (saved) R.drawable.ic_bookmark_24 else R.drawable.ic_bookmark_border_24)
                setTitle(if (saved) R.string.action_bookmark_remove else R.string.action_bookmark_add)
            }
        }
        // The History/Bookmarks list icons belong to the home screen; hide them while an
        // article is loading or open (Back returns to wherever it was opened from). Settings
        // stays available so the reader service / text size can be changed mid-article.
        val readingArticle = viewModel.pageState == MainViewModel.PageState.LOADING ||
                viewModel.pageState == MainViewModel.PageState.ARTICLE
        menu.findItem(R.id.action_history)?.isVisible = !readingArticle
        menu.findItem(R.id.action_bookmarks)?.isVisible = !readingArticle
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_bookmark -> {
                viewModel.currentMediumUrl?.let { toggleBookmark(it) }
                true
            }
            R.id.action_history -> {
                skipClipboardCheck = true
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            R.id.action_bookmarks -> {
                skipClipboardCheck = true
                startActivity(Intent(this, BookmarksActivity::class.java))
                true
            }
            R.id.action_settings -> {
                skipClipboardCheck = true
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Adds or removes the current article from bookmarks, gating UI updates on a successful write. */
    private fun toggleBookmark(url: String) {
        val wasBookmarked = viewModel.isCurrentBookmarked
        val committed = if (wasBookmarked) bookmarks.remove(url) else bookmarks.add(url)
        if (!committed) {
            Toast.makeText(this, R.string.bookmark_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.isCurrentBookmarked = !wasBookmarked
        invalidateOptionsMenu()
        Toast.makeText(
            this,
            if (viewModel.isCurrentBookmarked) R.string.bookmark_added else R.string.bookmark_removed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onResume() {
        super.onResume()
        // Reflect settings that may have changed while in the Settings screen.
        binding.webView.settings.textZoom = prefs.textZoom
        // Reading the clipboard needs window focus (Android 10+), so the offer runs in
        // onWindowFocusChanged. Skip it when returning from one of our own screens.
        pendingClipboardCheck = !skipClipboardCheck
        skipClipboardCheck = false
        val url = viewModel.currentMediumUrl ?: return
        // A bookmark may have been added/removed on the Bookmarks/Settings screen; re-sync the star.
        viewModel.isCurrentBookmarked = bookmarks.contains(url)
        invalidateOptionsMenu()
        val serviceId = prefs.selectedService.id
        if (serviceId != viewModel.lastServiceId) {
            Log.d(TAG, "Service changed to $serviceId; reloading current article")
            viewModel.lastServiceId = serviceId
            setupWebView(prefs.selectedService.buildUrl(url))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Clipboard reads require focus, so the offer deferred from onResume runs here, once.
        if (hasFocus && pendingClipboardCheck) {
            pendingClipboardCheck = false
            maybeOfferClipboard()
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun handleIntent(intent: Intent?) {
        // Reopen from History/Bookmarks arrives as an explicit, actionless intent carrying
        // EXTRA_OPEN_URL. Honor it only when there is no action (the only legitimate producers)
        // and only for an http(s) URL, so a crafted implicit intent can't smuggle a dangerous
        // scheme through this host-unrestricted branch (history/bookmarks hold custom-domain URLs).
        val reopenUrl = intent?.takeIf { it.action == null }?.getStringExtra(EXTRA_OPEN_URL)
        if (reopenUrl != null && isHttpUrl(reopenUrl.toUri())) {
            openWithSelectedService(reopenUrl)
            return
        }
        val mediumUrl = extractMediumUrlFromIntent(intent)
        Log.d(TAG, "Extracted Medium URL: $mediumUrl")

        if (mediumUrl != null) {
            openWithSelectedService(mediumUrl)
        } else {
            Log.d(TAG, "No Medium URL found, showing welcome page")
            viewModel.currentMediumUrl = null
            showWelcomePage()
        }
    }

    /** Loads [mediumUrl] through the currently selected service and remembers it. */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun openWithSelectedService(mediumUrl: String) {
        val service = prefs.selectedService
        val serviceUrl = service.buildUrl(mediumUrl)
        Log.d(TAG, "Opening via ${service.displayName}: $mediumUrl -> $serviceUrl")
        viewModel.currentMediumUrl = mediumUrl
        viewModel.lastServiceId = service.id
        viewModel.isCurrentBookmarked = bookmarks.contains(mediumUrl)
        history.add(mediumUrl, prefs.historySize)
        binding.urlInput.setText(mediumUrl)
        setupWebView(serviceUrl)
    }

    // --- Clipboard offer: on resume, surface a Medium link sitting in the clipboard ---

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun setupClipboardBanner() {
        binding.clipboardBannerOpen.setOnClickListener {
            val url = viewModel.clipboardOfferUrl ?: return@setOnClickListener
            dismissClipboardOffer(url)
            openWithSelectedService(url)
        }
        binding.clipboardBannerDismiss.setOnClickListener {
            dismissClipboardOffer(viewModel.clipboardOfferUrl)
        }
    }

    /** Marks [url] as handled (so it isn't offered again) and hides the banner. */
    private fun dismissClipboardOffer(url: String?) {
        viewModel.dismissedClipboardUrl = url
        viewModel.clipboardOfferUrl = null
        binding.clipboardBanner.isVisible = false
    }

    /**
     * Offers a URL from the clipboard (a Medium link if present, else any http(s) URL) - unless it is
     * already the open article or one the user already opened/dismissed. Called once per resume from
     * onWindowFocusChanged. State lives in the ViewModel, so the banner survives rotation.
     */
    private fun maybeOfferClipboard() {
        val candidate = clipboardUrl()
        if (candidate != null &&
            !isSameArticle(candidate, viewModel.currentMediumUrl) &&
            !isSameArticle(candidate, viewModel.dismissedClipboardUrl)
        ) {
            viewModel.clipboardOfferUrl = candidate
        }
        renderClipboardBanner()
    }

    private fun renderClipboardBanner() {
        binding.clipboardBanner.isVisible = viewModel.clipboardOfferUrl != null
    }

    private fun isSameArticle(a: String?, b: String?): Boolean =
        a != null && b != null && normalizeBookmarkUrl(a) == normalizeBookmarkUrl(b)

    /** A URL from the clipboard to offer - a Medium link if present, else any http(s) URL - or null. */
    private fun clipboardUrl(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) return null
        return extractMediumUrlFromText(text)
    }

    /** True if [url]'s host is medium.com or a *.medium.com subdomain. */
    private fun isMediumHostUrl(url: String): Boolean = runCatching {
        val host = url.toUri().host ?: return@runCatching false
        host.equals(MEDIUM_HOST, ignoreCase = true) || host.endsWith(".$MEDIUM_HOST", ignoreCase = true)
    }.getOrDefault(false)

    private fun setupUrlInput() {
        binding.urlInputLayout.setEndIconOnClickListener { submitUrl() }
        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                submitUrl()
                true
            } else {
                false
            }
        }
        // Clear the error as soon as the user edits the field.
        binding.urlInput.doAfterTextChanged { binding.urlInputLayout.error = null }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun submitUrl() {
        val raw = binding.urlInput.text?.toString().orEmpty().trim()
        val mediumUrl = resolveInputUrl(raw)
        if (mediumUrl == null) {
            binding.urlInputLayout.error = getString(R.string.url_input_error)
            return
        }
        hideKeyboard()
        binding.urlInput.clearFocus()
        openWithSelectedService(mediumUrl)
    }

    /** Pulls a loadable URL out of pasted [raw] text, adding https:// when missing. */
    private fun resolveInputUrl(raw: String): String? {
        if (raw.isBlank()) return null
        extractMediumUrlFromText(raw)?.let { return it }
        if (raw.contains(' ')) return null
        val candidate = "https://$raw"
        return if (candidate.toUri().host?.contains('.') == true) candidate else null
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
    }

    /** Shows the URL field only on welcome/error pages, and the loading bar only while an article loads. */
    private fun updateUrlBarVisibility() {
        val state = viewModel.pageState
        val showUrlBar = state == MainViewModel.PageState.WELCOME || state == MainViewModel.PageState.ERROR
        binding.urlInputLayout.isVisible = showUrlBar
        if (!showUrlBar) {
            hideKeyboard()
            binding.urlInput.clearFocus()
        }
        binding.loadingBar.isVisible = state == MainViewModel.PageState.LOADING
    }

    /**
     * Static pages (welcome/error) are meant to fit the screen, so suppress WebView
     * scrolling for them; article pages re-enable it (and drive the app-bar collapse).
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setWebViewScrollEnabled(enabled: Boolean) {
        if (enabled) {
            binding.webView.setOnTouchListener(null)
        } else {
            binding.webView.setOnTouchListener { _, event ->
                event.actionMasked == MotionEvent.ACTION_MOVE
            }
        }
    }

    private fun extractMediumUrlFromIntent(intent: Intent?): String? {
        return when (intent?.action) {
            // Deep-link tap: the intent carries one authoritative Uri; gate it by scheme (any host,
            // like a pasted link) so it opens through the reader service.
            Intent.ACTION_VIEW -> intent.data?.takeIf { isAcceptableViewUri(it) }?.toString()
            Intent.ACTION_SEND -> extractUrlFromSingleText(intent)
            Intent.ACTION_SEND_MULTIPLE -> extractUrlFromMultipleTexts(intent)
            else -> null
        }
    }

    private fun extractUrlFromSingleText(intent: Intent): String? {
        if (intent.type != "text/plain") return null

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return extractMediumUrlFromText(sharedText)
    }

    private fun extractUrlFromMultipleTexts(intent: Intent): String? {
        if (intent.type != "text/plain") return null

        val sharedTexts = intent.getStringArrayListExtra(Intent.EXTRA_TEXT) ?: return null
        return sharedTexts.firstNotNullOfOrNull { text ->
            extractMediumUrlFromText(text)
        }
    }

    private fun extractMediumUrlFromText(text: String): String? {
        val urls = Regex(URL_REGEX).findAll(text).map { it.value }.toList()
        if (urls.isEmpty()) return null
        // Prefer a Medium host if present (medium.com or *.medium.com), otherwise the first URL found.
        return urls.firstOrNull { isMediumHostUrl(it) } ?: urls.first()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun setupWebView(url: String) {
        if (!isNetworkAvailable()) {
            Log.e(TAG, "No network connectivity available")
            showErrorPage("No Internet Connection", "Please check your network connection and try again.")
            return
        }

        // A real article load is starting; the star becomes available once it finishes loading.
        loadErrored = false
        viewModel.pageState = MainViewModel.PageState.LOADING
        invalidateOptionsMenu()
        updateUrlBarVisibility()

        setWebViewScrollEnabled(true)
        binding.webView.apply {
            webViewClient = CustomWebViewClient()
            Log.d(TAG, "Loading URL: $url")
            loadUrl(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configureWebViewSettings() {
        Log.d(TAG, "Configuring WebView settings")
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = prefs.textZoom
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Only remote https service URLs (and inlined welcome/error HTML) are ever loaded -
            // no file:// or content:// loads - so deny both to remove a local-read/exfiltration primitive.
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
        }
        Log.d(TAG, "WebView settings configured")
    }

    private fun showWelcomePage() {
        Log.d(TAG, "Showing welcome page")
        viewModel.pageState = MainViewModel.PageState.WELCOME
        invalidateOptionsMenu()
        updateUrlBarVisibility()

        // Load the detailed welcome page HTML
        val welcomeHtml = createWelcomePageHtml()
        setWebViewScrollEnabled(false)
        binding.webView.loadDataWithBaseURL(null, welcomeHtml, "text/html", "UTF-8", null)
    }


    private fun themeColor(attr: Int): Int =
        MaterialColors.getColor(this, attr, Color.BLACK)

    private fun Int.toCssHex(): String = String.format("#%06X", 0xFFFFFF and this)

    private fun Int.toCssRgba(alpha: Double): String =
        "rgba(${Color.red(this)}, ${Color.green(this)}, ${Color.blue(this)}, $alpha)"

    private fun readAsset(name: String): String =
        assets.open(name).bufferedReader().use { it.readText() }

    private fun htmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun createWelcomePageHtml(): String {
        val surface = themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val primary = themeColor(com.google.android.material.R.attr.colorPrimary)
        val onPrimary = themeColor(com.google.android.material.R.attr.colorOnPrimary)

        // Inject the theme colors as CSS variables that assets/welcome.css consumes.
        val vars = buildString {
            append("--surface:${surface.toCssHex()};")
            append("--on-surface:${onSurface.toCssHex()};")
            append("--on-surface-variant:${onSurfaceVariant.toCssHex()};")
            append("--primary:${primary.toCssHex()};")
            append("--on-primary:${onPrimary.toCssHex()};")
            append("--card-bg:${onSurface.toCssRgba(0.05)};")
            append("--card-border:${onSurface.toCssRgba(0.12)};")
            append("--chip-bg:${primary.toCssRgba(0.14)};")
            append("--chip-border:${primary.toCssRgba(0.32)};")
        }
        val style = ":root { $vars }\n" + readAsset("welcome.css")
        return readAsset("welcome.html").replace("/*__STYLE__*/", style)
    }

    private fun showErrorPage(title: String, message: String) {
        viewModel.pageState = MainViewModel.PageState.ERROR
        invalidateOptionsMenu()
        updateUrlBarVisibility()
        val html = readAsset("error.html")
            .replace("/*__STYLE__*/", readAsset("error.css"))
            .replace("__TITLE__", htmlEscape(title))
            .replace("__MESSAGE__", htmlEscape(message))
        setWebViewScrollEnabled(false)
        binding.webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Opens [url] in an external browser, excluding FreeView itself so a link tapped inside the reader
     * doesn't just re-enter the app (FreeView is a registered http/https VIEW handler).
     */
    private fun openExternally(url: String) {
        val viewIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        val chooser = Intent.createChooser(viewIntent, getString(R.string.open_in_browser)).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@MainActivity, MainActivity::class.java)),
            )
        }
        if (runCatching { startActivity(chooser) }.isFailure) {
            Log.w(TAG, "No external app to open link: $url")
        }
    }

    private inner class CustomWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            request ?: return false
            val target = request.url?.toString() ?: return false
            // A genuine tap that leaves the reader page (e.g. Freedium's "open original" → medium.com)
            // opens in a browser; same-site links, redirects and JS-driven loads stay in the WebView.
            if (request.hasGesture() && leavesReaderDomain(view?.url, target)) {
                openExternally(target)
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d(TAG, "Page loading started: $url")
            // A fresh navigation for the current article (also covers the error-recovery reload).
            loadErrored = false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(TAG, "Page loaded successfully: $url")
            // Promote to ARTICLE only for a clean article load - never welcome/error pages, and
            // never a load that errored (the cache-miss reload promotes on its successful finish).
            if (viewModel.pageState == MainViewModel.PageState.LOADING && !loadErrored) {
                viewModel.pageState = MainViewModel.PageState.ARTICLE
                invalidateOptionsMenu()
                updateUrlBarVisibility()
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            // Only the main document may replace the page with an error; ignore subresource failures
            // (images, etc.). The deprecated 4-arg callback this replaces was main-frame only, so the
            // isForMainFrame guard preserves that behavior.
            if (request?.isForMainFrame != true) return
            val errorCode = error?.errorCode ?: return
            val description = error.description?.toString()
            val failingUrl = request.url?.toString()
            Log.e(TAG, "WebView error: $description (Code: $errorCode) for URL: $failingUrl")
            loadErrored = true

            when (errorCode) {
                ERROR_CONNECT -> {
                    Log.e(TAG, "Connection error - check network connectivity")
                    showErrorPage("Connection Error", "Unable to connect to the server. Please check your internet connection.")
                }
                ERROR_TIMEOUT -> {
                    Log.e(TAG, "Connection timeout - server may be slow")
                    showErrorPage("Timeout Error", "The connection timed out. Please try again.")
                }
                ERROR_HOST_LOOKUP -> {
                    Log.e(TAG, "Host lookup failed - DNS issue")
                    showErrorPage("Network Error", "Could not find the server. Please check your internet connection.")
                }
                ERROR_FAILED_SSL_HANDSHAKE -> {
                    Log.e(TAG, "SSL handshake failed")
                    showErrorPage("Security Error", "Could not establish a secure connection.")
                }
                ERROR_BAD_URL -> {
                    Log.e(TAG, "Bad URL format")
                    showErrorPage("Invalid URL", "The link format is not valid.")
                }
                ERROR_FILE_NOT_FOUND -> {
                    Log.e(TAG, "File not found - 404 error")
                    showErrorPage("Page Not Found", "The requested page could not be found.")
                }
                ERROR_TOO_MANY_REQUESTS -> {
                    Log.e(TAG, "Too many requests - rate limited")
                    showErrorPage("Rate Limited", "Too many requests. Please try again later.")
                }
                else -> {
                    Log.e(TAG, "Unknown error: $errorCode")
                    if (description?.contains("ERR_CACHE_MISS", ignoreCase = true) == true ||
                        description?.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) == true) {
                        Log.w(TAG, "Cache/Network error - attempting reload")
                        view?.settings?.cacheMode = WebSettings.LOAD_NO_CACHE
                        view?.postDelayed({
                            view.reload()
                        }, 1000)
                    } else {
                        showErrorPage("Loading Error", "An error occurred while loading the page. Please try again.")
                    }
                }
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Log.e(TAG, "HTTP Error: ${errorResponse?.statusCode} for ${request?.url}")
        }
    }
}