package com.example.browser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime

class BrowserApp : Application() {
    val runtime: GeckoRuntime by lazy { GeckoRuntime.create(this) }
}
