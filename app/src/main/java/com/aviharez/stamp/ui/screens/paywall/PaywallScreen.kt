package com.aviharez.stamp.ui.screens.paywall

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.data.billing.OffersState
import com.aviharez.stamp.data.billing.ProPlan
import com.aviharez.stamp.data.billing.ProRules
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.Stamp
import com.aviharez.stamp.ui.components.dashedOutline
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** What Pro gives. [available] = shipped today; the rest show as dashed "soon" slots. */
private enum class Perk(@DrawableRes val icon: Int, @StringRes val label: Int, val available: Boolean) {
    Albums(R.drawable.ic_perk_albums, R.string.paywall_perk_albums, true),
    NoMark(R.drawable.ic_perk_no_mark, R.string.paywall_perk_no_mark, true),
    Edges(R.drawable.ic_perk_edges, R.string.paywall_perk_edges, false),
    Scrapbook(R.drawable.ic_gallery, R.string.paywall_perk_scrapbook, false),
    Widget(R.drawable.ic_perk_widget, R.string.paywall_perk_widget, false),
}

private fun PaywallReason.perk(): Perk? = when (this) {
    PaywallReason.Albums -> Perk.Albums
    PaywallReason.Watermark -> Perk.NoMark
    PaywallReason.Profile -> null
}

/** Pro paywall ("stamp sheet"): perks as stamps on kraft, plans picked like the nav bar. */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(viewModel) { viewModel.close.collect { onClose() } }
    PaywallContent(
        state = state,
        onClose = onClose,
        onRestore = viewModel::restore,
        onSelect = viewModel::select,
        onBuy = { activity?.let(viewModel::buy) },
        onRetry = viewModel::loadOffers,
    )
}

@Composable
fun PaywallContent(
    state: PaywallUiState,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onSelect: (ProPlan) -> Unit,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .background(c.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = s.screen, vertical = s.m),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(R.drawable.ic_close, stringResource(R.string.paywall_close), onClose)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .heightIn(min = s.touchTarget)
                    .clickable(enabled = !state.busy, role = Role.Button, onClick = onRestore)
                    .padding(horizontal = s.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.paywall_restore), style = StampTheme.type.button, color = c.textMedium)
            }
        }

        Text(
            when (state.reason) {
                PaywallReason.Albums -> pluralStringResource(R.plurals.paywall_overline_albums, ProRules.FreeAlbumLimit, ProRules.FreeAlbumLimit)
                PaywallReason.Watermark -> stringResource(R.string.paywall_overline_watermark)
                PaywallReason.Profile -> stringResource(R.string.paywall_overline)
            }.uppercase(),
            style = StampTheme.type.overline,
            color = if (state.reason == PaywallReason.Profile) c.textMedium else c.airmailRed,
            modifier = Modifier.padding(top = s.xs),
        )
        Text(stringResource(R.string.paywall_title), style = StampTheme.type.screenTitle, color = c.textHigh)

        PerkSheet(highlight = state.reason.perk(), modifier = Modifier.padding(top = s.m))
        HandNote(
            stringResource(R.string.paywall_soon_note),
            style = StampTheme.type.noteSmall,
            strength = InkStrength.Soft,
            rotation = -1f,
            modifier = Modifier.padding(top = s.xs, start = s.xs),
        )

        val offers = (state.offers as? OffersState.Ready)?.offers.orEmpty()
        Row(
            Modifier.fillMaxWidth().padding(top = s.l),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlanStamp(
                name = stringResource(R.string.paywall_plan_lifetime),
                price = offers[ProPlan.Lifetime]?.formattedPrice,
                per = stringResource(R.string.paywall_per_lifetime),
                selected = state.selected == ProPlan.Lifetime,
                tag = stringResource(R.string.paywall_best_value),
                onClick = { onSelect(ProPlan.Lifetime) },
            )
            PlanStamp(
                name = stringResource(R.string.paywall_plan_yearly),
                price = offers[ProPlan.Yearly]?.formattedPrice,
                per = stringResource(R.string.paywall_per_yearly),
                selected = state.selected == ProPlan.Yearly,
                onClick = { onSelect(ProPlan.Yearly) },
            )
        }

        StatusLine(state, onRetry, Modifier.padding(top = s.l))

        PillButton(
            stringResource(if (state.selected == ProPlan.Lifetime) R.string.paywall_buy_lifetime else R.string.paywall_buy_yearly),
            onClick = onBuy,
            enabled = state.selectedOffer != null && !state.busy,
            trailingIcon = R.drawable.ic_arrow_right,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = s.s)
                .alpha(if (state.selectedOffer != null && !state.busy) 1f else DisabledAlpha),
        )
        if (state.selected == ProPlan.Yearly) {
            Text(
                stringResource(R.string.paywall_yearly_terms),
                style = StampTheme.type.label,
                color = c.textLow,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = s.xs),
            )
        }
        HandNote(
            stringResource(R.string.paywall_free_note),
            style = StampTheme.type.noteSmall,
            strength = InkStrength.Soft,
            rotation = -1.5f,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = s.m),
        )
    }
}

/** Kraft sheet: the shipped perks as stamps (the reason's one lit up red), the rest as slots. */
@Composable
private fun PerkSheet(highlight: Perk?, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    GrainedSurface(
        StampTheme.colors.kraft,
        modifier.fillMaxWidth().rotate(SheetTilt),
        shape = StampTheme.shapes.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = s.m), verticalArrangement = Arrangement.spacedBy(s.s)) {
            Perk.entries.chunked(PerksPerRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { perk -> PerkCell(perk, highlighted = perk == highlight) }
                }
            }
        }
    }
}

