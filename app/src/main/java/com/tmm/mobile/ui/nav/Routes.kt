package com.tmm.mobile.ui.nav

import kotlinx.serialization.Serializable

// PLAN_GEMINI.md 5.19 : types de route exacts (Navigation Compose, kotlinx.serialization).
@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable data class LibraryRoute(val picking: Boolean = false)
@Serializable object QueueRoute
@Serializable data class SeriesRoute(val seriesKey: String)
@Serializable object SettingsRoute
@Serializable object TranslationSettingsRoute
@Serializable object PermissionRoute
