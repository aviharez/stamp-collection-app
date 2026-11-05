package com.aviharez.stamp.data.image

import android.graphics.Bitmap
import android.util.Log
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-draws a saved stamp with another filter, from its original photo and the crop it was made
 * with (see [RenderSpec]); the old PNG is replaced. Stamps made before filters have no crop
 * saved and can't be filtered until they are reframed once.
 */
@Singleton
class StampFilterer @Inject constructor(
    private val renderer: StampRenderer,
    private val stamps: StampRepository,
    private val storage: StampStorage,
    private val clock: Clock,
) {
    /** Can [stamp] be drawn again? (Crop known and the original still there.) */
    fun canFilter(stamp: Stamp): Boolean = RenderSpec.decode(stamp.renderSpec) != null && File(stamp.originalPath).isFile

    /** The bare picture, small, for the picker's mini stamps. */
    suspend fun thumbnail(stamp: Stamp): Bitmap? {
        val spec = RenderSpec.decode(stamp.renderSpec) ?: return null
        return withContext(Dispatchers.Default) { renderer.picture(File(stamp.originalPath), spec, ThumbnailPx) }
    }

    /** Saves [filter] on [stamp] (new PNG, old one deleted). False if it could not be drawn. */
    suspend fun apply(stamp: Stamp, filter: StampFilter): Boolean {
        val output = storage.renderedFile(stamp.id, version = clock.millis())
        val rendered = render(stamp, filter, output) ?: return false
        stamps.replaceImage(stamp.id, rendered, filter)
        return true
    }

    /** Draws [stamp] with [filter] into a cache file without saving it (a Pro filter tried on). */
    suspend fun preview(stamp: Stamp, filter: StampFilter): File? {
        // Named after the current picture too, so a reframed stamp never shows an old cached try-on.
        val output = storage.filterPreviewFile(stamp.id, "${filter.name}-${Integer.toHexString(stamp.imagePath.hashCode())}")
        return render(stamp, filter, output)?.file
    }

    private suspend fun render(stamp: Stamp, filter: StampFilter, output: File): RenderedStamp? {
        val spec = RenderSpec.decode(stamp.renderSpec) ?: return null
        return try {
            withContext(Dispatchers.Default) {
                renderer.rerender(File(stamp.originalPath), spec, stamp.shape, stamp.perforation, filter, output)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { output.delete() }
            throw e
        } catch (e: Exception) {
            // Original gone or unreadable, or the disk is full: keep the stamp as it is.
            Log.w("StampFilterer", "Could not draw ${stamp.id} with $filter", e)
            withContext(Dispatchers.IO) { output.delete() }
            null
        }
    }

    private companion object {
        const val ThumbnailPx = 240
    }
}
