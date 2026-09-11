package com.hospital.supply.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一键截图：把当前页面整屏存成 PNG 到相册目录。
 *
 * 为什么不用 View.drawToBitmap()：Compose 走 RenderNode 硬件渲染，软件重绘容易截出空白，
 * 官方推荐 [PixelCopy]（API 24+，本项目 minSdk 26，放心用）。
 *
 * 存储：
 *   - Android 10(Q) 及以上 → MediaStore + RELATIVE_PATH，不需要任何权限
 *   - Android 8/9（26–28）  → 公共 Pictures 目录写文件，需要 WRITE_EXTERNAL_STORAGE
 * 目标目录：Pictures/耗材统计
 */
object Screenshot {
    const val ALBUM = "耗材统计"

    /** 是否需要老系统存储权限 */
    val needsLegacyPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun capture(activity: Activity, onResult: (ok: Boolean, detail: String) -> Unit) {
        val window = activity.window ?: run {
            onResult(false, "窗口未就绪")
            return
        }
        val decor = window.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) {
            onResult(false, "页面尚未绘制完成")
            return
        }
        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            onResult(false, "内存不足，无法截图")
            return
        }

        val loc = IntArray(2)
        decor.getLocationInWindow(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)

        try {
            PixelCopy.request(
                window, rect, bitmap,
                { res ->
                    if (res != PixelCopy.SUCCESS) {
                        onResult(false, "截屏失败（code $res）")
                        return@request
                    }
                    val name = fileName()
                    val uri = save(activity.applicationContext, bitmap, name)
                    if (uri == null) onResult(false, "保存失败，检查存储空间或权限")
                    else onResult(true, "已保存到相册 · $ALBUM/$name")
                },
                Handler(Looper.getMainLooper())
            )
        } catch (t: Throwable) {
            onResult(false, "截屏异常：${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun save(ctx: Context, bmp: Bitmap, name: String): android.net.Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                ?: return null
            ctx.contentResolver.openOutputStream(uri).use { out ->
                if (out == null) return null
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, done, null, null)
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            if (!dir.exists() && !dir.mkdirs()) return null
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MediaStore.Images.Media.insertImage(ctx.contentResolver, f.absolutePath, name, null)
            android.net.Uri.fromFile(f)
        }
    } catch (t: Throwable) {
        null
    }

    private fun fileName(): String {
        val s = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        return "耗材统计_$s.png"
    }
}
