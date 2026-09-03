package com.smarthealth.vitalhub.feature.analysis

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.SpanFactory
import io.noties.markwon.SpannableBuilder
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.tag.SimpleTagHandler
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImageSizeResolver
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import java.net.URLDecoder
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt
import org.commonmark.node.Link

internal data class MarkdownRenderBlock(
    val markdown: String,
    val tableColumnCount: Int? = null,
) {
    val isTable: Boolean get() = tableColumnCount != null
}

internal fun normalizeFootnotes(markdown: String): String {
    val definitions = linkedMapOf<String, String>()
    val contentLines = mutableListOf<String>()
    var fencedCodeMarker: String? = null
    markdown.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        val fence = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fence != null) {
            fencedCodeMarker = if (fencedCodeMarker == fence) null else fencedCodeMarker ?: fence
            contentLines += line
            return@forEach
        }
        val definition = if (fencedCodeMarker == null) FOOTNOTE_DEFINITION.matchEntire(line) else null
        if (definition == null) {
            contentLines += line
        } else {
            definitions[definition.groupValues[1]] = definition.groupValues[2]
        }
    }
    if (definitions.isEmpty()) return markdown

    val normalized = contentLines.joinToString("\n") { line ->
        replaceFootnoteReferences(line, definitions.keys)
    }.trimEnd()
    val renderedDefinitions = definitions.entries.joinToString("\n\n") { (label, text) ->
        "**〔$label〕** $text [返回正文](vitalhub-footnote://reference/${encodeFootnoteLabel(label)})"
    }
    return "$normalized\n\n---\n\n### 脚注\n\n$renderedDefinitions"
}

private fun replaceFootnoteReferences(line: String, labels: Set<String>): String {
    val output = StringBuilder(line.length)
    var inInlineCode = false
    var index = 0
    while (index < line.length) {
        if (line[index] == '`' && !line.isEscaped(index)) {
            inInlineCode = !inInlineCode
            output.append(line[index++])
            continue
        }
        val match = if (!inInlineCode) FOOTNOTE_REFERENCE.find(line, index) else null
        if (match != null && match.range.first == index && match.groupValues[1] in labels) {
            val label = match.groupValues[1]
            output.append('[')
                .append('［')
                .append(label)
                .append('］')
                .append("](vitalhub-footnote://definition/")
                .append(encodeFootnoteLabel(label))
                .append(')')
            index = match.range.last + 1
        } else {
            output.append(line[index++])
        }
    }
    return output.toString()
}

private fun encodeFootnoteLabel(label: String): String =
    java.net.URLEncoder.encode(label, Charsets.UTF_8.name())

private val FOOTNOTE_DEFINITION = Regex("^\\[\\^([^]]+)]\\s*:\\s*(.+)$")
private val FOOTNOTE_REFERENCE = Regex("\\[\\^([^]]+)]")

internal fun normalizeInlineLatex(markdown: String): String {
    var fencedCodeMarker: String? = null
    var latexBlock = false
    return markdown.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        val fence = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fence != null) {
            fencedCodeMarker = if (fencedCodeMarker == fence) null else fencedCodeMarker ?: fence
            return@joinToString line
        }
        if (fencedCodeMarker != null) return@joinToString line
        if (line.trim() == "$$") {
            latexBlock = !latexBlock
            return@joinToString line
        }
        if (latexBlock) return@joinToString line
        normalizeInlineLatexLine(line)
    }
}

