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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
 * 隐私说明里的一小节。
 *
 * 说明正文原本是一整块等宽文本，标题和内容长得一样，
 * 读者只能靠"1."、"2."去数段落。这里把标题单独提出来，
 * 层级才读得出来。
 */
private data class PolicySection(val heading: String, val lines: List<String>)

/**
 * 解析后的隐私说明：整篇标题 + 若干小节。
 */
private data class PolicyDocument(val title: String, val sections: List<PolicySection>)

/**
 * 把隐私说明拆成标题与小节。
 *
 * 空行分段：一段的第一行若是"数字 + 点"，它就是小节标题，
 * 其余行是该小节的正文。段首的"·"是权限条目，保留原样。
 * 这样结尾那句"最后更新"自然落进自己的小节，不会挂到上一节下面。
 */
private fun parsePolicy(text: String): PolicyDocument {
    val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    var title = ""
    val sections = mutableListOf<PolicySection>()
    paragraphs.forEach { paragraph ->
        val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return@forEach
        val first = lines.first()
        val isHeading = first.first().isDigit() && first.contains(". ")
        when {
            // 第一段是整篇的标题，单独提出来做导语。
            title.isEmpty() && sections.isEmpty() && !isHeading -> title = first
            isHeading -> sections += PolicySection(
                heading = first.substringAfter(". ").trim(),
                lines = lines.drop(1)
            )
            else -> sections += PolicySection(heading = "", lines = lines)
        }
    }
    return PolicyDocument(title, sections)
}

/**
 * 隐私面板的高度上限。
 *
 * 说明很长，一屏放不下；给一个上限让它在平板上不至于撑满整块屏。
 */
private val PolicyMaxPanelHeight = 640.dp

/**
 * 面板里除正文以外的部分大约占多高。
 *
 * 正文区要在这个高度之下滚动，标题才不会被挤走。
 * 包含：标题行（48dp）+ 间隔（12dp）+ 面板上下内边距（2 × 24dp）。
 * 取整：多留一点空白只是正文短一点，不会截断内容。
 */
private val PolicyHeaderAllowance = 112.dp

@Composable
fun PrivacyPolicyDialog(visible: Boolean, onDismiss: () -> Unit) {
    val colors = LocalLiuliColors.current
    val policy = remember { parsePolicy(PRIVACY_POLICY_TEXT) }
    LiquidDialog(
        visible = visible,
        onDismiss = onDismiss,
        panelModifier = Modifier
            .padding(Space.xl)
            .widthIn(max = 520.dp)
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
                    Column(Modifier.padding(Space.xl)) {
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
                        Column(
                            Modifier
                                .heightIn(
                                    max = (maxPanelHeight - PolicyHeaderAllowance)
                                        .coerceAtLeast(80.dp)
                                )
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (policy.title.isNotEmpty()) {
                                BasicText(
                                    text = policy.title,
                                    style = Type.body.copy(color = colors.textPrimary),
                                    modifier = Modifier.padding(bottom = Space.xs)
                                )
                            }
                            policy.sections.forEach { section ->
                                if (section.heading.isNotEmpty()) {
                                    BasicText(
                                        text = section.heading,
                                        style = Type.sectionTitle.copy(color = colors.textPrimary),
                                        modifier = Modifier.padding(top = Space.md, bottom = Space.xs)
                                    )
                                }
                                section.lines.forEach { line ->
                                    BasicText(
                                        text = line,
                                        style = Type.bodySmall.copy(color = colors.textSecondary),
                                        modifier = Modifier.padding(bottom = Space.xs)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
