package com.hospital.supply.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一键截图，存成 PNG 到相册目录 Pictures/耗材统计。
 *
 * 两条路径：
 *  1. [captureContent]（首选，**长图**）：把整段 Compose 内容在离屏重新渲染一遍再画到 Bitmap，
 *     所以能拿到滚出屏幕的全部内容，也不受屏幕尺寸限制。
 *  2. [capture]（兜底，整屏）：[PixelCopy] 截 window surface，只能截当前屏幕可见区域。
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

    /** 单张图片允许的像素上限，超过就等比缩小，防止 OOM */
    private const val MAX_PIXELS = 40_000_000L

    /**
     * 长图截图：把 [content] 在离屏的 [ComposeView] 里按「宽度固定、高度不限」重绘一遍，
     * 因此滚动到屏幕外的内容也会被完整画出来。
     *
     * 注意：[content] 里不要出现 `fillMaxSize()`——离屏时高度约束是 Unlimited，
     * 撑到无限高会直接崩。需要整页时用 `fillMaxWidth()` 即可。
     *
     * @param width 目标宽度（像素），一般传屏幕宽
     */
    fun captureContent(
        activity: Activity,
        width: Int,
        content: @Composable () -> Unit,
        onResult: (ok: Boolean, detail: String) -> Unit
    ) {
        val decor = activity.window?.decorView as? ViewGroup
        if (decor == null || width <= 0) {
            onResult(false, "窗口未就绪")
            return
        }

        val view = ComposeView(activity).apply {
            // 屏幕上完全透明（用户看不到），但仍然参与布局与绘制
            alpha = 0f
            layoutParams = ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setContent(content)
        }
        decor.addView(view)

        var tries = 0

        fun finish(ok: Boolean, detail: String) {
            view.post { runCatching { decor.removeView(view) } }
            onResult(ok, detail)
        }

        fun attempt() {
            // 高度 UNSPECIFIED → verticalScroll 的 Column 会把全部内容铺开，而不是只铺一屏
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val w = view.measuredWidth
            val h = view.measuredHeight
            if (w <= 0 || h <= 0) {
                // composition 还没跑出第一帧，等下一帧再取
                if (++tries < 8) {
                    view.post { attempt() }
                } else {
                    finish(false, "内容尚未渲染完成")
                }
                return
            }

            val scale = if (w.toLong() * h > MAX_PIXELS) 0.5f else 1f
            val bw = (w * scale).toInt().coerceAtLeast(1)
            val bh = (h * scale).toInt().coerceAtLeast(1)
            val bmp = try {
                Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            } catch (t: Throwable) {
                null
            }
            if (bmp == null) {
                finish(false, "内存不足，无法生成长图")
                return
            }

            try {
                view.layout(0, 0, w, h)
                val canvas = Canvas(bmp)
                if (scale != 1f) canvas.scale(scale, scale)
                view.draw(canvas)
            } catch (t: Throwable) {
                finish(false, "绘制失败：${t.message}")
                return
            }

            val name = fileName()
            val uri = save(activity.applicationContext, bmp, name)
            if (uri == null) finish(false, "保存失败，检查存储空间或权限")
            else finish(true, "已保存到相册 · $ALBUM/$name")
        }

        view.post { attempt() }
    }

    /** 兜底方案：整屏截图（只含当前屏幕可见区域） */
    fun capture(
        activity: Activity,
        onResult: (ok: Boolean, detail: String) -> Unit
    ) {
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

        val loc = IntArray(2)
        decor.getLocationInWindow(loc)
        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            onResult(false, "内存不足，无法截图")
            return
        }

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
    private fun save(ctx: Context, bmp: Bitmap, name: String): Uri? {
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

    private fun fileName(): String {
        val s = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        return "耗材统计_$s.png"
    }
}