internal fun splitMarkdownTables(markdown: String): List<MarkdownRenderBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownRenderBlock>()
    val plain = mutableListOf<String>()
    var fencedCodeMarker: String? = null
    var index = 0

    fun flushPlain() {
        val content = plain.joinToString("\n").trim('\n')
        if (content.isNotBlank()) blocks += MarkdownRenderBlock(content)
        plain.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        val fence = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fence != null) {
            fencedCodeMarker = if (fencedCodeMarker == fence) null else fencedCodeMarker ?: fence
        }

        val isTableStart = fencedCodeMarker == null &&
            index + 1 < lines.size &&
            tableColumnCount(line) > 0 &&
            isTableDelimiter(lines[index + 1])
        if (!isTableStart) {
            plain += line
            index += 1
            continue
        }

        flushPlain()
        val tableLines = mutableListOf(line, lines[index + 1])
        var columns = tableColumnCount(line)
        index += 2
        while (index < lines.size && lines[index].isNotBlank() && tableColumnCount(lines[index]) > 0) {
            columns = maxOf(columns, tableColumnCount(lines[index]))
            tableLines += lines[index]
            index += 1
        }
        blocks += MarkdownRenderBlock(tableLines.joinToString("\n"), columns)
    }
    flushPlain()
    return blocks
}

private fun normalizeInlineLatexLine(line: String): String {
    val output = StringBuilder(line.length)
    var inInlineCode = false
    var index = 0
    while (index < line.length) {
        val character = line[index]
        if (character == '`' && !line.isEscaped(index)) {
            inInlineCode = !inInlineCode
            output.append(character)
            index += 1
            continue
        }
        if (!inInlineCode && line.isSingleDollar(index)) {
            val closing = line.findClosingSingleDollar(index + 1)
            if (closing > index + 1 && line.substring(index + 1, closing).isNotBlank()) {
                output.append("$$")
                output.append(line, index + 1, closing)
                output.append("$$")
                index = closing + 1
                continue
            }
        }
        output.append(character)
        index += 1
    }
    return output.toString()
}

private fun String.findClosingSingleDollar(start: Int): Int {
    var inInlineCode = false
    for (index in start until length) {
        if (this[index] == '`' && !isEscaped(index)) inInlineCode = !inInlineCode
        if (!inInlineCode && isSingleDollar(index)) return index
    }
    return -1
}

private fun String.isSingleDollar(index: Int): Boolean =
    this[index] == '$' &&
        !isEscaped(index) &&
        getOrNull(index - 1) != '$' &&
        getOrNull(index + 1) != '$'

private fun String.isEscaped(index: Int): Boolean {
    var slashes = 0
    var cursor = index - 1
    while (cursor >= 0 && this[cursor] == '\\') {
        slashes += 1
        cursor -= 1
    }
    return slashes % 2 == 1
}

private fun isTableDelimiter(line: String): Boolean {
    val cells = tableCells(line)
    return cells.isNotEmpty() && cells.all { it.trim().matches(Regex(":?-{3,}:?")) }
}

private fun tableColumnCount(line: String): Int = tableCells(line).size

private fun tableCells(line: String): List<String> {
    if ('|' !in line) return emptyList()
    val value = line.trim().removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    value.forEachIndexed { index, character ->
        if (character == '|' && !value.isEscaped(index)) {
            cells += cell.toString()
            cell.clear()
        } else {
            cell.append(character)
        }
    }
    cells += cell.toString()
    return cells
}

internal const val MARKDOWN_LINK_COLOR: Int = 0xFF0969DA.toInt()
internal const val MARKDOWN_MUTED_BORDER_COLOR: Int = 0xFFD0D7DE.toInt()
internal const val MARKDOWN_CODE_BACKGROUND_COLOR: Int = 0xFFF6F8FA.toInt()
internal const val MARKDOWN_CODE_TEXT_COLOR: Int = 0xFF24292F.toInt()

