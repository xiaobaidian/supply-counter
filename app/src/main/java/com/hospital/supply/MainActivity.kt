package com.hospital.supply

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hospital.supply.data.AppState
import com.hospital.supply.data.Catalog
import com.hospital.supply.data.Store
import com.hospital.supply.ui.ConfigScreen
import com.hospital.supply.ui.HomeScreen
import com.hospital.supply.ui.Palette
import com.hospital.supply.ui.SettingsScreen
import com.hospital.supply.ui.SupplyCounterTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SupplyCounterTheme {
                Root()
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Config(val btnId: String) : Screen
}

private data class Ask(
    val title: String,
    val message: String,
    val okText: String,
    val action: () -> Unit
)

@Composable
fun Root() {
    val context = LocalContext.current
    val store = remember { Store(context.applicationContext) }
    var state by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var toast by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun buzz() {
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun persist(next: AppState) {
        state = next
        store.save(next)
    }

    fun inc(id: String) {
        persist(state.withCount(id, state.countOf(id) + 1))
    }

    fun dec(id: String) {
        val cur = state.countOf(id)
        if (cur <= 0) {
            toast = "已经是 0，无法再减少"
            buzz()
            return
        }
        persist(state.withCount(id, cur - 1))
        toast = "已减少 1 次：${Catalog.deptOf(id).name} · ${Catalog.btn(id).name} = ${cur - 1}"
        buzz()
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1500)
            toast = null
        }
    }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = if (screen is Screen.Config) Screen.Settings else Screen.Home
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.fillMaxSize()) {
            AppBar(
                title = when (screen) {
                    is Screen.Home -> "耗材统计"
                    is Screen.Settings -> "设置"
                    is Screen.Config -> {
                        val id = (screen as Screen.Config).btnId
                        "${Catalog.deptOf(id).name} · ${Catalog.btn(id).name}"
                    }
                },
                subtitle = when (screen) {
                    is Screen.Home -> "本台手术 · 实时汇总"
                    is Screen.Settings -> "小件用量配置"
                    else -> "单次消耗配置"
                },
                showBack = screen !is Screen.Home,
                onBack = { screen = if (screen is Screen.Config) Screen.Settings else Screen.Home },
                onSettings = { screen = Screen.Settings }
            )

            when (val s = screen) {
                is Screen.Home -> HomeScreen(
                    state = state,
                    onInc = { inc(it) },
                    onDec = { dec(it) },
                    onClear = {
                        ask = Ask(
                            title = "清空本台计数？",
                            message = "7 个按钮的记录次数将全部归零，按钮配置会保留。此操作不可撤销，仅在你点此确认后执行。",
                            okText = "清空"
                        ) {
                            persist(state.clearedCounts())
                            toast = "已清空本台计数"
                        }
                    }
                )

                is Screen.Settings -> SettingsScreen(
                    state = state,
                    onOpen = { screen = Screen.Config(it) },
                    onResetConfig = {
                        ask = Ask(
                            title = "恢复默认配置？",
                            message = "7 个按钮的小件用量会回到初始值，已记录的次数不受影响。",
                            okText = "恢复"
                        ) {
                            persist(state.defaultedConfig())
                            toast = "已恢复默认配置"
                        }
                    }
                )

                is Screen.Config -> ConfigScreen(
                    state = state,
                    btnId = s.btnId,
                    onChange = { item, v -> persist(state.withItem(s.btnId, item, v)) }
                )
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 34.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xF0101720))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    toast.orEmpty(),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }

    ask?.let { a ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = {
                Text(a.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            },
            text = {
                Text(a.message, fontSize = 13.sp, color = Palette.Ink2, lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    ask = null
                    a.action()
                }) {
                    Text(a.okText, color = Palette.Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { ask = null }) {
                    Text("取消", color = Palette.Ink2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun AppBar(
    title: String,
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val stroke = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    val path = Path().apply {
                        moveTo(size.width * 0.64f, size.height * 0.18f)
                        lineTo(size.width * 0.34f, size.height * 0.5f)
                        lineTo(size.width * 0.64f, size.height * 0.82f)
                    }
                    drawPath(path, Palette.Ink, style = stroke)
                }
            }
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink,
                letterSpacing = 0.4.sp,
                maxLines = 1
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, fontSize = 11.5.sp, color = Palette.Ink3, fontWeight = FontWeight.Medium)
            }
        }

        if (!showBack) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = Palette.Ink,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}
