package com.goomistudio.postmory.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.goomistudio.postmory.domain.model.AlbumCategory] name. */
    val category: String,
    /** [com.goomistudio.postmory.domain.model.AlbumCoverColor] name. */
    val cover: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "stamps",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("album_id"), Index("day")],
)
data class StampEntity(
    /** Shared with the image file names in StampStorage. */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    val shape: String,
    val perforation: String,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "original_path") val originalPath: String,
    /** Epoch millis. */
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    /** ISO local date (yyyy-MM-dd) in the time zone at capture; sorts as text. */
    val day: String,
    /**
     * ISO local date the stamp was made in the app. Only stamps made on their own [day] count for
     * the streak (an old photo imported later shows on the calendar but can't backfill a streak).
     */
    @ColumnInfo(name = "created_day", defaultValue = "") val createdDay: String,
    val city: String?,
    val country: String?,
    @ColumnInfo(name = "country_code") val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val title: String?,
    val caption: String?,
    val source: String,
    /** [com.goomistudio.postmory.domain.model.StampFilter] name. */
    @ColumnInfo(defaultValue = "Original") val filter: String = "Original",
    /** How the picture was cut from the original ([com.goomistudio.postmory.data.image.RenderSpec]); null before v3. */
    @ColumnInfo(name = "render_spec") val renderSpec: String? = null,
)

/** Row for the albums list: album + count + newest stamp image. */
data class AlbumSummaryRow(
    @Embedded val album: AlbumEntity,
    @ColumnInfo(name = "stamp_count") val stampCount: Int,
    @ColumnInfo(name = "latest_image_path") val latestImagePath: String?,
    @ColumnInfo(name = "latest_shape") val latestShape: String?,
    @ColumnInfo(name = "latest_perforation") val latestPerforation: String?,
    @ColumnInfo(name = "latest_taken_at") val latestTakenAt: Long?,
)

/** A free-form scrapbook page; its pieces are JSON ([com.goomistudio.postmory.data.repo.ScrapCodec]). */
@Entity(
    tableName = "scrap_pages",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("album_id")],
)
data class ScrapPageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val items: String,
)

/** Passport numbers on Profile. */
data class StampTotalsRow(val stamps: Int, val places: Int)
