package com.equisteg.mydaycompanion

import android.app.Application
import com.equisteg.mydaycompanion.reminders.ReminderReceiver

class MyDayCompanionApp : Application() {
    lateinit var services: AppServices
        private set

    override fun onCreate() {
        super.onCreate()
        services = AppContainer(this)
        ReminderReceiver.ensureChannels(this)
    }

    /** Lets tests run the real UI against an in-memory graph. */
    fun replaceServicesForTest(replacement: AppServices) {
        services = replacement
    }
}
