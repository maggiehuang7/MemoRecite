package com.example.memorecite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DailyStatsAdapter(private val list: List<DailyStats>) :
    RecyclerView.Adapter<DailyStatsAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate: TextView = v.findViewById(R.id.tvDate)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        val tvNewProgress: TextView = v.findViewById(R.id.tvNewProgress)
        val tvReviewProgress: TextView = v.findViewById(R.id.tvReviewProgress)
        val tvRate: TextView = v.findViewById(R.id.tvRate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_stats, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvDate.text = item.date

        if (item.isCompleted) {
            holder.tvStatus.text = "✅ 打卡成功"
            holder.tvStatus.setTextColor(holder.itemView.resources.getColor(R.color.accent, null))
        } else if (item.date != DailyStatsStore.todayStr()) {
            holder.tvStatus.text = "⚠️ 未达标(欠账)"
            holder.tvStatus.setTextColor(holder.itemView.resources.getColor(R.color.accent_light, null))
        } else {
            holder.tvStatus.text = "⏳ 进行中"
            holder.tvStatus.setTextColor(holder.itemView.resources.getColor(R.color.text_secondary, null))
        }

        holder.tvNewProgress.text = "新卡: ${item.newCardsDone}/${item.newCardsTarget}"
        holder.tvReviewProgress.text = "复习: ${item.reviewsDone}/${item.reviewsTarget}"
        holder.tvRate.text = "正确率: ${"%.1f".format(item.correctRate)}%"
    }

    override fun getItemCount(): Int = list.size
}
