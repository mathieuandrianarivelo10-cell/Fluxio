package com.fluxio.features.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.models.LiveChannel
import com.fluxio.features.admin.AdminDashboardTab

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsTab(
    isDarkTheme: Boolean,
    onIsDarkThemeChange: (Boolean) -> Unit,
    immersiveModeEnabled: Boolean,
    onImmersiveModeToggle: (Boolean) -> Unit,
    biometricLockEnabled: Boolean,
    onBiometricLockToggle: (Boolean) -> Unit,
    filterOfflineEnabled: Boolean,
    onFilterOfflineToggle: (Boolean) -> Unit,
    offlineChannelsCount: Int,
    onResetOfflineChannels: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit = {},
    userEmail: String,
    userName: String,
    isAdmin: Boolean,
    allChannels: List<LiveChannel>,
    adminFeaturedChannelIds: Set<String>,
    adminPublishedChannelIds: Set<String>,
    onPublishCatalogs: (Set<String>, Set<String>) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    isMuted: Boolean,
    onIsMutedChange: (Boolean) -> Unit,
    playerVolume: Float,
    onPlayerVolumeChange: (Float) -> Unit,
    videoRotationMode: String,
    onVideoRotationModeChange: (String) -> Unit,
    floatingSize: String,
    onFloatingSizeChange: (String) -> Unit,
    sharedPrefs: SharedPreferences,
    channelSortOrder: String,
    onChannelSortOrderChange: (String) -> Unit,
    triggerChannelTransform: Int,
    onTriggerChannelTransformChange: (Int) -> Unit,
    onRefreshChannels: () -> Unit,
    initialSubScreen: String? = null,
    onSubScreenChange: (String?) -> Unit = {}
) {
    val channels = allChannels
    val context = LocalContext.current
    var activeSubScreen by remember(initialSubScreen) {
        val mapped = if (initialSubScreen != null && initialSubScreen !in listOf(
            "Lecture et Audio", "Affichage", "Chaînes", "Compte & Abonnement", "Notification", "À Propos", "Administration"
        )) {
            "Administration"
        } else {
            initialSubScreen
        }
        mutableStateOf<String?>(mapped)
    }
    var isPaymentModeActive by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = activeSubScreen != null) {
        activeSubScreen = null
    }

    LaunchedEffect(activeSubScreen) {
        onSubScreenChange(activeSubScreen)
        if (activeSubScreen != "Compte & Abonnement") {
            isPaymentModeActive = false
        }
    }

    val playPrefs = remember { context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE) }
    var manualQualityMode by remember {
        mutableStateOf(playPrefs.getString("manual_quality_mode", "Standard (480p)") ?: "Standard (480p)")
    }
    var codecPref by remember {
        mutableStateOf(playPrefs.getString("codec_pref", "H.265 (HEVC)") ?: "H.265 (HEVC)")
    }
    var zappingBufferSec by remember {
        mutableStateOf(playPrefs.getInt("zapping_buffer_seconds", 3))
    }
    var dataConsumedToday by remember {
        mutableStateOf(playPrefs.getFloat("data_consumed_today", 450.0f))
    }
    var dataLimit by remember {
        mutableStateOf(playPrefs.getFloat("data_limit", 1000.0f))
    }
    var showHdWarningDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            dataConsumedToday = playPrefs.getFloat("data_consumed_today", 450.0f)
            dataLimit = playPrefs.getFloat("data_limit", 1000.0f)
            kotlinx.coroutines.delay(1000)
        }
    }

    AnimatedContent(
        targetState = activeSubScreen,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                ) with slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                )
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                ) with slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                )
            }
        },
        label = "SettingsNavigation"
    ) { screen ->
        if (screen == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Paramétrage",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                val menus = remember(isAdmin) {
                    val base = mutableListOf(
                        "Lecture et Audio",
                        "Affichage",
                        "Chaînes",
                        "Compte & Abonnement",
                        "Notification",
                        "À Propos"
                    )
                    if (isAdmin) {
                        base.add(0, "Administration")
                    }
                    base
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    menus.forEach { menuTitle ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .background(Color.Transparent)
                                .clickable { activeSubScreen = menuTitle }
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = menuTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onLogoutClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SE DÉCONNECTER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (screen == "Administration") 0.dp else 8.dp,
                        end = if (screen == "Administration") 0.dp else 8.dp,
                        top = if (screen == "Administration" || (screen == "Compte & Abonnement" && isPaymentModeActive)) 0.dp else 16.dp,
                        bottom = if (screen == "Administration") 0.dp else 16.dp
                    )
            ) {
                if (screen != "Administration" && !(screen == "Compte & Abonnement" && isPaymentModeActive)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = screen,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { activeSubScreen = null },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (screen) {
                        "Lecture et Audio" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Vitesse de lecture",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ProgressiveLineSelector(
                                        options = listOf(
                                            0.5f to "0.5x",
                                            1.0f to "1.0x",
                                            1.25f to "1.25x",
                                            1.5f to "1.5x",
                                            2.0f to "2.0x"
                                        ),
                                        selectedOption = playbackSpeed,
                                        onOptionSelected = { onPlaybackSpeedChange(it) }
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sourdine (Muet)",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Couper complètement le son de la lecture",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Switch(
                                        checked = isMuted,
                                        onCheckedChange = { onIsMutedChange(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        )
                                    )
                                }



                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Orientation de l'écran",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ProgressiveLineSelector(
                                        options = listOf(
                                            "auto" to "Auto",
                                            "portrait" to "Portrait",
                                            "landscape" to "Paysage"
                                        ),
                                        selectedOption = videoRotationMode,
                                        onOptionSelected = { onVideoRotationModeChange(it) }
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Taille du lecteur flottant",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ProgressiveLineSelector(
                                        options = listOf(
                                            "small" to "Petit",
                                            "medium" to "Moyen",
                                            "large" to "Grand"
                                        ),
                                        selectedOption = floatingSize,
                                        onOptionSelected = { onFloatingSizeChange(it) }
                                    )
                                }

                                // --- OPTIMISATION DU STREAMING (ABR) ---
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Qualité vidéo adaptative",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ajustement automatique de la qualité (ABR) en fonction du débit.",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )

                                    ProgressiveLineSelector(
                                        options = listOf(
                                            "Ultra Éco (144p)" to "144p",
                                            "Éco Extrême (240p)" to "240p",
                                            "Éco (360p)" to "360p",
                                            "Standard (480p)" to "480p",
                                            "HD (720p/1080p)" to "720p",
                                            "Audio uniquement" to "Audio"
                                        ),
                                        selectedOption = manualQualityMode,
                                        onOptionSelected = { modeKey ->
                                            if (modeKey == "HD (720p/1080p)") {
                                                showHdWarningDialog = true
                                            } else {
                                                manualQualityMode = modeKey
                                                playPrefs.edit().putString("manual_quality_mode", modeKey).apply()
                                            }
                                        }
                                    )
                                }

                                // --- CODECS NOUVELLE GÉNÉRATION ---
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Codecs vidéo",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Les codecs de nouvelle génération allègent le flux de 30% à 50% avec repli automatique vers H.264 sur les anciens appareils.",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                    ProgressiveLineSelector(
                                        options = listOf(
                                            "H.265 (HEVC)" to "H.265\n(HEVC)",
                                            "AV1" to "AV1\n(Nouveau)",
                                            "H.264 Fallback" to "H.264\n(Compatibilité)"
                                        ),
                                        selectedOption = codecPref,
                                        onOptionSelected = { codec ->
                                            codecPref = codec
                                            playPrefs.edit().putString("codec_pref", codec).apply()
                                        }
                                    )
                                }





                                if (showHdWarningDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showHdWarningDialog = false },
                                        title = { Text("Avertissement de consommation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                                        text = { Text("La qualité HD (720p/1080p) est fortement déconseillée en itinérance ou hors Wi-Fi car elle consomme beaucoup de données mobiles.", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp) },
                                        confirmButton = {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Button(
                                                    onClick = {
                                                        manualQualityMode = "HD (720p/1080p)"
                                                        playPrefs.edit().putString("manual_quality_mode", "HD (720p/1080p)").apply()
                                                        showHdWarningDialog = false
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Activer quand même", color = Color.White, fontWeight = FontWeight.Bold)
                                                }

                                                TextButton(
                                                    onClick = { showHdWarningDialog = false },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Annuler", color = Color.White.copy(alpha = 0.6f))
                                                }
                                            }
                                        },
                                        dismissButton = null,
                                        containerColor = Color.Black
                                    )
                                }
                            }
                        }
                        "Affichage" -> {
                            val displayPrefs = remember { context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE) }
                            var visualDetails by remember { 
                                mutableStateOf(displayPrefs.getBoolean("visual_details", true)) 
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Plein écran immersif",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Masquer la barre de statut et la navigation",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Switch(
                                        checked = immersiveModeEnabled,
                                        onCheckedChange = onImmersiveModeToggle,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        )
                                    )
                                }



                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Détails visuels des fiches",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Afficher les logos et informations",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Switch(
                                        checked = visualDetails,
                                        onCheckedChange = { 
                                            visualDetails = it 
                                            displayPrefs.edit().putBoolean("visual_details", it).apply()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        )
                                    )
                                }
                            }
                        }
                        "Chaînes" -> {
                            SettingsChannelsScreen(
                                channels = channels,
                                channelSortOrder = channelSortOrder,
                                onChannelSortOrderChange = onChannelSortOrderChange,
                                triggerChannelTransform = triggerChannelTransform,
                                onTriggerChannelTransformChange = onTriggerChannelTransformChange,
                                sharedPrefs = sharedPrefs
                            )
                        }
                        "Compte & Abonnement" -> {
                            SettingsAccountScreen(
                                userEmail = userEmail,
                                userName = userName,
                                biometricLockEnabled = biometricLockEnabled,
                                onBiometricLockToggle = onBiometricLockToggle,
                                onDeleteAccountClick = onDeleteAccountClick,
                                onPaymentModeChange = { isPaymentModeActive = it }
                            )
                        }
                        "Notification" -> {
                            SettingsNotificationsScreen(sharedPrefs = sharedPrefs)
                        }
                        "À Propos" -> {
                            SettingsAboutScreen()
                        }
                        "Administration" -> {
                            if (isAdmin) {
                                AdminDashboardTab(
                                    userEmail = userEmail,
                                    allChannels = channels,
                                    adminFeaturedChannelIds = adminFeaturedChannelIds,
                                    adminPublishedChannelIds = adminPublishedChannelIds,
                                    onPublishCatalogs = onPublishCatalogs,
                                    onLogoutClick = onLogoutClick,
                                    onRefreshChannels = onRefreshChannels,
                                    onBack = { activeSubScreen = null },
                                    onSubScreenChange = { subScreen ->
                                        onSubScreenChange(subScreen ?: "Administration")
                                    },
                                    initialSubScreen = initialSubScreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> ProgressiveLineSelector(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFE50914),
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val selectedIndex = options.indexOfFirst { it.first == selectedOption }.coerceAtLeast(0)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .background(inactiveColor, RoundedCornerShape(2.dp))
            )

            val fraction = if (options.size > 1) selectedIndex.toFloat() / (options.size - 1) else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fraction)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .background(activeColor, RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, (value, _) ->
                    val isSelected = index == selectedIndex
                    val isPastOrCurrent = index <= selectedIndex
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable { onOptionSelected(value) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isPastOrCurrent) activeColor else inactiveColor)
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            options.forEachIndexed { index, (_, label) ->
                val isSelected = index == selectedIndex
                Text(
                    text = label,
                    color = if (isSelected) activeColor else labelColor.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}
