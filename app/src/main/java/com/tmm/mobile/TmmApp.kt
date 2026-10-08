package com.tmm.mobile

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.DefaultAppContainer
import com.tmm.mobile.ui.components.newCoverImageLoader

class TmmApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }

    // Coil appelle cette fabrique au premier AsyncImage : en-tetes navigateur pour les couvertures.
    override fun newImageLoader(context: PlatformContext): ImageLoader = newCoverImageLoader(context)
}
