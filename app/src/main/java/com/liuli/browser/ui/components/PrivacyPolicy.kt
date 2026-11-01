package com.liuli.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@Composable
fun PrivacyPolicyDialog(visible: Boolean, onDismiss: () -> Unit) {
    val colors = LocalLiuliColors.current
    LiquidDialog(
        visible = visible,
        onDismiss = onDismiss,
        panelModifier = Modifier
            .padding(Space.xl)
            .widthIn(max = 520.dp)
            .heightIn(max = 640.dp)
    ) {
        // 面板背后没有可折射的内容（emptyBackdrop），模糊与折射都无从谈起。
        // 想要它读起来像玻璃而不是灰卡片，靠的是内阴影给出的厚度，
        // 再加一层从上方落下来的光。
        GlassSurface(
            backdrop = emptyBackdrop(),
            modifier = Modifier.fillMaxWidth(),
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
                    BasicText(
                        text = PRIVACY_POLICY_TEXT,
                        style = Type.bodySmall.copy(
                            color = colors.textSecondary,
                            lineHeight = 19.sp
                        ),
                        modifier = Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
