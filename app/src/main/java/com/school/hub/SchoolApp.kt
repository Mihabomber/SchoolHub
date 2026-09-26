package com.school.hub

import android.app.Application

class SchoolApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.school.hub.feature.translator.CyrillicOcr.init(this)
        container = AppContainer(this)
        container.start()
    }
}
