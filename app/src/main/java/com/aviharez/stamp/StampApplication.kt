package com.aviharez.stamp

import android.app.Application
import com.aviharez.stamp.data.billing.BillingRepository
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StampApplication : Application() {

    @Inject lateinit var billing: BillingRepository
    @Inject lateinit var stamps: StampRepository
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Re-check what Play says this account owns (restores Pro on a new phone, drops refunds).
        billing.start()
        // Photos left behind when Android closed the app halfway through a capture or import.
        appScope.launch { stamps.sweepOrphanFiles() }
    }
}