/** Single construction point for every Markdown parser, renderer and extension used by reports. */
internal fun createMarkdownRenderer(context: Context): Markwon {
    val markdownTextSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        14f,
        context.resources.displayMetrics,
    )
    val tableTheme = TableTheme.buildWithDefaults(context)
        .tableBorderColor(MARKDOWN_MUTED_BORDER_COLOR)
        .tableHeaderRowBackgroundColor(MARKDOWN_CODE_BACKGROUND_COLOR)
        .tableOddRowBackgroundColor(MARKDOWN_CODE_BACKGROUND_COLOR)
        .tableEvenRowBackgroundColor(android.graphics.Color.WHITE)
        .build()
    return Markwon.builder(context)
        .usePlugin(MarkdownStylePlugin())
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(tableTheme))
        .usePlugin(
            TaskListPlugin.create(
                MARKDOWN_LINK_COLOR,
                MARKDOWN_MUTED_BORDER_COLOR,
                android.graphics.Color.WHITE,
            ),
        )
        .usePlugin(CoilImagesPlugin.create(context, createMarkdownImageLoader(context)))
        .usePlugin(MarkdownImagePlugin.create())
        .usePlugin(HtmlPlugin.create { it.addHandler(MarkdownKbdTagHandler()) })
        .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
        .usePlugin(MarkdownEmailLinkPlugin.create())
        .usePlugin(MarkdownPhoneLinkPlugin.create())
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
        .usePlugin(JLatexMathPlugin.create(markdownTextSizePx) { it.inlinesEnabled(true) })
        .usePlugin(
            SyntaxHighlightPlugin.create(
                Prism4j(MarkdownGrammarLocator()),
                Prism4jThemeDefault.create(),
            ),
        )
        .build()
}

internal class MarkdownStylePlugin : AbstractMarkwonPlugin() {
    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.linkResolver(MarkdownLinkResolver())
    }

    override fun configureTheme(builder: MarkwonTheme.Builder) {
        builder
            .linkColor(MARKDOWN_LINK_COLOR)
            .blockQuoteColor(MARKDOWN_MUTED_BORDER_COLOR)
            .headingBreakColor(MARKDOWN_MUTED_BORDER_COLOR)
            .codeTextColor(MARKDOWN_CODE_TEXT_COLOR)
            .codeBlockTextColor(MARKDOWN_CODE_TEXT_COLOR)
            .codeBackgroundColor(MARKDOWN_CODE_BACKGROUND_COLOR)
            .codeBlockBackgroundColor(MARKDOWN_CODE_BACKGROUND_COLOR)
    }

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(Link::class.java, SpanFactory { configuration, props ->
            val destination = CoreProps.LINK_DESTINATION.require(props)
            val link = LinkSpan(
                configuration.theme(),
                destination,
                configuration.linkResolver(),
            )
            if (destination.startsWith("${FOOTNOTE_SCHEME}definition/")) {
                arrayOf(link, SuperscriptSpan(), RelativeSizeSpan(0.95f))
            } else {
                link
            }
        })
    }
}

internal class MarkdownKbdTagHandler : SimpleTagHandler() {
    override fun supportedTags(): Collection<String> = setOf("kbd")

    override fun getSpans(
        configuration: MarkwonConfiguration,
        renderProps: RenderProps,
        tag: HtmlTag,
    ): Any = arrayOf(
        TypefaceSpan("monospace"),
        ForegroundColorSpan(MARKDOWN_CODE_TEXT_COLOR),
        BackgroundColorSpan(MARKDOWN_CODE_BACKGROUND_COLOR),
    )
}

