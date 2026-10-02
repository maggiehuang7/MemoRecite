package com.example.memorecite

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GroupSettingsActivity : AppCompatActivity() {
    private lateinit var allGroups: MutableList<MemoGroup>
    private lateinit var group: MemoGroup
    private var pendingStartAt: Long = 0L
    private lateinit var tvStartAt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_settings)

        val groupId = intent.getStringExtra("group_id") ?: run { finish(); return }
        allGroups = MemoStore.load(this)
        group = allGroups.firstOrNull { it.id == groupId } ?: run { finish(); return }

        pendingStartAt = group.effectiveStartAt(allGroups)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val parent = group.parentGroup(allGroups)
        val titleBase = getString(R.string.gs_title, "${group.icon} ${group.name}")
        tvTitle.text = if (parent != null) {
            titleBase + getString(R.string.gs_inherit_from, "${parent.icon} ${parent.name}")
        } else titleBase

        tvStartAt = findViewById(R.id.tvStartAt)
        val etFirstDelay = findViewById<EditText>(R.id.etFirstDelay)
        val etBatchSize = findViewById<EditText>(R.id.etBatchSize)
        val etIntervals = findViewById<EditText>(R.id.etIntervals)

        etFirstDelay.setText(group.effectiveFirstDelay(allGroups).toString())
        etBatchSize.setText(group.effectiveBatchSize(allGroups).toString())
        etIntervals.setText(group.effectiveIntervals(allGroups).joinToString(", "))
        updateStartAtText()

        findViewById<Button>(R.id.btnStartNow).setOnClickListener {
            pendingStartAt = 0L
            updateStartAtText()
        }
        findViewById<Button>(R.id.btnStart1h).setOnClickListener {
            pendingStartAt = System.currentTimeMillis() + 60 * 60 * 1000L
            updateStartAtText()
        }
        findViewById<Button>(R.id.btnStartTomorrow).setOnClickListener {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 8)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            pendingStartAt = cal.timeInMillis
            updateStartAtText()
        }
        findViewById<Button>(R.id.btnStartCustom).setOnClickListener {
            pickCustomDateTime()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val firstDelay = etFirstDelay.text.toString().toLongOrNull()
            if (firstDelay == null || firstDelay < 0) {
                Toast.makeText(this, getString(R.string.gs_first_delay_invalid), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val batchSize = etBatchSize.text.toString().toIntOrNull()
            if (batchSize == null || batchSize < 1) {
                Toast.makeText(this, getString(R.string.gs_batch_invalid), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intervals = etIntervals.text.toString()
                .split(",", "，", " ")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { it > 0 }
            if (intervals.isEmpty()) {
                Toast.makeText(this, getString(R.string.gs_intervals_invalid), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val idx = allGroups.indexOfFirst { it.id == group.id }
            allGroups[idx] = group.copy(
                firstDelay = firstDelay,
                batchSize = batchSize,
                intervals = intervals,
                startAt = pendingStartAt
            )
            MemoStore.save(this, allGroups)
            AlarmScheduler.scheduleNext(this)
            Toast.makeText(this, getString(R.string.gs_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            etFirstDelay.setText(group.effectiveFirstDelay(allGroups).toString())
            etBatchSize.setText(group.effectiveBatchSize(allGroups).toString())
            etIntervals.setText(Prefs.DEFAULT_INTERVALS.joinToString(", "))
            Toast.makeText(this, getString(R.string.gs_reset_default), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnBulkImport).setOnClickListener { showBulkImport() }

        findViewById<Button>(R.id.btnResetProgress).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.gs_reset_progress_title))
                .setMessage(getString(R.string.gs_reset_progress_msg))
                .setPositiveButton(getString(R.string.import_restore)) { _, _ ->
                    val effectiveDelay = group.effectiveFirstDelay(allGroups)
                    group.cards.forEach { c ->
                        EbbinghausScheduler.initNewCard(c, effectiveDelay)
                    }
                    MemoStore.save(this, allGroups)
                    AlarmScheduler.scheduleNext(this)
                    Toast.makeText(this, getString(R.string.gs_reset_done), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
        }
    }

    private fun updateStartAtText() {
        tvStartAt.text = if (pendingStartAt == 0L) {
            getString(R.string.gs_start_now)
        } else {
            val now = System.currentTimeMillis()
            if (pendingStartAt <= now) {
                getString(R.string.gs_started)
            } else {
                SimpleDateFormat(getString(R.string.time_format), Locale.getDefault())
                    .format(Date(pendingStartAt))
            }
        }
    }

    private fun pickCustomDateTime() {
        val cal = Calendar.getInstance()
        if (pendingStartAt > 0L) cal.timeInMillis = pendingStartAt

        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val c = Calendar.getInstance()
                c.set(year, month, day, hour, minute, 0)
                c.set(Calendar.MILLISECOND, 0)
                pendingStartAt = c.timeInMillis
                updateStartAtText()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showBulkImport() {
        val input = EditText(this).apply {
            hint = getString(R.string.cl_bulk_hint)
            minLines = 8
            maxLines = 15
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
                Toast.makeText(this, getString(R.string.gs_bulk_imported, count), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }
}