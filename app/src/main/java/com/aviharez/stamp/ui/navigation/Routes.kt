package com.aviharez.stamp.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

/** Album tab: home page of the stamp album. */
@Serializable data object HomeRoute

/** Album tab: list of all albums (collections). */
@Serializable data object CollectionsRoute

/** One album's page (collection detail). Full screen, no nav bar. */
@Serializable data class AlbumPageRoute(val albumId: Long)

/** Camera. Full screen, dark. */
@Serializable data object CameraRoute

/** "Stamped!" celebration for a just-saved stamp. Full screen, cobalt. */
@Serializable data class StampedRoute(val stampId: String)

@Serializable data object CalendarRoute

@Serializable data object MapRoute

@Serializable data object ProfileRoute

/** Debug builds only: component gallery. */
@Serializable data object DebugGalleryRoute
