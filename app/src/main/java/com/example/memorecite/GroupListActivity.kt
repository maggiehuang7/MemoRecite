package com.example.memorecite

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GroupListActivity : AppCompatActivity() {

    private lateinit var allGroups: MutableList<MemoGroup>
    private lateinit var adapter: GroupAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            adapter.notifyDataSetChanged()
            updateTodayTaskCard()
            handler.postDelayed(this, 30_000L)
        }
    }

    private var parentId: String? = null

    private val iconOptions = arrayOf(
        "📚", "🧠", "💡", "🎯", "🔥", "⭐", "📖", "✏️",
        "🌍", "🧪", "🎨", "🎵", "📁", "🗂", "📂", "🧩"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_list)

        KeepAliveService.start(this)

        parentId = intent.getStringExtra("parent_id")

        allGroups = MemoStore.load(this)
        if (allGroups.isEmpty()) {
            val demo = MemoGroup(name = getString(R.string.app_name), icon = "📚")
            val sample = MemoCard(
                front = "What is the Ebbinghaus forgetting curve?",
                back = "A curve describing how human memory fades over time"
            )
            EbbinghausScheduler.initNewCard(sample, 1L)
            demo.cards.add(sample)
            allGroups.add(demo)
            MemoStore.save(this, allGroups)
        }

        updateTitle()

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = GroupAdapter(emptyList(),
            onClick = { group -> onGroupClick(group) },
            onLongClick = { showGroupMenu(it) }
        )
        recycler.adapter = adapter

        findViewById<TextView>(R.id.btnAddGroup).setOnClickListener { addGroup() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnPCUpload).setOnClickListener {
            startActivity(Intent(this, PCUploadActivity::class.java))
        }
        findViewById<TextView>(R.id.btnQuickTest).setOnClickListener {
            quickTestLockScreen()
        }

        findViewById<TextView>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }

    private fun updateTitle() {
        val tvTitle = findViewById<TextView>(R.id.tvTitle) ?: return
        if (parentId == null) {
            tvTitle.text = getString(R.string.gl_title)
        } else {
            val parent = allGroups.firstOrNull { it.id == parentId }
            tvTitle.text = getString(
                R.string.gl_sub_title,
                "${parent?.icon ?: "📁"} ${parent?.name ?: ""}"
            )
        }
    }

    /**
     * 🟢 点击组：
     * - 无子组无卡片 → 提示
     * - 只有子组 → 进入子组列表
     * - 只有卡片 → 进入卡片列表
     * - 两者都有 → 弹菜单让用户选
     */
    private fun onGroupClick(group: MemoGroup) {
        val childCount = MemoStore.getChildGroups(allGroups, group.id).size
        val cardCount = group.cards.size

        when {
            childCount == 0 && cardCount == 0 -> {
                Toast.makeText(this, getString(R.string.gl_toast_no_content), Toast.LENGTH_SHORT).show()
            }
            childCount == 0 -> {
                // 只有卡片 → 进入卡片列表
                openCardList(group)
            }
            cardCount == 0 -> {
                // 只有子组 → 进入子组列表
                openSubGroupList(group)
            }
            else -> {
                // 两者都有 → 弹菜单
                val options = arrayOf(
                    "📁 查看子组（$childCount 个）",
                    "🗂 查看卡片（$cardCount 张）"
                )
                AlertDialog.Builder(this)
                    .setTitle("${group.icon} ${group.name}")
                    .setItems(options) { _, which ->
                        if (which == 0) openSubGroupList(group)
                        else openCardList(group)
                    }
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show()
            }
        }
    }

    private fun openSubGroupList(group: MemoGroup) {
        val i = Intent(this, GroupListActivity::class.java)
        i.putExtra("parent_id", group.id)
        startActivity(i)
    }

    private fun openCardList(group: MemoGroup) {
        Prefs.clearSkip(this, group.id)
        val i = Intent(this, CardListActivity::class.java)
        i.putExtra("group_id", group.id)
        startActivity(i)
    }

    override fun onResume() {
        super.onResume()
        allGroups = MemoStore.load(this)
        updateTitle()
        refreshList()
        updateTodayTaskCard()
        AlarmScheduler.scheduleNext(this)
        handler.post(refreshTask)

        if (Prefs.isPaused(this)) {
            Prefs.clearPause(this)
        }
        PermissionUtils.checkAndRequestNotificationPermission(this)
        checkPermissionsOnce()
    }

    private fun updateTodayTaskCard() {
        val stats = DailyStatsStore.load(this)
        val now = System.currentTimeMillis()
        val dueCount = allGroups.sumOf { g ->
            if (g.isStarted(allGroups, now)) {
                g.allCardsRecursive(allGroups).count { it.isDue(now) }
            } else 0
        }
        val isArrears = DailyStatsStore.checkArrears(this)

        val tvStatus = findViewById<TextView>(R.id.tvTaskStatus)
        val tvNewProg = findViewById<TextView>(R.id.tvNewProgress)
        val pbNew = findViewById<ProgressBar>(R.id.pbNewCards)
        val tvRevProg = findViewById<TextView>(R.id.tvReviewProgress)
        val pbRev = findViewById<ProgressBar>(R.id.pbReviews)
        val tvFooter = findViewById<TextView>(R.id.tvTaskFooter)

        when {
            isArrears -> {
                tvStatus?.text = getString(R.string.task_status_arrears)
                tvStatus?.setTextColor(resources.getColor(R.color.accent_light, null))
            }
            dueCount > 0 -> {
                tvStatus?.text = getString(R.string.task_status_clear_reviews)
                tvStatus?.setTextColor(resources.getColor(R.color.accent_light, null))
            }
            stats.isCompleted -> {
                tvStatus?.text = getString(R.string.task_status_completed)
                tvStatus?.setTextColor(resources.getColor(R.color.accent, null))
            }
            else -> {
                tvStatus?.text = getString(R.string.task_status_in_progress)
                tvStatus?.setTextColor(resources.getColor(R.color.text_secondary, null))
            }
        }

        tvNewProg?.text = getString(R.string.task_new_cards_progress, stats.newCardsDone, stats.newCardsTarget)
        pbNew?.max = 100
        pbNew?.progress = if (stats.newCardsTarget == 0) 100 else ((stats.newCardsDone.toDouble() / stats.newCardsTarget) * 100).toInt().coerceIn(0, 100)

        tvRevProg?.text = getString(R.string.task_review_cards_progress, stats.reviewsDone, stats.reviewsTarget)
        pbRev?.max = 100
        pbRev?.progress = if (stats.reviewsTarget == 0) 100 else ((stats.reviewsDone.toDouble() / stats.reviewsTarget) * 100).toInt().coerceIn(0, 100)

        val streak = DailyStatsStore.getStreak(this)
        tvFooter?.text = getString(R.string.task_footer_summary, stats.totalInteractions, stats.correctRate, streak)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshTask)
    }

    private fun refreshList() {
        val currentLevelGroups = MemoStore.getChildGroups(allGroups, parentId)
        adapter = GroupAdapter(currentLevelGroups,
            onClick = { group -> onGroupClick(group) },
            onLongClick = { showGroupMenu(it) }
        )
        findViewById<RecyclerView>(R.id.recycler).adapter = adapter
    }

    // ========== 权限 ==========

    private fun areAllPermissionsGranted(): Boolean {
        return PermissionUtils.hasOverlayPermission(this) &&
                PermissionUtils.hasBatteryOptimizationExemption(this) &&
                PermissionUtils.hasExactAlarmPermission(this)
    }

    private fun checkPermissionsOnce() {
        if (Prefs.isPermissionPrompted(this)) return
        if (areAllPermissionsGranted()) {
            Prefs.setPermissionPrompted(this)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.perm_title))
            .setMessage(getString(R.string.perm_msg))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.perm_go_settings)) { _, _ ->
                Prefs.setPermissionPrompted(this)
                PermissionUtils.showBackgroundPermissionGuideDialog(this)
            }
            .setNegativeButton(getString(R.string.perm_later)) { _, _ ->
                Prefs.setPermissionPrompted(this)
            }
            .show()
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            ))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.perm_manual), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ========== 立即测试 ==========

    private fun quickTestLockScreen() {
        allGroups = MemoStore.load(this)
        val target = allGroups.firstOrNull { it.allCardsRecursive(allGroups).isNotEmpty() }
        if (target == null) {
            Toast.makeText(this, getString(R.string.gl_toast_no_cards), Toast.LENGTH_SHORT).show()
            return
        }

        Prefs.clearPause(this)
        allGroups.forEach { Prefs.clearSkip(this, it.id) }

        val allCards = target.allCardsRecursive(allGroups)
        val card = allCards.firstOrNull() ?: return
        card.nextReviewTime = System.currentTimeMillis() + 5000L
        if (card.lastReviewTime == 0L) card.lastReviewTime = System.currentTimeMillis()
        MemoStore.save(this, allGroups)

        AlarmScheduler.scheduleNext(this)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gl_test_title))
            .setMessage(getString(R.string.gl_test_msg))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.gl_test_ok)) { _, _ ->
                val home = Intent(Intent.ACTION_MAIN)
                home.addCategory(Intent.CATEGORY_HOME)
                home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(home)
            }
            .show()
    }

    // ========== 组管理 ==========

    private fun addGroup() {
        val now = System.currentTimeMillis()
        val dueCount = allGroups.sumOf { g ->
            if (g.isStarted(allGroups, now)) {
                g.allCardsRecursive(allGroups).count { it.isDue(now) }
            } else 0
        }
        if (dueCount > 0) {
            Toast.makeText(this, getString(R.string.task_toast_clear_due_first, dueCount), Toast.LENGTH_LONG).show()
            return
        }
        if (DailyStatsStore.checkArrears(this)) {
            Toast.makeText(this, getString(R.string.task_toast_arrears_blocked), Toast.LENGTH_LONG).show()
            return
        }

        val input = EditText(this).apply {
            hint = getString(R.string.gl_hint_group_name)
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_input)
            setPadding(32, 32, 32, 32)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_hint, null))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(
                if (parentId == null) R.string.gl_dialog_new_group
                else R.string.gl_dialog_new_sub
            ))
            .setView(input)
            .setPositiveButton(getString(R.string.gl_next)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.gl_toast_empty_name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                pickIconAndCreate(name)
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun pickIconAndCreate(name: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gl_dialog_pick_icon))
            .setItems(iconOptions) { _, which ->
                val g = MemoGroup(
                    name = name,
                    icon = iconOptions[which],
                    parentId = parentId,
                    createdAt = System.currentTimeMillis()
                )
                allGroups.add(g)
                MemoStore.save(this, allGroups)
                refreshList()
            }
            .show()
    }

    private fun showGroupMenu(group: MemoGroup) {
        val childCount = MemoStore.getChildGroups(allGroups, group.id).size
        val options = mutableListOf<String>()
        options.add(getString(R.string.gl_menu_open))
        options.add(getString(R.string.gl_menu_settings))
        options.add(getString(R.string.gl_menu_rename))
        if (childCount == 0) {
            options.add(getString(R.string.gl_menu_delete))
        } else {
            options.add(getString(R.string.gl_menu_delete_with_children, childCount))
        }

        AlertDialog.Builder(this)
            .setTitle("${group.icon} ${group.name}")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> onGroupClick(group)
                    1 -> {
                        val i = Intent(this, GroupSettingsActivity::class.java)
                        i.putExtra("group_id", group.id)
                        startActivity(i)
                    }
                    2 -> renameGroup(group)
                    3 -> deleteGroup(group, childCount)
                }
            }
            .show()
    }

    private fun renameGroup(group: MemoGroup) {
        val input = EditText(this).apply {
            setText(group.name)
            setBackgroundResource(R.drawable.bg_input)
            setPadding(32, 32, 32, 32)
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gl_dialog_rename))
            .setView(input)
            .setPositiveButton(getString(R.string.common_ok)) { _, _ ->
                val idx = allGroups.indexOfFirst { it.id == group.id }
                if (idx >= 0) {
                    allGroups[idx] = group.copy(name = input.text.toString().trim())
                    MemoStore.save(this, allGroups)
                    refreshList()
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun deleteGroup(group: MemoGroup, childCount: Int) {
        val msg = if (childCount > 0) {
            getString(R.string.gl_delete_msg_with_children, childCount)
        } else {
            getString(R.string.gl_delete_msg)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gl_dialog_delete))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                MemoStore.createSnapshot(this, "before_delete_group")
                MemoStore.deleteGroupRecursive(this, allGroups, group.id)
                MemoStore.save(this, allGroups)
                refreshList()
                AlarmScheduler.scheduleNext(this)
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }
}