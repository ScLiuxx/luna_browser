package com.liuli.browser.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.web.BrowserEngineInfo

/**
 * 内核低于最低安全基线时的启动提示。只提示，不强制退出，避免旧设备
 * 完全无法使用浏览器；用户可选择先更新 WebView，或明确继续。
 */
@Composable
fun EngineBaselineDialog(
    info: BrowserEngineInfo,
    backdrop: Backdrop,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLiuliColors.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp)
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
                    text = "WebView 内核低于安全基线",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "当前 ${info.provider} ${info.versionLabel}" +
                        "（Chromium ${info.chromiumMajor ?: "未知"}），" +
                        "低于最低安全基线 ${info.minimumMajor}。",
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = "旧内核可能存在已公开漏洞，网页隔离与反指纹加固也可能失效。" +
                        "建议先在系统设置中更新 Android System WebView。",
                    style = TextStyle(
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPillButton(
                        label = "仍要继续",
                        onClick = onDismiss,
                        backdrop = emptyBackdrop()
                    )
                    GlassPillButton(
                        label = "更新 WebView",
                        onClick = onOpenSettings,
                        backdrop = emptyBackdrop(),
                        accent = true,
                        glyph = Glyph.Shield
                    )
                }
            }
        }
    }
}
