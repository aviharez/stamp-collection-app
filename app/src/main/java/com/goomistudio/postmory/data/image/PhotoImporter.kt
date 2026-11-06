package com.goomistudio.postmory.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * A photo picked from the gallery, copied into app storage.
 *
 * @param takenAt when the photo was taken (EXIF, else the gallery's date), null if unknown.
 * @param localTakenAt the same moment on the clock where it was taken (decides the stamp's day).
 * @param latitude / longitude from EXIF GPS; usually null (the system photo picker strips it).
 */
data class ImportedPhoto(
    val id: String,
    val original: File,
    val width: Int,
    val height: Int,
    val takenAt: Instant?,
    val localTakenAt: LocalDateTime?,
    val latitude: Double?,
    val longitude: Double?,
)

/** Copies a picked photo into app-private storage and reads what the postmark needs from it. */
@Singleton
class PhotoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: StampStorage,
) {
    /** Copies [uri]; null when it can't be read or isn't a decodable image. Blocking work on IO. */
    suspend fun import(uri: Uri): ImportedPhoto? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val id = storage.newId()
        val file = storage.originalFile(id, extensionFor(resolver.getType(uri)))
        try {
            val input = resolver.openInputStream(uri) ?: return@withContext null
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
        } catch (e: IOException) {
            Log.w(TAG, "Copy failed", e)
            file.delete()
            return@withContext null
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to the picked photo", e)
            file.delete()
            return@withContext null
        }

        describe(id, file) { galleryDateTaken(uri) } ?: run {
            file.delete()
            null
        }
    }

    /**
     * Reads a photo [import] already copied, when the import screen comes back after Android
     * closed the app. [takenAt] is what the first read found (the gallery's date can't be asked again).
     */
    suspend fun reopen(file: File, takenAt: Instant?): ImportedPhoto? = withContext(Dispatchers.IO) {
        if (file.isFile) describe(file.nameWithoutExtension, file) { takenAt } else null
    }

    private fun describe(id: String, file: File, fallbackTakenAt: () -> Instant?): ImportedPhoto? {
        val (width, height) = uprightSize(file)
        if (width <= 0 || height <= 0) return null
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val local = exif?.localDateTime()
        val offset = exif?.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let(::parseOffset)
        val takenAt = when {
            local != null && offset != null -> local.toInstant(offset)
            local != null -> local.atZone(ZoneId.systemDefault()).toInstant()
            else -> fallbackTakenAt()
        }
        val latLong = exif?.latLong
        return ImportedPhoto(
            id = id,
            original = file,
            width = width,
            height = height,
            takenAt = takenAt,
            localTakenAt = local ?: takenAt?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1),
        )
    }

    /** Upright preview for the crop screen, long side ≤ [maxLongPx]. Blocking: call off the main thread. */
    fun decodePreview(file: File, maxLongPx: Int = PreviewLongPx): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongPx) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return decoded.applyExif(exifOrientation(file))
    }

    private fun ExifInterface.localDateTime(): LocalDateTime? {
        val raw = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return try {
            LocalDateTime.parse(raw.trim(), ExifDateFormat)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun parseOffset(raw: String): ZoneOffset? = runCatching { ZoneOffset.of(raw.trim()) }.getOrNull()

    /** MediaStore's DATE_TAKEN for screenshots and edited photos without EXIF dates. */
    private fun galleryDateTaken(uri: Uri): Instant? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 }?.let(Instant::ofEpochMilli) else null
        }
    }.getOrNull()

    private fun extensionFor(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }

    private companion object {
        const val TAG = "PhotoImporter"
        const val PreviewLongPx = 2048
        val ExifDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
