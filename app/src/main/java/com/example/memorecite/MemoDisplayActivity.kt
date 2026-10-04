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
        // 无操作 1 分钟后自动熄屏关闭，清除常亮标志并开启 30 分钟冷却
        shouldRecordClose = true
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
        finish()
    }

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

        // ✅ 修改：删除 `if (!ScreenState.isLocked(this)) { finish(); return }`
        // 原因：测试按钮 / 已解锁状态启动时会被这行直接 finish 掉，看不到弹屏
        // 用户解锁逻辑由下面的 ACTION_USER_PRESENT 广播负责

        Prefs.clearPause(this)

        isShowing = true
        setContentView(R.layout.activity_memo_display)

        // ✅ 修改：窗口标志放在 setContentView 之后，确保生效
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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

        tvGroupName.text = "${activeGroup?.icon ?: "📚"} ${activeGroup?.name ?: ""}"

        adapter = CardPagerAdapter(dueCards)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
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

    // ✅ 新增：singleInstance 复用时会走这里，需要刷新卡片
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Prefs.clearPause(this)
        shouldRecordClose = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        loadDueCards()
        if (dueCards.isEmpty()) {
            shouldRecordClose = false
            finish()
            return
        }

        adapter = CardPagerAdapter(dueCards)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(0, false)
        tvIndicator.text = "1 / ${dueCards.size}"
        tvGroupName.text = "${activeGroup?.icon ?: "📚"} ${activeGroup?.name ?: ""}"
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

        val candidateGroups = groups.filter { g ->
            val allCards = g.allCardsRecursive(groups)
            allCards.isNotEmpty()
                    && !Prefs.isGroupSkipped(this, g.id)
                    && g.isStarted(groups, now)
        }.sortedBy { g ->
            g.allCardsRecursive(groups).minOfOrNull { it.nextReviewTime } ?: Long.MAX_VALUE
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
            val remaining = groups
                .firstOrNull { it.id == activeGroup?.id }
                ?.cards?.count { it.isDue(now) || it.isNew } ?: 0

            if (remaining > 0) {
                reloadNextBatch()
            } else {
                shouldRecordClose = false
                finish()
            }
        }
    }

    private fun reloadNextBatch() {
        loadDueCards()
        if (dueCards.isEmpty()) {
            shouldRecordClose = false
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

        if (shouldRecordClose) {
            ScreenState.setLastCloseTime(this, System.currentTimeMillis())
        }

        MemoStore.save(this, groups)
        // ✅ 修改：删除 `AlarmScheduler.scheduleNext(this)`
        // 由 Receiver 统一调度，避免双重调度导致闹钟链叠加
    }
}