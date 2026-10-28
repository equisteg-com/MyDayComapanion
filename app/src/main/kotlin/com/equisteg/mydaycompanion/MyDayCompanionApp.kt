package com.equisteg.mydaycompanion

import android.app.Application

class MyDayCompanionApp : Application() {
    lateinit var services: AppServices
        private set

    override fun onCreate() {
        super.onCreate()
        services = AppContainer(this)
    }

    /** Lets tests run the real UI against an in-memory graph. */
    fun replaceServicesForTest(replacement: AppServices) {
        services = replacement
    }
}
