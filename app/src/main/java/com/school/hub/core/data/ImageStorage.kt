package com.school.hub.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Хранит фото/сканы шпаргалок во внутренней памяти.
 * Картинки ужимаются до 1600px / JPEG 80, чтобы быстро передаваться по Bluetooth.
 */
class ImageStorage(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "cheat_images").apply { mkdirs() }

    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null
            val rotation = context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val bitmap = scaleAndRotate(raw, rotation)
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            file.absolutePath
        }.getOrNull()
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Float): Bitmap {
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(src.width, src.height))
        if (scale == 1f && rotation == 0f) return src
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun toBase64(path: String): String? = runCatching {
        Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    fun fromBase64(data: String): String? = runCatching {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(Base64.decode(data, Base64.NO_WRAP))
        file.absolutePath
    }.getOrNull()

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    private companion object { const val MAX_SIDE = 1600 }
}
