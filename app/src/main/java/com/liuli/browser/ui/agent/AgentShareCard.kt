package com.liuli.browser.ui.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.liuli.browser.web.agent.AgentCreation
import java.io.File
import java.io.FileOutputStream

/**
 * P4.3 分享卡片：完全在本机渲染，不联网、不上传。
 */
object AgentShareCard {

    // 卡片基准尺寸：1080×1440 的 3:4 竖版，所有尺寸按宽度比例缩放。
    private const val BASE_WIDTH = 1080
    private const val BASE_HEIGHT = 1440

    // 版面留白与间距（基准宽度下的像素值）。
    private const val BASE_MARGIN = 72f
    private const val BASE_TOP = 84f
    private const val BASE_BOTTOM = 88f
    private const val BRAND_HEIGHT = 34f
    private const val GAP_BRAND_TITLE = 56f
    private const val GAP_TITLE_LINE = 40f
    private const val GAP_LINE_SUMMARY = 48f
    private const val GAP_SUMMARY_SOURCE = 46f
    private const val GAP_SOURCE_ITEMS = 12f

    // 文本长度上限，超出部分以省略号截断。
    private const val MAX_TITLE_CHARS = 80
    private const val MAX_CARD_CHARS = 160
    private const val MAX_SOURCE_TITLE_CHARS = 60
    private const val MAX_URL_CHARS = 200

    private const val BRAND_TEXT = "琉璃浏览器 · 琉璃 Agent"
    private const val TITLE_FALLBACK = "琉璃 Agent 分享卡片"
    private const val SUMMARY_FALLBACK = "本机整理的一段内容摘要"

    // 配色：深蓝紫底 + 品牌紫与天蓝点缀。
    private val COLOR_BG_TOP = 0xFF101018.toInt()
    private val COLOR_BG_BOTTOM = 0xFF1B2340.toInt()
    private val COLOR_ACCENT = 0xFF7C6BFF.toInt()
    private val COLOR_ACCENT_SKY = 0xFF4FB6FF.toInt()
    private val COLOR_TITLE = 0xFFFFFFFF.toInt()
    private val COLOR_BRAND = 0xFFD3D7E6.toInt()
    private val COLOR_BODY = 0xFFC7CBDC.toInt()
    private val COLOR_MUTED = 0xFF8A90A0.toInt()

    /** 渲染一张竖版分享卡片（默认 1080×1440）。 */
    fun render(creation: AgentCreation, widthPx: Int = 1080): Bitmap {
        val width = if (widthPx > 0) widthPx else BASE_WIDTH
        val scale = width / BASE_WIDTH.toFloat()
        val height = (BASE_HEIGHT * scale).toInt()
        val margin = BASE_MARGIN * scale
        val contentWidth = (width - margin * 2).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width, height)

        // 底部来源区先排版，摘要区才知道还剩多少可用高度。
        val url = creation.sourceUrl.trim()
        val host = hostOf(url)
        val sourceTitle = creation.sourceTitle.trim()
            .ifBlank { host.ifBlank { "未知来源" } }
        val sourceTitlePaint = textPaint(30f * scale, COLOR_TITLE)
        val hostPaint = textPaint(26f * scale, COLOR_ACCENT_SKY)
        val urlPaint = textPaint(24f * scale, COLOR_MUTED)
        val sourceTitleLayout = layout(
            ellipsize(sourceTitle, MAX_SOURCE_TITLE_CHARS),
            sourceTitlePaint,
            contentWidth,
            1
        )
        val hostLayout = if (host.isBlank()) {
            null
        } else {
            layout(host, hostPaint, contentWidth, 1)
        }
        val urlLayout = if (url.isBlank()) {
            null
        } else {
            layout(ellipsize(url, MAX_URL_CHARS), urlPaint, contentWidth, 2)
        }

        // 底部来源区：从下往上依次摆放完整链接、host 与站点名。
        val sourceBlocks = listOfNotNull(urlLayout, hostLayout, sourceTitleLayout)
        val blockTops = ArrayList<Float>(sourceBlocks.size)
        var cursor = height - BASE_BOTTOM * scale
        sourceBlocks.forEachIndexed { index, block ->
            cursor -= block.height
            blockTops += cursor
            if (index < sourceBlocks.lastIndex) cursor -= GAP_SOURCE_ITEMS * scale
        }
        val hairlineY = blockTops.last() - GAP_SUMMARY_SOURCE * scale

        // 品牌行
        val brandTop = BASE_TOP * scale
        drawBrand(canvas, margin, brandTop, scale)

        // 大标题（最多两行）
        val titlePaint = textPaint(66f * scale, COLOR_TITLE, bold = true)
        val titleLayout = layout(
            ellipsize(creation.title.trim().ifBlank { TITLE_FALLBACK }, MAX_TITLE_CHARS),
            titlePaint,
            contentWidth,
            2
        )
        var y = brandTop + BRAND_HEIGHT * scale + GAP_BRAND_TITLE * scale
        drawLayout(canvas, titleLayout, margin, y)
        y += titleLayout.height + GAP_TITLE_LINE * scale

