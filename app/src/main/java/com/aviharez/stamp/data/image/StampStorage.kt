package com.aviharez.stamp.data.image

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where stamp images live: app-private storage (CLAUDE.md §2), one original JPEG and one
 * rendered PNG per stamp, sharing an id.
 */
@Singleton
class StampStorage @Inject constructor(@ApplicationContext context: Context) {
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
}
