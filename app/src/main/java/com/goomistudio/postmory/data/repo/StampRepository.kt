package com.goomistudio.postmory.data.repo

import com.goomistudio.postmory.data.db.StampDao
import com.goomistudio.postmory.data.db.StampEntity
import com.goomistudio.postmory.data.db.StampTotalsRow
import com.goomistudio.postmory.data.image.RenderSpec
import com.goomistudio.postmory.data.image.RenderedStamp
import com.goomistudio.postmory.data.image.StampStorage
import com.goomistudio.postmory.data.settings.SettingsRepository
import com.goomistudio.postmory.domain.model.PerforationStyle
import com.goomistudio.postmory.domain.model.Place
import com.goomistudio.postmory.domain.model.Stamp
import com.goomistudio.postmory.domain.model.StampFilter
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.domain.model.StampSource
import com.goomistudio.postmory.domain.usecase.Streak
import com.goomistudio.postmory.domain.usecase.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import com.goomistudio.postmory.data.analytics.UsageStats
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
    val renderSpec: RenderSpec? = null,
    val filter: StampFilter = StampFilter.Original,
)

@Singleton
class StampRepository @Inject constructor(
    private val dao: StampDao,
    private val albums: AlbumRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val storage: StampStorage,
    private val usage: UsageStats,
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
                filter = new.filter.name,
                renderSpec = new.renderSpec?.encode(),
            ),
        )
        settings.setLastAlbumId(albumId)
        usage.stampMade(new.source.name, new.shape.name, withPlace = new.place.country != null)
        return new.id
    }

    suspend fun get(id: String): Stamp? = dao.get(id)?.toDomain()

    fun observe(id: String): Flow<Stamp?> = dao.observe(id).map { it?.toDomain() }

    fun observeNumberInAlbum(id: String): Flow<Int> = dao.observeNumberInAlbum(id)

    fun observeByAlbum(albumId: Long): Flow<List<Stamp>> =
        dao.observeByAlbum(albumId).map { list -> list.map { it.toDomain() } }

    /** The stamps with these ids that still exist (any album). */
    fun observeByIds(ids: Collection<String>): Flow<List<Stamp>> =
        if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.observeByIds(ids.toList()).map { list -> list.map { it.toDomain() } }

    /** Stamps with a country on their postmark, newest first (the map). */
    fun observeWithCountry(): Flow<List<Stamp>> =
        dao.observeWithCountry().map { list -> list.map { it.toDomain() } }

    fun observeWithoutCountryCount(): Flow<Int> = dao.observeWithoutCountryCount()

    fun observeRecent(limit: Int): Flow<List<Stamp>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    /** Stamps whose calendar day is in [days], oldest first. */
    fun observeBetween(days: ClosedRange<LocalDate>): Flow<List<Stamp>> =
        dao.observeBetween(days.start.toString(), days.endInclusive.toString()).map { list -> list.map { it.toDomain() } }

    fun observeDays(): Flow<Set<LocalDate>> =
        dao.observeDays().map { days -> days.mapTo(HashSet()) { LocalDate.parse(it) } }

    /** Stamp count and different places, for the Profile passport. */
    fun observeTotals(): Flow<StampTotalsRow> = dao.observeTotals()

    /** First day anything was stamped (calendar starts there), null when there are no stamps. */
    fun observeFirstDay(): Flow<LocalDate?> = observeDays().map { it.minOrNull() }

    /** Only stamps made on their own day count (importing an old photo can't backfill a streak). */
    fun observeStreak(): Flow<Streak> =
        dao.observeStreakDays().map { days -> StreakCalculator.calculate(days.map(LocalDate::parse), LocalDate.now(clock)) }

    suspend fun setCaption(id: String, caption: String) =
        dao.setCaption(id, caption.trim().ifEmpty { null })

    suspend fun setTitle(id: String, title: String) =
        dao.setTitle(id, title.trim().ifEmpty { null })

    /** Renames the place on these stamps' postmarks (blank = the country only). */
    suspend fun renamePlace(ids: Collection<String>, city: String) =
        ids.chunked(MaxIdsPerQuery).forEach { dao.setCity(it, city.trim().ifEmpty { null }) }

    suspend fun moveToAlbum(id: String, albumId: Long) {
        dao.moveToAlbum(id, albumId)
        settings.setLastAlbumId(albumId)
    }

    /**
     * A re-drawn picture (new crop / turn / shape / filter); the old PNG is deleted. Date, place
     * and words stay.
     */
    suspend fun replaceImage(id: String, image: RenderedStamp, filter: StampFilter, perforation: PerforationStyle) {
        val old = dao.get(id) ?: return
        dao.setImage(id, image.shape.name, image.file.path, filter.name, perforation.name, image.spec.encode())
        if (old.imagePath != image.file.path) withContext(Dispatchers.IO) { File(old.imagePath).delete() }
    }

    /** Deletes image files that no stamp uses any more (see [StampStorage.sweep]). */
    suspend fun sweepOrphanFiles() {
        val inUse = dao.all().flatMapTo(HashSet()) { listOf(it.imagePath, it.originalPath) }
        withContext(Dispatchers.IO) { storage.sweep(inUse) }
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

/** Under SQLite's bound-variable limit (999 on older Android). */
private const val MaxIdsPerQuery = 500

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
    filter = StampFilter.fromName(filter),
    renderSpec = renderSpec,
)
