package com.example.memorecite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

object ImageStore {
    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "images")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** 把系统 URI 的图片拷贝到 App 私有目录，返回文件名 */
    fun copyFromUri(ctx: Context, uri: Uri): String? {
        return try {
            val name = "img_${System.currentTimeMillis()}_${(0..99999).random()}.jpg"
            val dest = File(dir(ctx), name)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    /** 把磁盘上的临时文件拷贝到 App 私有目录，返回文件名（服务器用） */
    fun copyFromFile(ctx: Context, source: File): String? {
        return try {
            if (!source.exists() || source.length() == 0L) return null
            val name = "img_${System.currentTimeMillis()}_${(0..99999).random()}.jpg"
            val dest = File(dir(ctx), name)
            source.inputStream().use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 根据文件名获取图片文件
     * （保留 file() 和 loadFile() 两个名字，兼容不同调用）
     */
    fun file(ctx: Context, name: String?): File? {
        if (name.isNullOrBlank()) return null
        val f = File(dir(ctx), name)
        return if (f.exists()) f else null
    }

    /** 别名，等价于 file() */
    fun loadFile(ctx: Context, name: String?): File? = file(ctx, name)

    /**
     * 采样解码图片，避免大图 OOM
     * @param file 图片文件
     * @param reqWidth 期望宽度（像素），传 0 表示不限制
     * @param reqHeight 期望高度（像素），传 0 表示不限制
     */
    fun decodeSampled(file: File, reqWidth: Int = 0, reqHeight: Int = 0): Bitmap? {
        return try {
            if (!file.exists()) return null

            // 先只读边界，不算像素
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)

            // 计算采样率
            var sampleSize = 1
            if (reqWidth > 0 || reqHeight > 0) {
                val w = opts.outWidth
                val h = opts.outHeight
                val targetW = if (reqWidth > 0) reqWidth else w
                val targetH = if (reqHeight > 0) reqHeight else h
                while ((w / sampleSize) > targetW * 2 && (h / sampleSize) > targetH * 2) {
                    sampleSize *= 2
                }
            } else {
                // 没指定就压到最长边 <= 1080
                val maxSide = maxOf(opts.outWidth, opts.outHeight)
                while (maxSide / sampleSize > 1080) {
                    sampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context, name: String?) {
        if (name.isNullOrBlank()) return
        File(dir(ctx), name).takeIf { it.exists() }?.delete()
    }
}