package com.liuli.browser.ui.screens

import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.BuildConfig
import com.liuli.browser.data.SearchEngine
import com.liuli.browser.data.SettingsStore
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.components.PrivacyPolicyDialog
import com.liuli.browser.ui.liquid.LiquidSlider
import com.liuli.browser.ui.liquid.LiquidToggle
import com.liuli.browser.ui.theme.AccentPalettes
import com.liuli.browser.ui.theme.DarkModeOption
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.web.BrowserViewModel

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    vm: BrowserViewModel,
    backdrop: LayerBackdrop,
    isTablet: Boolean,
    onClose: () -> Unit,
    onRevokePrivacy: () -> Unit
) {
    val colors = LocalLiuliColors.current
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmRevoke by rememberSaveable { mutableStateOf(false) }
    val horizontal = if (isTablet) 28.dp else 18.dp
    val webViewPackage = remember {
        runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
    }
    val webViewVersion = webViewPackage?.versionName
    val webViewProvider = webViewPackage?.packageName ?: "系统 WebView"
    val chromiumMajor = webViewVersion
        ?.let { Regex("^([0-9]+)\\.").find(it)?.groupValues?.getOrNull(1) }
    val kernelRows = remember(webViewProvider, webViewVersion) {
        listOf(
            "WebView 提供方" to webViewProvider,
            "WebView 版本" to (webViewVersion ?: "未知"),
            "Chromium 主版本" to (chromiumMajor ?: "未知"),
            "内核形态" to "系统内置 WebView（APK 不打包 Chromium）"
        )
    }

    BackHandler(enabled = showPrivacy) { showPrivacy = false }
    BackHandler(enabled = confirmClear) { confirmClear = false }
    BackHandler(enabled = confirmRevoke) { confirmRevoke = false }
    BackHandler(enabled = !showPrivacy && !confirmClear && !confirmRevoke, onBack = onClose)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal)
                .padding(top = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        text = "设置",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = if (isTablet) 32.sp else 26.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = "外观、搜索与隐私合规",
                        style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
                    )
                }
                GlassIconButton(
                    glyph = Glyph.Close,
                    contentDescription = "关闭设置",
                    onClick = onClose,
                    backdrop = backdrop,
                    size = 46.dp,
                    glyphSize = 18.dp
                )
            }

            Spacer(Modifier.height(18.dp))

            SettingsSection(title = "外观", backdrop = backdrop) {
                SettingsLabel("主题色", "液态玻璃的折射色调")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentPalettes.forEachIndexed { index, palette ->
                        val selected = settings.accentIndex == index
                        Box(
                            Modifier
                                .size(Dimens.minTouch)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = palette.label) {
                                    settings.updateAccentIndex(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(palette.primary, palette.secondary, palette.tertiary)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    GlyphIcon(Glyph.Check, Color.White, size = 16.dp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SettingsLabel("深色模式", null)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.textPrimary.copy(alpha = 0.06f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DarkModeOption.entries.forEachIndexed { index, option ->
                        val selected = settings.darkMode == index
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) colors.glassTintStrong else Color.Transparent
                                )
                                .clickable(role = Role.RadioButton) { settings.updateDarkMode(index) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            BasicText(
                                text = option.label,
                                style = TextStyle(
                                    color = if (selected) colors.textPrimary else colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    title = "动态流光背景",
                    subtitle = "低功耗地缓慢流动；关闭后保持静态",
                    checked = settings.dynamicBackground,
                    onCheckedChange = { settings.updateDynamicBackground(it) },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "减弱动效",
                    subtitle = "尊重无障碍偏好，关闭一镜到底的位移动画",
                    checked = settings.reduceMotion,
                    onCheckedChange = { settings.updateReduceMotion(it) },
                    backdrop = backdrop
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsSection(title = "搜索", backdrop = backdrop) {
                SettingsLabel("默认搜索引擎", "地址栏输入非网址内容时使用")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimens.minTouch)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.RadioButton) {
                                    settings.updateSearchEngine(engine)
                                }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphIcon(
                                glyph = if (settings.searchEngine == engine) Glyph.Check else Glyph.Search,
                                tint = if (settings.searchEngine == engine) colors.accent else colors.textTertiary,
                                size = 17.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicText(
                                text = engine.label,
                                style = TextStyle(
                                    color = if (settings.searchEngine == engine) colors.accent else colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (settings.searchEngine == engine) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsSection(title = "浏览", backdrop = backdrop) {
                SettingsLabel("标签页上限", "达到上限后自动回收最久未使用的标签")
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidSlider(
                        value = { settings.maxTabs.toFloat() },
                        onValueChange = {
                            settings.updateMaxTabs(kotlin.math.round(it).toInt())
                        },
                        valueRange = 4f..12f,
                        visibilityThreshold = 1f,
                        backdrop = backdrop,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        contentDescription = "标签页上限"
                    )
                    BasicText(
                        text = "${settings.maxTabs} 个",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Divider()
                ToggleRow(
                    title = "新标签使用桌面版网站",
                    subtitle = "对需要完整布局的网站更友好",
                    checked = settings.desktopModeDefault,
                    onCheckedChange = { settings.updateDesktopModeDefault(it) },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "阻止第三方 Cookie",
                    subtitle = "默认开启，减少跨站跟踪",
                    checked = settings.blockThirdPartyCookies,
                    onCheckedChange = {
                        settings.updateBlockThirdPartyCookies(it)
                        vm.applyCookiePolicy()
                    },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "滚动时收起工具栏",
                    subtitle = "向下滚动时压缩顶栏并隐藏底栏，向上滚动恢复",
                    checked = settings.autoHideToolbar,
                    onCheckedChange = {
                        settings.updateAutoHideToolbar(it)
                        if (!it) vm.expandToolbar()
                    },
                    backdrop = backdrop
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsSection(title = "隐私护盾", backdrop = backdrop) {
                ToggleRow(
                    title = "隐私护盾",
                    subtitle = "拦截跟踪器、去除跟踪参数、使用通用 User-Agent",
                    checked = settings.privacyShield,
                    onCheckedChange = {
                        settings.updatePrivacyShield(it)
                        vm.applyPrivacySettings()
                    },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "退出即清除数据",
                    subtitle = "关闭应用时清除 Cookie、缓存、历史与标签会话",
                    checked = settings.clearOnExit,
                    onCheckedChange = { settings.updateClearOnExit(it) },
                    backdrop = backdrop
                )
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = "隐私护盾在本机运行，不上传网址或任何标识符。网页无法读取剪贴板、通讯录、定位、相机或麦克风。",
                    style = TextStyle(color = colors.textTertiary, fontSize = 10.sp, lineHeight = 16.sp)
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsSection(title = "金标联盟 · 隐私与合规", backdrop = backdrop) {
                ComplianceRow("权限最小化", "仅 INTERNET，无存储、定位、相机、通讯录等敏感权限")
                ComplianceRow("隐私首启同意", "首次启动展示说明，同意后才创建 WebView 与联网")
                ComplianceRow("无第三方 SDK", "零统计、零广告、零账号；书签与历史仅存本机")
                ComplianceRow("隐私护盾", "本机拦截追踪器、去除跟踪参数、退出即清除数据")
                ComplianceRow("64 位兼容", "仅打包 arm64-v8a / x86_64 64 位 ABI，无 32 位原生库")
                ComplianceRow("Android 17", "targetSdk 37，适配新版前台与内存策略")
                ComplianceRow("大屏与折叠屏", "响应式侧栏、多列标签、可调整窗口与旋转")
                ComplianceRow("一镜到底", "搜索框与标签卡片共享元素/容器，弹簧曲线驱动")
                ComplianceRow("深色与无障碍", "深色模式、48dp 触控、语义标签、字体缩放、减弱动效")
                ComplianceRow("安全浏览", "SSL 错误阻断、混合内容阻止、文件访问禁用、Safe Browsing")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPillButton(
                        label = "隐私说明",
                        onClick = { showPrivacy = true },
                        backdrop = backdrop,
                        glyph = Glyph.Shield
                    )
                    GlassPillButton(
                        label = "清除浏览数据",
                        onClick = { confirmClear = true },
                        backdrop = backdrop,
                        glyph = Glyph.Trash
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsSection(title = "关于", backdrop = backdrop) {
                AboutRow("应用版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                kernelRows.forEach { (label, value) -> AboutRow(label, value) }
                AboutRow("运行平台", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                AboutRow("设计语言", "Liquid Glass · Backdrop (Apache-2.0)")
                AboutRow("界面框架", "Jetpack Compose 1.12 · Kotlin 2.4")
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = "琉璃浏览器使用系统 Chromium WebView 作为内核，内核随系统或 WebView 应用更新，应用本身不内置 Chromium 运行时，因此 APK 约 2 MB。",
                    style = TextStyle(color = colors.textTertiary, fontSize = 11.sp, lineHeight = 17.sp)
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = "琉璃浏览器不提供云同步。所有浏览数据均保存在本机，可在上方一键清除。",
                    style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
                )
                Spacer(Modifier.height(14.dp))
                GlassPillButton(
                    label = "撤回隐私同意",
                    onClick = { confirmRevoke = true },
                    backdrop = backdrop,
                    glyph = Glyph.Info
                )
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showPrivacy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    }
    if (confirmClear) {
        ConfirmClearDialog(
            onConfirm = {
                confirmClear = false
                vm.clearBrowsingData()
            },
            onCancel = { confirmClear = false }
        )
    }
    if (confirmRevoke) {
        ConfirmRevokeDialog(
            onConfirm = {
                confirmRevoke = false
                onRevokePrivacy()
            },
            onCancel = { confirmRevoke = false }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    backdrop: LayerBackdrop,
    content: @Composable () -> Unit
) {
    val colors = LocalLiuliColors.current
    Column(Modifier.fillMaxWidth()) {
        BasicText(
            text = title,
            style = TextStyle(
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            tint = colors.glassTint,
            blurRadius = 20.dp,
            lensHeight = 16.dp,
            lensAmount = 22.dp,
            contentPadding = PaddingValues(18.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsLabel(title: String, subtitle: String?) {
    val colors = LocalLiuliColors.current
    Column {
        BasicText(
            text = title,
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(color = colors.textTertiary, fontSize = 10.sp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop
) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Switch) { onCheckedChange(!checked) }
        ) {
            BasicText(
                text = title,
                style = TextStyle(color = colors.textPrimary, fontSize = 14.sp)
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(color = colors.textTertiary, fontSize = 10.sp)
            )
        }
        Spacer(Modifier.width(12.dp))
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = backdrop,
            contentDescription = title
        )
    }
}

@Composable
private fun ComplianceRow(title: String, detail: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(colors.success.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Check, colors.success, size = 10.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(
                text = title,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = detail,
                style = TextStyle(color = colors.textTertiary, fontSize = 10.sp)
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = colors.textSecondary, fontSize = 12.sp),
            modifier = Modifier.weight(1f)
        )
        BasicText(
            text = value,
            style = TextStyle(color = colors.textPrimary, fontSize = 12.sp)
        )
    }
}

@Composable
private fun Divider() {
    val colors = LocalLiuliColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(colors.textPrimary.copy(alpha = 0.07f))
    )
}

@Composable
private fun ConfirmRevokeDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalLiuliColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(28.dp),
            tint = colors.glassTintStrong.copy(alpha = 0.96f),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            shadow = true
        ) {
            Column(Modifier.padding(22.dp)) {
                BasicText(
                    text = "撤回隐私同意？",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = "撤回后将清除本地书签、历史、Cookie、缓存与设置，并返回隐私告知页。此操作不可撤销。",
                    style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPillButton(
                        label = "取消",
                        onClick = onCancel,
                        backdrop = com.kyant.backdrop.backdrops.emptyBackdrop()
                    )
                    GlassPillButton(
                        label = "撤回并清除",
                        onClick = onConfirm,
                        backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
                        accent = true,
                        glyph = Glyph.Trash
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmClearDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalLiuliColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(28.dp),
            tint = colors.glassTintStrong.copy(alpha = 0.94f),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            shadow = true
        ) {
            Column(Modifier.padding(22.dp)) {
                BasicText(
                    text = "清除浏览数据？",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = "将删除 Cookie、缓存、书签与历史记录，此操作不可撤销。",
                    style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPillButton(label = "取消", onClick = onCancel, backdrop = com.kyant.backdrop.backdrops.emptyBackdrop())
                    GlassPillButton(
                        label = "确认清除",
                        onClick = onConfirm,
                        backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
                        accent = true,
                        glyph = Glyph.Trash
                    )
                }
            }
        }
    }
}

