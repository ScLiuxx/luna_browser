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
import java.util.regex.Pattern

/**
 * P4.3 分享卡片：完全在本机渲染，不联网、不上传。
 *
 * 这张卡片会被用户截图发给别人，所以它按"海报"而不是"界面"的标准排版：
 * 版面层级清楚、字号有对比、留白有节奏、长内容不会把版面压坏。
 *
 * 版面自上而下四层：标题 → 摘要 → 来源 → 页脚。
 * 页脚固定在卡片底部（品牌标志 + App 名 + "本机生成"徽标），
 * 接收者一眼就知道这张图是从哪儿来的。
 */
object AgentShareCard {

    // ---------------------------------------------------------------------
    // 画布
    // ---------------------------------------------------------------------

    /** 卡片基准宽度：1080px，与主流社交平台的分享图分辨率一致。 */
    private const val BASE_WIDTH = 1080

    /**
     * 画布高度区间（基准宽度下的像素）。
     *
     * 此前固定 1080×1440（3:4），于是两种坏情况同时存在：
     * 内容短时中间空出一大块，像没排完；内容长时静默截断，用户发出去的卡片少了一半内容。
     * 现在高度按内容算，再夹进这个区间：最短不低于 1:1（正文太少时不至于扁成一条横幅），
     * 最长不超过 16:9 竖版（超长正文不会把卡片拉成一根长条）。
     */
    private const val MIN_HEIGHT = 1080
    private const val MAX_HEIGHT = 1920

    // 版面留白与间距（基准宽度下的像素值）。
    private const val BASE_MARGIN = 72f
    private const val BASE_TOP = 84f
    private const val BASE_BOTTOM = 88f
    private const val FOOTER_MARK = 22f
    private const val FOOTER_TEXT_GAP = 10f
    private const val GAP_TITLE_LINE = 40f
    private const val GAP_LINE_SUMMARY = 48f
    private const val TITLE_ACCENT_WIDTH = 148f
    private const val TITLE_ACCENT_HEIGHT = 8f
    private const val GAP_SUMMARY_SOURCE = 46f
    private const val GAP_HAIRLINE_SOURCE = 40f
    private const val GAP_SOURCE_ITEMS = 12f
    private const val GAP_SOURCE_FOOTER = 40f

    /**
     * 摘要左侧留出的竖条位置。
     *
     * 摘要是一段"引用"，给它一条与品牌色同源的竖条，
     * 读者一眼就知道这是一段摘录，而不是又一段正文。
     */
    private const val QUOTE_INSET = 28f

    /**
     * 行距倍率。
     *
     * 排版与"能放下几行"的估算必须用同一个值：
     * 估算用 1.45、排版用 1.35 时会少算半行，摘要区凭空空出一截。
     */
    private const val LINE_SPACING = 1.35f

    // 文本长度上限。
    // 这里只是"兜底闸门"：真正决定显示多少的是 StaticLayout 的行数限制，
    // 闸门设得比实际能排下的行数宽松很多，避免在字与字之间硬切。
    private const val MAX_TITLE_CHARS = 240
    private const val MAX_CARD_CHARS = 600
    private const val MAX_SOURCE_TITLE_CHARS = 160
    private const val MAX_URL_CHARS = 400

    /** 标题最多两行；再多就不是标题而是正文了。 */
    private const val MAX_TITLE_LINES = 2

    /** 摘要最多八行：再多就不是"一句话摘要"，而是正文搬运。 */
    private const val MAX_SUMMARY_LINES = 8

    /** 完整链接最多两行。 */
    private const val MAX_URL_LINES = 2

    /** 缓存里最多留几张卡片：每分享一次就写一张新的 PNG，不清理会一直涨。 */
    private const val MAX_CACHED_CARDS = 3

