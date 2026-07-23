package com.rouf.freeview

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard on MainActivity's manifest intent-filters: the launcher and Share entry points
 * must keep resolving after the VIEW filter was added, and VIEW must resolve for any http/https web
 * link (Medium articles live on medium.com and on custom publication domains). Scoped to this package
 * so a browser can't satisfy it.
 */
@RunWith(AndroidJUnit4::class)
class IntentFilterResolutionTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val pkg: String = ctx.packageName

    @Suppress("DEPRECATION")
    private fun resolvesToApp(intent: Intent): Boolean =
        ctx.packageManager.queryIntentActivities(intent.setPackage(pkg), 0)
            .any { it.activityInfo.packageName == pkg }

    @Test
    fun launcher_still_resolves() {
        assertTrue(
            resolvesToApp(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)),
        )
    }

    @Test
    fun share_text_still_resolves() {
        assertTrue(resolvesToApp(Intent(Intent.ACTION_SEND).setType("text/plain")))
    }

    @Test
    fun view_medium_https_resolves() {
        assertTrue(
            resolvesToApp(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://medium.com/@user/post-abc123"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
            ),
        )
    }

    @Test
    fun view_custom_domain_and_any_web_link_resolves() {
        // No host restriction: Medium custom-domain publications and any other web link resolve.
        assertTrue(
            resolvesToApp(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.plainenglish.io/some-article-abc"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
            ),
        )
        assertTrue(
            resolvesToApp(
                Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/whatever"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
            ),
        )
    }
}
