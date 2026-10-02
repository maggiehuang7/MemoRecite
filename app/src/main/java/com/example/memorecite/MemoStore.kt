package com.example.memorecite

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MemoStore {
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<MemoGroup>>() {}.type

    private const val MAIN_FILE = "memo_data.json"
    private const val TEMP_FILE = "memo_data.json.tmp"
    private const val LOCK_FILE = "memo_data.lock"

    private const val KEEP_HOURLY = 24
    private const val KEEP_DAILY = 30
    private const val KEEP_WEEKLY = 10
    private const val KEEP_MONTHLY = 12

    @Volatile
    private var lastSavedHash: String = ""

    private fun mainFile(ctx: Context) = File(ctx.filesDir, MAIN_FILE)
    private fun tempFile(ctx: Context) = File(ctx.filesDir, TEMP_FILE)
    private fun lockFile(ctx: Context) = File(ctx.filesDir, LOCK_FILE)

    private fun backupRoot(ctx: Context): File {
        val d = File(ctx.filesDir, "backups")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun subDir(ctx: Context, name: String): File {
        val d = File(backupRoot(ctx), name)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun hourlyDir(ctx: Context) = subDir(ctx, "hourly")
    private fun dailyDir(ctx: Context) = subDir(ctx, "daily")
    private fun weeklyDir(ctx: Context) = subDir(ctx, "weekly")
    private fun monthlyDir(ctx: Context) = subDir(ctx, "monthly")
    private fun snapshotDir(ctx: Context) = subDir(ctx, "snapshots")

    // ========== 加载 ==========

    fun load(ctx: Context): MutableList<MemoGroup> {
        recoverFromCrash(ctx)

        val f = mainFile(ctx)
        if (!f.exists() || f.length() == 0L) {
            return tryRestoreFromBestBackup(ctx)
        }

        return try {
            val json = f.readText()
            val data = gson.fromJson<MutableList<MemoGroup>>(json, type) ?: mutableListOf()
            lastSavedHash = md5(json)

            if (data.isEmpty() && json.length > 100) {
                val restored = tryRestoreFromBestBackup(ctx)
                if (restored.isNotEmpty()) return restored
            }

            ensureDailyBackup(ctx, f)
            data
        } catch (e: Exception) {
            tryRestoreFromBestBackup(ctx)
        }
    }

    private fun ensureDailyBackup(ctx: Context, src: File) {
        try {
            val today = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
            val hasToday = dailyDir(ctx).listFiles()?.any {
                SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(it.lastModified())) == today
            } ?: false

            if (!hasToday) {
                val json = src.readText()
                val cardCount = try {
                    gson.fromJson<MutableList<MemoGroup>>(json, type)?.sumOf { it.cards.size } ?: 0
                } catch (e: Exception) { 0 }
                if (cardCount > 0) {
                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                    val name = "memo_d_${cardCount}c_${sdf.format(Date())}_${md5(json).take(8)}.json"
                    src.copyTo(File(dailyDir(ctx), name), overwrite = true)
                }
            }
        } catch (_: Exception) {}
    }

    // ========== 保存 ==========

    fun save(ctx: Context, groups: List<MemoGroup>) {
        try {
            val json = gson.toJson(groups)
            val newHash = md5(json)

            if (newHash == lastSavedHash) return

            val main = mainFile(ctx)
            val tmp = tempFile(ctx)

            val newCardCount = groups.sumOf { it.cards.size }
            val oldCardCount = if (main.exists()) {
                try {
                    val old = gson.fromJson<MutableList<MemoGroup>>(main.readText(), type)
                    old?.sumOf { it.cards.size } ?: 0
                } catch (e: Exception) { 0 }
            } else 0

            if (oldCardCount > 10 && newCardCount in 1..(oldCardCount * 3 / 10)) {
                makeBackup(ctx, main, oldCardCount, "refused")
                return
            }

            if (main.exists() && oldCardCount > 0) {
                makeBackup(ctx, main, oldCardCount, "auto")
            }

            lockFile(ctx).writeText(System.currentTimeMillis().toString())
            tmp.writeText(json)

            if (tmp.readText() != json) {
                tmp.delete()
                lockFile(ctx).delete()
                return
            }

            if (main.exists()) main.delete()
            tmp.renameTo(main)
            lockFile(ctx).delete()

            lastSavedHash = newHash

            if (newCardCount > 0) {
                makeBackup(ctx, main, newCardCount, "after")
            }
        } catch (e: Exception) {
            try {
                tempFile(ctx).delete()
                lockFile(ctx).delete()
            } catch (_: Exception) {}
        }
    }

    // ========== 崩溃恢复 ==========

    private fun recoverFromCrash(ctx: Context) {
        try {
            val lock = lockFile(ctx)
            if (!lock.exists()) return

            val tmp = tempFile(ctx)
            val main = mainFile(ctx)

            if (tmp.exists() && tmp.length() > 0) {
                try {
                    val json = tmp.readText()
                    val data = gson.fromJson<MutableList<MemoGroup>>(json, type)
                    if (!data.isNullOrEmpty()) {
                        main.writeText(json)
                        lastSavedHash = md5(json)
                    }
                } catch (_: Exception) {}
            }

            tmp.delete()
            lock.delete()
        } catch (_: Exception) {}
    }

    // ========== 多级备份 ==========

    private fun makeBackup(ctx: Context, src: File, cardCount: Int, tag: String) {
        try {
            val now = System.currentTimeMillis()
            val json = src.readText()
            val hash = md5(json).take(8)

            backupToLevel(hourlyDir(ctx), src, now, "h", cardCount, hash)
            backupToLevel(dailyDir(ctx), src, now, "d", cardCount, hash)
            backupToLevel(weeklyDir(ctx), src, now, "w", cardCount, hash)
            backupToLevel(monthlyDir(ctx), src, now, "m", cardCount, hash)

            cleanupLevel(hourlyDir(ctx), KEEP_HOURLY)
            cleanupLevel(dailyDir(ctx), KEEP_DAILY)
            cleanupLevel(weeklyDir(ctx), KEEP_WEEKLY)
            cleanupLevel(monthlyDir(ctx), KEEP_MONTHLY)
        } catch (_: Exception) {}
    }

    private fun backupToLevel(
        dir: File, src: File, timestamp: Long,
        prefix: String, cardCount: Int, hash: String
    ) {
        try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
            val name = "memo_${prefix}_${cardCount}c_${sdf.format(Date(timestamp))}_$hash.json"
            val dst = File(dir, name)

            if (!dst.exists()) {
                src.copyTo(dst, overwrite = true)
            }

            dedupLevel(dir, prefix)
        } catch (_: Exception) {}
    }

    private fun dedupLevel(dir: File, prefix: String) {
        try {
            val files = dir.listFiles()?.filter { it.name.startsWith("memo_${prefix}_") } ?: return

            val grouped = mutableMapOf<String, MutableList<File>>()
            files.forEach { f ->
                val nameTime = parseNameTime(f.name) ?: return@forEach
                val key = getTimeKey(nameTime, prefix)
                grouped.getOrPut(key) { mutableListOf() }.add(f)
            }

            grouped.forEach { (_, list) ->
                if (list.size > 1) {
                    list.sortedByDescending { parseNameTime(it.name) ?: 0L }
                        .drop(1)
                        .forEach { it.delete() }
                }
            }
        } catch (_: Exception) {}
    }

    private fun getTimeKey(time: Long, prefix: String): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        return when (prefix) {
            "h" -> SimpleDateFormat("yyyyMMddHH", Locale.CHINA).format(Date(time))
            "d" -> SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(time))
            "w" -> {
                val week = cal.get(Calendar.WEEK_OF_YEAR)
                val year = cal.get(Calendar.YEAR)
                "$year-W$week"
            }
            "m" -> SimpleDateFormat("yyyyMM", Locale.CHINA).format(Date(time))
            else -> SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date(time))
        }
    }

    private fun parseNameTime(name: String): Long? {
        return try {
            val match = Regex("""_(\d{8}_\d{6})_""").find(name)
            if (match != null) {
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                sdf.parse(match.groupValues[1])?.time
            } else null
        } catch (_: Exception) { null }
    }

    private fun cleanupLevel(dir: File, keep: Int) {
        try {
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            files.drop(keep).forEach { it.delete() }
        } catch (_: Exception) {}
    }

    // ========== 快照 ==========

    fun createSnapshot(ctx: Context, label: String) {
        try {
            val src = mainFile(ctx)
            if (!src.exists() || src.length() == 0L) return

            val json = src.readText()
            val cardCount = try {
                gson.fromJson<MutableList<MemoGroup>>(json, type)?.sumOf { it.cards.size } ?: 0
            } catch (e: Exception) { 0 }

            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
            val safeLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(20)
            val name = "snapshot_${safeLabel}_${cardCount}c_${sdf.format(Date())}.json"
            src.copyTo(File(snapshotDir(ctx), name), overwrite = true)
        } catch (_: Exception) {}
    }

    // ========== 恢复 ==========

    private fun tryRestoreFromBestBackup(ctx: Context): MutableList<MemoGroup> {
        try {
            val all = listAllBackupFiles(ctx)
            for (f in all) {
                try {
                    val json = f.readText()
                    val data = gson.fromJson<MutableList<MemoGroup>>(json, type)
                    if (!data.isNullOrEmpty() && data.any { it.cards.isNotEmpty() }) {
                        mainFile(ctx).writeText(json)
                        lastSavedHash = md5(json)
                        return data
                    }
                } catch (_: Exception) { continue }
            }
        } catch (_: Exception) {}
        return mutableListOf()
    }

    fun restoreFromBackup(ctx: Context, backupFile: File): Boolean {
        return try {
            if (!backupFile.exists()) return false
            val json = backupFile.readText()
            val data = gson.fromJson<MutableList<MemoGroup>>(json, type)
            if (data.isNullOrEmpty()) return false

            createSnapshot(ctx, "before_restore")
            mainFile(ctx).writeText(json)
            lastSavedHash = md5(json)
            true
        } catch (_: Exception) { false }
    }

    // ========== 列出备份 ==========

    data class BackupItem(
        val file: File,
        val level: String,
        val levelIcon: String,
        val timeStr: String,
        val cardCount: Int,
        val sizeKB: Long
    )

    fun listBackups(ctx: Context): List<BackupItem> {
        val items = mutableListOf<BackupItem>()

        snapshotDir(ctx).listFiles()?.forEach { items.add(parseBackup(it, "快照", "📸")) }
        hourlyDir(ctx).listFiles()?.forEach { items.add(parseBackup(it, "小时", "⏱")) }
        dailyDir(ctx).listFiles()?.forEach { items.add(parseBackup(it, "日", "📅")) }
        weeklyDir(ctx).listFiles()?.forEach { items.add(parseBackup(it, "周", "📆")) }
        monthlyDir(ctx).listFiles()?.forEach { items.add(parseBackup(it, "月", "🗓")) }

        val order = mapOf("快照" to 0, "小时" to 1, "日" to 2, "周" to 3, "月" to 4)
        return items.sortedWith(
            compareBy<BackupItem> { order[it.level] ?: 99 }
                .thenByDescending { it.file.lastModified() }
        )
    }

    private fun parseBackup(f: File, level: String, icon: String): BackupItem {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return BackupItem(
            file = f,
            level = level,
            levelIcon = icon,
            timeStr = sdf.format(Date(f.lastModified())),
            cardCount = extractCardCount(f.name),
            sizeKB = f.length() / 1024
        )
    }

    private fun listAllBackupFiles(ctx: Context): List<File> {
        val all = mutableListOf<File>()
        snapshotDir(ctx).listFiles()?.let { all.addAll(it) }
        hourlyDir(ctx).listFiles()?.let { all.addAll(it) }
        dailyDir(ctx).listFiles()?.let { all.addAll(it) }
        weeklyDir(ctx).listFiles()?.let { all.addAll(it) }
        monthlyDir(ctx).listFiles()?.let { all.addAll(it) }
        return all.sortedByDescending { extractCardCount(it.name) }
    }

    private fun extractCardCount(name: String): Int {
        return try {
            val match = Regex("""_(\d+)c_""").find(name)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    // ========== 🟢 树形工具 ==========

    /** 获取所有顶级组（parentId == null） */
    fun getTopLevelGroups(allGroups: List<MemoGroup>): List<MemoGroup> {
        return allGroups.filter { it.parentId == null }
    }

    /** 获取指定父组的直接子组 */
    fun getChildGroups(allGroups: List<MemoGroup>, parentId: String?): List<MemoGroup> {
        return allGroups.filter { it.parentId == parentId }
    }

    /** 删除组（含所有后代） */
    fun deleteGroupRecursive(ctx: Context, allGroups: MutableList<MemoGroup>, groupId: String) {
        val group = allGroups.firstOrNull { it.id == groupId } ?: return
        val idsToDelete = group.allDescendantIds(allGroups).toSet()

        // 删除所有图片
        allGroups.filter { idsToDelete.contains(it.id) }
            .flatMap { it.cards }
            .forEach { ImageStore.delete(ctx, it.imageFile) }

        allGroups.removeAll { idsToDelete.contains(it.id) }
    }
}