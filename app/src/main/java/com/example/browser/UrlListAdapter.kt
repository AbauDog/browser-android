package com.example.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UrlListAdapter(
    private val onClick: (UrlEntry) -> Unit,
    private val onDelete: (UrlEntry) -> Unit
) : RecyclerView.Adapter<UrlListAdapter.ViewHolder>() {

    private val items = mutableListOf<UrlEntry>()

    fun submitList(newItems: List<UrlEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_url_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick, onDelete)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.entryTitle)
        private val url = itemView.findViewById<TextView>(R.id.entryUrl)
        private val deleteButton = itemView.findViewById<ImageButton>(R.id.deleteButton)

        fun bind(entry: UrlEntry, onClick: (UrlEntry) -> Unit, onDelete: (UrlEntry) -> Unit) {
            title.text = entry.title.ifBlank { entry.url }
            url.text = entry.url
            itemView.setOnClickListener { onClick(entry) }
            deleteButton.setOnClickListener { onDelete(entry) }
        }
    }
}
