package com.aviharez.stamp.data.repo

import android.content.Context
import com.aviharez.stamp.R
import com.aviharez.stamp.data.db.AlbumDao
import com.aviharez.stamp.data.db.AlbumEntity
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import com.aviharez.stamp.domain.model.CoverStamp
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import com.aviharez.stamp.data.analytics.UsageStats
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepository @Inject constructor(
    private val dao: AlbumDao,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
    private val usage: UsageStats,
) {
    private val defaultLock = Mutex()

    fun observeSummaries(): Flow<List<AlbumSummary>> = dao.observeSummaries().map { rows ->
        rows.map { row ->
            val latest = row.latestImagePath?.let { path ->
                CoverStamp(
                    imagePath = path,
                    shape = enumOr(row.latestShape.orEmpty(), StampShape.Portrait),
                    perforation = enumOr(row.latestPerforation.orEmpty(), PerforationStyle.Classic),
                    takenAt = Instant.ofEpochMilli(row.latestTakenAt ?: 0L),
                )
            }
            AlbumSummary(row.album.toDomain(), row.stampCount, latest)
        }
    }

    fun observe(id: Long): Flow<Album?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: Long): Album? = dao.get(id)?.toDomain()

    suspend fun count(): Int = dao.count()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun create(name: String, category: AlbumCategory, cover: AlbumCoverColor): Long =
        dao.insert(
            AlbumEntity(
                name = name.trim(),
                category = category.name,
                cover = cover.name,
                createdAt = clock.millis(),
            ),
        ).also { usage.albumCreated() }

    suspend fun update(album: Album) = dao.update(album.toEntity())

    /** Rename / recolour / recategorise. Blank names are ignored. */
    suspend fun edit(id: Long, name: String, category: AlbumCategory, cover: AlbumCoverColor) {
        val album = get(id) ?: return
        dao.update(album.copy(name = name.trim().ifEmpty { album.name }, category = category, cover = cover).toEntity())
    }

    /** The first album, creating "Everyday" when there is none yet (first stamp ever). */
    suspend fun ensureDefault(): Long = defaultLock.withLock {
        dao.first()?.id ?: create(
            name = context.getString(R.string.album_default_name),
            category = AlbumCategory.Everyday,
            cover = AlbumCoverColor.Sage,
        )
    }
}

internal fun AlbumEntity.toDomain() = Album(
    id = id,
    name = name,
    category = enumOr(category, AlbumCategory.Other),
    cover = enumOr(cover, AlbumCoverColor.Kraft),
    createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun Album.toEntity() = AlbumEntity(
    id = id,
    name = name,
    category = category.name,
    cover = cover.name,
    createdAt = createdAt.toEpochMilli(),
)

/** Tolerates enum values removed or renamed since the row was written. */
internal inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback
