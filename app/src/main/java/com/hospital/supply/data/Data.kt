package com.hospital.supply.data

import android.content.Context
import org.json.JSONObject

/** 五种不计费小件 */
object Items {
    val ALL = listOf("压力泵", "三环", "三通", "Y阀", "压力延长管")
}

/** 一个计数按钮 */
data class Btn(
    val id: String,
    val name: String,
    val en: String,
    val color: Long,
    val default: Map<String, Int>
)

/** 一个科室卡片 */
data class Dept(
    val id: String,
    val name: String,
    val tint: Long,
    val accent: Long,
    val accentDark: Long,
    val buttons: List<Btn>
)

object Catalog {
    val DEPTS = listOf(
        Dept(
            "cardio", "心内科", 0xFFFFF0EC, 0xFFE8573F, 0xFFB23A25, listOf(
                // 造影：三环 1、三通 1
                Btn("c_angio", "造影", "ANGIO", 0xFFFF5A47, mapOf(
                    "压力泵" to 0, "三环" to 1, "三通" to 1, "Y阀" to 0, "压力延长管" to 0)),
                // 治疗：五种小件各 1
                Btn("c_tx", "治疗", "THERAPY", 0xFFF0932B, mapOf(
                    "压力泵" to 1, "三环" to 1, "三通" to 1, "Y阀" to 1, "压力延长管" to 1))
            )
        ),
        Dept(
            "neuro", "神经科", 0xFFEAF3FC, 0xFF2E86C8, 0xFF1B5E8F, listOf(
                // 造影：Y阀 1
                Btn("n_angio", "造影", "ANGIO", 0xFF2FA3E0, mapOf(
                    "压力泵" to 0, "三环" to 0, "三通" to 0, "Y阀" to 1, "压力延长管" to 0)),
                // 治疗：压力泵 1、Y阀 3
                Btn("n_tx", "治疗", "THERAPY", 0xFF22B07D, mapOf(
                    "压力泵" to 1, "三环" to 0, "三通" to 0, "Y阀" to 3, "压力延长管" to 0))
            )
        ),
        Dept(
            "vasc", "介入血管外科", 0xFFF0EBFC, 0xFF6C5CE7, 0xFF4B3FAD, listOf(
                // 球囊治疗：压力泵 1、Y阀 1
                Btn("v_balloon", "球囊治疗", "BALLOON", 0xFF6C5CE7, mapOf(
                    "压力泵" to 1, "三环" to 0, "三通" to 0, "Y阀" to 1, "压力延长管" to 0)),
                // 栓塞治疗：Y阀 1
                Btn("v_embol", "栓塞治疗", "EMBOLIC", 0xFFE0409E, mapOf(
                    "压力泵" to 0, "三环" to 0, "三通" to 0, "Y阀" to 1, "压力延长管" to 0)),
                // 其他治疗：Y阀 1
                Btn("v_other", "其他治疗", "OTHER", 0xFF0FA3A3, mapOf(
                    "压力泵" to 0, "三环" to 0, "三通" to 0, "Y阀" to 1, "压力延长管" to 0))
            )
        )
    )

    /** 所有按钮（含所属科室） */
    val ALL_BTNS: List<Pair<Btn, Dept>> =
        DEPTS.flatMap { d -> d.buttons.map { b -> b to d } }

    private val byId = ALL_BTNS.associateBy { it.first.id }

    fun btn(id: String): Btn = byId.getValue(id).first
    fun deptOf(id: String): Dept = byId.getValue(id).second

    fun defaultConfig(): Map<String, Map<String, Int>> =
        ALL_BTNS.associate { (b, _) -> b.id to b.default.toMap() }

    fun zeroCounts(): Map<String, Int> = ALL_BTNS.associate { (b, _) -> b.id to 0 }
}

