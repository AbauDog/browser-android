package com.example.browser

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

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
        toolbar.title = getString(R.string.title_history)
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
                    withContext(Dispatchers.IO) { repository.deleteHistoryEntry(entry.id) }
                    reload()
                }
            }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_clear_history) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.clearHistory() }
                reload()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { repository.listHistory() }
            adapter.submitList(items)
            emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
