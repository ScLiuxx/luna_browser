package com.liuli.browser.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.liuli.browser.data.LibraryStore
import com.liuli.browser.data.SettingsStore
import com.liuli.browser.ui.components.AuroraBackground
import com.liuli.browser.ui.screens.PrivacyGate
import com.liuli.browser.ui.theme.LiuliTheme
import com.liuli.browser.ui.theme.LocalLiuliColors

@Composable
fun LiuliApp(
    incomingUrl: String?,
    onIncomingConsumed: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val settings = remember { SettingsStore(context.applicationContext) }
    val library = remember { LibraryStore(context.applicationContext) }

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

            if (!settings.privacyAccepted) {
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
