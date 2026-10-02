package com.example.memorecite

import android.content.Context
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import java.io.File

class PCWebServer(
    private val appContext: Context,
    private val defaultGroupName: String,
    private val onUploaded: (Int) -> Unit,
    port: Int = 8080
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" -> servePage()
                session.method == Method.GET && session.uri == "/groups" -> serveGroups()
                session.method == Method.POST && session.uri == "/upload" -> serveUpload(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/plain", "not found"
                )
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain; charset=utf-8",
                "服务器错误: ${e.message}"
            )
        }
    }

    private fun servePage(): Response {
        val html = try {
            appContext.resources.openRawResource(R.raw.upload_page)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (e: Exception) {
            "<h1>页面加载失败: ${e.message}</h1>"
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            html
        )
    }

    private fun serveGroups(): Response {
        val groups = MemoStore.load(appContext)
        val now = System.currentTimeMillis()
        val sb = StringBuilder("[")
        groups.forEachIndexed { i, g ->
            if (i > 0) sb.append(",")
            val allCards = g.allCardsRecursive(groups)
            val dueCount = allCards.count { it.isDue(now) }
            val newCount = allCards.count { it.isNew }
            val mastered = allCards.count { it.reviewLevel >= 5 }
            sb.append("{")
            sb.append("\"id\":\"").append(g.id).append("\",")
            sb.append("\"name\":\"")
                .append(g.name.replace("\\", "\\\\").replace("\"", "\\\""))
                .append("\",")
            sb.append("\"icon\":\"").append(g.icon).append("\",")
            sb.append("\"total\":").append(allCards.size).append(",")
            sb.append("\"due\":").append(dueCount).append(",")
            sb.append("\"new\":").append(newCount).append(",")
            sb.append("\"mastered\":").append(mastered).append(",")
            sb.append("\"createdAt\":").append(g.createdAt).append(",")
            sb.append("\"startAt\":").append(g.effectiveStartAt(groups))
            sb.append("}")
        }
        sb.append("]")
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            sb.toString()
        )
    }

    private fun serveUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)

        val textBase64 = session.parameters["text_b64"]?.firstOrNull() ?: ""
        val text = if (textBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(textBase64, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            } catch (e: Exception) { "" }
        } else {
            session.parameters["text"]?.firstOrNull() ?: ""
        }

        val groupBase64 = session.parameters["group_b64"]?.firstOrNull() ?: ""
        val selectedGroup = if (groupBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(groupBase64, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8).trim()
            } catch (e: Exception) { "" }
        } else {
            session.parameters["group"]?.firstOrNull()?.trim().orEmpty()
        }
        val targetGroupName = if (selectedGroup.isBlank()) defaultGroupName else selectedGroup

        val imageMap = mutableMapOf<Int, String>()
        files.entries
            .filter { it.key.startsWith("img_") }
            .forEach { entry ->
                val lineNum = entry.key.removePrefix("img_").toIntOrNull() ?: return@forEach
                val copied = ImageStore.copyFromFile(appContext, File(entry.value))
                if (copied != null) imageMap[lineNum] = copied
            }

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return jsonResponse("""{"ok":false,"error":"没有文字内容"}""")
        }

        val groups = MemoStore.load(appContext)
        var group = groups.firstOrNull { it.name == targetGroupName }
        if (group == null) {
            group = MemoGroup(
                name = targetGroupName,
                icon = "💻",
                createdAt = System.currentTimeMillis()
            )
            groups.add(group)
        }

        // 🟢 用继承的 firstDelay
        val effectiveDelay = group.effectiveFirstDelay(groups)
        val now = System.currentTimeMillis()

        var count = 0
        var imageUsed = 0
        lines.forEachIndexed { index, line ->
            val lineNum = index + 1
            val imageFile = imageMap[lineNum]
            if (imageFile != null) imageUsed++

            val card = if (line.contains("|")) {
                val parts = line.split("|", limit = 2)
                MemoCard(
                    front = parts[0].trim(),
                    back = parts[1].trim(),
                    imageFile = imageFile,
                    createdAt = now
                )
            } else {
                MemoCard(
                    back = line,
                    imageFile = imageFile,
                    createdAt = now
                )
            }
            EbbinghausScheduler.initNewCard(card, effectiveDelay)
            group.cards.add(card)
            count++
        }

        MemoStore.save(appContext, groups)
        AlarmScheduler.scheduleNext(appContext)
        onUploaded(count)

        val safeName = targetGroupName.replace("\\", "\\\\").replace("\"", "\\\"")
        return jsonResponse(
            """{"ok":true,"count":$count,"images":$imageUsed,"group":"$safeName"}"""
        )
    }

    private fun jsonResponse(json: String): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            json
        )
    }
}