package com.example.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * 一般瀏覽器：用 GeckoView 渲染網頁，支援分頁、書籤、瀏覽紀錄。
 * 遇到 YouTube 網址時改丟給獨立的 YouTube 播放器 App 處理（不在 WebView 裡播放）。
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val YTPLAYER_PACKAGE = "com.example.ytplayer"
        private const val HOME_URL = "https://www.google.com"
        private val YOUTUBE_HOSTS = setOf(
            "youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtu.be"
        )
    }

    private lateinit var geckoView: GeckoView
    private lateinit var urlInput: EditText
    private lateinit var loadProgress: ProgressBar
    private lateinit var repository: BrowserRepository

    private val tabs = mutableListOf<Tab>()
    private lateinit var activeTab: Tab
    private var nextTabId = 1L

    private val bookmarksLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra(BookmarksActivity.RESULT_URL)?.let { activeTab.session.loadUri(it) }
    }
    private val historyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra(HistoryActivity.RESULT_URL)?.let { activeTab.session.loadUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = BrowserRepository(this)

        geckoView = findViewById(R.id.geckoView)
        urlInput = findViewById(R.id.urlInput)
        loadProgress = findViewById(R.id.loadProgress)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val goButton = findViewById<ImageButton>(R.id.goButton)
        val menuButton = findViewById<ImageButton>(R.id.menuButton)

        val startUrl = intent?.dataString ?: HOME_URL
        activeTab = createTab(startUrl, makeActive = true)

        goButton.setOnClickListener { loadFromInput() }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromInput()
                true
            } else {
                false
            }
        }
        backButton.setOnClickListener {
            if (activeTab.canGoBack) activeTab.session.goBack() else finish()
        }
        menuButton.setOnClickListener { showOverflowMenu(menuButton) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent.dataString ?: return
        createTab(url, makeActive = true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeTab.canGoBack) {
            activeTab.session.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun createTab(url: String, makeActive: Boolean): Tab {
        val tab = Tab(nextTabId++, GeckoSession())
        tab.session.open((application as BrowserApp).runtime)
        attachDelegates(tab)
        tabs.add(tab)
        if (makeActive) {
            switchToTab(tab)
        }
        tab.session.loadUri(url)
        return tab
    }

    private fun switchToTab(tab: Tab) {
        activeTab = tab
        geckoView.setSession(tab.session)
        urlInput.setText(tab.url)
        loadProgress.visibility = View.GONE
    }

    private fun closeTab(tab: Tab) {
        val wasActive = tab == activeTab
        tabs.remove(tab)
        tab.session.close()
        if (tabs.isEmpty()) {
            createTab(HOME_URL, makeActive = true)
        } else if (wasActive) {
            switchToTab(tabs.last())
        }
    }

    private fun attachDelegates(tab: Tab) {
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                geckoSession: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                if (isYoutubeWatchUrl(Uri.parse(uri))) {
                    openInYtPlayer(uri)
                    return GeckoResult.deny()
                }
                return GeckoResult.allow()
            }

            override fun onLocationChange(
                geckoSession: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url != null) {
                    tab.url = url
                    if (tab == activeTab) {
                        urlInput.setText(url)
                    }
                }
            }

            override fun onCanGoBack(geckoSession: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }
        }

        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(geckoSession: GeckoSession, title: String?) {
                tab.title = title ?: ""
            }
        }

        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(geckoSession: GeckoSession, url: String) {
                if (tab == activeTab) {
                    loadProgress.visibility = View.VISIBLE
                    loadProgress.progress = 0
                }
            }

            override fun onPageStop(geckoSession: GeckoSession, success: Boolean) {
                if (tab == activeTab) {
                    loadProgress.visibility = View.GONE
                }
                if (success && tab.url.startsWith("http")) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.addHistory(tab.url, tab.title) }
                    }
                }
            }

            override fun onProgressChange(geckoSession: GeckoSession, progress: Int) {
                if (tab == activeTab) {
                    loadProgress.progress = progress
                }
            }
        }
    }

    private fun loadFromInput() {
        val raw = urlInput.text.toString().trim()
        if (raw.isEmpty()) return
        activeTab.session.loadUri(normalizeInput(raw))
    }

    private fun normalizeInput(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${Uri.encode(input)}"
        }
    }

    /** 只有真正的播放頁才轉去播放器；首頁、搜尋結果、頻道頁等留在瀏覽器裡瀏覽 */
    private fun isYoutubeWatchUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (host !in YOUTUBE_HOSTS) return false
        if (host == "youtu.be") {
            val path = uri.path
            return !path.isNullOrEmpty() && path != "/"
        }
        val path = uri.path ?: return false
        return (path == "/watch" && !uri.getQueryParameter("v").isNullOrEmpty()) ||
            path.startsWith("/shorts/") ||
            path.startsWith("/live/")
    }

    private fun openInYtPlayer(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(YTPLAYER_PACKAGE)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "找不到 YouTube 播放器，請先安裝", Toast.LENGTH_LONG).show()
            activeTab.session.loadUri(url)
        }
    }

    private fun showOverflowMenu(anchor: View) {
        lifecycleScope.launch {
            val bookmarked = withContext(Dispatchers.IO) { repository.isBookmarked(activeTab.url) }
            val popup = PopupMenu(this@MainActivity, anchor)
            popup.menu.add(0, 1, 0, getString(R.string.menu_new_tab))
            popup.menu.add(0, 2, 1, getString(R.string.menu_tabs, tabs.size))
            popup.menu.add(
                0, 3, 2,
                getString(if (bookmarked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark)
            )
            popup.menu.add(0, 4, 3, getString(R.string.menu_bookmarks))
            popup.menu.add(0, 5, 4, getString(R.string.menu_history))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { createTab(HOME_URL, makeActive = true); true }
                    2 -> { showTabsDialog(); true }
                    3 -> { toggleBookmark(bookmarked); true }
                    4 -> { bookmarksLauncher.launch(Intent(this@MainActivity, BookmarksActivity::class.java)); true }
                    5 -> { historyLauncher.launch(Intent(this@MainActivity, HistoryActivity::class.java)); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun toggleBookmark(currentlyBookmarked: Boolean) {
        val tab = activeTab
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (currentlyBookmarked) {
                    repository.removeBookmark(tab.url)
                } else {
                    repository.addBookmark(tab.url, tab.title)
                }
            }
            Toast.makeText(
                this@MainActivity,
                getString(if (currentlyBookmarked) R.string.toast_bookmark_removed else R.string.toast_bookmark_added),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showTabsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_tabs, null)
        val list = view.findViewById<RecyclerView>(R.id.tabList)
        val newTabButton = view.findViewById<Button>(R.id.newTabButton)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_tabs)
            .setView(view)
            .create()

        lateinit var adapter: TabListAdapter
        adapter = TabListAdapter(
            onClick = { tab ->
                switchToTab(tab)
                dialog.dismiss()
            },
            onClose = { tab ->
                closeTab(tab)
                adapter.submitList(tabs.toList())
            }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        adapter.submitList(tabs.toList())

        newTabButton.setOnClickListener {
            createTab(HOME_URL, makeActive = true)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tabs.forEach { it.session.close() }
    }
}
