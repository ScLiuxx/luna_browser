package com.liuli.browser.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.components.BrandOrb
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.components.PrivacyPolicyDialog
import com.liuli.browser.ui.liquid.GlassShape
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

/**
 * 隐私门的最大宽度。
 *
 * 卡片与底部操作区共用这一个上限：两者的左右边缘因此严格对齐，
 * 平板上不会一个窄一个宽，读起来像两块不相干的拼图。
 */
private val GateMaxWidth = 560.dp

/** 次级动作里的图标尺寸。测量宽度时要用同一份数值，估出来的排版才准。 */
private val GateActionIconSize = 14.dp

/** 次级动作里图标与文字的间距。 */
private val GateActionIconGap = Space.xs

/** 次级动作的左右留白。 */
private val GateActionPadH = Space.md

@Composable
fun PrivacyGate(
    backdrop: LayerBackdrop,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val density = LocalDensity.current
    var showPolicy by remember { mutableStateOf(false) }

    // 卡片本体只做一次极短的淡入。
    // 此前是 220ms 的整卡淡入加位移，和卡内逐项浮现叠在一起，
    // 结果是元素各自的错峰全被整卡的淡入盖住，看上去仍是"整块一起亮"。
    var cardShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardShown = true }
    val cardAlpha by animateFloatAsState(
        targetValue = if (cardShown) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Fast),
        label = "gate-card-alpha"
    )

    // 次级动作的文字宽度：两个标签并排放不下时要改竖排，
    // 否则大字号下会被挤成省略号，"不同意并退出"就找不到了。
    val secondaryLabels = listOf("查看隐私说明", "不同意并退出")
    val secondaryWidths = rememberMeasuredWidths(
        secondaryLabels,
        Type.bodySmall.copy(color = colors.textSecondary)
    )
    val secondaryNeededPx = with(density) {
        secondaryWidths[0] + GateActionIconSize.toPx() + GateActionIconGap.toPx() +
            GateActionPadH.toPx() * 2f +
            secondaryWidths[1] + GateActionPadH.toPx() * 2f +
            Space.sm.toPx()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 卡片与底部操作区共用的外框宽度：先受上限约束，再扣掉外边距。
        // 两处用同一个值，按钮的左右边缘才与卡片边缘严格对齐。
        val blockWidth = minOf(maxWidth, GateMaxWidth)
        // 玻璃卡本身的宽度。
        val cardWidth = blockWidth - Space.md * 2
        // 卡片内容宽度：玻璃卡自己还有一层内边距。
        val contentWidth = cardWidth - Space.xl * 2
        // 底部操作区的可用宽度：与卡片同宽（它没有卡内边距）。
        val actionRowWidth = cardWidth
        val stackedSecondary = secondaryNeededPx > with(density) { actionRowWidth.toPx() }

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
                        // 卡片外再包一层留白：留白必须在玻璃卡之外，
                        // 包在卡里的话卡片仍然会顶到屏幕边缘，看着不像一张卡片。
                        Box(
                            Modifier
                                .widthIn(max = GateMaxWidth)
                                .fillMaxWidth()
                                .padding(horizontal = Space.md)
                        ) {
                            GlassSurface(
                                backdrop = backdrop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { alpha = cardAlpha },
                                // 圆角走 Radius 阶梯：此前单独写 34dp，
                                // 与其它玻璃面板（Radius.xl）不是同一档。
                                shape = GlassShape.squircle(Radius.xl),
                                tint = colors.glassTintStrong,
                                blurRadius = 26.dp,
                                lensHeight = 22.dp,
                                lensAmount = 30.dp,
                                contentPadding = PaddingValues(Space.xl)
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    GateBrandHeader(contentWidth = contentWidth)

                                    Spacer(Modifier.height(Space.lg))

                                    GateBadges(contentWidth = contentWidth)

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

                                    GateSectionTitle()

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
            }

            // 下半部分常驻底部：同意动作不跟着内容滚。
            // 六条要点在小屏上就占掉大半高度，按钮若跟着滚，
            // 用户得一路滑到底才找得到"同意"——同意页最不该出现的事就是这个。
            Column(
                Modifier
                    .widthIn(max = GateMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    // 与卡片同宽：按钮的左右边缘和卡片边缘严格对齐。
                    .padding(horizontal = Space.md)
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
                    maxLines = 3,
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
                GateSecondaryActions(
                    stacked = stackedSecondary,
                    onPolicy = { showPolicy = true },
                    onDecline = onDecline
                )
            }
        }
    }

    PrivacyPolicyDialog(visible = showPolicy, onDismiss = { showPolicy = false })
}

