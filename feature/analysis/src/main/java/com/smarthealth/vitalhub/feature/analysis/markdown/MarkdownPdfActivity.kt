package com.smarthealth.vitalhub.feature.analysis.markdown

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.VitalColors
import com.smarthealth.vitalhub.core.ui.VitalHubTheme
import com.smarthealth.vitalhub.feature.analysis.R
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MarkdownPdfActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.markdown_pdf_scale_enter, R.anim.markdown_pdf_source_hold)
        window.statusBarColor = AndroidColor.WHITE
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        setContentView(
            ComposeView(this).apply {
                setContent {
                    VitalHubTheme {
                        PdfViewerScreen(
                            url = url,
                            cacheDir = cacheDir,
                            onBack = onBackPressedDispatcher::onBackPressed,
                        )
                    }
                }
            },
        )
    }

    companion object {
        const val EXTRA_URL = "markdownPdfUrl"
    }
}

private sealed interface PdfViewerState {
    data object Loading : PdfViewerState
    data class Ready(val document: PdfDocument) : PdfViewerState
    data class Failed(val message: String) : PdfViewerState
}

@Composable
private fun PdfViewerScreen(url: String, cacheDir: File, onBack: () -> Unit) {
    var state by remember(url) { mutableStateOf<PdfViewerState>(PdfViewerState.Loading) }
    LaunchedEffect(url) {
        state = if (url.startsWith("https://") || url.startsWith("http://")) {
            runCatching { PdfDocument(downloadPdf(url, cacheDir)) }
                .fold(
                    onSuccess = { PdfViewerState.Ready(it) },
                    onFailure = { PdfViewerState.Failed(it.message ?: "PDF 加载失败") },
                )
        } else {
            PdfViewerState.Failed("不支持的 PDF 地址")
        }
    }
    DisposableEffect(state) {
        val document = (state as? PdfViewerState.Ready)?.document
        onDispose { document?.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.White)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "PDF 文档",
                color = VitalColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        when (val current = state) {
            PdfViewerState.Loading -> Box(
                Modifier.fillMaxSize().background(VitalColors.Background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color(MARKDOWN_LINK_COLOR))
            }
            is PdfViewerState.Failed -> Box(
                Modifier
                    .fillMaxSize()
                    .background(VitalColors.Background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(current.message, color = VitalColors.TextSecondary)
            }
            is PdfViewerState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().background(VitalColors.Background),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items((0 until current.document.pageCount).toList()) { pageIndex ->
                    PdfPage(current.document, pageIndex)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocument, pageIndex: Int) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        val density = LocalDensity.current
        val targetWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val bitmap by produceState<Bitmap?>(null, document, pageIndex, targetWidthPx) {
            value = withContext(Dispatchers.IO) {
                document.render(pageIndex, targetWidthPx)
            }
        }
        val page = bitmap
        if (page == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color(MARKDOWN_LINK_COLOR))
            }
        } else {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "PDF 第 ${pageIndex + 1} 页",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White),
                contentScale = ContentScale.FillWidth,
            )
            DisposableEffect(page) { onDispose(page::recycle) }
        }
    }
}

private suspend fun downloadPdf(url: String, cacheDir: File): File = withContext(Dispatchers.IO) {
    val cacheFile = File(
        cacheDir,
        "markdown-${url.sha256()}.pdf",
    )
    if (cacheFile.isFile && cacheFile.length() > 0L) return@withContext cacheFile
    val partialFile = File(cacheFile.path + ".part")
    val response = PdfCache.client.newCall(Request.Builder().url(url).build()).execute()
    try {
        response.use {
            if (!it.isSuccessful) throw IOException("PDF 下载失败（HTTP ${it.code}）")
            val body = it.body ?: throw IOException("PDF 内容为空")
            if (body.contentLength() > MAX_PDF_BYTES) throw IOException("PDF 文件超过 25 MB")
            partialFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_PDF_BYTES) throw IOException("PDF 文件超过 25 MB")
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        if (!partialFile.renameTo(cacheFile)) throw IOException("PDF 缓存写入失败")
    } finally {
        if (partialFile.exists()) partialFile.delete()
    }
    cacheFile
}

private class PdfDocument(file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    val pageCount: Int get() = renderer.pageCount

    @Synchronized
    fun render(index: Int, targetWidth: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val targetHeight = (targetWidth * page.height.toFloat() / page.width)
                .toInt()
                .coerceAtLeast(1)
            return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(AndroidColor.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}

private object PdfCache {
    val client = OkHttpClient()
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }

private const val MAX_PDF_BYTES = 25L * 1024L * 1024L
