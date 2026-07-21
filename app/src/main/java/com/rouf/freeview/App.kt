package com.rouf.freeview

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Applies the saved theme before any Activity is created, avoiding a flash. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppPreferences(this).nightMode)
    }
}
