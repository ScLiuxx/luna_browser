package com.liuli.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type

const val PRIVACY_POLICY_TEXT = """琉璃浏览器如何保护你的隐私

1. 我们收集什么
不收集。应用没有账号体系、统计 SDK、广告 SDK，也不会上传书签、历史记录或输入内容。

2. 需要哪些权限
· INTERNET：加载你访问的网页
不申请存储、定位、相机、麦克风、通讯录、电话等权限。

3. 数据保存在哪里
书签、历史、设置与标签页会话保存在设备本地。你可以随时在设置中清除，或撤回同意。

4. 网页权限
网页请求定位、摄像头或麦克风时默认拒绝，避免站点在未授权情况下采集信息。

5. Cookie 与跟踪
默认阻止第三方 Cookie；Cookie 仅由系统 WebView 管理，可在设置中清除。

6. 安全策略
SSL 证书错误会直接阻断访问；禁止混合内容、文件访问与通用文件 URL 访问；启用系统 Safe Browsing。

7. 第三方组件
界面使用 Apache-2.0 许可的 Backdrop 液态玻璃库与 Jetpack Compose；均不包含数据采集行为。

本说明随应用离线提供，最后更新：2026-10。"""

/**
 * 隐私说明里的一行正文。
 *
 * [bullet] 为真表示这是一条并列条目（原文以"·"开头），
 * 渲染时要缩进并带一个圆点，不能和段落混在一起。
 */
private data class PolicyLine(val text: String, val bullet: Boolean)

/**
 * 隐私说明里的一小节。
 *
 * 说明正文原本是一整块等宽文本，标题和内容长得一样，
 * 读者只能靠"1."、"2."去数段落。这里把标题单独提出来，
 * 层级才读得出来。
 */
private data class PolicySection(val number: Int?, val heading: String, val lines: List<PolicyLine>)

/**
 * 解析后的隐私说明：整篇标题 + 若干小节 + 末尾说明。
 */
private data class PolicyDocument(
    val title: String,
    val sections: List<PolicySection>,
    val closing: List<PolicyLine>
)

/**
 * 把隐私说明拆成标题、小节与末尾说明。
 *
 * 空行分段：一段的第一行若是"数字 + 点"，它就是小节标题，
 * 其余行是该小节的正文。段首的"·"是权限条目，保留原样。
 * 小节之后、不带标题的那一段是末尾说明（"最后更新"），
 * 单独拿出来，它就是全文的落点。
 */
private fun parsePolicy(text: String): PolicyDocument {
    val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    var title = ""
    val sections = mutableListOf<PolicySection>()
    val closing = mutableListOf<PolicyLine>()
    paragraphs.forEach { paragraph ->
        val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return@forEach
        val first = lines.first()
        val number = first.takeWhile { it.isDigit() }.toIntOrNull()
        val isHeading = number != null && first.contains(". ")
        when {
            // 第一段是整篇的标题，单独提出来做导语。
            title.isEmpty() && sections.isEmpty() && !isHeading -> title = first
            isHeading -> sections += PolicySection(
                number = number,
                heading = first.substringAfter(". ").trim(),
                lines = lines.drop(1).map { toPolicyLine(it) }
            )
            // 小节之后的非标题段落：末尾说明。
            sections.isNotEmpty() -> closing += lines.map { toPolicyLine(it) }
            else -> sections += PolicySection(
                number = null,
                heading = "",
                lines = lines.map { toPolicyLine(it) }
            )
        }
    }
    return PolicyDocument(title, sections, closing)
}

/** 把一行原文转成正文条目：开头是"·"的就是并列项。 */
private fun toPolicyLine(raw: String): PolicyLine {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("·")) {
        PolicyLine(trimmed.removePrefix("·").trim(), bullet = true)
    } else {
        PolicyLine(trimmed, bullet = false)
    }
}

/**
 * 隐私面板的高度上限。
 *
 * 说明很长，一屏放不下；给一个上限让它在平板上不至于撑满整块屏。
 */
private val PolicyMaxPanelHeight = 640.dp

