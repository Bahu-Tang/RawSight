package com.rawsight.storage

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles saving captured JPEG / DNG files to Pictures/RawSight
 * and registering them with MediaStore for gallery visibility.
 */
class FileSaver(private val context: Context) {

    private val baseDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        DIR_NAME
    )

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Save JPEG bytes → returns absolute file path on success, empty string on failure.
     */
    fun saveJpeg(jpegBytes: ByteArray): String {
        return saveBytes(jpegBytes, ".jpg", "image/jpeg")
    }

    /**
     * Save DNG bytes → returns absolute file path on success, empty string on failure.
     */
    fun saveDng(dngBytes: ByteArray): String {
        return saveBytes(dngBytes, ".dng", "image/x-adobe-dng")
    }

    // ── Internal ───────────────────────────────────────

    private fun saveBytes(bytes: ByteArray, extension: String, mimeType: String): String {
        if (!ensureDir()) return ""

        val filename = "RAW_${dateFormat.format(Date())}$extension"
        val file = File(baseDir, filename)

        return try {
            FileOutputStream(file).use { it.write(bytes) }
            registerMediaStore(file, mimeType, filename)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun ensureDir(): Boolean {
        return baseDir.exists() || baseDir.mkdirs()
    }

    private fun registerMediaStore(file: File, mimeType: String, displayName: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIR_NAME")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return

            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val DIR_NAME = "RawSight"
    }
}