    // 字号（基准宽度下的像素值）。
    // 卡片会被缩到手机屏幕宽（约 1080px）看，42px 的正文在 3 倍屏上约等于 14dp，
    // 是能舒服读完的最小字号；此前的 34px 只有 11dp，读起来要眯眼。
    private const val TITLE_TEXT_SIZE = 72f
    private const val SUMMARY_TEXT_SIZE = 42f
    private const val SOURCE_LABEL_SIZE = 24f
    private const val SOURCE_TITLE_SIZE = 32f
    private const val HOST_TEXT_SIZE = 28f
    private const val URL_TEXT_SIZE = 26f
    private const val FOOTER_TEXT_SIZE = 26f
    private const val BADGE_TEXT_SIZE = 24f

    private const val BRAND_TEXT = "琉璃浏览器 · 琉璃 Agent"
    private const val LOCAL_BADGE = "本机生成"
    private const val SOURCE_LABEL = "来源"
    private const val UNKNOWN_SOURCE = "未知来源"
    private const val TITLE_FALLBACK = "琉璃 Agent 分享卡片"
    private const val SUMMARY_FALLBACK = "本机整理的一段内容摘要"

    // 配色：深蓝紫底 + 品牌紫与天蓝点缀。
    // 这张图会被转发出去，深色是品牌的固定识别，不随系统主题变化：
    // 同一张卡在别人手机上是同一个样子，比"跟着主题变"更像一件成品。
    // 底色最深处 #101018、最浅处 #1B2340；下面几档文字与它对比都在 4.5:1 以上。
    private val COLOR_BG_TOP = 0xFF101018.toInt()
    private val COLOR_BG_BOTTOM = 0xFF1B2340.toInt()
    private val COLOR_ACCENT = 0xFF7C6BFF.toInt()
    private val COLOR_ACCENT_SKY = 0xFF4FB6FF.toInt()
    private val COLOR_TITLE = 0xFFFFFFFF.toInt()
    private val COLOR_BRAND = 0xFFD3D7E6.toInt()
    private val COLOR_BODY = 0xFFC7CBDC.toInt()
    // 来源区是页脚，用比正文更弱的一档颜色：
    // 此前它用的是纯白，比上方的摘要正文还亮，层级整个倒了过来。
    private val COLOR_SOURCE_TITLE = 0xFFAEB4C6.toInt()
    private val COLOR_MUTED = 0xFF9AA2B4.toInt()

    // ---------------------------------------------------------------------
    // 文本预处理
    // ---------------------------------------------------------------------

    /** 连续空白（含换行）压成一个空格：卡片是单栏版式，换行在这里没有意义。 */
    private val WHITESPACE = Regex("\\s+")

    /** Markdown 链接 [文字](地址)：卡片上只留文字，地址另有一行可读。 */
    private val MARKDOWN_LINK = Regex("\\[([^\\]\\n]{1,80})]\\((https?://[^)\\s]{1,200})\\)")

    /** Markdown 装饰（井号标题、列表符、粗体、代码围栏）：纯位图上它们只是噪点。 */
    private val MARKDOWN_NOISE = Regex(
        "(?m)^[ \\t]{0,3}(#{1,6}[ \\t]*|[-*+][ \\t]+|>[ \\t]?)" +
            "|(\\*\\*\\*|\\*\\*|__|~~|`{1,3})"
    )

    /**
     * 引用编号（如 "[1]"）。
     *
     * 卡片只有一条来源（页面链接），没有编号对照表；
     * 挂在句尾的 "[3]" 会被读成乱码，中间的 "[1]" 更是指向一个读者看不到的清单。
     * 编号是给站内那份带台账的回答用的，不该跟着摘录跑到卡片上，所以整体去掉。
     */
    private val CITATION = Regex("[\\[【]\\d{1,2}[\\]】]")

    /**
     * 把任意来源文本收拾成适合排版的一行。
     *
     * 素材来自页面抽取，里面常混着 Markdown 标记、链接语法与硬换行；
     * 直接画到图上会把这些噪点一起带出去。
     */
    private fun plainText(raw: String): String {
        val text = MARKDOWN_LINK.replace(raw) { it.groupValues[1] }
        return MARKDOWN_NOISE.replace(text, "")
            .replace(CITATION, "")
            .replace(WHITESPACE, " ")
            .trim()
    }

