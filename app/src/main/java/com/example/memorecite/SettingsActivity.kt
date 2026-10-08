package com.example.memorecite

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var etFirstDelay: EditText
    private lateinit var etIntervals: EditText
    private lateinit var etDailyNewTarget: EditText

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            MemoStore.createSnapshot(this, "before_import")
            val dst = File(filesDir, "memo_data.json")
            contentResolver.openInputStream(uri)?.use { input ->
                dst.outputStream().use { out -> input.copyTo(out) }
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_ok_title))
                .setMessage(getString(R.string.import_ok_msg))
                .setPositiveButton(getString(R.string.common_ok)) { _, _ -> finishAffinity() }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etFirstDelay = findViewById(R.id.etFirstDelay)
        etIntervals = findViewById(R.id.etIntervals)
        etDailyNewTarget = findViewById(R.id.etDailyNewTarget)

        etFirstDelay.setText(Prefs.getFirstDelayMinutes(this).toString())
        etIntervals.setText(Prefs.getIntervals(this).joinToString(", "))
        etDailyNewTarget.setText(Prefs.getDailyNewTarget(this).toString())

        // 🌐 语言按钮
        val btnLang = findViewById<Button>(R.id.btnLanguage)
        btnLang.text = getString(R.string.settings_language_button, LocaleHelper.getCurrentName(this))
        btnLang.setOnClickListener { showLanguagePicker() }

        // 🔤 字体大小按钮
        updateFontSizeButtonLabel()
        findViewById<Button>(R.id.btnFontSize)?.setOnClickListener { showFontSizePicker() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val firstDelay = etFirstDelay.text.toString().toLongOrNull()
            if (firstDelay == null || firstDelay < 0) {
                Toast.makeText(this, getString(R.string.common_input_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intervals = etIntervals.text.toString()
                .split(",", "，", " ")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { it > 0 }
            if (intervals.isEmpty()) {
                Toast.makeText(this, getString(R.string.common_input_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.saveFirstDelay(this, firstDelay)
            Prefs.saveIntervals(this, intervals)
            val dailyTarget = etDailyNewTarget.text.toString().toIntOrNull() ?: 80
            Prefs.saveDailyNewTarget(this, dailyTarget.coerceAtLeast(1))
            Toast.makeText(this, getString(R.string.common_save), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            etFirstDelay.setText("1")
            etIntervals.setText(Prefs.DEFAULT_INTERVALS.joinToString(", "))
            etDailyNewTarget.setText("80")
            Toast.makeText(this, getString(R.string.settings_reset), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnQuietTime).setOnClickListener { showQuietTimeSettings() }
        findViewById<Button>(R.id.btnPermissions).setOnClickListener { PermissionUtils.showBackgroundPermissionGuideDialog(this) }
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportData() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { showImportOptions() }
    }

    private fun showFontSizePicker() {
        val currentScale = Prefs.getFontSizeScale(this)
        val options = arrayOf(
            getString(R.string.font_size_small),
            getString(R.string.font_size_normal),
            getString(R.string.font_size_large),
            getString(R.string.font_size_xlarge)
        )
        val scales = floatArrayOf(0.8f, 1.0f, 1.25f, 1.5f)
        val selectedIdx = when {
            currentScale <= 0.85f -> 0
            currentScale <= 1.1f -> 1
            currentScale <= 1.35f -> 2
            else -> 3
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_font_size_title))
            .setSingleChoiceItems(options, selectedIdx) { dialog, which ->
                val pickedScale = scales[which]
                Prefs.saveFontSizeScale(this, pickedScale)
                updateFontSizeButtonLabel()
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.common_save), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun updateFontSizeButtonLabel() {
        val scale = Prefs.getFontSizeScale(this)
        val label = when {
            scale <= 0.85f -> getString(R.string.font_size_small)
            scale <= 1.1f -> getString(R.string.font_size_normal)
            scale <= 1.35f -> getString(R.string.font_size_large)
            else -> getString(R.string.font_size_xlarge)
        }
        val btnFont = findViewById<Button>(R.id.btnFontSize)
        btnFont?.text = getString(R.string.settings_font_size_button, label)
    }

    private fun showLanguagePicker() {
        val current = LocaleHelper.getCurrent(this)
        val langs = LocaleHelper.SUPPORTED
        val labels = langs.map { "${it.flag}  ${it.name}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_language_title))
            .setSingleChoiceItems(labels, langs.indexOfFirst { it.code == current }) { dialog, which ->
                val picked = langs[which]
                LocaleHelper.saveLanguage(this, picked.code)
                LocaleHelper.setLanguage(picked.code)
                dialog.dismiss()

                AlertDialog.Builder(this)
                    .setTitle("✅ ${picked.name}")
                    .setMessage(getString(R.string.settings_lang_changed))
                    .setPositiveButton(getString(R.string.common_ok)) { _, _ ->
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                        startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                    .setCancelable(false)
                    .show()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showQuietTimeSettings() {
        val t = Prefs.getQuietTime(this)
        val startStr = String.format(Locale.CHINA, "%02d:%02d", t[0], t[1])
        val endStr = String.format(Locale.CHINA, "%02d:%02d", t[2], t[3])
        val enabled = Prefs.isQuietEnabled(this)

        val options = arrayOf(
            if (enabled) "✅ ON" else "❌ OFF",
            "🌙 Start: $startStr",
            "☀️ End: $endStr"
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_quiet_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        Prefs.setQuietEnabled(this, !enabled)
                        Toast.makeText(this, if (!enabled) "ON" else "OFF", Toast.LENGTH_SHORT).show()
                    }
                    1 -> pickTime(t[0], t[1]) { h, m ->
                        Prefs.setQuietTime(this, h, m, t[2], t[3])
                    }
                    2 -> pickTime(t[2], t[3]) { h, m ->
                        Prefs.setQuietTime(this, t[0], t[1], h, m)
                    }
                }
            }
            .setNegativeButton(getString(R.string.common_close), null)
            .show()
    }

    private fun pickTime(initHour: Int, initMin: Int, onPicked: (Int, Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onPicked(h, m) }, initHour, initMin, true).show()
    }

    private fun exportData() {
        try {
            val src = File(filesDir, "memo_data.json")
            if (!src.exists()) {
                Toast.makeText(this, getString(R.string.export_no_data), Toast.LENGTH_SHORT).show()
                return
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val exportDir = getExternalFilesDir(null) ?: filesDir
            val dst = File(exportDir, "memorecite_backup_$timestamp.json")
            src.copyTo(dst, overwrite = true)

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.export_ok_title))
                .setMessage(getString(R.string.export_ok_msg, dst.absolutePath))
                .setPositiveButton(getString(R.string.common_ok), null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun showImportOptions() {
        val backups = MemoStore.listBackups(this)
        val options = mutableListOf<String>()
        options.add(getString(R.string.import_choose))
        if (backups.isNotEmpty()) {
            options.add(getString(R.string.import_from_builtin, backups.size))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_import))
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) importLauncher.launch("application/json")
                else showBuiltinBackups(backups)
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showBuiltinBackups(backups: List<MemoStore.BackupItem>) {
        val names = backups.map { item ->
            "${item.levelIcon} ${item.timeStr} · ${item.cardCount} · ${item.sizeKB}KB"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_pick_backup, backups.size))
            .setItems(names) { _, which ->
                val selected = backups[which]
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.import_confirm))
                    .setMessage(getString(
                        R.string.import_confirm_msg,
                        "${selected.levelIcon} ${selected.level}",
                        selected.timeStr,
                        selected.cardCount,
                        selected.sizeKB
                    ))
                    .setPositiveButton(getString(R.string.import_restore)) { _, _ ->
                        val success = MemoStore.restoreFromBackup(this, selected.file)
                        if (success) {
                            AlertDialog.Builder(this)
                                .setTitle(getString(R.string.import_restore_ok))
                                .setMessage(getString(R.string.import_restore_ok_msg))
                                .setPositiveButton(getString(R.string.common_ok)) { _, _ -> finishAffinity() }
                                .setCancelable(false)
                                .show()
                        } else {
                            Toast.makeText(this, getString(R.string.import_restore_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }
}