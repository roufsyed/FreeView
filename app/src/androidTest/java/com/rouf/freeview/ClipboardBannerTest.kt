package com.rouf.freeview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device checks for the clipboard-offer banner. Sets the app's own clipboard, launches to the
 * welcome page, waits for window focus (the offer is read in onWindowFocusChanged, since clipboard
 * access needs focus), then asserts the banner state. Detection accepts a Medium link, a custom-domain
 * link, or any http(s) URL; plain text shows nothing.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardBannerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun setClipboard(text: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("test", text))
        }
    }

    /** Launches MainActivity, waits until it has window focus, and returns the banner's visibility. */
    private fun bannerShown(): Boolean {
        var shown = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val deadline = System.currentTimeMillis() + 5000
            var focused = false
            while (!focused && System.currentTimeMillis() < deadline) {
                scenario.onActivity { focused = it.hasWindowFocus() }
                if (!focused) Thread.sleep(50)
            }
            scenario.onActivity {
                shown = it.findViewById<View>(R.id.clipboardBanner).visibility == View.VISIBLE
            }
        }
        return shown
    }

    @Test
    fun mediumUrl_showsBanner() {
        setClipboard("Check this https://medium.com/@author/great-post-abc123 out!")
        assertTrue(bannerShown())
    }

    @Test
    fun mediumCustomDomainUrl_showsBanner() {
        // The case that failed before: a Medium article on a custom publication domain.
        setClipboard("https://ai.plainenglish.io/uber-architecture-part-1-why-tracking-abc123")
        assertTrue(bannerShown())
    }

    @Test
    fun anyHttpUrl_showsBanner() {
        setClipboard("https://example.com/some-page")
        assertTrue(bannerShown())
    }

    @Test
    fun plainTextNoUrl_noBanner() {
        setClipboard("just some copied text with no link in it")
        assertFalse(bannerShown())
    }
}
