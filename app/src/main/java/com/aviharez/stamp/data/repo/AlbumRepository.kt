package com.aviharez.stamp.data.repo

import android.content.Context
import com.aviharez.stamp.R
import com.aviharez.stamp.data.db.AlbumDao
import com.aviharez.stamp.data.db.AlbumEntity
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepository @Inject constructor(
    private val dao: AlbumDao,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) {
    private val defaultLock = Mutex()

    fun observeSummaries(): Flow<List<AlbumSummary>> = dao.observeSummaries().map { rows ->
        rows.map { AlbumSummary(it.album.toDomain(), it.stampCount, it.latestImagePath) }
    }

    fun observe(id: Long): Flow<Album?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: Long): Album? = dao.get(id)?.toDomain()

    suspend fun count(): Int = dao.count()

    suspend fun create(name: String, category: AlbumCategory, cover: AlbumCoverColor): Long =
        dao.insert(
            AlbumEntity(
                name = name.trim(),
                category = category.name,
                cover = cover.name,
                createdAt = clock.millis(),
            ),
        )

    suspend fun update(album: Album) = dao.update(album.toEntity())

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