/**
 * 品牌区：品牌球 + 标题 + 副标题。
 *
 * 摆法取决于标题能不能与球并排放下：
 * - 并排：标题与球加起来超过卡片宽度时，标题会被挤去折行，
 *   "欢迎使用琉璃浏览器" 会变成"…浏览/器"，很难看；
 * - 上下：球在上、标题独占一行，窄屏与大字号都用这一种。
 *
 * 判断用的是实测文字宽度而不是写死的字号阈值：系统字号会缩放，
 * 写死的阈值换个字号就失效。
 */
@Composable
private fun GateBrandHeader(contentWidth: Dp) {
    val density = LocalDensity.current
    val titleStyle = Type.display.copy(color = LocalLiuliColors.current.textPrimary)
    val title = "欢迎使用琉璃浏览器"
    val titleWidth = rememberMeasuredWidth(title, titleStyle)
    val contentPx = with(density) { contentWidth.toPx() }
    val orbSize = 58.dp
    // 并排的前提：球 + 间距 + 标题都在一行里。
    // 留两像素余量：测量与实际排版之间总有零点几个像素的差。
    val inline = titleWidth + with(density) { (orbSize + Space.md).toPx() } <=
        contentPx - with(density) { 2.dp.toPx() }
    // 标题自己都放不下（超大字号）时，拆成两行的固定版式：
    // 由排版决定断点，好过让系统随手断成"浏览/器"。
    val lockupWidth = rememberMeasuredWidth("琉璃浏览器", titleStyle)
    val titleLines = when {
        titleWidth <= contentPx -> listOf(title)
        lockupWidth <= contentPx -> listOf("欢迎使用", "琉璃浏览器")
        // 连拆开都放不下：让它自己折行，好过把标题截掉半截。
        else -> listOf(title)
    }

    if (inline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 品牌球先落位，标题与副标题跟上。
            BrandOrb(
                size = orbSize,
                modifier = Modifier.gateReveal(
                    gateDelay(tier = 0, item = 0),
                    pop = true
                )
            )
            Spacer(Modifier.width(Space.md))
            GateTitles(
                lines = titleLines,
                centered = false,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandOrb(
                size = 48.dp,
                modifier = Modifier.gateReveal(
                    gateDelay(tier = 0, item = 0),
                    pop = true
                )
            )
            Spacer(Modifier.height(Space.sm))
            GateTitles(lines = titleLines, centered = true)
        }
    }
}

/**
 * 标题与副标题。
 *
 * 单独抽出来，是为了大字号下能换一种摆法：
 * 标题在品牌球右边时会被挤去折行，挪到球下方才留得下完整的一行。
 */
