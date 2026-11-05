package com.aviharez.stamp.data.image

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where stamp images live: app-private storage (CLAUDE.md §2), one original JPEG and one
 * rendered PNG per stamp, sharing an id.
 */
@Singleton
class StampStorage @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.filesDir, "stamps")
    private val originals = File(root, "originals")
    private val rendered = File(root, "rendered")

    fun newId(): String = UUID.randomUUID().toString()

    /** Full-quality source: the camera JPEG (with EXIF), or a copy of an imported photo. */
    fun originalFile(id: String, extension: String = "jpg"): File = File(originals.also { it.mkdirs() }, "$id.$extension")

    /**
     * Rendered stamp: PNG, perforated, transparent outside the paper. A re-framed stamp gets a new
     * [version] in its name, so image caches never show the old picture.
     */
    fun renderedFile(id: String, version: Long? = null): File =
        File(rendered.also { it.mkdirs() }, if (version == null) "$id.png" else "$id-$version.png")

    /** A Pro filter tried on by a free user: shown, never saved (cache; Android may clear it). */
    fun filterPreviewFile(id: String, filter: String): File =
        File(File(context.cacheDir, "filter_previews").also { it.mkdirs() }, "$id-$filter.png")

    /**
     * Enough space for one more stamp (a full-resolution photo plus its PNG)? Counts cached files
     * Android would clear to make room, as it does on a nearly full phone.
     */
    suspend fun hasRoomForStamp(): Boolean = withContext(Dispatchers.IO) {
        val dir = root.also { it.mkdirs() }
        val storage = context.getSystemService(StorageManager::class.java)
        val free = try {
            storage.getAllocatableBytes(storage.getUuidForPath(dir))
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read free space", e)
            return@withContext true // let the save try; a real failure is still caught there
        }
        free >= MinFreeBytes
    }

    /**
     * Deletes image files no stamp points at: left behind when Android closed the app halfway
     * through a capture or an import. Files younger than a day are kept, so a screen Android is
     * about to bring back still finds its photo. Blocking: call on IO.
     */
    fun sweep(inUse: Set<String>, now: Long = System.currentTimeMillis()) {
        val cutoff = now - OrphanAgeMillis
        listOf(originals, rendered).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.path !in inUse && file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private companion object {
        const val TAG = "StampStorage"
        const val MinFreeBytes = 64L * 1024 * 1024
        val OrphanAgeMillis = TimeUnit.DAYS.toMillis(1)
    }
}
