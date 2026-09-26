package com.tmm.mobile

import android.app.Application
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.DefaultAppContainer

class TmmApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
