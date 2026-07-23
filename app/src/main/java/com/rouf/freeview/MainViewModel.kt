package com.rouf.freeview

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    /** Which kind of page the WebView is showing; gates the bookmark star. */
    enum class PageState { WELCOME, LOADING, ARTICLE, ERROR }

    /** The Medium URL currently open, or null when the welcome page is showing. */
    var currentMediumUrl: String? = null

    /** Service id used for the current load; drives reload-on-return from Settings. */
    var lastServiceId: String? = null

    /** Current page kind; the star is only offered on [PageState.ARTICLE]. Survives rotation. */
    var pageState: PageState = PageState.WELCOME

    /** Cached "current article is bookmarked" flag, so the menu never re-parses the store. */
    var isCurrentBookmarked: Boolean = false
}
