package com.aviharez.stamp.data.backup

import com.aviharez.stamp.data.db.AlbumEntity
import com.aviharez.stamp.data.db.StampEntity
import com.aviharez.stamp.domain.model.StampFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A backup is one ZIP file: `backup.json` (this manifest) first, then every stamp's images as
 * `rendered/<id>.png` and `originals/<id>.<ext>`. Plain ZIP so it stays readable without the app.
 */
@Serializable
data class BackupManifest(
    val format: Int = BackupFormat.Version,
    val createdAt: Long,
    val albums: List<BackupAlbum>,
    val stamps: List<BackupStamp>,
    val settings: BackupSettings = BackupSettings(),
)

/** [key] is the album's id on the phone it came from; only used to link stamps inside the file. */
@Serializable
data class BackupAlbum(val key: Long, val name: String, val category: String, val cover: String, val createdAt: Long)

@Serializable
data class BackupStamp(
    val id: String,
    val albumKey: Long,
    val shape: String,
    val perforation: String,
    /** Entry name inside the ZIP. */
    val image: String,
    /** Entry name inside the ZIP; null when the original was missing at backup time. */
    val original: String? = null,
    val takenAt: Long,
    val day: String,
    val createdDay: String = day,
    val city: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val title: String? = null,
    val caption: String? = null,
    val source: String,
    /** Added with filters; older files restore as Original with no spec. */
    val filter: String? = null,
    val renderSpec: String? = null,
)

@Serializable
data class BackupSettings(val mirrorSelfie: Boolean? = null, val bonusAlbums: Int = 0)

/** What a restore will do, worked out before anything is written. */
data class RestorePlan(
    /** Backup album key → album already on this phone. */
    val reuseAlbums: Map<Long, Long>,
    /** Albums to create (not on this phone yet), empty ones included. */
    val newAlbums: List<BackupAlbum>,
    /** Stamps not on this phone yet, whose album exists in the file. */
    val newStamps: List<BackupStamp>,
    /** Stamps already here (same id), skipped. */
    val alreadyHere: Int,
)

object BackupFormat {
    const val Version = 1
    const val ManifestEntry = "backup.json"
    const val RenderedDir = "rendered"
    const val OriginalsDir = "originals"

    val json = Json {
        ignoreUnknownKeys = true // newer minor fields are fine
        encodeDefaults = true
    }

    private val Uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val ImageEntry = Regex("^($RenderedDir|$OriginalsDir)/([0-9a-fA-F-]{36})\\.([a-z0-9]{1,5})$")

    fun renderedEntry(id: String) = "$RenderedDir/$id.png"

    fun originalEntry(id: String, extension: String) = "$OriginalsDir/$id.${extension.lowercase()}"

    fun isStampId(id: String) = Uuid.matches(id)

    /** Only entries named exactly like ours are read: no paths, no "..", no surprises. */
    fun isImageEntry(name: String) = ImageEntry.matches(name)

    /** The file extension of an original image entry, e.g. "jpg". */
    fun extensionOf(entry: String): String = entry.substringAfterLast('.', "jpg")

    fun manifestOf(
        albums: List<AlbumEntity>,
        stamps: List<StampEntity>,
        originalExtension: (StampEntity) -> String?,
        settings: BackupSettings,
        now: Long,
    ) = BackupManifest(
        createdAt = now,
        albums = albums.map { BackupAlbum(it.id, it.name, it.category, it.cover, it.createdAt) },
        stamps = stamps.map { s ->
            BackupStamp(
                id = s.id,
                albumKey = s.albumId,
                shape = s.shape,
                perforation = s.perforation,
                image = renderedEntry(s.id),
                original = originalExtension(s)?.let { originalEntry(s.id, it) },
                takenAt = s.takenAt,
                day = s.day,
                createdDay = s.createdDay.ifEmpty { s.day },
                city = s.city,
                country = s.country,
                countryCode = s.countryCode,
                latitude = s.latitude,
                longitude = s.longitude,
                title = s.title,
                caption = s.caption,
                source = s.source,
                filter = s.filter.takeIf { it != StampFilter.Original.name },
                renderSpec = s.renderSpec,
            )
        },
        settings = settings,
    )

    /**
     * Restore merges, it never deletes or overwrites: a stamp already on this phone (same id) is kept
     * as it is, and an album with the same name and creation time is reused instead of duplicated.
     * Stamps whose image is missing from the file are left out; an original that is missing or
     * not this stamp's own is dropped (the rendered stamp still restores).
     */
    fun plan(
        manifest: BackupManifest,
        existingAlbums: List<AlbumEntity>,
        existingStampIds: Set<String>,
        imagesInFile: Set<String>,
    ): RestorePlan {
        val reuse = manifest.albums.mapNotNull { a ->
            existingAlbums.firstOrNull { it.name == a.name && it.createdAt == a.createdAt }?.let { a.key to it.id }
        }.toMap()
        val albumKeys = manifest.albums.mapTo(HashSet()) { it.key }
        val candidates = manifest.stamps.filter {
            isStampId(it.id) && it.albumKey in albumKeys && it.image == renderedEntry(it.id) && it.image in imagesInFile
        }.distinctBy { it.id.lowercase() }
        val (here, new) = candidates.partition { it.id in existingStampIds }
        return RestorePlan(
            reuseAlbums = reuse,
            // Empty albums come back too: the user made them.
            newAlbums = manifest.albums.filter { it.key !in reuse }.distinctBy { it.key },
            newStamps = new,
            alreadyHere = here.size,
        )
    }
}
