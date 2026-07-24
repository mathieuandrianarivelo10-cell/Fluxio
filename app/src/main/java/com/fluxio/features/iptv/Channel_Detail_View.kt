package com.fluxio.features.iptv

import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import com.fluxio.features.admin.addSupportReport
import kotlinx.coroutines.launch
import com.fluxio.shared.models.LiveChannel
import com.fluxio.shared.theme.*
import com.fluxio.shared.components.ChannelListRow

@Composable
fun ChannelDetailView(
    channel: LiveChannel,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onFullscreenToggle: () -> Unit,
    relatedChannels: List<LiveChannel>,
    onChannelSelect: (LiveChannel) -> Unit,
    onBackClick: () -> Unit,
    isLiked: Boolean,
    likesCount: Int,
    onLikeToggle: () -> Unit,
    sharedPrefs: SharedPreferences,
    favoriteSet: Set<String> = emptySet(),
    onToggleFavoriteGlobal: (String) -> Unit = {},
    isPremiumUser: Boolean = true,
    onLockedClick: () -> Unit = {}
) {
    var showReportSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = if (channel.channelNumber != null) "${channel.channelNumber} - ${channel.name}" else channel.name
                    Text(
                        text = displayTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (channel.country.isNotEmpty() && !channel.country.equals("Local", ignoreCase = true)) {
                            Text(
                                text = channel.country,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                val isLightTheme = MaterialTheme.colorScheme.background == Color(0xFFF8F9FA) || MaterialTheme.colorScheme.background == Color(0xFFFFFFFF) || MaterialTheme.colorScheme.background == Color.White
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, if (isLightTheme) Color(0xFF8A8A8A).copy(alpha = 0.3f) else Color(0xFF64748B).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .background(if (isLightTheme) Color(0xFFFFFFFF) else Color.Black)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (likesCount) {
                            0 -> "0 Like"
                            1 -> "1 Like"
                            else -> "$likesCount Likes"
                        },
                        color = if (isLightTheme) Color(0xFF111216) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (channel.description.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = channel.description,
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (likesCount) {
                            0 -> "0 Like"
                            1 -> "1 Like"
                            else -> "$likesCount Likes"
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Direct Continu",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                     Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val customInteractionSource1 = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    interactionSource = customInteractionSource1,
                                    indication = null
                                ) { onLikeToggle() }
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = if (isLiked) MaterialTheme.colorScheme.onBackground else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Aimer",
                                color = if (isLiked) MaterialTheme.colorScheme.onBackground else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color(0xFF64748B).copy(alpha = 0.3f))
                        )

                        val customInteractionSource2 = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    interactionSource = customInteractionSource2,
                                    indication = null
                                ) { onToggleFavorite() }
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                                contentDescription = "Favori",
                                tint = if (isFavorite) Color(0xFFFFD600) else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Favoris",
                                color = if (isFavorite) MaterialTheme.colorScheme.onBackground else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color(0xFF64748B).copy(alpha = 0.3f))
                        )

                        val customInteractionSource3 = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    interactionSource = customInteractionSource3,
                                    indication = null
                                ) { showReportSheet = true }
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Signaler",
                                tint = if (showReportSheet) MaterialTheme.colorScheme.onBackground else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Signaler",
                                color = if (showReportSheet) MaterialTheme.colorScheme.onBackground else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Button(
                    onClick = onFullscreenToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Regarder en Plein Écran",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (relatedChannels.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Chaînes Similaires",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        relatedChannels.forEach { related ->
                            ChannelListRow(
                                channel = related,
                                isFavorite = favoriteSet.contains(related.id),
                                onChannelSelect = { onChannelSelect(related) },
                                onToggleFavorite = { onToggleFavoriteGlobal(related.id) },
                                isPremiumUser = isPremiumUser,
                                onLockedClick = onLockedClick
                            )
                        }
                    }
                }
            }
        }

        if (showReportSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { 
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        showReportSheet = false 
                    }
            )
        }

        AnimatedVisibility(
            visible = showReportSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val context = LocalContext.current
            var selectedIssueType by remember { mutableStateOf("Flux hors-ligne / Écran noir") }
            var reportDescription by remember { mutableStateOf("") }
            val issuesList = listOf(
                "Flux hors-ligne / Écran noir",
                "Décalage audio / vidéo",
                "Qualité médiocre",
                "Autre"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (LocalImmersiveMode.current) Modifier else Modifier.navigationBarsPadding())
                    .imePadding()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(PrimaryBg)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF64748B).copy(alpha = 0.5f))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Signaler la Chaîne",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Canal : ${channel.name}",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { 
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showReportSheet = false 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Type de problème :",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    issuesList.forEach { issue ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedIssueType = issue }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedIssueType == issue),
                                onClick = { selectedIssueType = issue },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.White,
                                    unselectedColor = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = issue,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        label = { Text("Détails du problème / Description") },
                        placeholder = { Text("Veuillez donner plus d'explications sur le problème...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedContainerColor = SecondaryBg,
                            unfocusedContainerColor = SecondaryBg,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (reportDescription.isNotBlank()) {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    addSupportReport(context, channel.name, selectedIssueType, reportDescription.trim())
                                    Toast.makeText(context, "Signalement envoyé avec succès !", Toast.LENGTH_SHORT).show()
                                    showReportSheet = false
                                    reportDescription = ""
                                } else {
                                    Toast.makeText(context, "Veuillez décrire le problème", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Envoyer le signalement", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = { 
                                showReportSheet = false 
                                reportDescription = ""
                            },
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Annuler", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