/** 全部状态：每个按钮的台次 + 每个按钮的小件用量配置 + 本台备注 */
data class AppState(
    val counts: Map<String, Int>,
    val config: Map<String, Map<String, Int>>,
    val note: String = ""
) {
    /** 小件累计：Σ(按钮台次 × 该按钮配置用量) */
    fun totals(): Map<String, Int> {
        val t = Items.ALL.associateWith { 0 }.toMutableMap()
        Catalog.ALL_BTNS.forEach { (b, _) ->
            val n = counts[b.id] ?: 0
            if (n != 0) {
                val cfg = config[b.id].orEmpty()
                Items.ALL.forEach { i -> t[i] = (t[i] ?: 0) + n * (cfg[i] ?: 0) }
            }
        }
        return t
    }

    val grandTotal: Int get() = totals().values.sum()

    fun countOf(id: String) = counts[id] ?: 0
    fun cfgOf(id: String): Map<String, Int> = config[id].orEmpty()
    fun cfgValue(id: String, item: String) = config[id]?.get(item) ?: 0

    fun withCount(id: String, v: Int) = copy(counts = counts + (id to v.coerceAtLeast(0)))

    fun withItem(btnId: String, item: String, v: Int) =
        copy(config = config + (btnId to (config[btnId].orEmpty() + (item to v.coerceIn(0, 9)))))

    fun clearedCounts() = copy(counts = Catalog.zeroCounts())
    fun defaultedConfig() = copy(config = Catalog.defaultConfig())
    fun withNote(v: String) = copy(note = v.take(NOTE_MAX))

    companion object {
        /** 备注字数上限，防止无限输入导致存档膨胀 */
        const val NOTE_MAX = 200
    }
}

/** 本地存储：切页面、杀进程都不会丢，只有清空计数才会归零 */
class Store(context: Context) {
    private val sp = context.getSharedPreferences("supply_counter_v1", Context.MODE_PRIVATE)

    fun load(): AppState {
        val counts = Catalog.zeroCounts().toMutableMap()
        val config = Catalog.defaultConfig().mapValues { it.value.toMutableMap() }.toMutableMap()

        sp.getString(KEY_COUNTS, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                counts.keys.forEach { k -> if (o.has(k)) counts[k] = o.optInt(k, 0).coerceAtLeast(0) }
            }
        }
        // 配置结构变更（如小件改名）时，旧存档直接让位给新默认值；计数仍然保留
        if (sp.getInt(KEY_CFG_VER, 0) == CFG_VERSION) {
            sp.getString(KEY_CONFIG, null)?.let { raw ->
                runCatching {
                    val o = JSONObject(raw)
                    config.keys.forEach { bid ->
                        if (o.has(bid)) {
                            val bo = o.getJSONObject(bid)
                            config[bid]?.keys?.forEach { item ->
                                if (bo.has(item)) config[bid]!![item] = bo.optInt(item, 0).coerceIn(0, 9)
                            }
                        }
                    }
                }
            }
        }
        val note = sp.getString(KEY_NOTE, "").orEmpty().take(AppState.NOTE_MAX)
        return AppState(counts, config, note)
    }

    fun save(state: AppState) {
        val co = JSONObject()
        state.counts.forEach { (k, v) -> co.put(k, v) }
        val go = JSONObject()
        state.config.forEach { (bid, m) ->
            val o = JSONObject()
            m.forEach { (item, v) -> o.put(item, v) }
            go.put(bid, o)
        }
        sp.edit()
            .putString(KEY_COUNTS, co.toString())
            .putString(KEY_CONFIG, go.toString())
            .putInt(KEY_CFG_VER, CFG_VERSION)
            .putString(KEY_NOTE, state.note)
            .apply()
    }

    private companion object {
        const val KEY_COUNTS = "counts"
        const val KEY_CONFIG = "config"
        const val KEY_NOTE = "note"
        const val KEY_CFG_VER = "cfg_ver"
        /** 小件清单或默认用量结构变更时 +1：旧存档配置自动重置为默认值 */
        const val CFG_VERSION = 2
    }
}
