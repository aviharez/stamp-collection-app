package com.goomistudio.postmory.data.repo

import com.goomistudio.postmory.data.db.ScrapPageDao
import com.goomistudio.postmory.data.db.ScrapPageEntity
import com.goomistudio.postmory.domain.model.ScrapItem
import com.goomistudio.postmory.domain.model.ScrapKind
import com.goomistudio.postmory.domain.model.ScrapPage
import com.goomistudio.postmory.domain.model.ScrapRules
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Singleton
class ScrapbookRepository @Inject constructor(
    private val dao: ScrapPageDao,
    private val clock: Clock,
) {
    fun observeByAlbum(albumId: Long): Flow<List<ScrapPage>> = dao.observeByAlbum(albumId).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: String): ScrapPage? = dao.get(id)?.toDomain()

    /** Saves the page as it is now (blank notes and titles are dropped); returns its id. */
    suspend fun save(page: ScrapPage): String {
        val items = page.items.filterNot(ScrapItem::isBlankWriting)
        dao.upsert(page.copy(items = items, updatedAt = clock.instant()).toEntity())
        return page.id
    }

    suspend fun delete(id: String) = dao.delete(id)
}

/** JSON for a page's pieces. Field names are short and stable: they live in the DB and in backups. */
@Serializable
data class ScrapItemJson(
    val id: String,
    val kind: String,
    val ref: String = "",
    val text: String = "",
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

object ScrapCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val serializer = ListSerializer(ScrapItemJson.serializer())

    fun encode(items: List<ScrapItem>): String = json.encodeToString(serializer, items.map(ScrapItem::toJson))

    /** Unknown kinds (from a newer app) and broken JSON are dropped rather than crashing the album. */
    fun decode(text: String): List<ScrapItem> =
        runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList()).mapNotNull(ScrapItemJson::toDomain)
}

fun ScrapItem.toJson() = ScrapItemJson(id, kind.name, ref, text, x, y, scale, rotation)

fun ScrapItemJson.toDomain(): ScrapItem? {
    val k = ScrapKind.entries.firstOrNull { it.name == kind } ?: return null
    if (!x.isFinite() || !y.isFinite() || !scale.isFinite() || !rotation.isFinite()) return null
    val limit = if (k == ScrapKind.Title) ScrapRules.MaxTitleLength else ScrapRules.MaxNoteLength
    return ScrapItem(
        id = id,
        kind = k,
        ref = ref,
        text = text.take(limit),
        x = ScrapRules.clampPosition(x),
        y = ScrapRules.clampPosition(y),
        scale = ScrapRules.clampScale(scale),
        rotation = rotation % FullTurn,
    )
}

private const val FullTurn = 360f

internal fun ScrapPageEntity.toDomain() = ScrapPage(
    id = id,
    albumId = albumId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    items = ScrapCodec.decode(items),
)

internal fun ScrapPage.toEntity() = ScrapPageEntity(
    id = id,
    albumId = albumId,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    items = ScrapCodec.encode(items),
)
