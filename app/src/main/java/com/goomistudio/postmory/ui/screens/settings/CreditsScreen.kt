package com.goomistudio.postmory.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.goomistudio.postmory.R
import com.goomistudio.postmory.ui.components.CircleIconButton
import com.goomistudio.postmory.ui.components.HandNote
import com.goomistudio.postmory.ui.components.InkStrength
import com.goomistudio.postmory.ui.components.Tilt
import com.goomistudio.postmory.ui.components.TitleWithNote
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.tintedShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Licences the app ships under. [file] is the full text in assets/licenses; null = a link-only licence. */
enum class Licence(@StringRes val label: Int, val file: String?) {
    Ofl(R.string.credits_licence_ofl, null),
    Apache(R.string.credits_licence_apache, "Apache-2.0.txt"),
    PublicDomain(R.string.credits_licence_public_domain, "NaturalEarth.txt"),
    Google(R.string.credits_licence_google, null),
}

/** One thing the app is made with. Names and copyright lines are proper nouns, not translated. */
data class Credit(val name: String, val by: String, val licence: Licence, val file: String? = licence.file)

/** Every font, map and library inside the release app (keep in step with libs.versions.toml). */
val Credits: List<Pair<Int, List<Credit>>> = listOf(
    R.string.credits_section_fonts to listOf(
        Credit("Fraunces", "© 2020 The Fraunces Project Authors", Licence.Ofl, "OFL-Fraunces.txt"),
        Credit("Plus Jakarta Sans", "© 2020 The Plus Jakarta Sans Project Authors", Licence.Ofl, "OFL-PlusJakartaSans.txt"),
        Credit("Caveat", "© 2014 The Caveat Project Authors", Licence.Ofl, "OFL-Caveat.txt"),
    ),
    R.string.credits_section_map to listOf(
        Credit("Natural Earth", "naturalearthdata.com", Licence.PublicDomain),
    ),
    R.string.credits_section_code to listOf(
        Credit("Android Jetpack", "© The Android Open Source Project", Licence.Apache),
        Credit("Jetpack Compose", "© The Android Open Source Project", Licence.Apache),
        Credit("CameraX", "© The Android Open Source Project", Licence.Apache),
        Credit("Room · DataStore · Glance", "© The Android Open Source Project", Licence.Apache),
        Credit("Kotlin · kotlinx.coroutines · kotlinx.serialization", "© JetBrains s.r.o. and contributors", Licence.Apache),
        Credit("Dagger · Hilt", "© The Dagger Authors", Licence.Apache),
        Credit("Coil", "© Coil Contributors", Licence.Apache),
        Credit("Google Play services (location, ads, consent)", "© Google LLC", Licence.Google),
        Credit("Google Play Billing Library", "© Google LLC", Licence.Google),
    ),
)

/** Profile → "Credits": the fonts, map and code the app is made with, each opening its licence. */
@Composable
fun CreditsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val all = Credits.flatMap { it.second }
    all.firstOrNull { it.name == open }?.let { credit ->
        LicenceSheet(credit, onDismiss = { open = null })
    }
    CreditsContent(onBack, onOpen = { open = it.name }, modifier)
}

@Composable
fun CreditsContent(onBack: () -> Unit, onOpen: (Credit) -> Unit, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        modifier.fillMaxSize().background(c.paper).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = s.screen,
            end = s.screen,
            top = s.l,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + s.l,
        ),
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        item(contentType = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
                Row(Modifier.padding(bottom = s.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.s)) {
                    CircleIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), onBack)
                    Text(stringResource(R.string.nav_profile).uppercase(locale), style = StampTheme.type.overline, color = c.textMedium)
                }
                TitleWithNote(
                    note = {
                        HandNote(
                            stringResource(R.string.credits_note),
                            style = StampTheme.type.noteSmall,
                            rotation = Tilt.note(NoteSeed),
                            modifier = Modifier.padding(bottom = s.xxs),
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.credits_title),
                        style = StampTheme.type.screenTitle,
                        color = c.textHigh,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }
        Credits.forEach { (section, credits) ->
            item(key = "s$section", contentType = "section") {
                Text(
                    stringResource(section).uppercase(locale),
                    style = StampTheme.type.overline,
                    color = c.textMedium,
                    modifier = Modifier.padding(top = s.m).semantics { heading() },
                )
            }
            items(credits, key = { it.name }, contentType = { "credit" }) { credit -> CreditRow(credit, onOpen) }
        }
        item(contentType = "footer") {
            HandNote(
                stringResource(R.string.credits_footer),
                style = StampTheme.type.noteSmall,
                strength = InkStrength.Soft,
                modifier = Modifier.padding(top = s.m),
            )
        }
    }
}

@Composable
private fun CreditRow(credit: Credit, onOpen: (Credit) -> Unit) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val shape = StampTheme.shapes.tile
    val hasText = credit.file != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = s.touchTarget)
            .tintedShadow(Elevation.Low, shape, c.shadowWarm)
            .background(c.paperWhite, shape)
            .then(if (hasText) Modifier.clickable(role = Role.Button) { onOpen(credit) } else Modifier)
            .padding(horizontal = s.m, vertical = s.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(credit.name, style = StampTheme.type.body, color = c.textHigh)
            Text(credit.by, style = StampTheme.type.label, color = c.textMedium)
        }
        Text(stringResource(credit.licence.label), style = StampTheme.type.label, color = if (hasText) c.airmailRed else c.textMedium)
    }
}

/** The licence's full text, read from assets off the main thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenceSheet(credit: Credit, onDismiss: () -> Unit) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val context = LocalContext.current
    val text by produceState<String?>(null, credit.file) {
        value = credit.file?.let { name ->
            withContext(Dispatchers.IO) {
                runCatching { reflow(context.assets.open("licenses/$name").bufferedReader().use { it.readText() }) }.getOrNull()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.paper,
        contentColor = c.textHigh,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = s.screen)
                .padding(bottom = s.l),
            verticalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            Text(credit.name, style = StampTheme.type.cardTitle, color = c.textHigh)
            Text(stringResource(credit.licence.label), style = StampTheme.type.overline, color = c.textMedium)
            Text(text.orEmpty(), style = StampTheme.type.label, color = c.textHigh, modifier = Modifier.padding(top = s.xs))
        }
    }
}

/**
 * Licence files are hard-wrapped at ~70 columns, which breaks mid-sentence on a phone: joins the
 * lines of each paragraph, but keeps rule lines, numbered clauses and indented lines on their own.
 */
internal fun reflow(text: String): String =
    text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).joinToString("\n\n") { paragraph ->
        val lines = paragraph.trimEnd().lines()
        buildString {
            lines.forEachIndexed { i, line ->
                val prev = lines.getOrNull(i - 1)
                val own = prev == null || line.isBlank() || line.trimStart().startsWith("-") ||
                    prev.trimStart().startsWith("-") || indent(line) != indent(prev) || OwnLine.containsMatchIn(line) || Heading.matches(prev.trim())
                if (i > 0) append(if (own) "\n" else " ")
                append(line.trim())
            }
        }
    }

private fun indent(line: String) = line.length - line.trimStart().length

/** "1) …", "(a) …", "a. …", "TERMS AND CONDITIONS": starts its own line. */
private val OwnLine = Regex("^\\s*(\\(?[0-9a-z]{1,2}[).]\\s|[A-Z][A-Z ,]{6,}$)")
private val Heading = Regex("[A-Z][A-Z ,]{6,}")

private const val NoteSeed = 61L

@PaperPreview
@Composable
private fun CreditsPreview() {
    StampAppTheme { CreditsContent(onBack = {}, onOpen = {}) }
}
