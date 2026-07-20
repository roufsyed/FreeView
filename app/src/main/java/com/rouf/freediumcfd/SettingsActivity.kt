package com.rouf.freediumcfd

import android.content.Intent
import android.os.Bundle
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
