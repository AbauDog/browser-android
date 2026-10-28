package com.example.browser

import org.mozilla.geckoview.GeckoSession

class Tab(val id: Long, val session: GeckoSession) {
    var title: String = ""
    var url: String = ""
    var canGoBack: Boolean = false
}
