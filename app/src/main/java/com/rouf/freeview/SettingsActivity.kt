package com.rouf.freeview

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        applyWindowInsets()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Edge-to-edge is enforced on the target SDK, so pad for the system bars. */
    private fun applyWindowInsets() {
        val appBar = findViewById<View>(R.id.app_bar)
        val container = findViewById<View>(R.id.settings_container)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            container.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Show the app version in the About section.
            findPreference<Preference>(AppPreferences.KEY_ABOUT)?.summary = versionSummary()

            // Open Android's "Open by default" screen so the user can make FreeView the default
            // handler for medium.com links (unverified web links aren't offered automatically on
            // Android 12+).
            findPreference<Preference>(AppPreferences.KEY_OPEN_DEFAULT)
                ?.setOnPreferenceClickListener {
                    openDefaultAppSettings()
                    true
                }

            // Apply the theme immediately when it changes.
            findPreference<ListPreference>(AppPreferences.KEY_THEME)
                ?.setOnPreferenceChangeListener { _, newValue ->
                    AppCompatDelegate.setDefaultNightMode(nightModeFor(newValue as String))
                    true
                }

            // Clear WebView cache + cookies on tap.
            findPreference<Preference>(AppPreferences.KEY_CLEAR_CACHE)
                ?.setOnPreferenceClickListener {
                    clearWebViewData()
                    Toast.makeText(
                        requireContext(),
                        R.string.pref_clear_cache_done,
                        Toast.LENGTH_SHORT,
                    ).show()
                    true
                }

            // Open the FAQ screen.
            findPreference<Preference>(AppPreferences.KEY_FAQ)
                ?.setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), FaqActivity::class.java))
                    true
                }

            // Open the history screen.
            findPreference<Preference>(AppPreferences.KEY_VIEW_HISTORY)
                ?.setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), HistoryActivity::class.java))
                    true
                }

            // Clear stored history.
            findPreference<Preference>(AppPreferences.KEY_CLEAR_HISTORY)
                ?.setOnPreferenceClickListener {
                    HistoryStore(requireContext()).clear()
                    Toast.makeText(
                        requireContext(),
                        R.string.pref_clear_history_done,
                        Toast.LENGTH_SHORT,
                    ).show()
                    true
                }

            // Open the bookmarks screen.
            findPreference<Preference>(AppPreferences.KEY_VIEW_BOOKMARKS)
                ?.setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), BookmarksActivity::class.java))
                    true
                }

            // Clear stored bookmarks - deliberate saves, so confirm first.
            findPreference<Preference>(AppPreferences.KEY_CLEAR_BOOKMARKS)
                ?.setOnPreferenceClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.bookmarks_clear_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.bookmarks_clear) { _, _ ->
                            BookmarkStore(requireContext()).clear()
                            Toast.makeText(
                                requireContext(),
                                R.string.pref_clear_bookmarks_done,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        .show()
                    true
                }

            // Trim (or clear) stored history when the size changes.
            findPreference<ListPreference>(AppPreferences.KEY_HISTORY_SIZE)
                ?.setOnPreferenceChangeListener { _, newValue ->
                    HistoryStore(requireContext()).trimTo((newValue as String).toIntOrNull() ?: 0)
                    true
                }
        }

        private fun versionSummary(): String {
            val ctx = requireContext()
            val name = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            } catch (_: Exception) {
                null
            }
            return getString(R.string.about_version, name ?: "1.0")
        }

        private fun nightModeFor(theme: String): Int = when (theme) {
            AppPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        /** Sends the user to Android's per-app "Open by default" screen (app details as fallback). */
        private fun openDefaultAppSettings() {
            val uri = Uri.fromParts("package", requireContext().packageName, null)
            val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, uri)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
            }
            if (runCatching { startActivity(primary) }.isFailure) {
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)) }
            }
        }

        private fun clearWebViewData() {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            // A throwaway WebView clears the app-wide HTTP cache.
            WebView(requireContext()).apply {
                clearCache(true)
                clearHistory()
                destroy()
            }
        }
    }
}
