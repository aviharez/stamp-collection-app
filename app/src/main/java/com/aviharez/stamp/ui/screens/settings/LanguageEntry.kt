package com.aviharez.stamp.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import java.util.Locale

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
        /** The language picked for the app (here or in Android 13+'s App language setting); null = the phone's. */
        fun current(): AppLanguage? = of(AppCompatDelegate.getApplicationLocales()[0])

        fun of(locale: Locale?): AppLanguage? {
            val language = locale?.language ?: return null
            // Java still calls Indonesian "in".
            val tag = if (language == "in") "id" else language
            return entries.firstOrNull { it.tag == tag }
        }

        /**
         * Switches the app only (null = back to the phone's language); the phone keeps its own. The
         * screen is recreated in the new language; Android 13+ keeps it in the system's per-app
         * setting, AppCompat does on 8–12.
         */
        fun set(language: AppLanguage?) {
            AppCompatDelegate.setApplicationLocales(
                if (language == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}

/** Profile's Language section: "App language" with a dropdown (the phone's language or one of the app's). */
@Composable
fun LanguageEntry() {
    val context = LocalContext.current
    val phoneLocale = remember(context) { LocaleManagerCompat.getSystemLocales(context)[0] ?: Locale.getDefault() }
    // The phone's language in its own name, as the menu writes the others.
    val phoneLanguage = AppLanguage.of(phoneLocale)?.let { stringResource(it.nameRes) }
        ?: phoneLocale.getDisplayLanguage(phoneLocale).replaceFirstChar { it.titlecase(phoneLocale) }
    LanguageEntryContent(
        selected = AppLanguage.current(),
        phoneLanguage = phoneLanguage,
        onPick = { if (it != AppLanguage.current()) AppLanguage.set(it) },
    )
}

@Composable
fun LanguageEntryContent(
    selected: AppLanguage?,
    phoneLanguage: String,
    onPick: (AppLanguage?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val t = StampTheme.type
    var open by remember { mutableStateOf(false) }
    val current = selected?.let { stringResource(it.nameRes) } ?: stringResource(R.string.language_phone)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.xxs)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = s.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.s),
        ) {
            Text(stringResource(R.string.language_app), style = t.body, color = c.textHigh, modifier = Modifier.weight(1f))
            Box {
                // The value as a Surface2 pill with a chevron, like a select field.
                Row(
                    Modifier
                        .heightIn(min = s.touchTarget)
                        .clip(StampTheme.shapes.pill)
                        .clickable(onClickLabel = stringResource(R.string.language_change), role = Role.DropdownList) { open = true }
                        .padding(vertical = s.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .background(c.surface2, StampTheme.shapes.pill)
                            .padding(start = s.m, end = s.s, top = s.xs, bottom = s.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(s.xxs),
                    ) {
                        Text(current, style = t.button, color = c.ink, maxLines = 1)
                        Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, Modifier.size(ChevronSize), tint = c.ink)
                    }
                }
                LanguageMenu(
                    open = open,
                    selected = selected,
                    phoneLanguage = phoneLanguage,
                    onPick = {
                        open = false
                        onPick(it)
                    },
                    onDismiss = { open = false },
                )
            }
        }
        HandNote(
            if (selected == null) {
                stringResource(R.string.language_phone_note, phoneLanguage)
            } else {
                stringResource(R.string.language_picked_note, phoneLanguage)
            },
            style = t.noteSmall,
            strength = InkStrength.Soft,
        )
    }
}

/**
 * The menu: a PaperWhite card with a warm shadow (the Material menu's own surface is left
 * transparent so the shadow is ours, not black). The picked line is red with a tick.
 */
@Composable
private fun LanguageMenu(
    open: Boolean,
    selected: AppLanguage?,
    phoneLanguage: String,
    onPick: (AppLanguage?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, -s.xxs),
        shape = RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier
                .padding(horizontal = s.s, vertical = s.xxs)
                .tintedShadow(Elevation.Card, StampTheme.shapes.card, c.shadowWarm)
                .background(c.paperWhite, StampTheme.shapes.card)
                .clip(StampTheme.shapes.card)
                .widthIn(min = MenuMinWidth)
                .padding(vertical = s.xs),
        ) {
            LanguageMenuItem(stringResource(R.string.language_phone), note = phoneLanguage, picked = selected == null) { onPick(null) }
            AppLanguage.entries.forEach { language ->
                LanguageMenuItem(stringResource(language.nameRes), note = null, picked = language == selected) { onPick(language) }
            }
        }
    }
}

@Composable
private fun LanguageMenuItem(name: String, note: String?, picked: Boolean, onClick: () -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = s.touchTarget)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = picked }
            .padding(horizontal = s.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(name, style = StampTheme.type.body, color = if (picked) c.airmailRed else c.textHigh)
            if (note != null) Text(note, style = StampTheme.type.label, color = c.textMedium)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(start = s.m).size(CheckSize)) {
            if (picked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(CheckSize), tint = c.airmailRed)
        }
    }
}

private val ChevronSize = 16.dp
private val CheckSize = 18.dp
private val MenuMinWidth = 220.dp

@PaperPreview
@Composable
private fun LanguageEntryPreview() {
    StampAppTheme {
        Column(Modifier.padding(StampTheme.spacing.m)) {
            LanguageEntryContent(selected = null, phoneLanguage = "English", onPick = {})
            LanguageEntryContent(selected = AppLanguage.Indonesian, phoneLanguage = "English", onPick = {})
        }
    }
}