@Composable
private fun PerkCell(perk: Perk, highlighted: Boolean) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val label = stringResource(perk.label)
    val description = if (perk.available) label else stringResource(R.string.paywall_perk_soon_cd, label)
    Column(
        Modifier.width(PerkCellWidth).clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(PerkStampSize), contentAlignment = Alignment.Center) {
            if (perk.available) {
                Stamp(
                    StampShape.Square,
                    Modifier
                        .size(PerkStampSize)
                        .graphicsLayer { val sc = if (highlighted) LiftScale else 1f; scaleX = sc; scaleY = sc },
                    perforation = PerforationStyle.Fine,
                    tilt = if (highlighted) ActiveTilt else PerkTilts[perk.ordinal],
                    elevation = if (highlighted) Elevation.Card else Elevation.Low,
                ) {
                    Box(
                        Modifier.fillMaxSize().background(if (highlighted) c.airmailRed else c.paperWhite),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(perk.icon),
                            null,
                            tint = if (highlighted) c.paperWhite else c.ink,
                            modifier = Modifier.size(s.l),
                        )
                    }
                }
            } else {
                Box(
                    Modifier.size(PerkStampSize).dashedOutline(c.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(perk.icon), null, tint = c.textLow, modifier = Modifier.size(s.l).alpha(SoonIconAlpha))
                    HandNote(
                        stringResource(R.string.paywall_soon),
                        style = StampTheme.type.noteSmall,
                        strength = InkStrength.Soft,
                        rotation = -8f,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = s.xs, y = s.xs),
                    )
                }
            }
        }
        Text(
            label,
            style = StampTheme.type.label,
            color = if (perk.available) c.textHigh else c.textLow,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = s.xs),
        )
    }
}

/** A plan as a big stamp; the picked one turns red, tilts and lifts (like the nav bar). */
@Composable
private fun PlanStamp(
    name: String,
    price: String?,
    per: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String? = null,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val content = if (selected) c.textOnDarkHigh else c.textHigh
    val priceText = price ?: stringResource(R.string.paywall_price_missing)
    val selectedText = stringResource(R.string.paywall_selected_cd)
    val description = stringResource(R.string.paywall_plan_cd, name, priceText, per)
    Box(
        Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description + if (selected) ", $selectedText" else "" },
    ) {
        Stamp(
            StampShape.Portrait,
            Modifier
                .padding(top = s.s)
                .width(PlanStampWidth)
                .graphicsLayer { val sc = if (selected) LiftScale else 1f; scaleX = sc; scaleY = sc },
            tilt = if (selected) ActiveTilt else RestTilt,
            elevation = if (selected) Elevation.Raised else Elevation.Low,
        ) {
            Column(
                Modifier.fillMaxSize().background(if (selected) c.airmailRed else c.paperWhite).padding(s.s),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name.uppercase(), style = StampTheme.type.overline, color = content)
                Column {
                    Text(priceText, style = StampTheme.type.cardTitle, color = content, maxLines = 1)
                    HandNote(per, style = StampTheme.type.noteSmall, color = content)
                }
            }
        }
        if (tag != null) {
            Text(
                tag.uppercase(),
                style = StampTheme.type.overline,
                color = c.textOnDarkHigh,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .rotate(ActiveTilt / 2f)
                    .background(c.ink, StampTheme.shapes.pill)
                    .padding(horizontal = s.xs, vertical = s.xxs / 2),
            )
        }
    }
}

@Composable
private fun StatusLine(state: PaywallUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val text = when {
        state.message == PaywallMessage.Pending -> R.string.paywall_pending
        state.message == PaywallMessage.Failed -> R.string.paywall_failed
        state.message == PaywallMessage.NothingToRestore -> R.string.paywall_nothing_to_restore
        state.message == PaywallMessage.RestoreFailed -> R.string.paywall_restore_failed
        state.offers is OffersState.Loading -> R.string.paywall_loading
        state.offers is OffersState.Unavailable -> R.string.paywall_unavailable
        else -> null
    } ?: return
    val alert = state.message == PaywallMessage.Failed || state.offers is OffersState.Unavailable
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(text),
            style = StampTheme.type.label,
            color = if (alert) c.airmailRed else c.textMedium,
            textAlign = TextAlign.Center,
        )
        if (state.offers is OffersState.Unavailable && state.message == null) {
            PillButton(stringResource(R.string.paywall_retry), onRetry, style = PillStyle.Outline, modifier = Modifier.padding(top = StampTheme.spacing.xs))
        }
    }
}

private const val PerksPerRow = 3
private val PerkStampSize = 58.dp
private val PerkCellWidth = 96.dp
private val PlanStampWidth = 128.dp
private val PerkTilts = listOf(-3f, 2f, -1.5f, 2.5f, -2f)
private const val SheetTilt = -0.6f
private const val ActiveTilt = -6f
private const val RestTilt = 1.5f
private const val LiftScale = 1.08f
private const val SoonIconAlpha = 0.5f
private const val DisabledAlpha = 0.48f

@PaperPreview
@Composable
private fun PaywallPreview() {
    StampAppTheme {
        PaywallContent(
            state = PaywallUiState(reason = PaywallReason.Albums, offers = OffersState.Unavailable),
            onClose = {},
            onRestore = {},
            onSelect = {},
            onBuy = {},
            onRetry = {},
        )
    }
}
