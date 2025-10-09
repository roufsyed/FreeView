package com.rouf.freediumcfd

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.rouf.freediumcfd.databinding.ActivityMainBinding
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val MEDIUM_DOMAIN = "medium.com"
        private const val FREEDIUM_BASE_URL = "https://freedium.cfd"
        private const val URL_REGEX = "(https?://[\\w.-]+(?:/[\\w./?=&%\\-_~#@!$'()*+,;:]*)?)"
    }

    private lateinit var binding: ActivityMainBinding

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable edge-to-edge drawing
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide status bar completely
        val controller = ViewCompat.getWindowInsetsController(window.decorView)
        controller?.let {
            it.hide(WindowInsetsCompat.Type.statusBars()) // hides the status bar
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE // show on swipe
        }

        // Optional: Make status bar transparent if it ever appears
        window.statusBarColor = Color.TRANSPARENT

        // Apply proper insets so the WebView doesn't overlap with navigation bar (bottom)
        ViewCompat.setOnApplyWindowInsetsListener(binding.webView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Configure WebView
        binding.webView.configureWebViewSettings()

        Log.d(TAG, "About to handle intent")
        handleIntent(intent)
    }


    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun handleIntent(intent: Intent?) {
        val mediumUrl = extractMediumUrlFromIntent(intent)
        Log.d(TAG, "Extracted Medium URL: $mediumUrl")

        if (mediumUrl != null) {
            val freediumUrl = convertToFreediumUrl(mediumUrl)
            Log.d(TAG, "Converting Medium URL: $mediumUrl -> $freediumUrl")
            setupWebView(freediumUrl)
        } else {
            Log.d(TAG, "No Medium URL found, showing welcome page")
            showWelcomePage()
        }
    }

    private fun setupEdgeToEdgeForWebView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.webView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Add padding so content is not hidden behind status or nav bar
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)

            WindowInsetsCompat.CONSUMED
        }
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

    private fun convertToFreediumUrl(mediumUrl: String): String {
        return try {
            mediumUrl.replace(Regex("""https?://[^/]+/"""), "$FREEDIUM_BASE_URL/")
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URL: $mediumUrl", e)
            mediumUrl
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun setupWebView(url: String) {
        if (!isNetworkAvailable()) {
            Log.e(TAG, "No network connectivity available")
            showErrorPage("No Internet Connection", "Please check your network connection and try again.")
            return
        }

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
        binding.webView.loadDataWithBaseURL(null, welcomeHtml, "text/html", "UTF-8", null)
    }


    private fun createWelcomePageHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                            text-align: center;
                            padding: 40px 20px;
                            background: linear-gradient(135deg, #2c3e50 0%, #34495e 50%, #4a5568 100%);
                            color: #e2e8f0;
                            margin: 0;
                            min-height: 100vh;
                            display: flex;
                            flex-direction: column;
                            justify-content: center;
                            line-height: 1.6;
                        }
                        .container {
                            max-width: 420px;
                            margin: 0 auto;
                        }
                        .app-icon {
                            font-size: 3.5em;
                            margin-bottom: 24px;
                            filter: drop-shadow(0 4px 8px rgba(0,0,0,0.3));
                        }
                        h1 {
                            font-size: 2.2em;
                            margin-bottom: 16px;
                            font-weight: 600;
                            color: #f8fafc;
                            text-shadow: 0 2px 4px rgba(0,0,0,0.3);
                        }
                        .subtitle {
                            font-size: 1.1em;
                            color: #cbd5e0;
                            margin-bottom: 32px;
                            font-weight: 300;
                        }
                        .instructions {
                            background: rgba(255,255,255,0.08);
                            border-radius: 12px;
                            padding: 24px;
                            margin: 24px 0;
                            backdrop-filter: blur(10px);
                            border: 1px solid rgba(255,255,255,0.1);
                        }
                        .step {
                            display: flex;
                            align-items: center;
                            margin: 16px 0;
                            text-align: left;
                            font-size: 0.95em;
                        }
                        .step-number {
                            background: #4299e1;
                            color: white;
                            border-radius: 50%;
                            width: 24px;
                            height: 24px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 0.8em;
                            font-weight: 600;
                            margin-right: 12px;
                            flex-shrink: 0;
                        }
                        .features {
                            margin-top: 24px;
                            font-size: 0.9em;
                            color: #a0aec0;
                        }
                        .feature {
                            margin: 8px 0;
                        }
                        .feature::before {
                            content: "✓ ";
                            color: #48bb78;
                            font-weight: bold;
                            margin-right: 8px;
                        }
                        .ready-indicator {
                            margin-top: 32px;
                            padding: 12px 20px;
                            background: rgba(72, 187, 120, 0.15);
                            border: 1px solid rgba(72, 187, 120, 0.3);
                            border-radius: 8px;
                            color: #68d391;
                            font-size: 0.9em;
                            font-weight: 500;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="app-icon">📚</div>
                        <h1>FreeView</h1>
                        <p class="subtitle">Access Medium articles without subscription limits</p>
                        
                        <div class="instructions">
                            <div class="step">
                                <div class="step-number">1</div>
                                <div>Find a Medium article you want to read</div>
                            </div>
                            <div class="step">
                                <div class="step-number">2</div>
                                <div>Tap the Share button in your browser or Medium app</div>
                            </div>
                            <div class="step">
                                <div class="step-number">3</div>
                                <div>Select "Freeview" from the share menu</div>
                            </div>
                            <div class="step">
                                <div class="step-number">4</div>
                                <div>Read the full article without any restrictions</div>
                            </div>
                        </div>
                        
                        <div class="features">
                            <div class="feature">No subscription required</div>
                            <div class="feature">Bypass paywall restrictions</div>
                            <div class="feature">Clean reading experience</div>
                            <div class="feature">Works with any Medium article</div>
                        </div>
                        
                        <div class="ready-indicator">
                            🟢 Ready to receive shared links
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()
    }

    private fun showErrorPage(title: String, message: String) {
        val errorHtml = """
            <!DOCTYPE html>
            <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            text-align: center;
                            padding: 40px 20px;
                            background: linear-gradient(135deg, #ff6b6b 0%, #ee5a52 100%);
                            color: white;
                            margin: 0;
                            min-height: 100vh;
                            display: flex;
                            flex-direction: column;
                            justify-content: center;
                        }
                        .container {
                            max-width: 400px;
                            margin: 0 auto;
                        }
                        h1 {
                            font-size: 2.2em;
                            margin-bottom: 20px;
                            font-weight: 300;
                        }
                        p {
                            font-size: 1.1em;
                            line-height: 1.6;
                            opacity: 0.9;
                        }
                        .icon {
                            font-size: 4em;
                            margin-bottom: 20px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="icon">⚠️</div>
                        <h1>$title</h1>
                        <p>$message</p>
                    </div>
                </body>
            </html>
        """.trimIndent()

        binding.webView.loadData(errorHtml, "text/html", "UTF-8")
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