package com.aviharez.stamp.ui.screens.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.data.ads.RewardedAds
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AdPrivacyViewModel @Inject constructor(val ads: RewardedAds) : ViewModel()

/** "Ad privacy choices": only where the consent rules ask for it (EEA/UK/some US states). */
@Composable
fun AdPrivacyEntry(modifier: Modifier = Modifier, viewModel: AdPrivacyViewModel = hiltViewModel()) {
    val required by viewModel.ads.privacyOptionsRequired.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    if (required) AdPrivacyChip(onClick = { activity?.let(viewModel.ads::showPrivacyOptions) }, modifier)
}

@Composable
private fun AdPrivacyChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PaperChip(stringResource(R.string.profile_ad_privacy), selected = false, onClick = onClick, modifier = modifier)
}

@PaperPreview
@Composable
private fun AdPrivacyChipPreview() {
    StampAppTheme { AdPrivacyChip(onClick = {}) }
}
