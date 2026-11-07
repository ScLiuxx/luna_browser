package com.liuli.browser.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.liuli.browser.data.LibraryStore
import com.liuli.browser.data.SettingsStore
import com.liuli.browser.ui.components.AuroraBackground
import com.liuli.browser.ui.screens.PrivacyGate
import com.liuli.browser.ui.theme.LiuliTheme
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.LocalReduceMotion
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.reduceMotion

@Composable
fun LiuliApp(
    incomingUrl: String?,
    onIncomingConsumed: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val settings = remember { SettingsStore(context.applicationContext) }
    val library = remember { LibraryStore(context.applicationContext) }

    // "减少动态效果"此前只有极光背景一处生效，这里提升为全局开关。
    //
    // 它必须包在 LiuliTheme **外面**：主题过渡本身也要读这个开关
    // （开了就把颜色过渡降级成最短档），开关放在主题里面，主题读不到。
    CompositionLocalProvider(LocalReduceMotion provides settings.reduceMotion) {
        LiuliTheme(
            darkMode = settings.darkModeOption,
            accentIndex = settings.accentIndex
        ) {
            val colors = LocalLiuliColors.current
            val view = LocalView.current
            val backdrop = rememberLayerBackdrop()
            var backgroundActive by remember { mutableStateOf(true) }

            SideEffect {
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = !colors.isDark
                    isAppearanceLightNavigationBars = !colors.isDark
                }
            }

            Box(Modifier.fillMaxSize()) {
                AuroraBackground(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize(),
                    dynamic = settings.dynamicBackground && !settings.reduceMotion && backgroundActive
                )

                // 隐私告知页与主界面之间淡入淡出：
                // 同意后不是"啪"地一下换屏，而是内容交叉淡化。
                // transitionSpec 不是组合上下文，规格要用非组合版本。
                val reduced = reduceMotion
                AnimatedContent(
                    targetState = settings.privacyAccepted,
                    transitionSpec = {
                        if (reduced) {
                            // "减少动态效果"：只交叉淡化，不做位移与缩放。
                            fadeIn(MotionSpec.enter(reduced, Motion.Fast)) togetherWith
                                fadeOut(MotionSpec.exit(reduced, Motion.Fast))
                        } else {
                            // 主界面朝观众压过来，隐私页往后退：
                            // 同意之后界面像"走近了一步"，而不是硬切。
                            val enter = fadeIn(MotionSpec.enter(reduced)) +
                                slideInVertically(
                                    animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                                    initialOffsetY = { it / 12 }
                                ) +
                                scaleIn(
                                    animationSpec = MotionSpec.pageSpring(reduced),
                                    initialScale = 0.98f
                                )
                            val exit = fadeOut(MotionSpec.exit(reduced)) +
                                slideOutVertically(
                                    animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                                    targetOffsetY = { -it / 16 }
                                ) +
                                scaleOut(
                                    animationSpec = MotionSpec.pageSpring(reduced),
                                    targetScale = 1.02f
                                )
                            enter togetherWith exit
                        }
                    },
                    label = "gate-transition"
                ) { accepted ->
                    if (!accepted) {
                        PrivacyGate(
                            backdrop = backdrop,
                            onAccept = { settings.acceptPrivacy() },
                            onDecline = { activity.finishAffinity() }
                        )
                    } else {
                        BrowserRoot(
                            settings = settings,
                            library = library,
                            auroraBackdrop = backdrop,
                            incomingUrl = incomingUrl,
                            onIncomingConsumed = onIncomingConsumed,
                            onBackgroundActiveChanged = { backgroundActive = it }
                        )
                    }
                }
            }
        }
    }
}
