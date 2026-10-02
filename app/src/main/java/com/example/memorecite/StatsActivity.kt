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

        val groups = MemoStore.load(this)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = StatsAdapter(groups)

        val totalCards = groups.sumOf { it.cards.size }
        val totalMastered = groups.sumOf { g ->
            g.cards.count { it.reviewLevel >= 5 }
        }
        findViewById<TextView>(R.id.tvSummary).text =
            "共 ${groups.size} 组 · $totalCards 张卡片 · 已掌握 $totalMastered 张"
    }
}