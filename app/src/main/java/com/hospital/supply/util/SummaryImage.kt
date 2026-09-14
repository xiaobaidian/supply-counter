package com.hospital.supply.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.hospital.supply.data.AppState
import com.hospital.supply.data.Catalog
import com.hospital.supply.data.Dept
import com.hospital.supply.data.Items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「导出汇总图」：用原生 Canvas 直接画出一张竖版图片（宽 1080），存进相册。
 *
 * 为什么不复用页面渲染（Compose 离屏重绘 / PixelCopy）：
 *   - PixelCopy 只能截屏幕可见区域，屏幕外的内容拿不到；
 *   - 离屏重绘整页在本机实测会崩，且 Android 没有第三方可调用的系统长截图 API
 *     （API 31 的 ScrollCapture 是「供系统截图工具调用」的回调，方向相反）。
 * 手绘是纯软件 2D 绘制，不触碰渲染管线，也没有滚动/拼接时序问题，最稳。
 * 输出的是一张「小件用量记录单」，比 UI 截图更适合发微信、存档。
 *
 * 画布固定 1080 宽、1dp = 3px；先画到一张足够高的画布上，最后按实际内容高度裁掉多余部分。
 */
object SummaryImage {

    private const val W = 1080
    private const val S = 3f                 // 1dp 对应的像素数
    private const val MAX_H = 5200           // 预分配高度上限，画完再裁
    private const val PAD = 16f              // 页面左右边距（dp）
    private const val GAP = 14f              // 卡片间距（dp）
    private const val RADIUS = 20f           // 卡片圆角（dp）

    private const val BG = 0xFFE9ECF0.toInt()
    private const val CARD = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF12181F.toInt()
    private const val INK2 = 0xFF5A6672.toInt()
    private const val INK3 = 0xFF93A0AC.toInt()
    private const val STAT_TOP = 0xFF1C2530.toInt()
    private const val STAT_BOTTOM = 0xFF101720.toInt()
    private const val ON_STAT = 0xFF8FA3B6.toInt()

    private fun px(dp: Float) = dp * S

