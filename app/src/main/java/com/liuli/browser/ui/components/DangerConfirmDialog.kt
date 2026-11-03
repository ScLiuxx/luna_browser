package com.liuli.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.liuli.browser.ui.liquid.LiquidButton
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type

/**
 * 破坏性操作的确认框。
 *
 * 此前设置页与书签页各写了一份几乎一样的实现，差别只有文案：
 * 同样的标题层级、同样的按钮、同样的措辞，唯一的提示是正文里那句"不可撤销"。
 * 这里合成一个：先给一枚警示徽标，让破坏性操作在对话框里先自报身份；
 * 确认按钮改用危险色，主色按钮配"不可撤销"的文案看着像一次普通跳转。
 *
 * 徽标与确认按钮用同一个图标：徽标是盾、按钮是垃圾桶时，
 * 用户会以为这是两件事。图标由调用方给出，两处都用它。
 */
@Composable
fun DangerConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    glyph: Glyph = Glyph.Trash,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalLiuliColors.current
    // 危险色在深浅两套主题里明暗相反：浅色主题是深红、深色主题是亮红，
    // 文字颜色要跟着反过来选，否则深色主题下白字压在亮红上根本看不清。
    val onDanger = colors.onDanger
    LiquidDialog(
        visible = visible,
        onDismiss = onCancel,
        panelModifier = Modifier
            .padding(Space.xl)
            .widthIn(max = Dimens.dialogWidth)
    ) {
        GlassSurface(
            backdrop = emptyBackdrop(),
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(Radius.xl),
            tint = colors.glassTintStrong.copy(alpha = 0.96f),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            // 与隐私说明同一个面板质感：背后没有可折射的内容，
            // 内阴影给出的厚度才让它像一块玻璃而不是灰卡片。
            innerShadow = true,
            shadow = true
        ) {
            Column(Modifier.padding(Space.xl)) {
                // 徽标在标题之上：字号再大也没有一枚红色的图标来得直接，
                // "这一步会丢东西"必须比"我们在聊什么"更先被看到。
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.danger.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(glyph, colors.danger, size = 20.dp)
                }
                Spacer(Modifier.height(Space.md))
                BasicText(
                    text = title,
                    style = Type.title.copy(color = colors.textPrimary)
                )
                Spacer(Modifier.height(Space.sm))
                BasicText(
                    text = message,
                    style = Type.bodySmall.copy(color = colors.textSecondary)
                )
                Spacer(Modifier.height(Space.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GlassPillButton(
                        label = "取消",
                        onClick = onCancel,
                        backdrop = emptyBackdrop()
                    )
                    // 确认按钮用危险色：强调色的按钮看着像"去商店"，
                    // 和"不可撤销"这件事对不上。
                    LiquidButton(
                        onClick = onConfirm,
                        backdrop = emptyBackdrop(),
                        tint = colors.danger,
                        surfaceColor = colors.danger,
                        contentPadding = PaddingValues(horizontal = Space.md)
                    ) {
                        GlyphIcon(glyph, onDanger, size = 18.dp)
                        BasicText(
                            text = confirmLabel,
                            style = Type.body.copy(
                                color = onDanger,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
