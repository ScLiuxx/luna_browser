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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.delay

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
        Column(Modifier.fillMaxSize()) {
            // 上半部分可滚动：品牌、徽标、隐私要点都在这里。
            // 内容比可视区短时整体居中，高了才滚——早先整块内容直接顶在屏幕上沿，
            // 大屏上卡片孤零零吊在顶部，两头不靠。
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // 可视区高度。滚动区在内容不足时撑到这么高，
                // 多出来的空间正好用来居中；内容超过时容器跟着长，那一段才需要滚。
                val viewportHeight = maxHeight
                Box(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = viewportHeight),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassSurface(
                            backdrop = backdrop,
                            modifier = Modifier
                                // 宽度上限罩在最外层：留白算在里面，
                                // 卡片总宽才不会比预期多出一截。
                                .widthIn(max = 560.dp)
                                .padding(22.dp)
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
                                // 大字号下标题挤在品牌球右边会被截断，
                                // 这时把球挪到标题上方，让标题独占整行宽度。
                                val stacked = LocalDensity.current.fontScale > 1.3f
                                if (stacked) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Box(
                                            Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BrandOrb(
                                                size = 48.dp,
                                                modifier = Modifier.gateReveal(
                                                    gateDelay(tier = 0, item = 0),
                                                    pop = true
                                                )
                                            )
                                        }
                                        Spacer(Modifier.height(Space.sm))
                                        GateTitles(maxLines = 3)
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 品牌球先落位，标题与副标题跟上。
                                        BrandOrb(
                                            size = 58.dp,
                                            modifier = Modifier.gateReveal(
                                                gateDelay(tier = 0, item = 0),
                                                pop = true
                                            )
                                        )
                                        Spacer(Modifier.width(Space.md))
                                        GateTitles(maxLines = 2, modifier = Modifier.weight(1f))
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

                                // 一条细线把"卖点"和"承诺"切成两段。
                                // 徽标是营销，隐私要点是条款，混在一处读起来像同一段文案。
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.textPrimary.copy(alpha = 0.10f))
                                )

                                Spacer(Modifier.height(Space.md))

                                // 小节标题：告诉用户下面这段是硬承诺。
                                Row(
                                    Modifier.gateReveal(gateDelay(tier = 2, item = 0)),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.xs)
                                ) {
                                    GlyphIcon(Glyph.Shield, colors.success, size = 14.dp)
                                    BasicText(
                                        text = "隐私要点",
                                        style = Type.sectionTitle.copy(color = colors.textSecondary)
                                    )
                                }

                                Spacer(Modifier.height(Space.sm))

                                GatePoint("不收集、不上传任何浏览数据", gateDelay(tier = 2, item = 1))
                                GatePoint("仅申请联网权限", gateDelay(tier = 2, item = 2))
                                GatePoint("网页的定位、相机、麦克风请求默认拒绝", gateDelay(tier = 2, item = 3))
                                GatePoint("隐私护盾默认开启：拦截跟踪器与跟踪参数", gateDelay(tier = 2, item = 4))
                                GatePoint("退出应用时自动清除 Cookie、缓存与历史", gateDelay(tier = 2, item = 5))
                                GatePoint("书签与设置只保存在本机，可随时清除", gateDelay(tier = 2, item = 6))
                            }
                        }
                    }
                }
            }

            // 下半部分常驻底部：同意动作不跟着内容滚。
            // 六条要点在小屏上就占掉大半高度，按钮若跟着滚，
            // 用户得一路滑到底才找得到"同意"——同意页最不该出现的事就是这个。
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = Space.xs, bottom = Space.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 法务小字跟着按钮走：它解释的正是"同意并继续"这个动作。
                BasicText(
                    text = "点击“同意并继续”即表示你已阅读并同意隐私说明",
                    style = Type.caption.copy(
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .gateReveal(gateDelay(tier = 3, item = 0)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.sm))
                // 主操作独占一行：这一屏只该有一个明确的主按钮。
                // 之前它和"隐私说明"并排，同意与拒绝被隔在两行，层级读不出来。
                GlassPillButton(
                    label = "同意并继续",
                    onClick = onAccept,
                    backdrop = backdrop,
                    accent = true,
                    glyph = Glyph.Check,
                    modifier = Modifier
                        .fillMaxWidth()
                        .gateReveal(gateDelay(tier = 3, item = 1))
                )
                Spacer(Modifier.height(Space.xs))
                // 次级动作：读说明与拒绝并排，都是安静的文字动作。
                Row(
                    Modifier
                        .fillMaxWidth()
                        .gateReveal(gateDelay(tier = 3, item = 2)),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GateTextAction(
                        label = "隐私说明",
                        glyph = Glyph.Shield,
                        modifier = Modifier.weight(1f)
                    ) { showPolicy = true }
                    GateTextAction(
                        label = "不同意并退出",
                        modifier = Modifier.weight(1f)
                    ) { onDecline() }
                }
            }
        }
    }

    PrivacyPolicyDialog(visible = showPolicy, onDismiss = { showPolicy = false })
}