internal class MarkdownEmailLinkPlugin private constructor() : AbstractMarkwonPlugin() {
    override fun configure(registry: MarkwonPlugin.Registry) {
        registry.require(CorePlugin::class.java) { corePlugin ->
            corePlugin.addOnTextAddedListener { visitor, text, start ->
                val spanFactory = visitor.configuration().spansFactory().get(Link::class.java)
                    ?: return@addOnTextAddedListener
                EMAIL_REGEX.findAll(text).forEach { match ->
                    CoreProps.LINK_DESTINATION.set(visitor.renderProps(), "mailto:${match.value}")
                    SpannableBuilder.setSpans(
                        visitor.builder(),
                        spanFactory.getSpans(visitor.configuration(), visitor.renderProps()),
                        start + match.range.first,
                        start + match.range.last + 1,
                    )
                }
            }
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex(
            "(?<![A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+",
        )

        fun create(): MarkdownEmailLinkPlugin = MarkdownEmailLinkPlugin()

        internal fun emailMatches(text: String): List<String> =
            EMAIL_REGEX.findAll(text).map { it.value }.toList()
    }
}

/** Links explicit international phone numbers without treating metric ranges as numbers. */
internal class MarkdownPhoneLinkPlugin private constructor() : AbstractMarkwonPlugin() {
    override fun configure(registry: MarkwonPlugin.Registry) {
        registry.require(CorePlugin::class.java) { corePlugin ->
            corePlugin.addOnTextAddedListener { visitor, text, start ->
                val spanFactory = visitor.configuration().spansFactory().get(Link::class.java)
                    ?: return@addOnTextAddedListener
                phoneMatches(text).forEach { match ->
                    val destination = match.value.filter { it == '+' || it.isDigit() }
                    CoreProps.LINK_DESTINATION.set(visitor.renderProps(), "tel:$destination")
                    SpannableBuilder.setSpans(
                        visitor.builder(),
                        spanFactory.getSpans(visitor.configuration(), visitor.renderProps()),
                        start + match.range.first,
                        start + match.range.last + 1,
                    )
                }
            }
        }
    }

    companion object {
        private val PHONE_REGEX = Regex(
            "(?<![A-Za-z0-9])\\+[0-9](?:[  -]?[0-9]){9,14}(?![A-Za-z0-9])",
        )

        fun create(): MarkdownPhoneLinkPlugin = MarkdownPhoneLinkPlugin()

        internal fun phoneMatches(text: String): List<MatchResult> =
            PHONE_REGEX.findAll(text).toList()
    }
}

private class MarkdownLinkResolver : LinkResolver {
    private val fallback = LinkResolverDef()

    override fun resolve(view: View, link: String) {
        when {
            link.startsWith(FOOTNOTE_SCHEME) -> scrollToFootnote(view, link)
            link.isPdfLink() -> view.context.startActivity(
                Intent(view.context, MarkdownPdfActivity::class.java)
                    .putExtra(MarkdownPdfActivity.EXTRA_URL, link),
            )
            else -> fallback.resolve(view, link)
        }
    }

    private fun scrollToFootnote(source: View, link: String) {
        val uri = Uri.parse(link)
        val label = URLDecoder.decode(uri.lastPathSegment.orEmpty(), Charsets.UTF_8.name())
        val targetText = when (uri.host) {
            "definition" -> "〔$label〕"
            "reference" -> "［$label］"
            else -> return
        }
        val target = source.rootView.descendants()
            .filterIsInstance<TextView>()
            .mapNotNull { textView ->
                textView.text.indexOf(targetText).takeIf { it >= 0 }?.let { textView to it }
            }
            .firstOrNull() ?: return
        val layout = target.first.layout ?: return
        val line = layout.getLineForOffset(target.second)
        target.first.requestRectangleOnScreen(
            Rect(
                layout.getLineLeft(line).toInt(),
                layout.getLineTop(line),
                layout.getLineRight(line).toInt(),
                layout.getLineBottom(line),
            ),
            true,
        )
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }

