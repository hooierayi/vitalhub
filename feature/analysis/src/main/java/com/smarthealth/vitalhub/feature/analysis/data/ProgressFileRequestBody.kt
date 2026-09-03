package com.smarthealth.vitalhub.feature.analysis.data

import java.io.File
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

internal class ProgressFileRequestBody(
    private val file: File,
    private val mediaType: MediaType,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()
        var writtenBytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sink.write(buffer, 0, count)
                writtenBytes += count
                val percent = if (totalBytes == 0L) 100 else {
                    (writtenBytes * 100L / totalBytes).toInt()
                }
                onProgress(percent.coerceIn(0, 100))
            }
        }
    }
}