    /**
     * 按码点截断，避免把代理对劈开。
     *
     * 直接用 take(limit) 会在 emoji 落在边界上时切掉半个代理对，
     * 画出来就是一个豆腐块。offsetByCodePoints 永远落在字符边界上。
     */
    private fun truncate(text: String, limit: Int): String {
        if (text.codePointCount(0, text.length) <= limit) return text
        val end = text.offsetByCodePoints(0, limit)
        return text.substring(0, end)
    }

    /** 渲染一张 3:4 竖版分享卡片（默认 1080 宽）。 */
    fun render(creation: AgentCreation, widthPx: Int = 1080): Bitmap {
        // 宽度上限：调用方传一个离谱的值会直接 OOM，
        // 卡片本来就是给社交平台用的，1600 宽足够清晰。
        val width = widthPx.coerceIn(480, 1600)
        val scale = width / BASE_WIDTH.toFloat()
        val margin = BASE_MARGIN * scale
        val contentWidth = (width - margin * 2).toInt().coerceAtLeast(1)

        // -----------------------------------------------------------------
        // 文案
        // -----------------------------------------------------------------
        val url = creation.sourceUrl.trim()
        val host = hostOf(url)
        val rawSourceTitle = plainText(creation.sourceTitle.trim())
        val titleText = plainText(creation.title.trim().ifBlank { TITLE_FALLBACK })
        var summaryText = plainText(creation.cardText.trim().ifBlank { SUMMARY_FALLBACK })
        // 本机生成的摘要末尾会跟着"—— host"，而下方来源区已经有 host：
        // 同一张卡上出现两遍域名是多余的，去掉。
        if (host.isNotBlank()) {
            summaryText = summaryText.replace(
                Regex("\\s*——\\s*" + Pattern.quote(host) + "$"),
                ""
            )
        }

        // -----------------------------------------------------------------
        // 画笔
        // -----------------------------------------------------------------
        val titlePaint = textPaint(TITLE_TEXT_SIZE * scale, COLOR_TITLE, bold = true)
        val summaryPaint = textPaint(SUMMARY_TEXT_SIZE * scale, COLOR_BODY)
        val labelPaint = textPaint(SOURCE_LABEL_SIZE * scale, COLOR_MUTED)
        val sourceTitlePaint = textPaint(SOURCE_TITLE_SIZE * scale, COLOR_SOURCE_TITLE)
        val hostPaint = textPaint(HOST_TEXT_SIZE * scale, COLOR_ACCENT_SKY)
        val urlPaint = textPaint(URL_TEXT_SIZE * scale, COLOR_MUTED)

        // -----------------------------------------------------------------
        // 量版式
        // -----------------------------------------------------------------
        val titleTextLimited = truncate(titleText, MAX_TITLE_CHARS)
        val summaryTextLimited = truncate(summaryText, MAX_CARD_CHARS)

        // 标题（最多两行）
        var titleLayout = layout(titleTextLimited, titlePaint, contentWidth, MAX_TITLE_LINES)

        // 摘要：左侧给竖条让位，正文缩进
        val inset = QUOTE_INSET * scale
        val summaryWidth = (contentWidth - inset).toInt().coerceAtLeast(1)
        var summaryLayout = layout(summaryTextLimited, summaryPaint, summaryWidth, MAX_SUMMARY_LINES)

        // 来源区：标签、站点名、host、完整链接
        // 站点名与 host 相同时不重复画：页面标题常常就是域名，
        // 同一个字符串在卡上出现两行只会显得是排版出错。
        val sourceTitle = rawSourceTitle.ifBlank { host.ifBlank { UNKNOWN_SOURCE } }
        val sourceItems = ArrayList<StaticLayout>(4)
        sourceItems += layout(SOURCE_LABEL, labelPaint, contentWidth, 1)
        sourceItems += layout(
            truncate(sourceTitle, MAX_SOURCE_TITLE_CHARS),
            sourceTitlePaint,
            contentWidth,
            1
        )
        if (host.isNotBlank() && !host.equals(sourceTitle, ignoreCase = true)) {
            sourceItems += layout(host, hostPaint, contentWidth, 1)
        }
        var urlLayout: StaticLayout? = null
        if (url.isNotBlank()) {
            // 长链接从中间截断：结尾往往才是真正有区别的那一段（?id=…），
            // 只砍尾巴会把有用的信息一起砍掉。
            urlLayout = layout(
                truncate(url, MAX_URL_CHARS),
                urlPaint,
                contentWidth,
                MAX_URL_LINES,
                TextUtils.TruncateAt.MIDDLE
            )
            sourceItems += urlLayout
        }

        // -----------------------------------------------------------------
        // 排版求解
        // -----------------------------------------------------------------
        // 版面是"上（标题）— 中（摘要）— 下（来源 + 页脚）"三段：
        // 标题贴顶，出处与品牌信息贴底，摘要在中间那段里居中。
        // 固定高度画布（此前 1080×1440 写死）会让短内容在中下方空出一大块，
        // 长内容则被静默截断；现在画布高度跟着内容走，
        // 只在极端情况下才夹到一个合理区间内。
        val canvasHeightMax = MAX_HEIGHT * scale
        val canvasHeightMin = MIN_HEIGHT * scale

        /** 来源区的高度：各行文字高度之和，加上行间距。 */
        fun sourceHeight(): Float = sourceItems.sumOf { it.height } +
            GAP_SOURCE_ITEMS * scale * (sourceItems.size - 1).coerceAtLeast(0)

        /** 内容自然需要多高（用固定间距计算，不含被摊开的留白）。 */
        fun naturalHeight(): Float = BASE_TOP * scale +
            titleLayout.height +
            GAP_TITLE_LINE * scale +
            TITLE_ACCENT_HEIGHT * scale +
            GAP_LINE_SUMMARY * scale +
            summaryLayout.height +
            GAP_SUMMARY_SOURCE * scale +
            GAP_HAIRLINE_SOURCE * scale +
            sourceHeight() +
            GAP_SOURCE_FOOTER * scale +
            FOOTER_MARK * scale +
            BASE_BOTTOM * scale

        // 内容装不下时，按重要性依次牺牲：
        // 摘要行数 → 链接行数 → 标题行数。
        // 摘要最该让位（它是"节选"）；链接压到一行仍能读；
        // 标题是这张卡的身份，放到最后才动。
        // 每一步都重新量一次实际高度，而不是拿字号估算半行。
        var guard = 0
        while (naturalHeight() > canvasHeightMax && guard < 12) {
            guard++
            if (summaryLayout.lineCount > 1) {
                val next = layout(
                    summaryTextLimited,
                    summaryPaint,
                    summaryWidth,
                    summaryLayout.lineCount - 1
                )
                if (next.lineCount < summaryLayout.lineCount) {
                    summaryLayout = next
                    continue
                }
            }
            if (urlLayout != null && urlLayout.lineCount > 1) {
                val next = layout(
                    truncate(url, MAX_URL_CHARS),
                    urlPaint,
                    contentWidth,
                    1,
                    TextUtils.TruncateAt.MIDDLE
                )
                if (next.lineCount < urlLayout.lineCount) {
                    urlLayout = next
                    sourceItems[sourceItems.lastIndex] = next
                    continue
                }
            }
            if (titleLayout.lineCount > 1) {
                val next = layout(titleTextLimited, titlePaint, contentWidth, 1)
                if (next.lineCount < titleLayout.lineCount) {
                    titleLayout = next
                    continue
                }
            }
            break
        }

        val canvasHeight = naturalHeight().coerceIn(canvasHeightMin, canvasHeightMax)

        // -----------------------------------------------------------------
        // 定位
        // -----------------------------------------------------------------
        // 标题贴顶，下面跟一小段品牌色短杠（标题自己的下划线）。
        val titleTop = BASE_TOP * scale
        val accentY = titleTop + titleLayout.height + GAP_TITLE_LINE * scale

        // 页脚钉在卡片底部，来源区紧贴在页脚上方：
        // 出处与品牌信息是"落款"，永远待在角上，不随内容长短上下漂。
        val footerTop = canvasHeight - BASE_BOTTOM * scale - FOOTER_MARK * scale
        val sourceTop = footerTop - GAP_SOURCE_FOOTER * scale - sourceHeight()
        val hairlineY = sourceTop - GAP_HAIRLINE_SOURCE * scale

        // 摘要在这段带子里垂直居中：
        // 内容短时它浮在卡片中段，像海报上的一句引文；
        // 内容长时它自然填满，上下留白收紧。
        // 若把余量摊进几个小间距里，短卡片的中下方会留一个说不清的大洞。
        val bandTop = accentY + TITLE_ACCENT_HEIGHT * scale + GAP_LINE_SUMMARY * scale
        val bandBottom = hairlineY - GAP_SUMMARY_SOURCE * scale
        val summaryTop = bandTop + (bandBottom - bandTop - summaryLayout.height).coerceAtLeast(0f) / 2f
        var sourceY = sourceTop

        // -----------------------------------------------------------------
        // 画
        // -----------------------------------------------------------------
        val bitmap = Bitmap.createBitmap(width, canvasHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width, canvasHeight.toInt())

        drawLayout(canvas, titleLayout, margin, titleTop)
        drawTitleAccent(canvas, margin, accentY, scale)

        // 摘要：竖条 + 缩进的正文
        drawQuoteBar(canvas, margin, summaryTop, summaryLayout.height.toFloat(), scale)
        drawLayout(canvas, summaryLayout, margin + inset, summaryTop)

        drawHairline(canvas, margin, width - margin, hairlineY, scale)
        sourceItems.forEachIndexed { index, item ->
            drawLayout(canvas, item, margin, sourceY)
            sourceY += item.height
            if (index < sourceItems.lastIndex) sourceY += GAP_SOURCE_ITEMS * scale
        }

        drawHairline(canvas, margin, width - margin, footerTop - GAP_SOURCE_FOOTER * scale / 2f, scale)
        drawFooter(canvas, margin, footerTop, scale, width - margin)

        return bitmap
    }

