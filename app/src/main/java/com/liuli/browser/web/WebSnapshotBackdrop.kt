package com.liuli.browser.web

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/**
 * Samples a recent screenshot of the live WebView so the glass bars can refract
 * the actual page instead of a placeholder. The bitmap is refreshed when a page
 * finishes loading or when scrolling settles.
 */
class WebSnapshotBackdrop(
    bitmap: Bitmap,
    private val bounds: Rect
) : Backdrop {

    private val image = bitmap.asImageBitmap()

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val position = coordinates?.positionInWindow() ?: return
        drawImage(
            image = image,
            dstOffset = IntOffset(
                (bounds.left - position.x).roundToInt(),
                (bounds.top - position.y).roundToInt()
            ),
            dstSize = IntSize(bounds.width(), bounds.height())
        )
    }
}
