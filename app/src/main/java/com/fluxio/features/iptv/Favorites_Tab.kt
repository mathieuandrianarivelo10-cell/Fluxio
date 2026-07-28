package com.fluxio.features.iptv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.models.LiveChannel
import com.fluxio.shared.components.ChannelListRow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.Color

@Composable
fun FavoritesTab(
    channels: List<LiveChannel>,
    favoriteSet: Set<String>,
    onChannelSelect: (LiveChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    isPremiumUser: Boolean = true,
    onLockedClick: () -> Unit = {},
    unreadCount: Int = 0,
    onNotificationClick: () -> Unit = {}
) {
    val favoriteChannels = remember(favoriteSet, channels) {
        channels.filter { favoriteSet.contains(it.id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mes Favoris (${favoriteChannels.size})",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (favoriteChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Aucune chaîne en favori", 
                        color = MaterialTheme.colorScheme.onBackground, 
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Ajoutez des chaînes en cliquant sur l'étoile.", 
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), 
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoriteChannels, key = { it.id }) { channel ->
                    ChannelListRow(
                        channel = channel,
                        isFavorite = true,
                        onChannelSelect = { onChannelSelect(channel) },
                        onToggleFavorite = { onToggleFavorite(channel.id) },
                        isPremiumUser = isPremiumUser,
                        onLockedClick = onLockedClick
                    )
                }
            }
        }
    }
}
