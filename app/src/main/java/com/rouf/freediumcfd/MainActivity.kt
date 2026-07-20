package com.rouf.freediumcfd

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.transition.TransitionManager
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.rouf.freediumcfd.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_URL = "com.rouf.freediumcfd.extra.OPEN_URL"

        private const val TAG = "MainActivity"
        private const val MEDIUM_DOMAIN = "medium.com"
        private const val URL_REGEX = "(https?://[\\w.-]+(?:/[\\w./?=&%\\-_~#@!$'()*+,;:]*)?)"
    }

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { AppPreferences(this) }
    private val history by lazy { HistoryStore(this) }
    private val viewModel: MainViewModel by viewModels()

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onCreate(savedInstanceState: Bundle?) {
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
        setUrlBarVisible(viewModel.isUrlBarVisible, animate = false)
        val url = viewModel.currentMediumUrl
        if (url != null) {
            setupWebView(prefs.selectedService.buildUrl(url))
        } else {
            showWelcomePage()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // The search action toggles the URL bar; its icon reflects the current state.
        menu.findItem(R.id.action_toggle_search)?.apply {
            val showingSearch = binding.urlInputLayout.isVisible
            setIcon(if (showingSearch) R.drawable.ic_close_24 else R.drawable.ic_search_24)
            setTitle(if (showingSearch) R.string.action_close_search else R.string.action_search)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_search -> {
                val show = !binding.urlInputLayout.isVisible
                setUrlBarVisible(show, focus = show)
                true
            }
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onResume() {
        super.onResume()
        // Reflect settings that may have changed while in the Settings screen.
        binding.webView.settings.textZoom = prefs.textZoom
        val url = viewModel.currentMediumUrl ?: return
        val serviceId = prefs.selectedService.id
        if (serviceId != viewModel.lastServiceId) {
            Log.d(TAG, "Service changed to $serviceId; reloading current article")
            viewModel.lastServiceId = serviceId
            setupWebView(prefs.selectedService.buildUrl(url))
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
        // Reopen request coming back from the history screen.
        intent?.getStringExtra(EXTRA_OPEN_URL)?.let { directUrl ->
            openWithSelectedService(directUrl)
            return
        }
        val mediumUrl = extractMediumUrlFromIntent(intent)
        Log.d(TAG, "Extracted Medium URL: $mediumUrl")

        if (mediumUrl != null) {
            openWithSelectedService(mediumUrl)
        } else {
            Log.d(TAG, "No Medium URL found, showing welcome page")
            viewModel.currentMediumUrl = null
            setUrlBarVisible(true)
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
        history.add(mediumUrl, prefs.historySize)
        binding.urlInput.setText(mediumUrl)
        setUrlBarVisible(false)
        setupWebView(serviceUrl)
    }

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

    /**
     * Shows/hides the URL bar (collapsing the app bar). When revealed via [focus]
     * it grabs focus and pops the keyboard; when hidden it dismisses both.
     */
    private fun setUrlBarVisible(visible: Boolean, focus: Boolean = false, animate: Boolean = true) {
        viewModel.isUrlBarVisible = visible
        if (binding.urlInputLayout.isVisible != visible) {
            if (animate) TransitionManager.beginDelayedTransition(binding.appBar)
            binding.urlInputLayout.isVisible = visible
        }
        if (visible && focus) {
            binding.urlInput.requestFocus()
            binding.urlInput.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.urlInput, InputMethodManager.SHOW_IMPLICIT)
            }
        } else if (!visible) {
            hideKeyboard()
            binding.urlInput.clearFocus()
        }
        invalidateOptionsMenu()
    }

    private fun extractMediumUrlFromIntent(intent: Intent?): String? {
        return when (intent?.action) {
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
        val regex = Regex(URL_REGEX)
        val urls = regex.findAll(text).map { it.value }.toList()
        if (urls.isEmpty()) return null

        // Prefer a medium-host if present (medium.com or *.medium.com), otherwise fallback to first URL found
        val preferred = urls.firstOrNull { url ->
            try {
                val host = url.toUri().host ?: return@firstOrNull false
                host.equals(MEDIUM_DOMAIN, ignoreCase = true) ||
                        host.endsWith(".${MEDIUM_DOMAIN}", ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

        return preferred ?: urls.first()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun setupWebView(url: String) {
        if (!isNetworkAvailable()) {
            Log.e(TAG, "No network connectivity available")
            showErrorPage("No Internet Connection", "Please check your network connection and try again.")
            return
        }

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
            allowFileAccess = true
            allowContentAccess = true
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
        }
        Log.d(TAG, "WebView settings configured")
    }

    private fun showWelcomePage() {
        Log.d(TAG, "Showing welcome page")

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

    private inner class CustomWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d(TAG, "Page loading started: $url")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(TAG, "Page loaded successfully: $url")
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.e(TAG, "WebView error: $description (Code: $errorCode) for URL: $failingUrl")

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