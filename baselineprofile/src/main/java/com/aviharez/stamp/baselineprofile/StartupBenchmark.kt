package com.aviharez.stamp.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start and the main paths' frame times, without and with the baseline profile.
 * Run: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` (without the flag Gradle
 * uninstalls the app afterwards, deleting the phone's stamps). Numbers only mean something on a
 * real phone; on an emulator compare the two modes, not the absolute values.
 *
 * Xiaomi HyperOS keeps app trace sections out of the system trace, so the browse tests fail there
 * with "0 found for frameDurationCpuMs"; use `adb shell dumpsys gfxinfo <pkg> framestats` instead.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun startupNoProfile() = startup(CompilationMode.None())

    @Test fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun browseNoProfile() = browse(CompilationMode.None())

    @Test fun browseWithProfile() = browse(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = Iterations,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForHome()
    }

    private fun browse(mode: CompilationMode) = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = Iterations,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForHome()
        },
    ) {
        browseAlbums()
        browseCalendar()
    }
}

private const val Iterations = 5