/**
 * 隐私门的标题与副标题。
 *
 * 单独抽出来，是为了大字号下能换一种摆法：
 * 标题在品牌球右边时被截断，挪到球下方才留得下完整的一行。
 */
@Composable
private fun GateTitles(maxLines: Int, modifier: Modifier = Modifier) {
    val colors = LocalLiuliColors.current
    Column(modifier) {
        BasicText(
            text = "欢迎使用琉璃浏览器",
            style = Type.display.copy(color = colors.textPrimary),
            modifier = Modifier.gateReveal(gateDelay(tier = 0, item = 1)),
            maxLines = maxLines,
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
 * 次级文字动作。
 *
 * "隐私说明"与"不同意并退出"都不该和主按钮抢注意力，
 * 但也不能弱到按下去没反应：按下时文字提亮一档，并带一点回缩。
 */
@Composable
private fun GateTextAction(
    label: String,
    glyph: Glyph? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(
        targetValue = if (pressed) colors.textSecondary else colors.textTertiary,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "gate-action-tint"
    )
    Row(
        modifier
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.94f, interactionSource = interaction)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        // 居中而不是靠左：两个次级动作各占一半宽度，
        // 大字号下文字换行也不会把彼此挤出屏幕。
        horizontalArrangement = Arrangement.Center
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, tint, size = 14.dp)
            Spacer(Modifier.width(Space.xs))
        }
        BasicText(
            text = label,
            style = Type.bodySmall.copy(color = tint),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 隐私门内的出场延迟。
 *
 * 分层是为了控制节奏：品牌层一个一个来（球 → 标题 → 副标题），
 * 徽标与要点快一些，操作层最后才亮。
 * 早先各层只隔 150ms、层内只隔 22ms，球和标题几乎同时出现，
 * "逐个浮现"看着像整块一起亮；层内拉开之后顺序才读得出来。
 * "减少动态效果"开启时全部返回 0，整屏一次亮起。
 */
@Composable
private fun gateDelay(tier: Int, item: Int): Int {
    if (reduceMotion) return 0
    val tierDelay = when (tier) {
        0 -> 0
        1 -> 200
        2 -> 370
        else -> 640
    }
    val itemDelay = when (tier) {
        0 -> item * 75
        1 -> item * 40
        2 -> item * 38
        else -> item * 55
    }
    return tierDelay + itemDelay
}

/**
 * 隐私门内的元素依次浮现。
 *
 * 延迟值必须在组合作用域里取好再进 LaunchedEffect，
 * 因为 [gateDelay] 要读全局的"减少动态效果"开关，不能在协程里调用。
 */
@Composable
private fun Modifier.gateReveal(delayMillis: Int, pop: Boolean = false): Modifier {
    val reduced = reduceMotion
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "gate-reveal-alpha"
    )
    return graphicsLayer {
        this.alpha = alpha
        // "减少动态效果"下只留淡入，位移与缩放都不做。
        if (!reduced) {
            translationY = (1f - alpha) * 12.dp.toPx()
            if (pop) {
                val scale = 0.78f + 0.22f * alpha
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
private fun GatePoint(text: String, delayMillis: Int) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    // 隐私要点逐条浮现：六条同时亮起会让人抓不住重点。
    var appeared by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (delayMillis > 0) delay(delayMillis.toLong())
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
                // "减少动态效果"下只留淡入，不做横向滑入。
                if (!reduced) {
                    translationX = (1f - alpha) * 12.dp.toPx()
                }
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