        // 分隔线
        drawDivider(canvas, margin, width - margin, y, scale)
        y += GAP_LINE_SUMMARY * scale

        // 一句话摘要（最多三行）
        val summaryPaint = textPaint(34f * scale, COLOR_BODY)
        val summaryLineHeight = summaryPaint.fontSpacing * 1.45f
        val room = (hairlineY - y).coerceAtLeast(summaryLineHeight)
        val summaryLines = (room / summaryLineHeight).toInt().coerceIn(1, 3)
        val summaryLayout = layout(
            ellipsize(creation.cardText.trim().ifBlank { SUMMARY_FALLBACK }, MAX_CARD_CHARS),
            summaryPaint,
            contentWidth,
            summaryLines
        )
        drawLayout(canvas, summaryLayout, margin, y)

        // 底部来源区：站点名 + host + 完整链接
        drawHairline(canvas, margin, width - margin, hairlineY, scale)
        sourceBlocks.zip(blockTops).forEach { (block, top) ->
            drawLayout(canvas, block, margin, top)
        }

        return bitmap
    }

    /** 把卡片写入本机缓存目录，返回 PNG 文件（同名文件会被覆盖）。 */
    fun writeToCache(context: Context, creation: AgentCreation): File {
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "liuli-card-${System.currentTimeMillis()}.png")
        val bitmap = render(creation)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    /** 通过 FileProvider 得到可分享的 content:// URI。 */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // ---------------------------------------------------------------------
    // 绘制
    // ---------------------------------------------------------------------

    /** 深色渐变底 + 两处品牌色柔光。 */
    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(COLOR_BG_TOP, COLOR_BG_BOTTOM),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), base)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.88f,
                height * 0.05f,
                width * 0.72f,
                intArrayOf(withAlpha(COLOR_ACCENT, 0.30f), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)

        val glowBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.06f,
                height * 1.02f,
                width * 0.66f,
                intArrayOf(withAlpha(COLOR_ACCENT_SKY, 0.20f), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowBottom)
    }

    /** 品牌行：渐变小方块 + 品牌名。 */
    private fun drawBrand(canvas: Canvas, margin: Float, top: Float, scale: Float) {
        val mark = BRAND_HEIGHT * scale
        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                margin,
                top,
                margin + mark,
                top + mark,
                intArrayOf(COLOR_ACCENT, COLOR_ACCENT_SKY),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            margin,
            top,
            margin + mark,
            top + mark,
            10f * scale,
            10f * scale,
            markPaint
        )
        val paint = textPaint(30f * scale, COLOR_BRAND)
        val metrics = paint.fontMetrics
        canvas.drawText(
            BRAND_TEXT,
            margin + mark + 14f * scale,
            top - metrics.top,
            paint
        )
    }

    /** 标题下方的分隔线：品牌色渐隐。 */
    private fun drawDivider(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float,
        scale: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f * scale
            shader = LinearGradient(
                left,
                0f,
                right,
                0f,
                intArrayOf(COLOR_ACCENT, COLOR_ACCENT_SKY, withAlpha(COLOR_ACCENT_SKY, 0f)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawLine(left, y, right, y, paint)
    }

    /** 来源区上方的细线。 */
    private fun drawHairline(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float,
        scale: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1.5f * scale
            color = withAlpha(Color.WHITE, 0.18f)
        }
        canvas.drawLine(left, y, right, y, paint)
    }

    // ---------------------------------------------------------------------
    // 文本
    // ---------------------------------------------------------------------

    /** 用 StaticLayout 排版，自动处理中文换行，超出行数以省略号截断。 */
    private fun layout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.35f)
        .setIncludePad(false)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setEllipsizedWidth(width)
        .build()

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(x, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun textPaint(textSize: Float, color: Int, bold: Boolean = false): TextPaint =
        TextPaint().apply {
            isAntiAlias = true
            this.textSize = textSize
            this.color = color
            setSubpixelText(true)
            typeface = if (bold) {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
        }

    /** 折叠空白并按长度截断，超长时补省略号。 */
    private fun ellipsize(text: String, limit: Int): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return ""
        return if (clean.length <= limit) clean else clean.take(limit - 1) + "…"
    }

    /** 取链接的 host；没有协议时退化为手写解析，避免丢掉出处。 */
    private fun hostOf(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val host = runCatching { java.net.URI(trimmed).host }.getOrNull()
        if (!host.isNullOrBlank()) return host.lowercase()
        val authority = trimmed.substringAfter("://", trimmed)
            .substringBefore('/')
            .substringBefore('?')
            .substringAfter('@')
        return authority.lowercase()
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255f).toInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
