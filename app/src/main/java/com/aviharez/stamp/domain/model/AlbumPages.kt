package com.aviharez.stamp.domain.model

/**
 * An album is a book of pages with [SlotsPerPage] slots (3 × 3), filled oldest first. The last
 * page always keeps one empty "next stamp…" slot, so a full last page means a fresh page follows.
 */
object AlbumPages {
    const val SlotsPerPage = 9

    /** Number of pages for [stampCount] stamps (always ≥ 1, room for the next stamp included). */
    fun pageCount(stampCount: Int): Int = stampCount.coerceAtLeast(0) / SlotsPerPage + 1

    /** 1-based page holding the stamp at 0-based [index] (oldest = 0). */
    fun pageOf(index: Int): Int = index.coerceAtLeast(0) / SlotsPerPage + 1

    /** 1-based page of the newest stamp, or 1 for an empty album. */
    fun latestPage(stampCount: Int): Int = pageOf(stampCount - 1)

    /** Splits stamps (oldest first) into pages; the last page may be empty. */
    fun <T> paginate(oldestFirst: List<T>): List<List<T>> {
        val pages = oldestFirst.chunked(SlotsPerPage).toMutableList()
        if (pages.isEmpty() || pages.last().size == SlotsPerPage) pages += emptyList<T>()
        return pages
    }
}