@Composable
fun PrivacyPolicyDialog(visible: Boolean, onDismiss: () -> Unit) {
    val colors = LocalLiuliColors.current
    val policy = remember { parsePolicy(PRIVACY_POLICY_TEXT) }
    val scrollState = rememberScrollState()
    LiquidDialog(
        visible = visible,
        onDismiss = onDismiss,
        // 隐私说明是整篇文档，比普通确认框宽：
        // 380dp 的确认框宽度装不下这么多字，行会短得读起来费劲。
        maxPanelWidth = 520.dp,
        panelModifier = Modifier.padding(Space.xl)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 面板高度跟着可用高度走：此前写死 640dp，
            // 屏幕再矮一点（横屏、小屏）底部那段就被切掉。
            val maxPanelHeight = minOf(maxHeight, PolicyMaxPanelHeight)
            // 面板背后没有可折射的内容（emptyBackdrop），模糊与折射都无从谈起。
            // 想要它读起来像玻璃而不是灰卡片，靠的是内阴影给出的厚度，
            // 再加一层从上方落下来的光。
            GlassSurface(
                backdrop = emptyBackdrop(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanelHeight),
                shape = RoundedCornerShape(Radius.xl),
                tint = colors.glassTintStrong.copy(alpha = 0.96f),
                blurRadius = 0.dp,
                lensHeight = 0.dp,
                innerShadow = true,
                shadow = true
            ) {
                Box(
                    Modifier.background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (colors.isDark) 0.07f else 0.32f),
                            0.55f to Color.Transparent
                        )
                    )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            // 高度上限交给这一层，正文区才能用 weight 拿到"剩下的高度"。
                            // 此前正文区的可用高度是按固定值（标题行 48dp + 间隔 + 内边距）估出来的，
                            // 系统字号放大到 1.3 倍以上时标题行会长高，估的值就不准了，
                            // 正文会被挤出面板底部。现在由布局自己算，字体再大也不会错位。
                            .heightIn(max = maxPanelHeight)
                            .padding(Space.xl)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.Shield, colors.success, size = 22.dp)
                            Spacer(Modifier.width(Space.md))
                            BasicText(
                                text = "隐私说明",
                                style = Type.title.copy(color = colors.textPrimary),
                                modifier = Modifier.weight(1f)
                            )
                            GlassIconButton(
                                glyph = Glyph.Close,
                                contentDescription = "关闭隐私说明",
                                onClick = onDismiss,
                                backdrop = emptyBackdrop(),
                                size = 40.dp,
                                glyphSize = 16.dp
                            )
                        }
                        Spacer(Modifier.height(Space.md))
                        // 正文单独滚动，标题与关闭键始终留在原地。
                        // weight 让正文自动占满"标题之下"的全部空间：
                        // 内容短时面板跟着收短，内容长时正文滚动、面板不越界。
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(scrollState)
                        ) {
                            if (policy.title.isNotEmpty()) {
                                BasicText(
                                    text = policy.title,
                                    style = PolicyLeadStyle(color = colors.textPrimary),
                                    modifier = Modifier.padding(bottom = Space.sm)
                                )
                            }
                            policy.sections.forEach { section ->
                                PolicySectionHeader(
                                    number = section.number,
                                    heading = section.heading
                                )
                                section.lines.forEach { line ->
                                    PolicyBodyLine(line)
                                }
                            }
                            if (policy.closing.isNotEmpty()) {
                                PolicyClosing(policy.closing)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 导语样式：整篇说明的开头一句。
 *
 * 比正文大一档、比小节标题小一档，读者一眼知道"这是总起"，
 * 不会把它误读成第一条。
 */
private fun PolicyLeadStyle(color: Color) = Type.body.copy(
    fontSize = 16.sp,
    lineHeight = 24.sp,
    color = color
)

/**
 * 正文样式：14sp / 22sp 行距。
 *
 * 此前的 bodySmall 是 12sp / 17sp：字号偏小、行距只有 1.42 倍，
 * 一屏能塞下更多字，但读起来是"挤"的——这份说明是要读完的，不是扫一眼的。
 * 14sp 配 1.57 倍行距，长段落读起来才不累。
 */
private fun PolicyBodyStyle(color: Color) = Type.body.copy(
    fontSize = 14.sp,
    lineHeight = 22.sp,
    color = color
)

/**
 * 小节标题：编号 + 标题。
 *
 * 编号用一枚强调色的小方块托着，是这份说明里唯一的"分组"信号：
 * 七个小节一眼能数清，读者也能直接说"第 3 条"。
 */
@Composable
private fun PolicySectionHeader(number: Int?, heading: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.padding(top = Space.lg, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(colors.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = number.toString(),
                    style = Type.label.copy(color = colors.accent, textAlign = TextAlign.Center)
                )
            }
            Spacer(Modifier.width(Space.sm))
        }
        BasicText(
            text = heading,
            style = Type.sectionTitle.copy(fontSize = 15.sp, color = colors.textPrimary)
        )
    }
}

/** 正文里的一行：段落直接排，并列条目缩进并带圆点。 */
@Composable
private fun PolicyBodyLine(line: PolicyLine) {
    val colors = LocalLiuliColors.current
    val style = PolicyBodyStyle(colors.textSecondary)
    if (line.bullet) {
        Row(Modifier.padding(bottom = Space.xs)) {
            Box(
                Modifier
                    .padding(top = 9.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.55f))
            )
            Spacer(Modifier.width(Space.sm))
            BasicText(
                text = line.text,
                style = style,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        BasicText(
            text = line.text,
            style = style,
            modifier = Modifier.padding(bottom = Space.sm)
        )
    }
}

/**
 * 末尾说明：全文的落点。
 *
 * 一份长文档滚到底如果什么都没有，读者会以为还有内容没加载出来。
 * 这里用一条短线 + 一句"以上为全部内容"明确收尾，
 * 再把原文的"最后更新"跟在下面，形成一个可辨认的终点。
 */
@Composable
private fun PolicyClosing(closing: List<PolicyLine>) {
    val colors = LocalLiuliColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .width(56.dp)
                .height(1.dp)
                .background(colors.textTertiary.copy(alpha = 0.35f))
        )
        Spacer(Modifier.height(Space.sm))
        BasicText(
            text = "以上为全部内容",
            style = Type.caption.copy(color = colors.textTertiary, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
        closing.forEach { line ->
            Spacer(Modifier.height(Space.xs))
            BasicText(
                text = line.text,
                style = Type.bodySmall.copy(
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
