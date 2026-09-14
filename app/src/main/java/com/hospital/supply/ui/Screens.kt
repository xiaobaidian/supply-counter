package com.hospital.supply.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hospital.supply.data.AppState
import com.hospital.supply.data.Btn
import com.hospital.supply.data.Catalog
import com.hospital.supply.data.Items
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 600L
private const val REPEAT_MS = 380L

private fun Modifier.tnum() = this

/* ------------------------------------------------------------------ */
/*  计数按钮：单击 +1，长按 0.6s -1 并可连减                            */
/* ------------------------------------------------------------------ */
@Composable
fun CountButton(
    btn: Btn,
    displayName: String,
    count: Int,
    compact: Boolean,
    onInc: () -> Unit,
    onDec: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = Color(btn.color)
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "press"
    )
    val bump = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun incWithPop() {
        onInc()
        scope.launch {
            bump.snapTo(1.3f)
            bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = if (compact) 84.dp else 92.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp), spotColor = color, ambientColor = color)
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .pointerInput(btn.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pressed = true
                    var longFired = false
                    val job = scope.launch {
                        delay(LONG_PRESS_MS)
                        longFired = true
                        onDec()
                        while (isActive) {
                            delay(REPEAT_MS)
                            onDec()
                        }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.none { it.pressed }) break
                    }
                    job.cancel()
                    pressed = false
                    if (!longFired) incWithPop()
                }
            }
            .padding(horizontal = 13.dp, vertical = 13.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayName,
                color = Color.White,
                fontSize = if (compact) 14.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                lineHeight = 18.sp,
                maxLines = 2
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$count",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        scaleX = bump.value
                        scaleY = bump.value
                    }
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "次",
                    color = Color(0xCCFFFFFF),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  科室卡片                                                           */
/* ------------------------------------------------------------------ */
@Composable
fun DeptCard(state: AppState, dept: com.hospital.supply.data.Dept, onInc: (String) -> Unit, onDec: (String) -> Unit) {
    val accent = Color(dept.accent)
    val accentDark = Color(dept.accentDark)
    val sum = dept.buttons.sumOf { state.countOf(it.id) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0x3312181F))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(dept.tint))
            .padding(start = 17.dp, end = 13.dp, top = 14.dp, bottom = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dept.name,
                color = accentDark,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            if (sum > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("$sum 台次", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "已记录 $sum",
                color = accentDark.copy(alpha = 0.75f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            dept.buttons.forEach { b ->
                CountButton(
                    btn = b,
                    displayName = state.nameOf(b.id),
                    count = state.countOf(b.id),
                    compact = dept.buttons.size > 2,
                    onInc = { onInc(b.id) },
                    onDec = { onDec(b.id) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  底部统计卡片                                                       */
/* ------------------------------------------------------------------ */
@Composable
fun StatCard(state: AppState, onClear: () -> Unit) {
    val totals = state.totals()
    val grand = state.grandTotal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0x99101720))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Palette.StatTop, Palette.StatBottom)))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "小件累计总数",
                color = Color(0xFF8FA3B6),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onClear() }
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text("清空计数", color = Color(0xFFC9D6E2), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Items.ALL.forEach { item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x0FFFFFFF))
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${totals[item] ?: 0}",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = item,
                        color = Color(0xFF93A6B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "TOTAL",
                color = Color(0xFF8FA3B6),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(bottom = 5.dp)
            )
            Spacer(Modifier.weight(1f))
            Text("$grand", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text(
                text = "件",
                color = Color(0xFF8FA3B6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  备注卡片：大字号输入 + 右下角一键截图                                */
/* ------------------------------------------------------------------ */
@Composable
fun NoteCard(
    note: String,
    onNoteChange: (String) -> Unit,
    onShot: () -> Unit,
    capture: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Card)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "备注",
                color = Palette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "随计数一起自动保存",
                color = Palette.Ink3,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(11.dp))

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            textStyle = TextStyle(
                fontSize = 20.sp,          // 大字号，一行约 15 字
                fontWeight = FontWeight.SemiBold,
                color = Palette.Ink,
                lineHeight = 29.sp
            ),
            placeholder = {
                Text(
                    "记录本台特殊情况…",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Palette.Ink3
                )
            },
            shape = RoundedCornerShape(15.dp),
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0x332E86C8),
                unfocusedBorderColor = Color(0x1A12181F),
                focusedContainerColor = Palette.FieldBg,
                unfocusedContainerColor = Palette.FieldBg,
                cursorColor = Color(0xFF2E86C8),
                focusedTextColor = Palette.Ink,
                unfocusedTextColor = Palette.Ink
            )
        )

        // 截图是「离屏重绘整页」，这里直接不渲染本行，图里就干净了
        if (!capture) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "截图含整页内容，不含本行与按钮",
                    color = Palette.Ink3,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 13.dp)
                )
                Spacer(Modifier.weight(1f))
                ShotButton(onClick = onShot)
            }
        }
    }
}

