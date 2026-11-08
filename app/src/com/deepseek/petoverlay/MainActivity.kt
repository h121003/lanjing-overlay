package com.deepseek.petoverlay

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private val pickWallpaper =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                prefs.edit().putString(KEY_WALLPAPER, uri.toString()).apply()
                recreate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        }

        setContent {
            AppRoot(
                wallUri = prefs.getString(KEY_WALLPAPER, null),
                themeMode = prefs.getString(KEY_THEME, "system") ?: "system",
                onPickWallpaper = { pickWallpaper.launch(arrayOf("image/*")) },
                onClearWallpaper = {
                    prefs.edit().remove(KEY_WALLPAPER).apply()
                    recreate()
                },
                onTheme = { v ->
                    prefs.edit().putString(KEY_THEME, v).apply()
                    recreate()
                },
                onStartPet = {
                    val i = Intent(this, OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                },
                onStopPet = { stopService(Intent(this, OverlayService::class.java)) },
                onOverlayPermission = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                },
                onBigger = { OverlayService.resize(1.12f) },
                onSmaller = { OverlayService.resize(0.89f) },
                onReset = { OverlayService.resetSize() }
            )
        }
    }

    companion object {
        const val PREFS = "lanjing"
        const val KEY_WALLPAPER = "wallpaper_uri"
        const val KEY_THEME = "theme"
    }
}

private val cBgDark = Color(0xFF0F1114)
private val cBgLight = Color(0xFFF4F6F9)
private val cTextDark = Color(0xFFEDF2F7)
private val cTextLight = Color(0xFF11161C)
private val cSubDark = Color(0xFF9FB0C0)
private val cSubLight = Color(0xFF55606B)
private val cCardDark = Color(0xCC1B2027)
private val cCardLight = Color(0xFFE3E9F0)
private val accent = Color(0xFF11A34C)

@Composable
private fun AppRoot(
    wallUri: String?,
    themeMode: String,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onTheme: (String) -> Unit,
    onStartPet: () -> Unit,
    onStopPet: () -> Unit,
    onOverlayPermission: () -> Unit,
    onBigger: () -> Unit,
    onSmaller: () -> Unit,
    onReset: () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val bg = if (isDark) cBgDark else cBgLight
    val fg = if (isDark) cTextDark else cTextLight
    val sub = if (isDark) cSubDark else cSubLight
    val card = if (isDark) cCardDark else cCardLight

    var tab by remember { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // 壁纸
        val ctx = LocalContext.current
        val bmp = remember(wallUri) {
            wallUri?.let {
                runCatching {
                    ctx.contentResolver.openInputStream(Uri.parse(it))?.use { s ->
                        BitmapFactory.decodeStream(s)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
        if (bmp != null) {
            Image(
                bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(bg))
        }
        // 蒙版
        Box(
            Modifier
                .fillMaxSize()
                .background(if (isDark) Color(0xB3000000) else Color(0x66FFFFFF))
        )

        // 内容（记录到 backdrop）
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                when (tab) {
                    0 -> PetPage(fg, sub, card, onOverlayPermission, onStartPet, onStopPet)
                    1 -> LookPage(fg, sub, card, wallUri != null, onPickWallpaper, onClearWallpaper, onBigger, onSmaller, onReset)
                    2 -> ThemePage(fg, sub, card, themeMode, onTheme)
                    else -> AboutPage(fg, sub, card)
                }
            }
            Spacer(Modifier.height(120.dp))
        }

        // 液态玻璃底栏
        GlassBar(
            backdrop = backdrop,
            isDark = isDark,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 22.dp)
        )
    }
}

@Composable
private fun GlassBar(
    backdrop: com.kyant.backdrop.Backdrop,
    isDark: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf("宠物", "外观", "设置", "关于")
    val density = LocalDensity.current
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(with(density) { 14.dp.toPx() })
                    lens(
                        refractionHeight = with(density) { 22.dp.toPx() },
                        refractionAmount = with(density) { 14.dp.toPx() },
                        depthEffect = false,
                        chromaticAberration = true
                    )
                }
            )
            .clip(shape)
            .background(if (isDark) Color(0x8A151A20) else Color(0x8AF2F5F9)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (on) accent else (if (isDark) Color(0x99FFFFFF) else Color(0x99111820)))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        color = if (on) accent else (if (isDark) Color(0xCCFFFFFF) else Color(0xCC111820)),
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PageTitle(t: String, fg: Color, sub: Color) {
    Spacer(Modifier.height(48.dp))
    Text(t, color = fg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        "蓝鲸悬浮窗 · 桌面小鲸鱼", color = sub, fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 20.dp)
    )
}

@Composable
private fun CardBtn(
    title: String, desc: String?, card: Color, fg: Color, sub: Color,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (desc != null) {
            Spacer(Modifier.height(4.dp))
            Text(desc, color = sub, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PetPage(
    fg: Color, sub: Color, card: Color,
    onOverlayPermission: () -> Unit, onStart: () -> Unit, onStop: () -> Unit
) {
    PageTitle("宠物", fg, sub)
    Text("① 授予悬浮窗权限\n② 启动悬浮宠物\n③ 回到桌面，可拖动、点击切换动作",
        color = sub, fontSize = 15.sp, lineHeight = 24.sp,
        modifier = Modifier.padding(bottom = 16.dp))
    CardBtn("授予悬浮窗权限", "在系统设置里允许「显示在其他应用上层」", card, fg, sub, onOverlayPermission)
    CardBtn("启动悬浮宠物", "小鲸鱼会出现在桌面上", card, fg, sub, onStart)
    CardBtn("停止", "关闭悬浮窗", card, fg, sub, onStop)
}

@Composable
private fun LookPage(
    fg: Color, sub: Color, card: Color, hasWall: Boolean,
    onPick: () -> Unit, onClear: () -> Unit,
    onBigger: () -> Unit, onSmaller: () -> Unit, onReset: () -> Unit
) {
    PageTitle("外观", fg, sub)
    CardBtn("选择壁纸", "从相册里挑一张", card, fg, sub, onPick)
    if (hasWall) CardBtn("清除壁纸", null, card, fg, sub, onClear)
    Spacer(Modifier.height(20.dp))
    Text("悬浮窗大小（需先启动悬浮宠物）", color = sub, fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 8.dp))
    CardBtn("调大一点", null, card, fg, sub, onBigger)
    CardBtn("调小一点", null, card, fg, sub, onSmaller)
    CardBtn("恢复默认大小", null, card, fg, sub, onReset)
}

@Composable
private fun ThemePage(fg: Color, sub: Color, card: Color, cur: String, onTheme: (String) -> Unit) {
    PageTitle("设置", fg, sub)
    Text("主题", color = sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (k, name) ->
        CardBtn(
            if (cur == k) "✓ $name" else name, null,
            if (cur == k) Color(0x3322C55E) else card, fg, sub
        ) { onTheme(k) }
    }
}

@Composable
private fun AboutPage(fg: Color, sub: Color, card: Color) {
    PageTitle("关于", fg, sub)
    Text("蓝鲸悬浮窗 1.3.0", color = fg, fontSize = 16.sp)
    Spacer(Modifier.height(8.dp))
    Text(
        "开源地址：github.com/h121003/lanjing-overlay\n" +
                "内置 106 段动画，底部导航为液态玻璃效果",
        color = sub, fontSize = 13.sp, lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .padding(16.dp)
    )
}