    private fun String.isPdfLink(): Boolean = runCatching {
        val uri = Uri.parse(this)
        (uri.scheme == "https" || uri.scheme == "http") &&
            uri.path.orEmpty().endsWith(".pdf", ignoreCase = true)
    }.getOrDefault(false)
}

internal class MarkdownImagePlugin private constructor() : AbstractMarkwonPlugin() {
    private val imageSizeResolver = ResponsiveImageSizeResolver()
    private val pendingAnimations = WeakHashMap<TextView, Runnable>()
    private val trimmedSvgDrawables = Collections.newSetFromMap(WeakHashMap<AsyncDrawable, Boolean>())

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.imageSizeResolver(imageSizeResolver)
    }

    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        pendingAnimations.remove(textView)?.let(textView::removeCallbacks)
    }

    override fun afterSetText(textView: TextView) {
        installImageClickSpans(textView)
        var attempts = 0
        val starter = object : Runnable {
            override fun run() {
                if (!textView.isAttachedToWindow) {
                    pendingAnimations.remove(textView)
                    return
                }
                val text = textView.text as? Spanned ?: return
                val images = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
                var waitingForResult = false
                images.forEach { span ->
                    val drawable = span.drawable
                    var result = drawable.result
                    if (
                        result != null &&
                        drawable.destination.substringBefore('?').endsWith(".svg", ignoreCase = true) &&
                        trimmedSvgDrawables.add(drawable)
                    ) {
                        result = trimTransparentPadding(result, textView)?.also(drawable::setResult)
                            ?: result
                    }
                    if (result != null && result !is AspectFitMarkdownDrawable) {
                        result = AspectFitMarkdownDrawable(result).also(drawable::setResult)
                    }
                    if (result is Animatable) {
                        if (!result.isRunning) result.start()
                    } else if (!drawable.hasResult()) {
                        waitingForResult = true
                    }
                }
                attempts += 1
                if (waitingForResult && attempts < MAX_START_ATTEMPTS) {
                    textView.postDelayed(this, START_RETRY_MILLIS)
                } else {
                    pendingAnimations.remove(textView)
                }
            }
        }
        pendingAnimations[textView] = starter
        textView.post(starter)
    }

    private fun installImageClickSpans(textView: TextView) {
        val original = textView.text as? Spanned ?: return
        val text = original as? Spannable ?: SpannableStringBuilder(original)
        original.getSpans(0, original.length, AsyncDrawableSpan::class.java).forEach { span ->
            val start = original.getSpanStart(span)
            val end = original.getSpanEnd(span)
            if (text.getSpans(start, end, MarkdownImageClickSpan::class.java).isEmpty()) {
                text.setSpan(
                    MarkdownImageClickSpan(span.drawable.destination),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        if (text !== original) textView.text = text
    }

    private fun trimTransparentPadding(result: Drawable, textView: TextView): Drawable? {
        val sourceWidth = maxOf(result.intrinsicWidth, result.bounds.width(), 1)
        val sourceHeight = maxOf(result.intrinsicHeight, result.bounds.height(), 1)
        val largestDimension = maxOf(sourceWidth, sourceHeight)
        val renderScale = minOf(
            SVG_RENDER_MAX_SIZE.toFloat() / largestDimension,
            maxOf(1f, SVG_RENDER_MIN_SIZE.toFloat() / largestDimension),
        )
        val width = (sourceWidth * renderScale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * renderScale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val previousBounds = Rect(result.bounds)
        result.setBounds(0, 0, width, height)
        result.draw(Canvas(bitmap))
        result.bounds = previousBounds

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitmap.getPixel(x, y).ushr(24) > MIN_VISIBLE_ALPHA) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < left || bottom < top) {
            bitmap.recycle()
            return null
        }
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        if (cropped !== bitmap) bitmap.recycle()
        cropped.density = textView.resources.displayMetrics.densityDpi
        return BitmapDrawable(textView.resources, cropped).apply {
            setBounds(0, 0, cropped.width, cropped.height)
        }
    }

    companion object {
        private const val MAX_START_ATTEMPTS = 100
        private const val START_RETRY_MILLIS = 100L
        private const val SVG_RENDER_MIN_SIZE = 256
        private const val SVG_RENDER_MAX_SIZE = 1024
        private const val MIN_VISIBLE_ALPHA = 8

        fun create(): MarkdownImagePlugin = MarkdownImagePlugin()
    }
}

private class ResponsiveImageSizeResolver : ImageSizeResolver() {
    override fun resolveImageSize(drawable: AsyncDrawable): Rect {
        val result = drawable.result
        val sourceWidth = maxOf(result?.intrinsicWidth ?: drawable.bounds.width(), 1)
        val sourceHeight = maxOf(result?.intrinsicHeight ?: drawable.bounds.height(), 1)
        val availableWidth = drawable.lastKnownCanvasWidth.takeIf { it > 0 } ?: sourceWidth
        val sourceRatio = sourceWidth.toFloat() / sourceHeight
        val layoutRatio = sourceRatio.coerceIn(MIN_LAYOUT_RATIO, MAX_LAYOUT_RATIO)
        return Rect(0, 0, availableWidth, (availableWidth / layoutRatio).roundToInt())
    }

    private companion object {
        const val MIN_LAYOUT_RATIO = 0.8f
        const val MAX_LAYOUT_RATIO = 2.4f
    }
}

private class AspectFitMarkdownDrawable(private val source: Drawable) : Drawable(), Animatable,
    Drawable.Callback {
    init {
        source.callback = this
    }

    override fun draw(canvas: Canvas) {
        val sourceWidth = maxOf(source.intrinsicWidth, 1)
        val sourceHeight = maxOf(source.intrinsicHeight, 1)
        val scale = minOf(
            bounds.width().toFloat() / sourceWidth,
            bounds.height().toFloat() / sourceHeight,
        )
        val width = (sourceWidth * scale).roundToInt()
        val height = (sourceHeight * scale).roundToInt()
        val left = bounds.left + (bounds.width() - width) / 2
        val top = bounds.top + (bounds.height() - height) / 2
        source.setBounds(left, top, left + width, top + height)
        source.draw(canvas)
    }

    override fun getIntrinsicWidth(): Int = source.intrinsicWidth

    override fun getIntrinsicHeight(): Int = source.intrinsicHeight

    override fun setAlpha(alpha: Int) {
        source.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        source.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun start() {
        (source as? Animatable)?.start()
    }

    override fun stop() {
        (source as? Animatable)?.stop()
    }

    override fun isRunning(): Boolean = (source as? Animatable)?.isRunning == true

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}

private class MarkdownImageClickSpan(private val imageUrl: String) : android.text.style.ClickableSpan() {
    override fun onClick(widget: View) {
        showMarkdownImageDialog(widget.context, imageUrl)
    }

    override fun updateDrawState(ds: TextPaint) = Unit
}

private fun showMarkdownImageDialog(context: Context, imageUrl: String) {
    val dialog = Dialog(context)
    val imageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Markdown 图片预览"
    }
    val imageContainer = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            cornerRadius = context.dp(12f).toFloat()
        }
        addView(
            imageView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
    val close = TextView(context).apply {
        text = "×"
        textSize = 28f
        gravity = Gravity.CENTER
        setTextColor(MARKDOWN_CODE_TEXT_COLOR)
        contentDescription = "关闭图片预览"
        setOnClickListener { dialog.dismiss() }
    }
    dialog.setContentView(
        FrameLayout(context).apply {
            setPadding(context.dp(16f), context.dp(16f), context.dp(16f), context.dp(16f))
            addView(
                imageContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.heightPixels * 0.72f).roundToInt(),
                ),
            )
            addView(
                close,
                FrameLayout.LayoutParams(
                    context.dp(48f),
                    context.dp(48f),
                    Gravity.TOP or Gravity.END,
                ),
            )
        },
    )
    dialog.setCanceledOnTouchOutside(true)
    dialog.show()
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setDimAmount(0.72f)
        addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }
    val imageLoader = createMarkdownImageLoader(context)
    val request = imageLoader.enqueue(
        ImageRequest.Builder(context)
            .data(imageUrl)
            .target(imageView)
            .build(),
    )
    dialog.setOnDismissListener { request.dispose() }
}

internal fun createMarkdownImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .componentRegistry {
        add(GifDecoder())
        add(SvgDecoder(context))
    }
    .build()

private fun Context.dp(value: Float): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    resources.displayMetrics,
).roundToInt()

private const val FOOTNOTE_SCHEME = "vitalhub-footnote://"
