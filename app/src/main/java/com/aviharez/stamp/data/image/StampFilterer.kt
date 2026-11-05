package com.aviharez.stamp.data.image

import android.graphics.Bitmap
import android.util.Log
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampLook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-draws a saved stamp with another look (filter and edge), from its original photo and the
 * crop it was made with (see [RenderSpec]); the old PNG is replaced. Stamps made before filters
 * have no crop saved and can't be re-drawn until they are reframed once.
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

    /** Saves [look] on [stamp] (new PNG, old one deleted). False if it could not be drawn. */
    suspend fun apply(stamp: Stamp, look: StampLook): Boolean {
        val output = storage.renderedFile(stamp.id, version = clock.millis())
        val rendered = render(stamp, look, output) ?: return false
        stamps.replaceImage(stamp.id, rendered, look.filter, look.edge)
        return true
    }

    /** Draws [stamp] with [look] into a cache file without saving it (a Pro look tried on). */
    suspend fun preview(stamp: Stamp, look: StampLook): File? {
        // Named after the current picture too, so a reframed stamp never shows an old cached try-on.
        val name = "${look.filter.name}-${look.edge.name}-${Integer.toHexString(stamp.imagePath.hashCode())}"
        return render(stamp, look, storage.filterPreviewFile(stamp.id, name))?.file
    }

    private suspend fun render(stamp: Stamp, look: StampLook, output: File): RenderedStamp? {
        val spec = RenderSpec.decode(stamp.renderSpec) ?: return null
        return try {
            withContext(Dispatchers.Default) {
                renderer.rerender(File(stamp.originalPath), spec, stamp.shape, look.edge, look.filter, output)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { output.delete() }
            throw e
        } catch (e: Exception) {
            // Original gone or unreadable, or the disk is full: keep the stamp as it is.
            Log.w("StampFilterer", "Could not draw ${stamp.id} with $look", e)
            withContext(Dispatchers.IO) { output.delete() }
            null
        }
    }

    private companion object {
        const val ThumbnailPx = 240
    }
}
