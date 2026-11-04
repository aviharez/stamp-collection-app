package com.aviharez.stamp.data.repo

import com.aviharez.stamp.data.db.StampDao
import com.aviharez.stamp.data.db.StampEntity
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.Place
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.domain.model.StampSource
import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.domain.usecase.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A freshly made stamp, before it has an album. */
data class NewStamp(
    val id: String,
    val shape: StampShape,
    val perforation: PerforationStyle,
    val imageFile: File,
    val originalFile: File,
    val place: Place,
    val source: StampSource,
    /** When the photo was taken; null = now (camera). */
    val takenAt: Instant? = null,
    /** The calendar day it counts for; null = [takenAt]'s day here. */
    val day: LocalDate? = null,
)

@Singleton
class StampRepository @Inject constructor(
    private val dao: StampDao,
    private val albums: AlbumRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** Saves a new stamp into the last-used album (or the default one). Returns the stamp id. */
    suspend fun add(new: NewStamp): String {
        val albumId = settings.lastAlbumId()?.takeIf { albums.get(it) != null } ?: albums.ensureDefault()
        val takenAt = new.takenAt ?: clock.instant()
        val day = new.day ?: takenAt.atZone(clock.zone).toLocalDate()
        dao.insert(
            StampEntity(
                id = new.id,
                albumId = albumId,
                shape = new.shape.name,
                perforation = new.perforation.name,
                imagePath = new.imageFile.path,
                originalPath = new.originalFile.path,
                takenAt = takenAt.toEpochMilli(),
                day = day.toString(),
                createdDay = LocalDate.now(clock).toString(),
                city = new.place.city,
                country = new.place.country,
                countryCode = new.place.countryCode,
                latitude = new.place.latitude,
                longitude = new.place.longitude,
                title = null,
                caption = null,
                source = new.source.name,
            ),
        )
        settings.setLastAlbumId(albumId)
        return new.id
    }

    suspend fun get(id: String): Stamp? = dao.get(id)?.toDomain()

    fun observe(id: String): Flow<Stamp?> = dao.observe(id).map { it?.toDomain() }

    fun observeNumberInAlbum(id: String): Flow<Int> = dao.observeNumberInAlbum(id)

    fun observeByAlbum(albumId: Long): Flow<List<Stamp>> =
        dao.observeByAlbum(albumId).map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int): Flow<List<Stamp>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    /** Stamps whose calendar day is in [days], oldest first. */
    fun observeBetween(days: ClosedRange<LocalDate>): Flow<List<Stamp>> =
        dao.observeBetween(days.start.toString(), days.endInclusive.toString()).map { list -> list.map { it.toDomain() } }

    fun observeDays(): Flow<Set<LocalDate>> =
        dao.observeDays().map { days -> days.mapTo(HashSet()) { LocalDate.parse(it) } }

    /** First day anything was stamped (calendar starts there), null when there are no stamps. */
    fun observeFirstDay(): Flow<LocalDate?> = observeDays().map { it.minOrNull() }

    /** Only stamps made on their own day count (importing an old photo can't backfill a streak). */
    fun observeStreak(): Flow<Streak> =
        dao.observeStreakDays().map { days -> StreakCalculator.calculate(days.map(LocalDate::parse), LocalDate.now(clock)) }

    suspend fun setCaption(id: String, caption: String) =
        dao.setCaption(id, caption.trim().ifEmpty { null })

    suspend fun setTitle(id: String, title: String) =
        dao.setTitle(id, title.trim().ifEmpty { null })

    suspend fun moveToAlbum(id: String, albumId: Long) {
        dao.moveToAlbum(id, albumId)
        settings.setLastAlbumId(albumId)
    }

    /** A re-framed picture (new crop / turn / shape); the old PNG is deleted. Date, place and words stay. */
    suspend fun replaceImage(id: String, shape: StampShape, image: File) {
        val old = dao.get(id) ?: return
        dao.setImage(id, shape.name, image.path)
        if (old.imagePath != image.path) withContext(Dispatchers.IO) { File(old.imagePath).delete() }
    }

    /** Removes the stamp and both of its image files (e.g. "Retake"). */
    suspend fun delete(id: String) {
        val stamp = dao.get(id) ?: return
        dao.delete(id)
        withContext(Dispatchers.IO) {
            File(stamp.imagePath).delete()
            File(stamp.originalPath).delete()
        }
    }
}

internal fun StampEntity.toDomain() = Stamp(
    id = id,
    albumId = albumId,
    shape = enumOr(shape, StampShape.Portrait),
    perforation = enumOr(perforation, PerforationStyle.Classic),
    imagePath = imagePath,
    originalPath = originalPath,
    takenAt = Instant.ofEpochMilli(takenAt),
    day = LocalDate.parse(day),
    place = Place(city, country, countryCode, latitude, longitude),
    title = title,
    caption = caption,
    source = enumOr(source, StampSource.Camera),
)
