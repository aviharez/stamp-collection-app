package com.aviharez.stamp.domain

import com.aviharez.stamp.domain.model.AlbumPages
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPagesTest {
    @Test fun emptyAlbumHasOnePage() {
        assertEquals(1, AlbumPages.pageCount(0))
        assertEquals(listOf(emptyList<Int>()), AlbumPages.paginate(emptyList<Int>()))
        assertEquals(1, AlbumPages.latestPage(0))
    }

    @Test fun eightStampsLeaveRoomForTheNextOnPageOne() {
        assertEquals(1, AlbumPages.pageCount(8))
        assertEquals(1, AlbumPages.paginate((1..8).toList()).size)
        assertEquals(1, AlbumPages.latestPage(8))
    }

    @Test fun fullPageStartsAFreshOne() {
        assertEquals(2, AlbumPages.pageCount(9))
        val pages = AlbumPages.paginate((1..9).toList())
        assertEquals(listOf((1..9).toList(), emptyList()), pages)
        assertEquals(1, AlbumPages.latestPage(9))
    }

    @Test fun tenthStampIsOnPageTwo() {
        assertEquals(2, AlbumPages.pageCount(10))
        assertEquals(2, AlbumPages.latestPage(10))
        assertEquals(listOf(10), AlbumPages.paginate((1..10).toList())[1])
    }
}
