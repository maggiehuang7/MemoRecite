package com.example.memorecite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StatsAdapter(private val groups: List<MemoGroup>) :
    RecyclerView.Adapter<StatsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvNumbers: TextView = view.findViewById(R.id.tvNumbers)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val tvPercent: TextView = view.findViewById(R.id.tvPercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_stats, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g = groups[position]
        val ctx = holder.itemView.context
        val total = g.cards.size
        val mastered = g.cards.count { it.reviewLevel >= 5 }
        val newCount = g.cards.count { it.isNew }
        val dueCount = g.cards.count { it.isDue() }
        val inProgress = total - mastered - newCount

        holder.tvIcon.text = g.icon
        holder.tvName.text = g.name

        val sep = ctx.getString(R.string.st_separator)
        holder.tvNumbers.text = listOf(
            ctx.getString(R.string.st_mastered, mastered),
            ctx.getString(R.string.st_reviewing, inProgress),
            ctx.getString(R.string.st_new, newCount),
            ctx.getString(R.string.st_due, dueCount)
        ).joinToString(sep)

        val percent = if (total == 0) 0 else mastered * 100 / total
        holder.progressBar.progress = percent
        holder.tvPercent.text = ctx.getString(R.string.st_percent, percent)
    }

    override fun getItemCount() = groups.size
}