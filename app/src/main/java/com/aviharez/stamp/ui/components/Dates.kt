package com.aviharez.stamp.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Formats [date] with a pattern from strings.xml in the user's current locale. */
@Composable
fun rememberDateText(@StringRes pattern: Int, date: LocalDate = LocalDate.now()): String {
    val locale = LocalConfiguration.current.locales[0]
    val patternText = stringResource(pattern)
    return remember(patternText, locale, date) {
        DateTimeFormatter.ofPattern(patternText, locale).format(date)
    }
}
