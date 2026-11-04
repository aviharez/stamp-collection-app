package com.aviharez.stamp.ui

import com.aviharez.stamp.data.billing.ProRules
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import com.aviharez.stamp.ui.screens.collections.CollectionsUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlbumLimitTest {
    private fun albums(n: Int) = List(n) {
        AlbumSummary(Album(it.toLong(), "A$it", AlbumCategory.Everyday, AlbumCoverColor.Ink, Instant.EPOCH), 0, null)
    }

    @Test fun freeUserCanFillTheFreeAlbums() {
        assertTrue(CollectionsUiState(albums = albums(ProRules.FreeAlbumLimit - 1)).canCreateAlbum)
    }

    @Test fun freeUserAtTheLimitNeedsPro() {
        assertFalse(CollectionsUiState(albums = albums(ProRules.FreeAlbumLimit)).canCreateAlbum)
    }

    @Test fun proHasNoLimit() {
        assertTrue(CollectionsUiState(albums = albums(ProRules.FreeAlbumLimit + 5), isPro = true).canCreateAlbum)
    }
}
