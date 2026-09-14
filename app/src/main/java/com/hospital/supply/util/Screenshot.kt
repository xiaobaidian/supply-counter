package com.hospital.supply.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片落盘工具：把 Bitmap 存成 PNG 到相册目录 Pictures/耗材统计。
 *
 * 实际的图由 [SummaryImage] 用原生 Canvas 画出来（手绘汇总图）。
 * 早期试过两条路，都不好用，勿再引入：
 *  1. `PixelCopy` 截 window surface → 只能截屏幕可见区域，长内容拿不全；
 *  2. 离屏 `ComposeView` 重绘整页 → **真机一点就崩**（Android 也没有第三方可调用的
 *     系统长截图 API：API 31 的 ScrollCapture 是给系统截图工具调用的回调，方向相反）。
 *
 * 存储：
 *   - Android 10(Q) 及以上 → MediaStore + RELATIVE_PATH，不需要任何权限
 *   - Android 8/9（26–28）  → 公共 Pictures 目录写文件，需要 WRITE_EXTERNAL_STORAGE
 */
object Screenshot {
    const val ALBUM = "耗材统计"

    /** 是否需要老系统存储权限 */
    val needsLegacyPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    @Suppress("DEPRECATION")
    internal fun save(ctx: Context, bmp: Bitmap, name: String): Uri? {
        return try {
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
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    ALBUM
                )
                if (!dir.exists() && !dir.mkdirs()) return null
                val f = File(dir, name)
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaStore.Images.Media.insertImage(ctx.contentResolver, f.absolutePath, name, null)
                Uri.fromFile(f)
            }
        } catch (t: Throwable) {
            null
        }
    }

    internal fun fileName(): String {
        val s = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        return "耗材统计_$s.png"
    }
}
