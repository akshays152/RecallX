package com.recallx.core.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class PreparedSearchImage(val file: File, val displayName: String = "visual-search.jpg") {
    fun delete() { if (file.exists()) file.delete() }
}

object ImageInputProcessor {
    private const val MAX_DIMENSION = 2048

    fun fromUri(resolver: ContentResolver, uri: Uri, cacheDir: File): Result<PreparedSearchImage> = runCatching {
        val raw = File.createTempFile("recallx-visual-raw-", ".input", cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input -> FileOutputStream(raw).use { output -> input.copyTo(output) } }
                ?: error("Unable to read selected image")
            normalize(raw, cacheDir)
        } finally { raw.delete() }
    }

    fun fromCapturedFile(source: File, cacheDir: File): Result<PreparedSearchImage> = runCatching {
        try { normalize(source, cacheDir) } finally { source.delete() }
    }

    private fun normalize(source: File, cacheDir: File): PreparedSearchImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        val sample = calculateSample(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: error("Unable to decode image")
        val orientation = ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotated = applyOrientation(decoded, orientation)
        if (rotated !== decoded) decoded.recycle()
        val output = File.createTempFile("recallx-visual-", ".jpg", cacheDir)
        FileOutputStream(output).use { stream -> require(rotated.compress(Bitmap.CompressFormat.JPEG, 85, stream)) { "Unable to prepare image" } }
        rotated.recycle()
        return PreparedSearchImage(output)
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DIMENSION || height / sample > MAX_DIMENSION) sample *= 2
        return sample
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
