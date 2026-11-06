package com.goomistudio.postmory.ui

import com.goomistudio.postmory.data.billing.ProRules
import com.goomistudio.postmory.domain.model.Album
import com.goomistudio.postmory.domain.model.AlbumCategory
import com.goomistudio.postmory.domain.model.AlbumCoverColor
import com.goomistudio.postmory.domain.model.AlbumSummary
import com.goomistudio.postmory.ui.screens.collections.CollectionsUiState
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

    @Test fun eachAdBonusAddsOneAlbum() {
        assertTrue(CollectionsUiState(albums = albums(ProRules.FreeAlbumLimit), bonusAlbums = 1).canCreateAlbum)
        assertFalse(CollectionsUiState(albums = albums(ProRules.FreeAlbumLimit + 1), bonusAlbums = 1).canCreateAlbum)
    }
}
