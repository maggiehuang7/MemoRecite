package com.example.memorecite

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        // 1. 最近 7 天记忆任务打卡记录
        val recent7 = DailyStatsStore.loadRecentDays(this, 7)
        val recycler7Days = findViewById<RecyclerView>(R.id.recycler7Days)
        recycler7Days.layoutManager = LinearLayoutManager(this)
        recycler7Days.adapter = DailyStatsAdapter(recent7)

        // 2. 组别记忆掌握度
        val groups = MemoStore.load(this)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = StatsAdapter(groups)

        val streak = DailyStatsStore.getStreak(this)
        val totalCards = groups.sumOf { it.allCardsRecursive(groups).size }
        val totalMastered = groups.sumOf { g ->
            g.allCardsRecursive(groups).count { it.reviewLevel >= 5 }
        }
        findViewById<TextView>(R.id.tvSummary).text =
            "🔥 连续打卡 $streak 天 · 共 ${groups.size} 组 · $totalCards 张卡片 · 已掌握 $totalMastered 张"
    }
}