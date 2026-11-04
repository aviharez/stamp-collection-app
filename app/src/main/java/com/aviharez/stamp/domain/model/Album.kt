package com.aviharez.stamp.domain.model

import java.time.Instant

/** Collections filter chips (CLAUDE.md §3). */
enum class AlbumCategory { Trips, Everyday, Pets, Other }

/** Cloth colour of an album cover. Mapped to Airmail colour tokens in the UI. */
enum class AlbumCoverColor { Cobalt, Red, Sage, Kraft, Ink }

/** A collection of stamps ("album"). */
data class Album(
    val id: Long,
    val name: String,
    val category: AlbumCategory,
    val cover: AlbumCoverColor,
    val createdAt: Instant,
)

/** An album with what its cover and page previews need. */
data class AlbumSummary(
    val album: Album,
    val stampCount: Int,
    val latestStampPath: String?,
)
