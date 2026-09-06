package com.recallx.core.network

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okhttp3.MediaType.Companion.toMediaType

class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val contentType: String,
    private val length: Long?
) : RequestBody() {
    override fun contentType(): MediaType = contentType.toMediaType()
    override fun contentLength(): Long = length ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: error("Unable to open selected content")
        input.use { stream -> stream.copyTo(sink.outputStream()) }
    }
}
