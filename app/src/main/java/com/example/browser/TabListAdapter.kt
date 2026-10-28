package com.example.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabListAdapter(
    private val onClick: (Tab) -> Unit,
    private val onClose: (Tab) -> Unit
) : RecyclerView.Adapter<TabListAdapter.ViewHolder>() {

    private val items = mutableListOf<Tab>()

    fun submitList(newItems: List<Tab>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick, onClose)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.tabTitle)
        private val url = itemView.findViewById<TextView>(R.id.tabUrl)
        private val closeButton = itemView.findViewById<ImageButton>(R.id.closeTabButton)

        fun bind(tab: Tab, onClick: (Tab) -> Unit, onClose: (Tab) -> Unit) {
            title.text = tab.title.ifBlank { tab.url.ifBlank { "新分頁" } }
            url.text = tab.url
            itemView.setOnClickListener { onClick(tab) }
            closeButton.setOnClickListener { onClose(tab) }
        }
    }
}
