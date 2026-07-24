package com.fluxio.features.admin

import androidx.compose.runtime.Composable
import com.fluxio.shared.models.LiveChannel

@Composable
fun ImporterChaineView(
    allChannels: List<LiveChannel> = emptyList(),
    adminFeaturedChannelIds: Set<String> = emptySet(),
    adminPublishedChannelIds: Set<String> = emptySet(),
    onPublishCatalogs: (Set<String>, Set<String>) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onRefreshChannels: () -> Unit
) {
    GererChainesView(
        allChannels = allChannels,
        adminFeaturedChannelIds = adminFeaturedChannelIds,
        adminPublishedChannelIds = adminPublishedChannelIds,
        onPublishCatalogs = onPublishCatalogs,
        onBack = onBack,
        onRefreshChannels = onRefreshChannels
    )
}
