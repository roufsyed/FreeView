package com.rouf.freediumcfd

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    /** The Medium URL currently open, or null when the welcome page is showing. */
    var currentMediumUrl: String? = null

    /** Service id used for the current load; drives reload-on-return from Settings. */
    var lastServiceId: String? = null

    /** Whether the URL input bar is expanded. */
    var isUrlBarVisible: Boolean = false
}
