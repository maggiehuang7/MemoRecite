package com.example.memorecite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupAdapter(
    private val groups: List<MemoGroup>,
    private val onClick: (MemoGroup) -> Unit,
    private val onLongClick: (MemoGroup) -> Unit
) : RecyclerView.Adapter<GroupAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val tvName: TextView = view.findViewById(R.id.tvGroupName)
        val tvCount: TextView = view.findViewById(R.id.tvGroupCount)
        val tvTime: TextView = view.findViewById(R.id.tvGroupTime)
        val tvCountdown: TextView = view.findViewById(R.id.tvGroupCountdown)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g = groups[position]
        val ctx = holder.itemView.context
        val sdf = SimpleDateFormat(ctx.getString(R.string.time_format), Locale.getDefault())
        val allGroups = MemoStore.load(ctx)

        holder.tvIcon.text = g.icon
        holder.tvName.text = g.name

        val childCount = MemoStore.getChildGroups(allGroups, g.id).size
        val totalCards = g.allCardsRecursive(allGroups).size

        val parts = mutableListOf<String>()
        if (childCount > 0) parts.add(ctx.getString(R.string.gl_info_children, childCount))
        if (totalCards > 0) parts.add(ctx.getString(R.string.gl_info_cards, totalCards))
        holder.tvCount.text = if (parts.isEmpty()) {
            ctx.getString(R.string.gl_info_empty)
        } else {
            parts.joinToString(ctx.getString(R.string.gl_info_separator))
        }

        holder.tvTime.text = sdf.format(Date(g.createdAt))

        val now = System.currentTimeMillis()
        val allCards = g.allCardsRecursive(allGroups)
        val startAt = g.effectiveStartAt(allGroups)

        if (startAt > 0 && startAt > now) {
            holder.tvCountdown.text = ctx.getString(
                R.string.gl_info_start_at,
                sdf.format(Date(startAt))
            )
        } else if (allCards.isEmpty()) {
            holder.tvCountdown.text = ""
        } else {
            val dueCount = allCards.count { it.isDue(now) }
            if (dueCount > 0) {
                holder.tvCountdown.text = ctx.getString(R.string.gl_info_due, dueCount)
            } else {
                val nextTime = allCards.map { it.nextReviewTime }.filter { it > now }.minOrNull()
                holder.tvCountdown.text = if (nextTime != null) {
                    ctx.getString(R.string.gl_info_next, EbbinghausScheduler.formatCountdown(ctx, nextTime))
                } else ""
            }
        }

        holder.itemView.setOnClickListener { onClick(g) }
        holder.itemView.setOnLongClickListener { onLongClick(g); true }
    }

    override fun getItemCount() = groups.size
}