    /** 把卡片写入本机缓存目录，返回 PNG 文件（同名文件会被覆盖）。 */
    fun writeToCache(context: Context, creation: AgentCreation): File {
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        // 每次分享都会写一张新的 PNG；不清理的话缓存目录会一直涨。
        pruneOldCards(dir)
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

    /** 只留最近几张卡片，其余删掉。 */
    private fun pruneOldCards(dir: File) {
        val cards = dir.listFiles { file ->
            file.isFile && file.name.startsWith("liuli-card-") && file.name.endsWith(".png")
        }?.sortedByDescending { it.lastModified() } ?: return
        cards.drop(MAX_CACHED_CARDS).forEach { it.delete() }
    }

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

    /** 页脚：品牌标志 + App 名，右侧挂一枚"本机生成"徽标。 */
    private fun drawFooter(canvas: Canvas, margin: Float, top: Float, scale: Float, right: Float) {
        val mark = FOOTER_MARK * scale
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
        val radius = 7f * scale
        canvas.drawRoundRect(margin, top, margin + mark, top + mark, radius, radius, markPaint)

        val paint = textPaint(FOOTER_TEXT_SIZE * scale, COLOR_BRAND)
        val metrics = paint.fontMetrics
        // 品牌名按方块的中心线对齐，而不是按字体的 top 对齐：
        // 中英混排时字体 top 与视觉中线并不重合，按 top 对齐字会浮在方块上沿。
        val baseline = top + mark / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(
            BRAND_TEXT,
            margin + mark + FOOTER_TEXT_GAP * scale,
            baseline,
            paint
        )
        // "本机生成"是这张卡最想说的话：内容没上过云。
        drawLocalBadge(canvas, right, top + mark / 2f, scale)
    }

    /** 右上角的"本机生成"徽标：半透明描边胶囊。 */
    private fun drawLocalBadge(canvas: Canvas, right: Float, centerY: Float, scale: Float) {
        val paint = textPaint(BADGE_TEXT_SIZE * scale, COLOR_ACCENT_SKY)
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(LOCAL_BADGE)
        val padH = 16f * scale
        val padV = 10f * scale
        val badgeHeight = (metrics.descent - metrics.ascent) + padV * 2
        val badgeWidth = textWidth + padH * 2
        val left = right - badgeWidth
        val rectTop = centerY - badgeHeight / 2f
        val rectBottom = rectTop + badgeHeight

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(COLOR_ACCENT_SKY, 0.12f)
        }
        canvas.drawRoundRect(
            left,
            rectTop,
            right,
            rectBottom,
            badgeHeight / 2f,
            badgeHeight / 2f,
            fill
        )
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
            color = withAlpha(COLOR_ACCENT_SKY, 0.45f)
        }
        canvas.drawRoundRect(
            left,
            rectTop,
            right,
            rectBottom,
            badgeHeight / 2f,
            badgeHeight / 2f,
            stroke
        )
        // 文字按胶囊中线居中。此前基线是拿"字体的 top"推出来的，
        // 结果整行字掉到胶囊下面，挂在底沿外面。
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(
            LOCAL_BADGE,
            left + padH,
            baseline,
            paint
        )
    }

    /**
     * 标题下方的短色条。
     *
     * 此前是一条贯穿整幅的渐变分隔线：标题与摘要被它硬生生分成两块，
     * 摘要若在中间悬着，那条线看起来像是浮在半空、无所依附。
     * 改成一小段品牌色渐变的短杠，它就是标题自己的下划线。
     */
    private fun drawTitleAccent(canvas: Canvas, left: Float, y: Float, scale: Float) {
        val length = TITLE_ACCENT_WIDTH * scale
        val thickness = TITLE_ACCENT_HEIGHT * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left,
                y,
                left + length,
                y,
                intArrayOf(COLOR_ACCENT, COLOR_ACCENT_SKY),
                null,
                Shader.TileMode.CLAMP
            )
        }
        val radius = thickness / 2f
        canvas.drawRoundRect(left, y, left + length, y + thickness, radius, radius, paint)
    }

    /** 摘要左侧的品牌色竖条：把这段文字标成"摘录"。 */
    private fun drawQuoteBar(
        canvas: Canvas,
        x: Float,
        top: Float,
        height: Float,
        scale: Float
    ) {
        if (height <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x,
                top,
                x,
                top + height,
                intArrayOf(COLOR_ACCENT, withAlpha(COLOR_ACCENT_SKY, 0.15f)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        val barWidth = 6f * scale
        val radius = barWidth / 2f
        canvas.drawRoundRect(x, top, x + barWidth, top + height, radius, radius, paint)
    }

    /** 分隔细线。 */
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
        maxLines: Int,
        ellipsize: TextUtils.TruncateAt = TextUtils.TruncateAt.END
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, LINE_SPACING)
        .setIncludePad(false)
        .setMaxLines(maxLines)
        .setEllipsize(ellipsize)
        .setEllipsizedWidth(width.coerceAtLeast(1))
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
            // 用 "sans-serif" 而不是 Typeface.DEFAULT：前者会把字重传递给中文字体，
            // 后者只加粗拉丁字母，中文标题会细一大截。
            typeface = if (bold) {
                Typeface.create("sans-serif", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
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
        // 去掉端口号，但别把 IPv6 的方括号一起切掉。
        val withoutPort = if (authority.startsWith("[")) {
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }
        return withoutPort.lowercase()
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255f).toInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
