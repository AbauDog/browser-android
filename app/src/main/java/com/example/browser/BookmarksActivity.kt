package com.example.browser

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksActivity : AppCompatActivity() {

    companion object {
        const val RESULT_URL = "result_url"
    }

    private lateinit var repository: BrowserRepository
    private lateinit var adapter: UrlListAdapter
    private lateinit var emptyText: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_url_list)

        repository = BrowserRepository(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_bookmarks)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        emptyText = findViewById(R.id.emptyText)
        val list = findViewById<RecyclerView>(R.id.list)
        adapter = UrlListAdapter(
            onClick = { entry ->
                setResult(RESULT_OK, Intent().putExtra(RESULT_URL, entry.url))
                finish()
            },
            onDelete = { entry ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.removeBookmark(entry.url) }
                    reload()
                }
            }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { repository.listBookmarks() }
            adapter.submitList(items)
            emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
