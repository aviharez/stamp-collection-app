package com.aviharez.stamp.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

/** First run only: what the app does + the way to a first stamp. Full screen, cobalt. */
@Serializable data object WelcomeRoute

/** Album tab: home page of the stamp album. */
@Serializable data object HomeRoute

/** Album tab: list of all albums (collections). */
@Serializable data object CollectionsRoute

/** One album's page (collection detail). Full screen, no nav bar. */
@Serializable data class AlbumPageRoute(val albumId: Long)

/**
 * Camera. Full screen, dark. [fromWelcome]: the welcome already explained it, so ask for the camera
 * permission straight away instead of showing the in-app pre-prompt first.
 */
@Serializable data class CameraRoute(val fromWelcome: Boolean = false)

/** Gallery import: frame a picked photo ([uri] from the system photo picker). Full screen, dark. */
@Serializable data class ImportRoute(val uri: String)

/** "Stamped!" celebration for a just-saved stamp. Full screen, cobalt. */
@Serializable data class StampedRoute(val stampId: String)

/** Pro paywall, opened only by the user asking for something Pro covers. [reason] = PaywallReason name. */
@Serializable data class PaywallRoute(val reason: String)

@Serializable data object CalendarRoute

@Serializable data object MapRoute

@Serializable data object ProfileRoute

/** Debug builds only: component gallery. */
@Serializable data object DebugGalleryRoute