    private fun paint(dp: Float, bold: Boolean, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px(dp)
            this.color = color
            textAlign = align
            typeface = Typeface.DEFAULT
            isFakeBoldText = bold
        }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun cardPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
        setShadowLayer(px(3f), 0f, px(1.5f), 0x18000000)
    }

    /** 生成并保存汇总图 */
    fun export(ctx: Context, state: AppState, onResult: (ok: Boolean, detail: String) -> Unit) {
        val bmp = try {
            render(state)
        } catch (t: Throwable) {
            null
        }
        if (bmp == null) {
            onResult(false, "生成汇总图失败")
            return
        }
        val name = Screenshot.fileName()
        val uri = try {
            Screenshot.save(ctx, bmp, name)
        } catch (t: Throwable) {
            null
        }
        if (uri == null) onResult(false, "保存失败，检查存储空间或权限")
        else onResult(true, "已保存到相册 · ${Screenshot.ALBUM}/$name")
    }

    private fun render(state: AppState): Bitmap {
        val bmp = Bitmap.createBitmap(W, MAX_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)

        var y = drawHeader(canvas)
        Catalog.DEPTS.forEach { d ->
            y = drawDept(canvas, state, d, y)
        }
        y = drawStat(canvas, state, y)
        y = drawNote(canvas, state, y)
        y = drawFooter(canvas, y)

        val h = (y + px(6f)).toInt().coerceIn(1, MAX_H)
        return Bitmap.createBitmap(bmp, 0, 0, W, h)
    }

    /* ---------------- 头部：标题 + 时间 ---------------- */
    private fun drawHeader(canvas: Canvas): Float {
        val x = px(PAD)
        val y = px(PAD + 6f)
        canvas.drawText("耗材统计", x, y + px(20f), paint(23f, true, INK))
        canvas.drawText(
            "本台手术 · 不计费小件汇总", x, y + px(42f), paint(12f, false, INK2)
        )
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        canvas.drawText(
            stamp, (W - px(PAD)), y + px(16f),
            paint(11.5f, false, INK3, Paint.Align.RIGHT)
        )
        return y + px(58f)
    }

    /* ---------------- 科室卡片 ---------------- */
    private fun drawDept(canvas: Canvas, state: AppState, dept: Dept, top: Float): Float {
        val left = px(PAD)
        val right = W - px(PAD)
        val width = right - left
        val padX = px(15f)
        val padY = px(13f)
        val rowH = px(34f)
        val rowGap = px(8f)

        val rows = dept.buttons.size
        val height = padY * 2 + px(22f) + rows * rowH + (rows - 1) * rowGap
        val rect = RectF(left, top, right, top + height)

        canvas.drawRoundRect(rect, px(RADIUS), px(RADIUS), cardPaint(dept.tint.toInt()))

        // 科室名 + 该科合计台次
        var y = top + padY
        val sum = dept.buttons.sumOf { state.countOf(it.id) }
        canvas.drawText(dept.name, left + padX, y + px(15f), paint(14.5f, true, dept.accentDark.toInt()))
        canvas.drawText(
            "合计 $sum 次", right - padX, y + px(15f),
            paint(11.5f, false, INK2, Paint.Align.RIGHT)
        )

        y += px(22f)
        dept.buttons.forEach { b ->
            val cy = y + rowH / 2f
            // 色块
            val chipSize = px(22f)
            canvas.drawRoundRect(
                RectF(left + padX, cy - chipSize / 2f, left + padX + chipSize, cy + chipSize / 2f),
                px(7f), px(7f), fillPaint(b.color.toInt())
            )
            // 名称（用自定义名，若有）
            val name = state.nameOf(b.id)
            canvas.drawText(
                name, left + padX + chipSize + px(11f), cy + px(5f),
                paint(15f, true, INK)
            )
            // 次数
            val n = state.countOf(b.id)
            canvas.drawText(
                "$n 次", right - padX, cy + px(5f),
                paint(15f, true, if (n > 0) INK else INK3, Paint.Align.RIGHT)
            )
            y += rowH + rowGap
        }
        return top + height + px(GAP)
    }

    /* ---------------- 统计卡 ---------------- */
    private fun drawStat(canvas: Canvas, state: AppState, top: Float): Float {
        val left = px(PAD)
        val right = W - px(PAD)
        val padX = px(17f)
        val padY = px(15f)
        val totals = state.totals()

        val gridTop = padY + px(18f) + px(46f) + px(14f)
        val height = gridTop + px(46f) + padY
        val rect = RectF(left, top, right, top + height)

        val grad = LinearGradient(
            left, top, left, top + height, STAT_TOP, STAT_BOTTOM, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, px(RADIUS), px(RADIUS), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = grad
            setShadowLayer(px(5f), 0f, px(2f), 0x33000000)
        })

        canvas.drawText("小件累计总数", left + padX, top + padY + px(13f), paint(12.5f, true, ON_STAT))
        canvas.drawText(
            "${state.grandTotal}", left + padX, top + padY + px(56f),
            paint(40f, true, 0xFFFFFFFF.toInt())
        )
        val numW = paint(40f, true, 0xFFFFFFFF.toInt()).measureText("${state.grandTotal}")
        canvas.drawText(
            "件", left + padX + numW + px(7f), top + padY + px(56f),
            paint(13f, false, ON_STAT)
        )

        // 五种小件：一行五等分
        val colW = (right - left - padX * 2) / Items.ALL.size
        Items.ALL.forEachIndexed { i, item ->
            val cx = left + padX + colW * i + colW / 2f
            val n = totals[item] ?: 0
            canvas.drawText(
                item, cx, top + gridTop + px(12f),
                paint(11f, false, ON_STAT, Paint.Align.CENTER)
            )
            canvas.drawText(
                "$n", cx, top + gridTop + px(40f),
                paint(21f, true, if (n > 0) 0xFFFFFFFF.toInt() else 0xFF5A6875.toInt(), Paint.Align.CENTER)
            )
        }
        return top + height + px(GAP)
    }

    /* ---------------- 备注卡 ---------------- */
    private fun drawNote(canvas: Canvas, state: AppState, top: Float): Float {
        val left = px(PAD)
        val right = W - px(PAD)
        val padX = px(16f)
        val padY = px(14f)
        val innerW = (right - left - padX * 2).toInt()

        val tp = paint(17f, false, INK)
        val note = state.note.trim()
        val layout = if (note.isEmpty()) null else StaticLayout.Builder
            .obtain(note, 0, note.length, tp, innerW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(px(6f), 1f)
            .setIncludePad(false)
            .build()

        val bodyH = layout?.height?.toFloat() ?: px(24f)
        val height = padY * 2 + px(20f) + bodyH
        val rect = RectF(left, top, right, top + height)
        canvas.drawRoundRect(rect, px(RADIUS), px(RADIUS), cardPaint(CARD))

        canvas.drawText("备注", left + padX, top + padY + px(13f), paint(13f, true, INK))

        if (layout != null) {
            canvas.save()
            canvas.translate(left + padX, top + padY + px(20f))
            layout.draw(canvas)
            canvas.restore()
        } else {
            canvas.drawText(
                "（无备注）", left + padX, top + padY + px(38f), paint(14f, false, INK3)
            )
        }
        return top + height + px(GAP)
    }

    /* ---------------- 底部说明 ---------------- */
    private fun drawFooter(canvas: Canvas, top: Float): Float {
        canvas.drawText(
            "由「耗材统计」生成 · ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())}",
            (W / 2f), top + px(16f),
            paint(10.5f, false, INK3, Paint.Align.CENTER)
        )
        return top + px(34f)
    }
}
