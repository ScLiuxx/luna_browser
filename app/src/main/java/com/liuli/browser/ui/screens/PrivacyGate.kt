package com.liuli.browser.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.components.BrandOrb
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.components.PrivacyPolicyDialog
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type

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
        animationSpec = MotionSpec.value(Motion.Normal),
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
                        // 品牌球先落位，标题与副标题跟上。
                        BrandOrb(
                            size = 58.dp,
                            modifier = Modifier.gateReveal(gateDelay(tier = 0, item = 0), pop = true)
                        )
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                text = "欢迎使用琉璃浏览器",
                                style = Type.display.copy(color = colors.textPrimary),
                                modifier = Modifier.gateReveal(gateDelay(tier = 0, item = 1)),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            BasicText(
                                text = "液态玻璃设计 · Chromium 内核 · 一镜到底",
                                style = Type.bodySmall.copy(color = colors.textTertiary),
                                modifier = Modifier.gateReveal(gateDelay(tier = 0, item = 2)),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(Space.lg))

                    // 徽标横排可能在大字号下超出宽度，可横向滚动比硬裁切好。
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        GateBadge("无广告", Glyph.Sparkle, gateDelay(tier = 1, item = 0))
                        GateBadge("无账号", Glyph.Shield, gateDelay(tier = 1, item = 1))
                        GateBadge("本地数据", Glyph.Lock, gateDelay(tier = 1, item = 2))
                    }

                    Spacer(Modifier.height(Space.lg))

                    BasicText(
                        text = "在开始浏览前，请了解我们如何保护你的隐私：",
                        style = Type.bodySmall.copy(color = colors.textSecondary),
                        modifier = Modifier.gateReveal(gateDelay(tier = 2, item = 0))
                    )
                    Spacer(Modifier.height(Space.sm))
                    GatePoint("不收集、不上传任何浏览数据", gateDelay(tier = 2, item = 1))
                    GatePoint("仅申请联网权限", gateDelay(tier = 2, item = 2))
                    GatePoint("网页的定位、相机、麦克风请求默认拒绝", gateDelay(tier = 2, item = 3))
                    GatePoint("隐私护盾默认开启：拦截跟踪器与跟踪参数", gateDelay(tier = 2, item = 4))
                    GatePoint("退出应用时自动清除 Cookie、缓存与历史", gateDelay(tier = 2, item = 5))
                    GatePoint("书签与设置只保存在本机，可随时清除", gateDelay(tier = 2, item = 6))

                    Spacer(Modifier.height(Space.lg))

                    Box(
                        Modifier
                            .gateReveal(gateDelay(tier = 3, item = 0))
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.textPrimary.copy(alpha = 0.05f))
                            .padding(horizontal = Space.md, vertical = Space.sm)
                    ) {
                        BasicText(
                            text = "点击“同意并继续”即表示你已阅读并同意隐私说明",
                            style = Type.caption.copy(color = colors.textTertiary)
                        )
                    }

                    Spacer(Modifier.height(Space.lg))

                    Row(
                        Modifier
                            .gateReveal(gateDelay(tier = 3, item = 1))
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
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

                    Spacer(Modifier.height(Space.sm))

                    // "不同意并退出"是退路，不能和同意按钮一样重：
                    // 文字降级，但按下时要有明确反馈，否则用户不知道点没点上。
                    val declineInteraction = remember { MutableInteractionSource() }
                    val declinePressed by declineInteraction.collectIsPressedAsState()
                    val declineTint by animateColorAsState(
                        targetValue = if (declinePressed) colors.textSecondary else colors.textTertiary,
                        animationSpec = MotionSpec.color(Motion.Quick),
                        label = "decline-tint"
                    )
                    Box(
                        Modifier
                            .gateReveal(gateDelay(tier = 3, item = 2))
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouch)
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable(
                                interactionSource = declineInteraction,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "不同意并退出",
                                onClick = onDecline
                            )
                            .pressScale(pressedScale = 0.94f, interactionSource = declineInteraction),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "不同意并退出",
                            style = Type.bodySmall.copy(color = declineTint)
                        )
                    }
                }
            }
        }
    }

    PrivacyPolicyDialog(visible = showPolicy, onDismiss = { showPolicy = false })
}

@Composable
private fun GateBadge(label: String, glyph: Glyph, delayMillis: Int) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier
            .gateReveal(delayMillis)
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.textPrimary.copy(alpha = 0.05f))
            .padding(horizontal = Space.sm, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, colors.accent, size = 12.dp)
        BasicText(
            text = label,
            style = Type.label.copy(color = colors.textSecondary)
        )
    }
}

/**
 * 隐私门内的出场延迟。
 *
 * 分层是为了控制节奏：整块玻璃先落位，里面的元素再一层层浮现。
 * 层与层之间留出固定间隔，层内逐条错开；
 * "减少动态效果"开启时 staggerDelay 全部返回 0，整屏一次亮起。
 */
@Composable
private fun gateDelay(tier: Int, item: Int): Int {
    val tierDelay = MotionSpec.staggerDelay(tier, stepMillis = 150)
    val itemDelay = MotionSpec.staggerDelay(item, stepMillis = 22)
    return tierDelay + itemDelay
}

/**
 * 隐私门内的元素依次浮现。
 *
 * 延迟值必须在组合作用域里取好再进 LaunchedEffect，
 * 因为 [MotionSpec.staggerDelay] 是 @Composable，不能在协程里调用。
 */
@Composable
private fun Modifier.gateReveal(delayMillis: Int, pop: Boolean = false): Modifier {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "gate-reveal-alpha"
    )
    return graphicsLayer {
        this.alpha = alpha
        translationY = (1f - alpha) * 12.dp.toPx()
        if (pop) {
            val scale = 0.78f + 0.22f * alpha
            scaleX = scale
            scaleY = scale
        }
    }
}

@Composable
private fun GatePoint(text: String, delayMillis: Int) {
    val colors = LocalLiuliColors.current
    // 隐私要点逐条浮现：六条同时亮起会让人抓不住重点。
    var appeared by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "gate-point-alpha"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationX = (1f - alpha) * 12.dp.toPx()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(colors.success.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Check, colors.success, size = 10.dp)
        }
        Spacer(Modifier.width(Space.sm))
        BasicText(
            text = text,
            style = Type.bodySmall.copy(color = colors.textSecondary)
        )
    }
}
