package com.example.memorecite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class MemoDisplayActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var isShowing: Boolean = false
            private set

        private const val AUTO_CLOSE_MS = 60_000L
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tvIndicator: TextView
    private lateinit var tvGroupName: TextView

    private var groups: MutableList<MemoGroup> = mutableListOf()
    private var activeGroup: MemoGroup? = null
    private var dueCards: MutableList<MemoCard> = mutableListOf()
    private lateinit var adapter: CardPagerAdapter

    private var shouldRecordClose = true

    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable {
        shouldRecordClose = true
        finish()
    }

    /**
     * 只监听 USER_PRESENT（真正解锁）
     * SCREEN_OFF 交给 KeepAliveService 的 ScreenStateReceiver 统一处理
     */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                shouldRecordClose = false
                ScreenState.clearCooldown(this@MemoDisplayActivity)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Prefs.isInQuietHours(this)) { finish(); return }
        if (!ScreenState.isLocked(this)) { finish(); return }
        App.resetForeground()
        Prefs.clearPause(this)

        isShowing = true
        setContentView(R.layout.activity_memo_display)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(userPresentReceiver, filter)

        viewPager = findViewById(R.id.viewPager)
        tvIndicator = findViewById(R.id.tvPageIndicator)
        tvGroupName = findViewById(R.id.tvGroupName)

        findViewById<TextView>(R.id.btnSkipGroup).setOnClickListener {
            shouldRecordClose = false
            skipCurrentGroup()
        }

        loadDueCards()
        if (dueCards.isEmpty()) {
            shouldRecordClose = false
            finish()
            return
        }

        // 显示组名（图标 + 名称）
        tvGroupName.text = "${activeGroup?.icon ?: "📚"} ${activeGroup?.name ?: ""}"

        adapter = CardPagerAdapter(dueCards)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // 使用本地化的 "1 / N" 格式
                tvIndicator.text = "${position + 1} / ${dueCards.size}"
            }
        })
        tvIndicator.text = "1 / ${dueCards.size}"

        findViewById<Button>(R.id.btnForgot).setOnClickListener {
            shouldRecordClose = false
            onQuality(EbbinghausScheduler.QUALITY_FORGOT)
        }
        findViewById<Button>(R.id.btnVague).setOnClickListener {
            shouldRecordClose = false
            onQuality(EbbinghausScheduler.QUALITY_VAGUE)
        }
        findViewById<Button>(R.id.btnGood).setOnClickListener {
            shouldRecordClose = false
            onQuality(EbbinghausScheduler.QUALITY_GOOD)
        }

        resetAutoCloseTimer()
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        super.onPause()
        isShowing = false
    }

    /** 触屏 → 重置 1 分钟计时 */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null && (ev.action == MotionEvent.ACTION_DOWN ||
                    ev.action == MotionEvent.ACTION_MOVE)) {
            resetAutoCloseTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun resetAutoCloseTimer() {
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
        autoCloseHandler.postDelayed(autoCloseRunnable, AUTO_CLOSE_MS)
    }

    private fun loadDueCards() {
        groups = MemoStore.load(this)
        val now = System.currentTimeMillis()

        // 🟢 用 allCardsRecursive 支持无限层级
        // 找最早到期、且已开始的组
        val candidateGroups = groups.filter {
            val allCards = it.allCardsRecursive(groups)
            allCards.isNotEmpty()
                    && it.isStarted(groups, now)
                    && !Prefs.isGroupSkipped(this, it.id)
        }.sortedBy { g ->
            val allCards = g.allCardsRecursive(groups)
            allCards.minOfOrNull { it.nextReviewTime } ?: Long.MAX_VALUE
        }

        for (g in candidateGroups) {
            val allCards = g.allCardsRecursive(groups)
            val due = allCards.filter { it.isDue(now) }
            val fresh = allCards.filter { it.isNew }

            val ordered = mutableListOf<MemoCard>()
            ordered.addAll(due)
            fresh.forEach { f ->
                if (ordered.none { it.id == f.id }) ordered.add(f)
            }

            if (ordered.isNotEmpty()) {
                activeGroup = g
                val batchSize = g.effectiveBatchSize(groups)
                dueCards = ordered.take(batchSize).shuffled().toMutableList()
                return
            }
        }

        activeGroup = null
        dueCards = mutableListOf()
    }

    private fun skipCurrentGroup() {
        val g = activeGroup ?: return
        shouldRecordClose = false
        Prefs.skipGroup(this, g.id)
        MemoStore.save(this, groups)
        val i = Intent(this, MemoDisplayActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(i)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun onQuality(quality: Int) {
        val position = viewPager.currentItem
        if (position >= dueCards.size) return
        val card = dueCards[position]
        val g = activeGroup

        // 🟢 卡片自定义曲线 > 组的有效曲线（含继承）
        val intervals = card.customIntervals ?: run {
            g?.effectiveIntervals(groups) ?: Prefs.getIntervals(this)
        }

        EbbinghausScheduler.schedule(card, quality, intervals)
        MemoStore.save(this, groups)

        resetAutoCloseTimer()

        if (position < dueCards.size - 1) {
            viewPager.setCurrentItem(position + 1, true)
        } else {
            val now = System.currentTimeMillis()
            // 检查该组还有没有剩余
            val remaining = activeGroup?.let { ag ->
                ag.allCardsRecursive(groups).count { it.isDue(now) || it.isNew }
            } ?: 0

            if (remaining > 0) {
                reloadNextBatch()
            } else {
                shouldRecordClose = false
                AlarmScheduler.scheduleNext(this)
                finish()
            }
        }
    }

    private fun reloadNextBatch() {
        loadDueCards()
        if (dueCards.isEmpty()) {
            shouldRecordClose = false
            AlarmScheduler.scheduleNext(this)
            finish()
            return
        }
        adapter = CardPagerAdapter(dueCards)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(0, false)
        tvIndicator.text = "1 / ${dueCards.size}"
        tvGroupName.text = "${activeGroup?.icon ?: "📚"} ${activeGroup?.name ?: ""}"
    }

    override fun onDestroy() {
        super.onDestroy()
        isShowing = false
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
        try { unregisterReceiver(userPresentReceiver) } catch (_: Exception) {}

        // 记录冷却（所有非"用户主动操作"的关闭都记录）
        if (shouldRecordClose) {
            ScreenState.setLastCloseTime(this, System.currentTimeMillis())
        }

        MemoStore.save(this, groups)
        AlarmScheduler.scheduleNext(this)
    }
}