package com.aviharez.stamp

import android.app.Application
import com.aviharez.stamp.data.billing.BillingRepository
import com.aviharez.stamp.data.reminder.StreakReminder
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.widget.WidgetUpdater
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

    override fun onCreate() {
        super.onCreate()
        // Re-check what Play says this account owns (restores Pro on a new phone, drops refunds).
        billing.start()
        // Photos left behind when Android closed the app halfway through a capture or import.
        appScope.launch { stamps.sweepOrphanFiles() }
        // Home-screen widgets follow new stamps, the streak and Pro while the app runs.
        widgets.start()
        // Evening streak reminder: re-arm its alarm, pull the note once today is stamped.
        reminder.start()
    }
}
