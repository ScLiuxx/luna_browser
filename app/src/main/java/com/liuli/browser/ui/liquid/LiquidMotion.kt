package com.liuli.browser.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pointer-driven specular highlight from AndroidLiquidGlass's InteractiveHighlight.
 * It is used by every liquid button/tab to light up the glass under the finger.
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    // 归位动画跑在 0f→1f 的进度上，再自己插值出位置。
    // 直接对 Offset 做弹簧需要按维度向量化，Compose 那套 API 只覆盖 Dp/DpOffset，
    // 自己搭一套反而更重。
    private val positionAnimationSpec = spring(0.5f, 300f, 0.001f)

    private val pressProgressAnimation = Animatable(0f, 0.001f)

    // 高光位置用一个普通状态，而不是 Animatable：
    // 拖拽期间要 1:1 跟手，而 Animatable 的落位是挂起调用，
    // 在指针回调里只能另起协程——一次拖拽上百个指针事件，
    // 上百个协程互相打断，高光会明显滞后于手指。
    // 位置本身不需要动画，松手后的归位才需要。
    private val positionState = mutableStateOf(Offset.Zero)

    private var startPosition = Offset.Zero
    private var springBackJob: Job? = null

    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionState.value - startPosition

    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
                shader.apply {
                    val position = position(size, positionState.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.15f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        position.x.fastCoerceIn(0f, size.width),
                        position.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                // 直接落位，不走动画：按下瞬间高光就该出现在指下，
                // 走一次弹簧反而让按下有延迟感。
                positionState.value = down.position
                // 上一次的归位动画可能还在跑，它会跟这次拖拽抢位置。
                springBackJob?.cancel()
                springBackJob = null
                animationScope.launch {
                    pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
                }
            },
            onDragEnd = {
                animationScope.launch {
                    pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
                }
                startSpringBack()
            },
            onDragCancel = {
                animationScope.launch {
                    pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
                }
                startSpringBack()
            }
        ) { change, _ ->
            // 1:1 跟手：直接写状态。
            // 早先这里每个指针事件都起一个协程去 snapTo，
            // 一次拖动上百个事件、上百个协程互相打断，
            // 高光会明显滞后于手指，玻璃的"被拖动"感全丢了。
            positionState.value = change.position
        }
    }

    /**
     * 松手后高光弹回按下时的位置。
     *
     * 高光是随手指走的，玻璃被拖歪之后得自己回正——
     * 这一下回弹是"有弹性"最直接的来源。
     */
    private fun startSpringBack() {
        springBackJob?.cancel()
        val from = positionState.value
        val to = startPosition
        springBackJob = animationScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = positionAnimationSpec
            ) { progress, _ ->
                positionState.value = Offset(
                    x = from.x + (to.x - from.x) * progress,
                    y = from.y + (to.y - from.y) * progress
                )
            }
        }
    }
}

/**
 * Spring-driven value used by the glass toggle, slider and bottom tabs.
 * This is the same damped drag model used by the AndroidLiquidGlass catalog.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    /** 数值动画的阻尼比；默认临界阻尼（不过冲），液态场景可传入更小的值。 */
    val valueDampingRatio: Float = 1f,
    /** 数值动画的刚度。 */
    val valueStiffness: Float = 1000f,
    /**
     * 是否响应系统的"减少动态效果"。
     * 开启后弹簧不过冲、收得更快，液滴不再有甩出去的过冲感。
     */
    val reducedMotion: Boolean = false,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = if (reducedMotion) {
        // 减少动态效果：临界阻尼 + 高刚度，快而稳，不产生过冲。
        spring(dampingRatio = 1f, stiffness = 2000f, visibilityThreshold = visibilityThreshold)
    } else {
        spring(
            dampingRatio = valueDampingRatio,
            stiffness = valueStiffness,
            visibilityThreshold = visibilityThreshold
        )
    }
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = if (reducedMotion) {
        spring(1f, 800f, 0.001f)
    } else {
        spring(0.6f, 250f, 0.001f)
    }
    private val scaleYAnimationSpec = if (reducedMotion) {
        spring(1f, 800f, 0.001f)
    } else {
        spring(0.7f, 250f, 0.001f)
    }

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    // 手指速度单独跟踪：数值动画的速度说的是"自己动得多快"，
    // 甩动手感需要的是"手指甩得多快"，两者不是一回事。
    private val pointerVelocityTracker = VelocityTracker()

    /**
     * 最近一次拖拽中手指的横向速度（像素/秒）。
     * 调用方按自己的单位换算（例如除以标签宽度得到"标签/秒"）。
     */
    var pointerVelocity: Float = 0f
        private set

    val value: Float get() = valueAnimation.value
    val progress: Float
        get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                pointerVelocityTracker.resetTracking()
                pointerVelocityTracker.addPointerInputChange(down)
                pointerVelocity = 0f
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            pointerVelocityTracker.addPointerInputChange(change)
            pointerVelocity = pointerVelocityTracker.calculateVelocity().x
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    /**
     * 直接跳到目标值，不走弹簧。
     * 拖拽过程中必须用它，否则拇指会落后于手指半拍。
     */
    fun snapValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.snapTo(targetValue)
        }
    }

    /**
     * 上报拖拽速度（value 单位/秒）。
     * 液滴据此在运动方向上拉伸，停手后自然回弹。
     */
    fun reportDragVelocity(dragVelocity: Float) {
        animationScope.launch {
            velocityAnimation.animateTo(dragVelocity, velocityAnimationSpec)
        }
    }

    /**
     * 弹到目标值。
     * [initialVelocity] 用于甩动：松手时把手指的速度交给弹簧，
     * 液滴会带着惯性继续走，狠甩比轻推落得更远。
     */
    fun animateToValue(value: Float, initialVelocity: Float = 0f) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch {
                    valueAnimation.animateTo(targetValue, valueAnimationSpec, initialVelocity)
                }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.nanoTime() / 1_000_000L,
            Offset(value, 0f)
        )
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = drag(pointerId = down.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent else pointer = otherDown.id
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) return dragEvent
        }
    }
}
