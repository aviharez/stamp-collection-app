package com.aviharez.stamp

import android.app.Application
import com.aviharez.stamp.data.billing.BillingRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StampApplication : Application() {

    @Inject lateinit var billing: BillingRepository

    override fun onCreate() {
        super.onCreate()
        // Re-check what Play says this account owns (restores Pro on a new phone, drops refunds).
        billing.start()
    }
}
