package com.example.memorecite

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object MemoStore {
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<MemoGroup>>() {}.type

    private fun file(ctx: Context) = File(ctx.filesDir, "memo_data.json")

    fun load(ctx: Context): MutableList<MemoGroup> {
        val f = file(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            gson.fromJson<MutableList<MemoGroup>>(f.readText(), type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(ctx: Context, groups: List<MemoGroup>) {
        file(ctx).writeText(gson.toJson(groups))
    }
}