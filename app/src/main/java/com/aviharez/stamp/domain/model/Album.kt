package com.aviharez.stamp.domain.model

import java.time.Instant

/** Collections filter chips (CLAUDE.md §3). */
enum class AlbumCategory { Trips, Everyday, Pets, Other }

/** Cloth colour of an album cover. Mapped to Airmail colour tokens in the UI. */
enum class AlbumCoverColor { Ink, Cobalt, Red, Sage, Kraft }

/** A collection of stamps ("album"). */
data class Album(
    val id: Long,
    val name: String,
    val category: AlbumCategory,
    val cover: AlbumCoverColor,
    val createdAt: Instant,
)

/** The newest stamp of an album, as shown on its cover. */
data class CoverStamp(
    val imagePath: String,
    val shape: StampShape,
    val perforation: PerforationStyle,
    val takenAt: Instant,
)

/** An album with what its cover and page previews need. */
data class AlbumSummary(
    val album: Album,
    val stampCount: Int,
    val latest: CoverStamp?,
)
