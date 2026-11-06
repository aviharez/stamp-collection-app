package com.goomistudio.postmory.data.backup

import com.goomistudio.postmory.data.db.AlbumEntity
import com.goomistudio.postmory.data.db.ScrapPageEntity
import com.goomistudio.postmory.data.db.StampEntity
import com.goomistudio.postmory.data.repo.ScrapCodec
import com.goomistudio.postmory.domain.model.ScrapItem
import com.goomistudio.postmory.domain.model.ScrapKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val idA = "11111111-1111-1111-1111-111111111111"
    private val idB = "22222222-2222-2222-2222-222222222222"
    private val trip = AlbumEntity(id = 7, name = "Japan", category = "Trips", cover = "Cobalt", createdAt = 100)
    private val empty = AlbumEntity(id = 8, name = "Pets", category = "Pets", cover = "Sage", createdAt = 200)

    private fun stamp(id: String, album: Long = 7) = StampEntity(
        id = id, albumId = album, shape = "Portrait", perforation = "Classic",
        imagePath = "/x/$id.png", originalPath = "/x/$id.jpg", takenAt = 1, day = "2026-11-04",
        createdDay = "2026-11-04", city = "Kyoto", country = "Japan", countryCode = "JP",
        latitude = null, longitude = null, title = null, caption = "hi", source = "Camera",
    )

    private val pageId = "33333333-3333-3333-3333-333333333333"
    private val pageItems = listOf(
        ScrapItem(id = "a", kind = ScrapKind.Stamp, ref = idA, x = 0.5f, y = 0.4f, scale = 1.2f, rotation = -4f),
        ScrapItem(id = "b", kind = ScrapKind.Note, text = "first day", x = 0.3f, y = 0.8f),
    )
    private val page = ScrapPageEntity(pageId, albumId = 7, createdAt = 10, updatedAt = 11, items = ScrapCodec.encode(pageItems))

    private val manifest = BackupFormat.manifestOf(
        albums = listOf(trip, empty),
        stamps = listOf(stamp(idA), stamp(idB)),
        scrapPages = listOf(page),
        originalExtension = { "jpg" },
        settings = BackupSettings(mirrorSelfie = false, bonusAlbums = 1),
        now = 5,
    )
    private val allImages = setOf(BackupFormat.renderedEntry(idA), BackupFormat.renderedEntry(idB))

    @Test
    fun manifestSurvivesJson() {
        val json = BackupFormat.json.encodeToString(BackupManifest.serializer(), manifest)
        assertEquals(manifest, BackupFormat.json.decodeFromString(BackupManifest.serializer(), json))
    }

    @Test
    fun restoreOnEmptyPhoneBringsEverythingIncludingEmptyAlbums() {
        val plan = BackupFormat.plan(manifest, existingAlbums = emptyList(), existingStampIds = emptySet(), imagesInFile = allImages)
        assertEquals(2, plan.newAlbums.size)
        assertEquals(2, plan.newStamps.size)
        assertEquals(0, plan.alreadyHere)
        assertEquals(listOf(pageId), plan.newScrapPages.map { it.id })
        val restored = BackupFormat.scrapPageEntity(plan.newScrapPages[0], albumId = 70)
        assertEquals(pageItems, ScrapCodec.decode(restored.items))
        assertEquals(70L, restored.albumId)
    }

    @Test
    fun scrapPagesAlreadyHereOrWithoutTheirAlbumAreSkipped() {
        val orphan = manifest.scrapPages[0].copy(id = "44444444-4444-4444-4444-444444444444", albumKey = 99)
        val withOrphan = manifest.copy(scrapPages = manifest.scrapPages + orphan)
        assertTrue(BackupFormat.plan(withOrphan, emptyList(), emptySet(), allImages, existingScrapPageIds = setOf(pageId)).newScrapPages.isEmpty())
    }

    @Test
    fun restoreOnSamePhoneAddsNothingAndReusesAlbums() {
        val plan = BackupFormat.plan(manifest, listOf(trip.copy(id = 70), empty.copy(id = 80)), setOf(idA, idB), allImages)
        assertEquals(mapOf(7L to 70L, 8L to 80L), plan.reuseAlbums)
        assertTrue(plan.newAlbums.isEmpty())
        assertTrue(plan.newStamps.isEmpty())
        assertEquals(2, plan.alreadyHere)
    }

    @Test
    fun stampsWithoutTheirPictureOrWithBadIdsAreLeftOut() {
        val tampered = manifest.copy(stamps = manifest.stamps + manifest.stamps[0].copy(id = "../../evil", image = "rendered/../../evil.png"))
        val plan = BackupFormat.plan(tampered, emptyList(), emptySet(), setOf(BackupFormat.renderedEntry(idA)))
        assertEquals(listOf(idA), plan.newStamps.map { it.id })
    }

    @Test
    fun onlyOurOwnEntryNamesAreRead() {
        assertTrue(BackupFormat.isImageEntry("rendered/$idA.png"))
        assertTrue(BackupFormat.isImageEntry("originals/$idA.jpg"))
        assertFalse(BackupFormat.isImageEntry("../rendered/$idA.png"))
        assertFalse(BackupFormat.isImageEntry("rendered/../../shared_prefs/x.png"))
        assertFalse(BackupFormat.isImageEntry("rendered/$idA.png/../x"))
        assertFalse(BackupFormat.isImageEntry("databases/stamps.db"))
    }
}
