package com.aviharez.stamp.data.db

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
    /** [com.aviharez.stamp.domain.model.AlbumCategory] name. */
    val category: String,
    /** [com.aviharez.stamp.domain.model.AlbumCoverColor] name. */
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
    val city: String?,
    val country: String?,
    @ColumnInfo(name = "country_code") val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val title: String?,
    val caption: String?,
    val source: String,
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
