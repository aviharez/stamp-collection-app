package com.aviharez.stamp.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code the app runs on start and on its main paths, so it ships compiled ahead of
 * time. Run: `./gradlew :app:generateBaselineProfile` with a device or emulator connected (API 33+,
 * or rooted 28+). Output lands in app/src/release/generated/baselineProfiles. The paths need
 * stamps on the device (restore a backup first); the run uninstalls the app at the end.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TargetPackage, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        browseAlbums()
        browseCalendar()
        openCamera()
    }
}
