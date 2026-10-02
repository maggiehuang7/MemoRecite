package com.example.memorecite

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CardListActivity : AppCompatActivity() {
    private lateinit var allGroups: MutableList<MemoGroup>
    private lateinit var group: MemoGroup
    private lateinit var adapter: CardAdapter

    private var isSelectMode = false
    private val selectedIds = mutableSetOf<String>()

    private var currentFilterHour: Long = 0L
    private val availableHours = mutableListOf<Long>()

    private lateinit var toolbar: View
    private lateinit var dateFilterBar: View
    private lateinit var dateFilterContainer: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var tvNormalTitle: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvGroupTitle: TextView
    private lateinit var recycler: RecyclerView

    private val iconOptions = arrayOf(
        "📚", "🧠", "💡", "🎯", "🔥", "⭐", "📖", "✏️",
        "🌍", "🧪", "🎨", "🎵", "📁", "🗂", "📂", "🧩"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_list)

        val groupId = intent.getStringExtra("group_id") ?: run { finish(); return }
        allGroups = MemoStore.load(this)
        group = allGroups.firstOrNull { it.id == groupId } ?: run { finish(); return }

        toolbar = findViewById(R.id.toolbar)
        dateFilterBar = findViewById(R.id.dateFilterBar)
        dateFilterContainer = findViewById(R.id.dateFilterContainer)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        tvNormalTitle = findViewById(R.id.tvNormalTitle)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvGroupTitle = findViewById(R.id.tvGroupTitle)
        recycler = findViewById(R.id.recycler)

        recycler.layoutManager = LinearLayoutManager(this)

        tvGroupTitle.text = "${group.icon} ${group.name}"

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnNewSubGroup).setOnClickListener {
            showCreateSubGroupDialog()
        }

        findViewById<TextView>(R.id.btnAddCard).setOnClickListener {
            if (isSelectMode) return@setOnClickListener
            val i = Intent(this, CardEditActivity::class.java)
            i.putExtra("group_id", group.id)
            startActivity(i)
        }
        findViewById<TextView>(R.id.btnBulkAdd).setOnClickListener {
            if (isSelectMode) return@setOnClickListener
            showBulkAdd()
        }

        findViewById<TextView>(R.id.btnSelectAll).setOnClickListener { selectAll() }
        findViewById<TextView>(R.id.btnBatchAdjust).setOnClickListener { showBatchAdjust() }
        findViewById<TextView>(R.id.btnBatchDelete).setOnClickListener { confirmBatchDelete() }
        findViewById<TextView>(R.id.btnCancelSelect).setOnClickListener { exitSelectMode() }
    }

    // ========== 新建子组 ==========

    private fun showCreateSubGroupDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.cl_sub_group_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_input)
            setPadding(32, 32, 32, 32)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_hint, null))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cl_sub_group_title, group.name))
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
                val newGroup = MemoGroup(
                    name = name,
                    icon = iconOptions[which],
                    parentId = group.id,
                    createdAt = System.currentTimeMillis()
                )
                allGroups.add(newGroup)
                MemoStore.save(this, allGroups)

                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.cl_sub_created_title))
                    .setMessage(getString(R.string.cl_sub_created_msg, group.name, name))
                    .setPositiveButton(getString(R.string.common_ok), null)
                    .show()
            }
            .show()
    }

    // ========== 时间筛选 ==========

    private fun collectAvailableHours() {
        availableHours.clear()
        val hourSet = mutableSetOf<Long>()
        group.cards.forEach { card -> hourSet.add(getHourOnly(card.createdAt)) }
        availableHours.addAll(hourSet.sortedDescending())
    }

    private fun getHourOnly(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getDateOnly(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatHourChip(hourTime: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = hourTime
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val hourStr = String.format(Locale.CHINA, "%02d:00", hour)

        val today = getDateOnly(System.currentTimeMillis())
        val yesterday = today - 24 * 60 * 60 * 1000L
        val dayOfHour = getDateOnly(hourTime)

        val dayLabel = when (dayOfHour) {
            today -> getString(R.string.cl_today)
            yesterday -> getString(R.string.cl_yesterday)
            else -> SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(dayOfHour))
        }
        return "$dayLabel $hourStr"
    }

    private fun rebuildTimeFilters() {
        collectAvailableHours()
        dateFilterContainer.removeAllViews()
        if (group.cards.isEmpty()) {
            dateFilterBar.visibility = View.GONE
            return
        }
        dateFilterBar.visibility = View.VISIBLE
        addTimeChip(getString(R.string.cl_filter_all, group.cards.size), 0L)
        availableHours.forEach { hour ->
            val count = group.cards.count { getHourOnly(it.createdAt) == hour }
            addTimeChip(getString(R.string.cl_filter_hour, formatHourChip(hour), count), hour)
        }
    }

    private fun addTimeChip(label: String, hour: Long) {
        val tv = TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(36, 20, 36, 20)
            val isSelected = (hour == currentFilterHour)
            setTextColor(ContextCompat.getColor(
                this@CardListActivity,
                if (isSelected) R.color.text_primary else R.color.text_secondary
            ))
            background = if (isSelected) {
                ContextCompat.getDrawable(this@CardListActivity, R.drawable.bg_button_primary)
            } else {
                ContextCompat.getDrawable(this@CardListActivity, R.drawable.bg_card)
            }
            setOnClickListener {
                currentFilterHour = hour
                rebuildTimeFilters()
                refreshCardList()
            }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = 8
        dateFilterContainer.addView(tv, lp)
    }

    private fun getFilteredCards(): List<MemoCard> {
        return if (currentFilterHour == 0L) {
            group.cards
        } else {
            group.cards.filter { getHourOnly(it.createdAt) == currentFilterHour }
        }
    }

    private fun refreshCardList() {
        val filtered = getFilteredCards().sortedByDescending { it.createdAt }
        adapter = CardAdapter(filtered,
            onClick = { card ->
                if (isSelectMode) toggleSelect(card)
                else {
                    val i = Intent(this, CardEditActivity::class.java)
                    i.putExtra("group_id", group.id)
                    i.putExtra("card_id", card.id)
                    startActivity(i)
                }
            },
            onLongClick = { card -> if (!isSelectMode) enterSelectMode(card) },
            isSelectModeProvider = { isSelectMode },
            isSelectedProvider = { selectedIds.contains(it.id) }
        )
        recycler.adapter = adapter
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        allGroups = MemoStore.load(this)
        group = allGroups.firstOrNull { it.id == group.id } ?: run { finish(); return }
        tvGroupTitle.text = "${group.icon} ${group.name}"
        rebuildTimeFilters()
        refreshCardList()
    }

    // ========== 多选 ==========

    private fun enterSelectMode(card: MemoCard) {
        isSelectMode = true
        selectedIds.clear()
        selectedIds.add(card.id)
        updateSelectUI()
    }

    private fun exitSelectMode() {
        isSelectMode = false
        selectedIds.clear()
        updateSelectUI()
    }

    private fun toggleSelect(card: MemoCard) {
        if (selectedIds.contains(card.id)) selectedIds.remove(card.id)
        else selectedIds.add(card.id)
        if (selectedIds.isEmpty()) exitSelectMode() else updateSelectUI()
    }

    private fun selectAll() {
        val filtered = getFilteredCards()
        if (selectedIds.size == filtered.size) {
            selectedIds.clear()
        } else {
            selectedIds.clear()
            filtered.forEach { selectedIds.add(it.id) }
        }
        updateSelectUI()
    }

    private fun updateSelectUI() {
        toolbar.visibility = if (isSelectMode) View.VISIBLE else View.GONE
        dateFilterBar.visibility = if (isSelectMode) View.GONE
        else if (group.cards.isEmpty()) View.GONE else View.VISIBLE
        tvNormalTitle.visibility = if (isSelectMode) View.GONE else View.VISIBLE
        tvSelectedCount.text = getString(R.string.cl_selected_count, selectedIds.size)
        adapter.notifyDataSetChanged()
    }

    // ========== 批量删除 ==========

    private fun confirmBatchDelete() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, getString(R.string.cl_select_hint), Toast.LENGTH_SHORT).show()
            return
        }
        val count = selectedIds.size
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cl_delete_title, count))
            .setMessage(getString(R.string.cl_delete_msg))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                MemoStore.createSnapshot(this, "before_batch_delete")
                group.cards.filter { selectedIds.contains(it.id) }
                    .forEach { ImageStore.delete(this, it.imageFile) }
                group.cards.removeAll { selectedIds.contains(it.id) }
                MemoStore.save(this, allGroups)
                AlarmScheduler.scheduleNext(this)
                Toast.makeText(this, getString(R.string.cl_deleted_toast, count), Toast.LENGTH_SHORT).show()
                exitSelectMode()
                rebuildTimeFilters()
                refreshCardList()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    // ========== 批量调整 ==========

    private fun showBatchAdjust() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, getString(R.string.cl_select_hint), Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(
            "📦 移动到其他组",                      // 🟢 新增
            getString(R.string.cl_batch_custom_curve),
            getString(R.string.cl_batch_clear_curve),
            getString(R.string.cl_batch_start_time),
            getString(R.string.cl_batch_reset_new),
            getString(R.string.cl_batch_now),
            getString(R.string.cl_batch_10min),
            getString(R.string.cl_batch_1h),
            getString(R.string.cl_batch_1d),
            getString(R.string.cl_batch_mastered),
            getString(R.string.cl_batch_level_plus)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cl_batch_title, selectedIds.size))
            .setItems(options) { _, which -> applyBatchAdjust(which) }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun applyBatchAdjust(which: Int) {
        val now = System.currentTimeMillis()
        val targets = group.cards.filter { selectedIds.contains(it.id) }
        val effectiveIntervals = group.effectiveIntervals(allGroups)
        val effectiveFirstDelay = group.effectiveFirstDelay(allGroups)

        when (which) {
            0 -> { showMoveToGroupDialog(targets); return }   // 🟢 移动到其他组
            1 -> { showCurveEditor(targets); return }
            2 -> {
                targets.forEach { card ->
                    val idx = group.cards.indexOfFirst { it.id == card.id }
                    if (idx >= 0) group.cards[idx] = card.copy(customIntervals = null)
                }
                MemoStore.save(this, allGroups)
                Toast.makeText(this, getString(R.string.cl_cleared_curve_toast, targets.size), Toast.LENGTH_SHORT).show()
                exitSelectMode()
                refreshCardList()
                return
            }
            3 -> { showStartTimeEditor(targets); return }
        }

        targets.forEach { card ->
            when (which) {
                4 -> EbbinghausScheduler.initNewCard(card, effectiveFirstDelay, now)
                5 -> {
                    card.nextReviewTime = now
                    if (card.lastReviewTime == 0L) card.lastReviewTime = now
                }
                6 -> {
                    card.nextReviewTime = now + 10 * 60_000L
                    if (card.lastReviewTime == 0L) card.lastReviewTime = now
                }
                7 -> {
                    card.nextReviewTime = now + 60 * 60_000L
                    if (card.lastReviewTime == 0L) card.lastReviewTime = now
                }
                8 -> {
                    card.nextReviewTime = now + 24 * 60 * 60_000L
                    if (card.lastReviewTime == 0L) card.lastReviewTime = now
                }
                9 -> {
                    val intervals = card.customIntervals ?: effectiveIntervals
                    card.reviewLevel = intervals.size - 1
                    card.nextReviewTime = now + intervals.last() * 60_000L
                    if (card.lastReviewTime == 0L) card.lastReviewTime = now
                }
                10 -> {
                    val intervals = card.customIntervals ?: effectiveIntervals
                    card.reviewLevel = (card.reviewLevel + 1).coerceAtMost(intervals.size - 1)
                    val level = card.reviewLevel
                    card.nextReviewTime = now + intervals[level] * 60_000L
                    if (card.lastReviewTime == 0L) card.lastReviewTime = now
                }
            }
        }
        MemoStore.save(this, allGroups)
        AlarmScheduler.scheduleNext(this)
        Toast.makeText(this, getString(R.string.cl_adjusted_toast, targets.size), Toast.LENGTH_SHORT).show()
        exitSelectMode()
        refreshCardList()
    }

    // ========== 🟢 移动到其他组 ==========

    /**
     * 弹出所有可选的组（排除当前组），让用户选择移动目标
     * 用面包屑路径显示，方便识别层级
     */
    private fun showMoveToGroupDialog(targets: List<MemoCard>) {
        val otherGroups = allGroups.filter { it.id != group.id }
        if (otherGroups.isEmpty()) {
            Toast.makeText(this, "没有其他组可移动", Toast.LENGTH_SHORT).show()
            return
        }

        // 为每个组生成面包屑路径名
        val labels = otherGroups.map { g ->
            val path = g.breadcrumbPath(allGroups).joinToString(" / ") { "${it.icon} ${it.name}" }
            path
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("移动 ${targets.size} 张卡片到：")
            .setItems(labels) { _, which ->
                val targetGroup = otherGroups[which]
                moveCards(targets, targetGroup)
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun moveCards(cards: List<MemoCard>, targetGroup: MemoGroup) {
        // 从当前组移除
        group.cards.removeAll { card -> cards.any { it.id == card.id } }

        // 加到目标组
        val targetIdx = allGroups.indexOfFirst { it.id == targetGroup.id }
        if (targetIdx >= 0) {
            allGroups[targetIdx].cards.addAll(cards)
        }

        MemoStore.save(this, allGroups)
        AlarmScheduler.scheduleNext(this)

        Toast.makeText(
            this,
            "✅ 已移动 ${cards.size} 张卡片到「${targetGroup.icon} ${targetGroup.name}」",
            Toast.LENGTH_LONG
        ).show()

        exitSelectMode()
        rebuildTimeFilters()
        refreshCardList()
    }

    // ========== 曲线编辑器 ==========

    private fun showCurveEditor(targets: List<MemoCard>) {
        val initial = targets.firstOrNull()?.customIntervals
            ?: group.effectiveIntervals(allGroups)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 24)
        }
        val inputs = mutableListOf<EditText>()

        for (i in 0 until 10) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                text = getString(R.string.curve_level, i + 1)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@CardListActivity, R.color.text_secondary))
                width = 160
            }
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(initial.getOrNull(i)?.toString() ?: "0")
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@CardListActivity, R.color.text_primary))
                setBackgroundResource(R.drawable.bg_input)
                setPadding(20, 16, 20, 16)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            inputs.add(et)
            val unit = TextView(this).apply {
                text = getString(R.string.curve_minutes)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@CardListActivity, R.color.text_hint))
            }
            row.addView(label)
            row.addView(et)
            row.addView(unit)
            container.addView(row)
        }

        val hint = TextView(this).apply {
            text = getString(R.string.curve_hint)
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@CardListActivity, R.color.text_hint))
            setPadding(0, 16, 0, 0)
        }
        container.addView(hint)

        val presetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 20)
        }
        val presetTitle = TextView(this).apply {
            text = getString(R.string.curve_preset_title)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@CardListActivity, R.color.text_secondary))
            setPadding(0, 0, 0, 8)
        }
        presetContainer.addView(presetTitle)

        val presetButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        EbbinghausScheduler.PRESET_CURVES.forEach { (nameKey, curve) ->
            val name = getString(resources.getIdentifier(nameKey, "string", packageName))
            val btn = TextView(this).apply {
                text = name
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@CardListActivity, R.color.text_primary))
                setPadding(24, 12, 24, 12)
                background = ContextCompat.getDrawable(this@CardListActivity, R.drawable.bg_button_secondary)
                setOnClickListener {
                    inputs.forEachIndexed { idx, et ->
                        et.setText(curve.getOrNull(idx)?.toString() ?: "0")
                    }
                    Toast.makeText(this@CardListActivity, getString(R.string.curve_preset_applied, name), Toast.LENGTH_SHORT).show()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 8
            presetButtonsLayout.addView(btn, lp)
        }
        presetContainer.addView(presetButtonsLayout)

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollContent.addView(presetContainer)
        scrollContent.addView(container)

        val scrollView = ScrollView(this).apply { addView(scrollContent) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.curve_title, targets.size))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                val newCurve = inputs.mapNotNull {
                    it.text.toString().trim().toLongOrNull()?.takeIf { v -> v > 0 }
                }
                if (newCurve.isEmpty()) {
                    Toast.makeText(this, getString(R.string.curve_need_one), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                setCustomCurve(targets, newCurve)
            }
            .setNeutralButton(getString(R.string.curve_use_default)) { _, _ ->
                targets.forEach { card ->
                    val idx = group.cards.indexOfFirst { it.id == card.id }
                    if (idx >= 0) group.cards[idx] = card.copy(customIntervals = null)
                }
                MemoStore.save(this, allGroups)
                Toast.makeText(this, getString(R.string.cl_curve_default_toast), Toast.LENGTH_SHORT).show()
                exitSelectMode()
                refreshCardList()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun setCustomCurve(targets: List<MemoCard>, curve: List<Long>) {
        targets.forEach { card ->
            val idx = group.cards.indexOfFirst { it.id == card.id }
            if (idx >= 0) {
                group.cards[idx] = card.copy(
                    customIntervals = curve,
                    reviewLevel = 0
                )
            }
        }
        MemoStore.save(this, allGroups)
        AlarmScheduler.scheduleNext(this)
        Toast.makeText(
            this,
            getString(R.string.cl_curve_set_toast, targets.size, curve.size),
            Toast.LENGTH_SHORT
        ).show()
        exitSelectMode()
        refreshCardList()
    }

    // ========== 开始时间编辑器 ==========

    private fun showStartTimeEditor(targets: List<MemoCard>) {
        val options = arrayOf(
            getString(R.string.start_time_now),
            getString(R.string.start_time_1h),
            getString(R.string.start_time_3h),
            getString(R.string.start_time_tomorrow),
            getString(R.string.start_time_3d),
            getString(R.string.start_time_7d),
            getString(R.string.start_time_custom)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_time_title, targets.size))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setStartTime(targets, System.currentTimeMillis())
                    1 -> setStartTime(targets, System.currentTimeMillis() + 60 * 60_000L)
                    2 -> setStartTime(targets, System.currentTimeMillis() + 3 * 60 * 60_000L)
                    3 -> {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        cal.set(Calendar.HOUR_OF_DAY, 8)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        setStartTime(targets, cal.timeInMillis)
                    }
                    4 -> setStartTime(targets, System.currentTimeMillis() + 3 * 24 * 60 * 60_000L)
                    5 -> setStartTime(targets, System.currentTimeMillis() + 7 * 24 * 60 * 60_000L)
                    6 -> pickCustomDateTime { time -> setStartTime(targets, time) }
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun setStartTime(targets: List<MemoCard>, time: Long) {
        val now = System.currentTimeMillis()
        targets.forEach { card ->
            card.nextReviewTime = time
            card.reviewLevel = 0
            if (card.lastReviewTime == 0L) card.lastReviewTime = now
        }
        MemoStore.save(this, allGroups)
        AlarmScheduler.scheduleNext(this)
        val timeStr = SimpleDateFormat(getString(R.string.time_format), Locale.getDefault()).format(Date(time))
        Toast.makeText(this, getString(R.string.cl_time_set_toast, targets.size, timeStr), Toast.LENGTH_SHORT).show()
        exitSelectMode()
        refreshCardList()
    }

    private fun pickCustomDateTime(onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val c = Calendar.getInstance()
                c.set(year, month, day, hour, minute, 0)
                c.set(Calendar.MILLISECOND, 0)
                onPicked(c.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ========== 批量添加 ==========

    private fun showBulkAdd() {
        val input = EditText(this).apply {
            hint = getString(R.string.cl_bulk_hint)
            minLines = 6
            maxLines = 12
            setBackgroundResource(R.drawable.bg_input)
            setPadding(32, 32, 32, 32)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_hint, null))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cl_bulk_add_title, group.name))
            .setView(input)
            .setPositiveButton(getString(R.string.common_add)) { _, _ ->
                val lines = input.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) {
                    Toast.makeText(this, getString(R.string.cl_no_content), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val now = System.currentTimeMillis()
                val effectiveDelay = group.effectiveFirstDelay(allGroups)
                var count = 0
                lines.forEach { line ->
                    val card = if (line.contains("|")) {
                        val parts = line.split("|", limit = 2)
                        MemoCard(
                            front = parts[0].trim(),
                            back = parts[1].trim(),
                            createdAt = now
                        )
                    } else {
                        MemoCard(back = line, createdAt = now)
                    }
                    EbbinghausScheduler.initNewCard(card, effectiveDelay)
                    group.cards.add(card)
                    count++
                }
                MemoStore.save(this, allGroups)
                AlarmScheduler.scheduleNext(this)
                Toast.makeText(this, getString(R.string.cl_bulk_added_toast, count), Toast.LENGTH_SHORT).show()
                rebuildTimeFilters()
                refreshCardList()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }
}