@Composable
private fun ShotButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Ink)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(19.dp)) {
            val line = Stroke(
                width = 2.1.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val bodyTop = size.height * 0.28f
            val bodyH = size.height * 0.60f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, bodyTop),
                size = Size(size.width, bodyH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = line
            )
            // 顶部取景框凸起
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.31f, size.height * 0.12f),
                size = Size(size.width * 0.38f, size.height * 0.17f),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                style = line
            )
            drawCircle(
                color = Color.White,
                radius = size.width * 0.17f,
                center = Offset(size.width * 0.5f, bodyTop + bodyH * 0.5f),
                style = line
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "截图",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/*  主页                                                               */
/* ------------------------------------------------------------------ */
@Composable
fun HomeScreen(
    state: AppState,
    onInc: (String) -> Unit,
    onDec: (String) -> Unit,
    onClear: () -> Unit,
    onNoteChange: (String) -> Unit,
    onShot: () -> Unit,
    capture: Boolean = false
) {
    Column(
        modifier = Modifier
            // 离屏截图时高度是「无限」，绝不能用 fillMaxSize，否则撑到无限高直接崩
            .then(if (capture) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Catalog.DEPTS.forEach { d ->
            DeptCard(state = state, dept = d, onInc = onInc, onDec = onDec)
        }
        StatCard(state = state, onClear = onClear)
        NoteCard(
            note = state.note,
            onNoteChange = onNoteChange,
            onShot = onShot,
            capture = capture
        )
    }
}

/* ------------------------------------------------------------------ */
/*  设置页                                                             */
/* ------------------------------------------------------------------ */
@Composable
fun SettingsScreen(state: AppState, onOpen: (String) -> Unit, onResetConfig: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text("按钮配置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Spacer(Modifier.height(5.dp))
        Text(
            "为每个按钮设定「做一次」所消耗的小件数量。改完立刻生效，主页统计自动重算。",
            fontSize = 12.5.sp,
            color = Palette.Ink2,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(16.dp))

        Catalog.DEPTS.forEach { dept ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(dept.accent))
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    dept.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Palette.Ink2
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x2212181F))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.Card)
            ) {
                dept.buttons.forEachIndexed { idx, b ->
                    val cfg = state.cfgOf(b.id)
                    val shown = Items.ALL.filter { (cfg[it] ?: 0) > 0 }
                        .joinToString(" · ") { "$it${cfg[it]}" }
                        .ifEmpty { "未配置" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(b.id) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(b.color))
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.nameOf(b.id),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Ink,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(shown, fontSize = 11.sp, color = Palette.Ink3)
                        }
                        Text("›", fontSize = 20.sp, color = Palette.Ink3)
                    }
                    if (idx != dept.buttons.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp)
                                .height(1.dp)
                                .background(Palette.Line)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFFFF5F5))
                .clickable { onResetConfig() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("恢复默认配置", color = Color(0xFFC43A3A), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "默认配置可按科室实际习惯调整。配置与计数分别保存，恢复默认不会清零已记录台次。",
            fontSize = 11.5.sp,
            color = Palette.Ink3,
            lineHeight = 18.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/*  单按钮配置页                                                       */
/* ------------------------------------------------------------------ */
@Composable
fun ConfigScreen(
    state: AppState,
    btnId: String,
    onChange: (String, Int) -> Unit,
    onNameChange: (String) -> Unit
) {
    val btn = Catalog.btn(btnId)
    val dept = Catalog.deptOf(btnId)
    val cfg = state.cfgOf(btnId)
    val count = state.countOf(btnId)
    val contribution = Items.ALL.sumOf { (cfg[it] ?: 0) * count }
    val nameValue = state.names[btnId].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(btn.color))
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = "${dept.name} · 当前已记录 $count 次",
                fontSize = 11.5.sp,
                color = Palette.Ink3
            )
        }
        Spacer(Modifier.height(11.dp))

        // 手术名称：改过就存自己的，清空自动回到内置名称
        OutlinedTextField(
            value = nameValue,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink
            ),
            label = {
                Text("手术名称", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            },
            placeholder = {
                Text(
                    btn.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Palette.Ink3
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2E86C8),
                unfocusedBorderColor = Color(0x1A12181F),
                focusedContainerColor = Palette.Card,
                unfocusedContainerColor = Palette.Card,
                cursorColor = Color(0xFF2E86C8),
                focusedTextColor = Palette.Ink,
                unfocusedTextColor = Palette.Ink
            )
        )
        Text(
            text = "留空则显示默认名称「${btn.name}」，最多 ${AppState.NAME_MAX} 个字",
            fontSize = 10.5.sp,
            color = Palette.Ink3,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, top = 5.dp)
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x2212181F))
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Card)
        ) {
            Items.ALL.forEachIndexed { idx, item ->
                val v = cfg[item] ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                        Spacer(Modifier.height(2.dp))
                        Text("单次用量", fontSize = 11.sp, color = Palette.Ink3)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.FieldBg)
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StepBtn("−", enabled = v > 0) { onChange(item, v - 1) }
                        Text(
                            "$v",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(38.dp)
                        )
                        StepBtn("+", enabled = v < 9) { onChange(item, v + 1) }
                    }
                }
                if (idx != Items.ALL.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(1.dp)
                            .background(Palette.Line)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF4F7FA))
                .padding(14.dp)
        ) {
            val parts = Items.ALL.filter { (cfg[it] ?: 0) > 0 }
            Text(
                text = if (parts.isEmpty()) "做 1 次 ${state.nameOf(btnId)} 消耗：（未配置小件）"
                else "做 1 次 ${state.nameOf(btnId)} 消耗：" + parts.joinToString("、") { "$it ×${cfg[it]}" },
                fontSize = 12.sp,
                color = Palette.Ink2,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "已记录 $count 次 → 累计贡献 $contribution 件",
                fontSize = 12.sp,
                color = Palette.Ink2,
                lineHeight = 19.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "数量范围 0–9。改完直接返回，主页底部统计会立即按新配置重算。",
            fontSize = 11.5.sp,
            color = Palette.Ink3,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .shadow(if (enabled) 1.dp else 0.dp, RoundedCornerShape(9.dp))
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) Color.White else Color(0x0AFFFFFF))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Palette.Ink else Palette.Ink3
        )
    }
}