@Composable
private fun GateTitles(
    lines: List<String>,
    centered: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        lines.forEachIndexed { index, line ->
            if (index > 0) Spacer(Modifier.height(Space.xxs))
            BasicText(
                text = line,
                style = Type.display.copy(color = colors.textPrimary, textAlign = align),
                modifier = Modifier.gateReveal(gateDelay(tier = 0, item = 1)),
                // 拆好的两行版式各自只占一行；
                // 单行标题在极端大字号下允许自己折行，好过被截掉半截。
                maxLines = if (lines.size > 1) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(Space.xxs))
        BasicText(
            text = "液态玻璃设计 · Chromium 内核 · 一镜到底",
            style = Type.bodySmall.copy(color = colors.textTertiary, textAlign = align),
            modifier = Modifier.gateReveal(gateDelay(tier = 0, item = 2)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 卖点徽标。
 *
 * 三个徽标横排要占掉两百多像素，大字号下会超出卡片宽度。
 * 与其让它被裁掉一半（第三个徽标用户根本看不到），不如整排改成纵向：
 * 放不下就换摆法，而不是给同意页塞一条"还要横滑一下"的暗示。
 */
@Composable
private fun GateBadges(contentWidth: Dp) {
    val density = LocalDensity.current
    val badges = listOf(
        "无广告" to Glyph.Sparkle,
        "无账号" to Glyph.Shield,
        "本地数据" to Glyph.Lock
    )
    val labelStyle = Type.label.copy(color = LocalLiuliColors.current.textSecondary)
    val widths = rememberMeasuredWidths(badges.map { it.first }, labelStyle)
    val badgeWidths = widths.map { width ->
        with(density) {
            width + GateBadgeIconSize.toPx() + GateBadgeIconGap.toPx() + Space.sm.toPx() * 2f
        }
    }
    // 行内还要算上徽标之间的间隔，留一点余量免得顶到边。
    val rowWidth = badgeWidths.sum() + with(density) { Space.sm.toPx() * 2f }
    val fits = rowWidth <= with(density) { contentWidth.toPx() - 2.dp.toPx() }

    if (fits) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            badges.forEachIndexed { index, (label, glyph) ->
                GateBadge(label, glyph, gateDelay(tier = 1, item = index))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            badges.forEachIndexed { index, (label, glyph) ->
                GateBadge(label, glyph, gateDelay(tier = 1, item = index))
            }
        }
    }
}

/** 徽标里的图标尺寸。 */
private val GateBadgeIconSize = 12.dp

/** 徽标里图标与文字的间距。 */
private val GateBadgeIconGap = Space.xs

@Composable
private fun GateBadge(label: String, glyph: Glyph, delayMillis: Int) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier
            .gateReveal(delayMillis)
            .clip(GlassShape.capsule())
            .background(colors.textPrimary.copy(alpha = 0.05f))
            .padding(horizontal = Space.sm, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(GateBadgeIconGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, colors.accent, size = GateBadgeIconSize)
        BasicText(
            text = label,
            style = Type.label.copy(color = colors.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * "隐私要点"这组小标题。
 *
 * 给它一个带底的胶囊：标题与下面的正文之间要有一级落差，
 * 否则两者都是十二三号字，读起来像同一段文案。
 */
@Composable
private fun GateSectionTitle() {
    val colors = LocalLiuliColors.current
    Row(
        Modifier
            .gateReveal(gateDelay(tier = 2, item = 0))
            .clip(GlassShape.capsule())
            .background(colors.success.copy(alpha = 0.12f))
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        GlyphIcon(Glyph.Shield, colors.success, size = 13.dp)
        BasicText(
            text = "隐私要点",
            // 卡内小标题用 textPrimary：页面级分区名用强调色，
            // 卡内标题用正文色，两级各归各的。
            style = Type.sectionTitle.copy(color = colors.textPrimary),
            // 读屏时这一行是"标题"，用户可以据此跳过下面的正文。
            modifier = Modifier.semantics { heading() }
        )
    }
}

/**
 * 次级动作区。
 *
 * 层级必须一眼分得开：主按钮是实心强调色，"查看隐私说明"是淡强调色的胶囊，
 * "不同意并退出"则保持最安静的一档——它是一个必须存在的出口，但不该抢戏。
 */
@Composable
private fun GateSecondaryActions(
    stacked: Boolean,
    onPolicy: () -> Unit,
    onDecline: () -> Unit
) {
    if (stacked) {
        Column(
            Modifier
                .fillMaxWidth()
                .gateReveal(gateDelay(tier = 3, item = 2)),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            GatePolicyAction(Modifier.fillMaxWidth(), onPolicy)
            GateDeclineAction(Modifier.fillMaxWidth(), onDecline)
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .gateReveal(gateDelay(tier = 3, item = 2)),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GatePolicyAction(Modifier.weight(1f), onPolicy)
            GateDeclineAction(Modifier.weight(1f), onDecline)
        }
    }
}

/**
 * "查看隐私说明"入口。
 *
 * 这是用户唯一能读到完整条款的地方，之前它和"不同意并退出"长得一模一样，
 * 想了解的人找不到入口。给它一层淡强调色底：够显眼，但不会盖过主按钮。
 */
@Composable
private fun GatePolicyAction(modifier: Modifier, onClick: () -> Unit) {
    GateTextAction(
        label = "查看隐私说明",
        glyph = Glyph.Info,
        accented = true,
        modifier = modifier,
        onClick = onClick
    )
}

/**
 * "不同意并退出"。
 *
 * 保留出口很重要，但它必须比主按钮安静：用户最想点的是"同意并继续"。
 */
@Composable
private fun GateDeclineAction(modifier: Modifier, onClick: () -> Unit) {
    GateTextAction(
        label = "不同意并退出",
        modifier = modifier,
        onClick = onClick
    )
}

/**
 * 次级文字动作。
 *
 * "查看隐私说明"与"不同意并退出"都不该和主按钮抢注意力，
 * 但也不能弱到按下去没反应：按下时文字提亮一档，并带一点回缩。
 */
@Composable
private fun GateTextAction(
    label: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    accented: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    // 触感落在"按下"这一刻：与文字提亮、回缩同时发生，手感才对得上。
    LaunchedEffect(pressed) {
        if (pressed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val resting = if (accented) colors.accent else colors.textTertiary
    val active = if (accented) colors.accent else colors.textSecondary
    val tint by animateColorAsState(
        targetValue = if (pressed) active else resting,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "gate-action-tint"
    )
    // 强调档的底色随按压加深：光靠文字颜色变化，强调档按下时看不出变化。
    val chip by animateColorAsState(
        targetValue = if (accented) {
            colors.accent.copy(alpha = if (pressed) 0.18f else 0.10f)
        } else {
            Color.Transparent
        },
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "gate-action-chip"
    )
    Row(
        modifier
            .heightIn(min = Dimens.minTouch)
            .clip(GlassShape.capsule())
            .background(chip)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.94f, interactionSource = interaction)
            .padding(horizontal = GateActionPadH),
        verticalAlignment = Alignment.CenterVertically,
        // 居中而不是靠左：两个次级动作各占一半宽度，
        // 大字号下文字换行也不会把彼此挤出屏幕。
        horizontalArrangement = Arrangement.Center
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, tint, size = GateActionIconSize)
            Spacer(Modifier.width(GateActionIconGap))
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
        1 -> 250
        2 -> 440
        else -> 780
    }
    val itemDelay = when (tier) {
        0 -> item * 80
        1 -> item * 50
        2 -> item * 40
        else -> item * 70
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
            .padding(vertical = Space.xxs)
            .graphicsLayer {
                this.alpha = alpha
                // "减少动态效果"下只留淡入，不做横向滑入。
                if (!reduced) {
                    translationX = (1f - alpha) * 12.dp.toPx()
                }
            },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
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
            // 要点是这一屏真正要读的内容，用正文字号：
            // 十二号的小字放在承诺前面，读起来像脚注。
            style = Type.body.copy(color = colors.textSecondary)
        )
    }
}

/**
 * 实测一段文字的宽度（像素）。
 *
 * 用它来决定"标题能不能与品牌球并排""徽标要不要竖排"这类排版问题：
 * 写死字号阈值会在系统字号缩放后失效，而这一屏恰恰要在大字号下稳住。
 */
@Composable
private fun rememberMeasuredWidth(text: String, style: TextStyle): Int =
    rememberMeasuredWidths(listOf(text), style).first()

/**
 * 实测多段文字的宽度（像素）。
 *
 * 一次测量一批，省得每处都新建一个测量器；
 * 测量器本身按密度缓存，字号变化时会重建。
 */
@Composable
private fun rememberMeasuredWidths(texts: List<String>, style: TextStyle): List<Int> {
    val measurer = rememberTextMeasurer()
    return remember(texts, style, measurer) {
        texts.map { measurer.measure(it, style).size.width }
    }
}
