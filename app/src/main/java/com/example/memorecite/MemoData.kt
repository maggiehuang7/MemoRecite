package com.example.memorecite

import java.util.UUID

data class MemoCard(
    val id: String = UUID.randomUUID().toString(),
    val front: String = "",
    val back: String = "",
    val imageFile: String? = null,
    var reviewLevel: Int = 0,
    var easeFactor: Double = 2.5,
    var lastReviewTime: Long = 0L,
    var nextReviewTime: Long = 0L,
    val createdAt: Long = 0L,
    val customIntervals: List<Long>? = null
) {
    val isNew: Boolean get() = lastReviewTime == 0L
    fun isDue(now: Long = System.currentTimeMillis()): Boolean =
        !isNew && nextReviewTime <= now
    val hasImage: Boolean get() = !imageFile.isNullOrBlank()

    fun getEffectiveIntervals(groupIntervals: List<Long>): List<Long> =
        customIntervals ?: groupIntervals
}

data class MemoGroup(
    val id: String = UUID.randomUUID().toString(),
    /** 🟢 父组 ID（null = 顶级组） */
    val parentId: String? = null,
    val name: String = "",
    val icon: String = "📚",
    // 🟢 所有设置改为可空，null = 继承父级
    val firstDelay: Long? = null,
    val intervals: List<Long>? = null,
    val batchSize: Int? = null,
    val startAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // 🟢 卡片只属于这个组自己
    val cards: MutableList<MemoCard> = mutableListOf()
) {
    // ========== 继承链查找 ==========

    /** 获取有效的 firstDelay（向上继承） */
    fun effectiveFirstDelay(allGroups: List<MemoGroup>): Long {
        var current: MemoGroup? = this
        var guard = 0
        while (current != null && guard < 100) {
            current.firstDelay?.let { return it }
            current = current.parentGroup(allGroups)
            guard++
        }
        return 1L  // 全局默认
    }

    fun effectiveIntervals(allGroups: List<MemoGroup>): List<Long> {
        var current: MemoGroup? = this
        var guard = 0
        while (current != null && guard < 100) {
            current.intervals?.let { if (it.isNotEmpty()) return it }
            current = current.parentGroup(allGroups)
            guard++
        }
        return Prefs.DEFAULT_INTERVALS
    }

    fun effectiveBatchSize(allGroups: List<MemoGroup>): Int {
        var current: MemoGroup? = this
        var guard = 0
        while (current != null && guard < 100) {
            current.batchSize?.let { if (it > 0) return it }
            current = current.parentGroup(allGroups)
            guard++
        }
        return 20  // 全局默认
    }

    fun effectiveStartAt(allGroups: List<MemoGroup>): Long {
        var current: MemoGroup? = this
        var guard = 0
        while (current != null && guard < 100) {
            current.startAt?.let { return it }
            current = current.parentGroup(allGroups)
            guard++
        }
        return 0L
    }

    fun isStarted(allGroups: List<MemoGroup>, now: Long = System.currentTimeMillis()): Boolean {
        val startAt = effectiveStartAt(allGroups)
        return startAt == 0L || startAt <= now
    }

    /** 找父组 */
    fun parentGroup(allGroups: List<MemoGroup>): MemoGroup? {
        return parentId?.let { pid -> allGroups.firstOrNull { it.id == pid } }
    }

    /** 获取所有子组 */
    fun childGroups(allGroups: List<MemoGroup>): List<MemoGroup> {
        return allGroups.filter { it.parentId == this.id }
    }

    /** 获取所有后代组的 ID（含自己） */
    fun allDescendantIds(allGroups: List<MemoGroup>): List<String> {
        val result = mutableListOf(this.id)
        childGroups(allGroups).forEach { child ->
            result.addAll(child.allDescendantIds(allGroups))
        }
        return result
    }

    /** 获取这个组以及所有后代的所有卡片 */
    fun allCardsRecursive(allGroups: List<MemoGroup>): List<MemoCard> {
        val result = mutableListOf<MemoCard>()
        result.addAll(this.cards)
        childGroups(allGroups).forEach { child ->
            result.addAll(child.allCardsRecursive(allGroups))
        }
        return result
    }

    /** 从根到当前组的面包屑路径 */
    fun breadcrumbPath(allGroups: List<MemoGroup>): List<MemoGroup> {
        val path = mutableListOf<MemoGroup>()
        var current: MemoGroup? = this
        var guard = 0
        while (current != null && guard < 100) {
            path.add(0, current)
            current = current.parentGroup(allGroups)
            guard++
        }
        return path
    }
}