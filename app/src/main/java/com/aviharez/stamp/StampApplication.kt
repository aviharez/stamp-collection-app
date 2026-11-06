package com.aviharez.stamp

import android.app.Application
import android.content.res.Configuration
import com.aviharez.stamp.data.analytics.UsageStats
import com.aviharez.stamp.data.billing.BillingRepository
import com.aviharez.stamp.data.reminder.StreakReminder
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.widget.WidgetUpdater
import com.aviharez.stamp.widget.updateStampWidgets
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StampApplication : Application() {

    @Inject lateinit var billing: BillingRepository
    @Inject lateinit var stamps: StampRepository
    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var widgets: WidgetUpdater
    @Inject lateinit var reminder: StreakReminder
    @Inject lateinit var usageStats: UsageStats

    override fun onCreate() {
        super.onCreate()
        locales = resources.configuration.locales.toLanguageTags()
        // Crash reports + usage counts, if the Profile switch allows (first, so start-up crashes count).
        usageStats.start()
        // Re-check what Play says this account owns (restores Pro on a new phone, drops refunds).
        billing.start()
        // Photos left behind when Android closed the app halfway through a capture or import.
        appScope.launch { stamps.sweepOrphanFiles() }
        // Home-screen widgets follow new stamps, the streak and Pro while the app runs.
        widgets.start()
        // Evening streak reminder: re-arm its alarm, pull the note once today is stamped.
        reminder.start()
    }

    private var locales: String? = null

    /** The app's language changed (Profile → Language, or the system's App language): widgets redraw in it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val now = newConfig.locales.toLanguageTags()
        if (locales != null && locales != now) appScope.launch { updateStampWidgets(this@StampApplication) }
        locales = now
    }
}
