package com.aviharez.stamp.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.core.os.LocaleListCompat
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** A language the app is translated into: its tag and its own name ("Deutsch", "日本語"). */
enum class AppLanguage(val tag: String, val nameRes: Int) {
    English("en", R.string.language_en),
    Indonesian("id", R.string.language_id),
    Spanish("es", R.string.language_es),
    Portuguese("pt", R.string.language_pt),
    French("fr", R.string.language_fr),
    German("de", R.string.language_de),
    Japanese("ja", R.string.language_ja),
    ;

    companion object {
        /** The language picked in the app (or Android 13+'s App language setting); null = the phone's. */
        fun current(): AppLanguage? {
            val language = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return null
            // Java still calls Indonesian "in".
            val tag = if (language == "in") "id" else language
            return entries.firstOrNull { it.tag == tag }
        }

        /**
         * Switches the whole app (null = back to the phone's language). The screen is recreated in
         * the new language; Android 13+ keeps it in the system's per-app setting, AppCompat does on 8–12.
         */
        fun set(language: AppLanguage?) {
            val locales = if (language == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}

/** Profile's Language section: the phone's language or one of the app's, as chips. */
@Composable
fun LanguageEntry() {
    LanguageEntryContent(selected = AppLanguage.current(), onPick = { if (it != AppLanguage.current()) AppLanguage.set(it) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageEntryContent(selected: AppLanguage?, onPick: (AppLanguage?) -> Unit, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.xxs)) {
        FlowRow(
            Modifier.semantics { selectableGroup() },
            horizontalArrangement = Arrangement.spacedBy(s.xs),
            verticalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            PaperChip(stringResource(R.string.language_phone), selected = selected == null, onClick = { onPick(null) })
            AppLanguage.entries.forEach { language ->
                PaperChip(stringResource(language.nameRes), selected = language == selected, onClick = { onPick(language) })
            }
        }
        HandNote(
            stringResource(if (selected == null) R.string.language_phone_note else R.string.language_picked_note),
            style = StampTheme.type.noteSmall,
            strength = InkStrength.Soft,
        )
    }
}

@PaperPreview
@Composable
private fun LanguageEntryPreview() {
    StampAppTheme {
        Column {
            LanguageEntryContent(selected = null, onPick = {})
            LanguageEntryContent(selected = AppLanguage.Japanese, onPick = {})
        }
    }
}
