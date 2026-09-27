package com.tmm.mobile.ui.nav

import kotlinx.serialization.Serializable

// PLAN_GEMINI.md 5.19 : types de route exacts (Navigation Compose, kotlinx.serialization).
@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable object LibraryRoute
@Serializable data class LibraryFilteredRoute(val categoryOrder: Long)
@Serializable object TransfersRoute
@Serializable object QueueRoute
@Serializable object CatalogRoute
@Serializable data class SeriesRoute(val seriesKey: String)
@Serializable object SettingsRoute
@Serializable object TranslationSettingsRoute
@Serializable object PermissionRoute
