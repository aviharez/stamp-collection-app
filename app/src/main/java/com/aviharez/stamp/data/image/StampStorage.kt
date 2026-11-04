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

    /** Full-quality capture as delivered by the camera (with EXIF). */
    fun originalFile(id: String): File = File(originals.also { it.mkdirs() }, "$id.jpg")

    /** Rendered stamp: PNG, perforated, transparent outside the paper. */
    fun renderedFile(id: String): File = File(rendered.also { it.mkdirs() }, "$id.png")
}
