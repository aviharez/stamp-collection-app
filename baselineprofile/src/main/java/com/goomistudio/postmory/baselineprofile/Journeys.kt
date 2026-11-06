package com.goomistudio.postmory.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/**
 * The app's main paths, shared by the baseline profile generator and the benchmarks. Selectors are
 * the on-screen texts / content descriptions from strings.xml, so a copy change may need an update
 * here. Albums are whatever is on the device; the steps skip what isn't there (empty install).
 */
internal const val TargetPackage = "com.goomistudio.postmory"
private const val WaitMillis = 5_000L

internal fun MacrobenchmarkScope.waitForHome() {
    // Fresh install: the welcome screen comes first.
    device.wait(Until.findObject(By.text("Skip")), 1_500)?.click()
    device.wait(Until.hasObject(By.text("Your stamp album")), WaitMillis)
    device.waitForIdle()
}

/** Home → Collections → an album → swipe its pages → open a stamp → back to Home. */
internal fun MacrobenchmarkScope.browseAlbums() {
    if (!tap(By.text("ALL ALBUMS"))) return
    device.wait(Until.hasObject(By.text("Collections")), WaitMillis)
    device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
    device.findObject(By.scrollable(true))?.fling(Direction.UP)
    // The biggest album has the most pages to swipe through; fall back to any album cover.
    val album = device.findObject(By.textEndsWith("stamps · updated today"))
        ?: device.findObject(By.textContains(" stamp"))
    if (album != null) {
        album.click()
        device.waitForIdle()
        repeat(2) { swipePage(Direction.RIGHT) } // older pages
        repeat(2) { swipePage(Direction.LEFT) }
        if (tap(By.descStartsWith("Stamp, "))) device.pressBack()
        device.pressBack()
    }
    device.pressBack()
    device.waitForIdle()
}

/** Calendar tab: change month both ways, pick a day. */
internal fun MacrobenchmarkScope.browseCalendar() {
    if (!tap(By.text("calendar"))) return
    repeat(2) { swipePage(Direction.RIGHT) }
    repeat(2) { swipePage(Direction.LEFT) }
    tap(By.text("album"))
    device.wait(Until.hasObject(By.text("Your stamp album")), WaitMillis)
}

/** Open the camera from Home and close it again. */
internal fun MacrobenchmarkScope.openCamera() {
    device.wait(Until.hasObject(By.text("Your stamp album")), WaitMillis)
    device.waitForIdle()
    val button = device.findObject(By.text("Stamp it")) ?: device.findObject(By.desc("Stamp another")) ?: return
    button.click()
    device.wait(Until.hasObject(By.desc("Take stamp photo")), WaitMillis)
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.hasObject(By.text("Your stamp album")), WaitMillis)
}

private fun MacrobenchmarkScope.tap(selector: BySelector): Boolean {
    val target = device.wait(Until.findObject(selector), WaitMillis) ?: return false
    target.click()
    device.waitForIdle()
    return true
}

/** A sideways swipe across the middle of the screen (album pages, calendar months). */
private fun MacrobenchmarkScope.swipePage(direction: Direction) {
    val w = device.displayWidth
    val y = device.displayHeight / 2
    val (from, to) = if (direction == Direction.LEFT) w * 4 / 5 to w / 5 else w / 5 to w * 4 / 5
    device.swipe(from, y, to, y, SwipeSteps)
    device.waitForIdle()
}

private const val SwipeSteps = 12
