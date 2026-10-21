package com.liuli.browser.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.components.BrandOrb
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.components.PrivacyPolicyDialog
import com.liuli.browser.ui.theme.LocalLiuliColors

@Composable
fun PrivacyGate(
    backdrop: LayerBackdrop,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val colors = LocalLiuliColors.current
    var entered by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(520),
        label = "gate-entrance"
    )

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .padding(22.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 26.dp.toPx()
                    },
                shape = RoundedCornerShape(34.dp),
                tint = colors.glassTintStrong,
                blurRadius = 26.dp,
                lensHeight = 22.dp,
                lensAmount = 30.dp,
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandOrb(size = 58.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            BasicText(
                                text = "欢迎使用琉璃浏览器",
                                style = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(Modifier.height(3.dp))
                            BasicText(
                                text = "液态玻璃设计 · Chromium 内核 · 一镜到底",
                                style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GateBadge("无广告", Glyph.Sparkle)
                        GateBadge("无账号", Glyph.Shield)
                        GateBadge("本地数据", Glyph.Lock)
                    }

                    Spacer(Modifier.height(18.dp))

                    BasicText(
                        text = "在开始浏览前，请了解我们如何保护你的隐私：",
                        style = TextStyle(color = colors.textSecondary, fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    GatePoint("不收集、不上传任何浏览数据")
                    GatePoint("仅申请联网权限")
                    GatePoint("网页的定位、相机、麦克风请求默认拒绝")
                    GatePoint("隐私护盾默认开启：拦截跟踪器与跟踪参数")
                    GatePoint("退出应用时自动清除 Cookie、缓存与历史")
                    GatePoint("书签与设置只保存在本机，可随时清除")

                    Spacer(Modifier.height(16.dp))

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.textPrimary.copy(alpha = 0.05f))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        BasicText(
                            text = "点击“同意并继续”即表示你已阅读并同意隐私说明",
                            style = TextStyle(color = colors.textTertiary, fontSize = 10.sp)
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassPillButton(
                            label = "同意并继续",
                            onClick = onAccept,
                            backdrop = backdrop,
                            accent = true,
                            glyph = Glyph.Check,
                            modifier = Modifier.weight(1f)
                        )
                        GlassPillButton(
                            label = "隐私说明",
                            onClick = { showPolicy = true },
                            backdrop = backdrop,
                            glyph = Glyph.Shield
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "不同意并退出",
                                onClick = onDecline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "不同意并退出",
                            style = TextStyle(
                                color = colors.textTertiary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }

    if (showPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPolicy = false })
    }
}

@Composable
private fun GateBadge(label: String, glyph: Glyph) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.textPrimary.copy(alpha = 0.05f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, colors.accent, size = 11.dp)
        BasicText(
            text = label,
            style = TextStyle(color = colors.textSecondary, fontSize = 9.sp)
        )
    }
}

@Composable
private fun GatePoint(text: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(colors.success.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Check, colors.success, size = 8.dp)
        }
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = text,
            style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
        )
    }
}
