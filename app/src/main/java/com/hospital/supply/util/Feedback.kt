package com.hospital.supply.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 按钮操作的触/听觉反馈：短促提示音 + 轻震动。
 *
 * 提示音用系统内置 [ToneGenerator]，不引入任何音频资源文件：
 *   - 加一  → TONE_PROP_BEEP 1200Hz / 35ms（高、脆）
 *   - 减一  → TONE_PROP_BEEP2  660Hz / 35ms（低、钝）
 *   - 到 0  → TONE_SUP_ERROR 长一点的双音，明显区别于上面两个
 * 音走的 STREAM_MUSIC，跟随媒体音量； vibrate 与 tone 全部 try 兜底，
 * 机型静音策略 / 无振动马达都不会影响计数主流程。
 */
class Feedback(context: Context) {
    private val app = context.applicationContext

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (t: Throwable) {
        null
    }

    private val tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    } catch (t: Throwable) {
        null
    }

    /** 计数 +1 */
    fun plus() {
        tone(ToneGenerator.TONE_PROP_BEEP, 35)
        buzz(20)
    }

    /** 计数 -1（长按触发） */
    fun minus() {
        tone(ToneGenerator.TONE_PROP_BEEP2, 35)
        buzz(25)
    }

    /** 已经是 0，减不动了 */
    fun blocked() {
        tone(ToneGenerator.TONE_SUP_ERROR, 150)
        buzz(45)
    }

    /** 通用轻震，用于提示类操作 */
    fun tap() {
        buzz(20)
    }

    fun release() {
        runCatching { tones?.release() }
    }

    private fun tone(type: Int, ms: Int) {
        runCatching { tones?.startTone(type, ms) }
    }

    private fun buzz(ms: Long) {
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private companion object {
        /** 0–100，占 STREAM_MUSIC 满音量的比例 */
        const val TONE_VOLUME = 90
    }
}
