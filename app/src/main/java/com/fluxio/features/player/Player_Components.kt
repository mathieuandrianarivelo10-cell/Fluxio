package com.fluxio.features.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fluxio.shared.models.LiveChannel
import com.fluxio.shared.theme.*
import com.fluxio.features.iptv.StreamObfuscator
import com.fluxio.shared.components.CustomPauseIcon
import com.fluxio.shared.components.CustomFullscreenIcon
import com.fluxio.shared.components.CustomReduceIcon
import com.fluxio.shared.components.CustomMaximizeIcon
import com.fluxio.shared.components.CustomVolumeIcon
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerWithSubtitles(
    url: String,
    channel: LiveChannel,
    videoRotationMode: String,
    onVideoRotationModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    onPlayerError: (() -> Unit)? = null,
    isPlayerFloating: Boolean = false,
    onMaximize: (() -> Unit)? = null,
    onCloseFloating: (() -> Unit)? = null,
    floatingSize: String = "medium",
    onFloatingSizeChange: ((String) -> Unit)? = null,
    showPlayerSettings: Boolean = false,
    onShowPlayerSettingsChange: ((Boolean) -> Unit)? = null,
    showFloatingControls: Boolean = false,
    onShowFloatingControlsChange: ((Boolean) -> Unit)? = null,
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: ((Float) -> Unit)? = null,
    isMuted: Boolean = false,
    onIsMutedChange: ((Boolean) -> Unit)? = null,
    playerVolume: Float = 1.0f,
    onPlayerVolumeChange: ((Float) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onNotificationClick: (() -> Unit)? = null,
    showDetailsOfPlaying: Boolean = false
) {
    var isPlaying by remember { mutableStateOf(playWhenReady) }
    var showControls by remember { mutableStateOf(false) }
    var localShowPlayerSettings by remember { mutableStateOf(false) }
    val showPlayerSettingsValue = onShowPlayerSettingsChange?.let { showPlayerSettings } ?: localShowPlayerSettings
    val setShowPlayerSettingsValue: (Boolean) -> Unit = { value ->
        if (onShowPlayerSettingsChange != null) {
            onShowPlayerSettingsChange(value)
        } else {
            localShowPlayerSettings = value
        }
    }
    var localShowFloatingControls by remember { mutableStateOf(false) }
    val showFloatingControlsValue = onShowFloatingControlsChange?.let { showFloatingControls } ?: localShowFloatingControls
    val setShowFloatingControlsValue: (Boolean) -> Unit = { value ->
        if (onShowFloatingControlsChange != null) {
            onShowFloatingControlsChange(value)
        } else {
            localShowFloatingControls = value
        }
    }

    val context = LocalContext.current
    val playPrefs = remember { context.getSharedPreferences("horizon_iptv", android.content.Context.MODE_PRIVATE) }

    val userEmail = remember(playPrefs) { playPrefs.getString("user_email", "utilisateur@fluxio.com") ?: "utilisateur@fluxio.com" }

    DisposableEffect(channel.id) {
        val deviceId = com.fluxio.core.security.SecurityUtils.getDeviceID(context)
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "device_$deviceId"
        val startMs = System.currentTimeMillis()

        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val sessionData = hashMapOf<String, Any>(
                "deviceId" to deviceId,
                "channelId" to channel.id,
                "channelName" to channel.name,
                "email" to userEmail,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("active_sessions").document(deviceId).set(sessionData)
            firestore.collection("active_viewers").document(channel.id).collection("viewers").document(uid).set(mapOf("active" to true))

            // Mirror active viewers to Realtime Database /active_viewers/{channelId}/{userId}
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("active_viewers")
                    .child(channel.id)
                    .child(uid)
                    .setValue(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Increment totalViews in view_statistics Firestore
            val statsRef = firestore.collection("view_statistics").document(channel.id)
            val statsInit = mapOf(
                "channelId" to channel.id,
                "totalViews" to com.google.firebase.firestore.FieldValue.increment(1)
            )
            statsRef.set(statsInit, com.google.firebase.firestore.SetOptions.merge())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                firestore.collection("active_sessions").document(deviceId).delete()
                firestore.collection("active_viewers").document(channel.id).collection("viewers").document(uid).delete()

                // Remove from Realtime Database as well
                try {
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("active_viewers")
                        .child(channel.id)
                        .child(uid)
                        .removeValue()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val durationSec = (System.currentTimeMillis() - startMs) / 1000
                if (durationSec > 0) {
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val statsRef = firestore.collection("view_statistics").document(channel.id)
                    statsRef.set(
                        mapOf("totalDurationSeconds" to com.google.firebase.firestore.FieldValue.increment(durationSec)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
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

    LaunchedEffect(Unit) {
        while (true) {
            manualQualityMode = playPrefs.getString("manual_quality_mode", "Standard (480p)") ?: "Standard (480p)"
            codecPref = playPrefs.getString("codec_pref", "H.265 (HEVC)") ?: "H.265 (HEVC)"
            zappingBufferSec = playPrefs.getInt("zapping_buffer_seconds", 3)
            dataConsumedToday = playPrefs.getFloat("data_consumed_today", 450.0f)
            dataLimit = playPrefs.getFloat("data_limit", 1000.0f)
            delay(1000)
        }
    }

    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                break
            }
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }

    LaunchedEffect(showControls, showPlayerSettingsValue) {
        if (showControls) {
            if (!showPlayerSettingsValue) {
                delay(4000)
                showControls = false
            }
        } else {
            if (!showPlayerSettingsValue) {
                setShowPlayerSettingsValue(false)
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
                if (isPlayerFloating) {
                    setShowFloatingControlsValue(!showFloatingControlsValue)
                } else {
                    showControls = !showControls
                }
            }
    ) {
        VideoPlayerView(
            url = url,
            playbackSpeed = playbackSpeed,
            isMuted = isMuted,
            playerVolume = playerVolume,
            playWhenReady = isPlaying,
            onPlayerError = onPlayerError,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls && !isPlayerFloating,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (onBackClick != null || onNotificationClick != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBackClick != null) {
                            IconButton(
                                onClick = { onBackClick() },
                                modifier = Modifier
                                    .size(32.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Retour",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (showDetailsOfPlaying) channel.name else "En Lecture : ${channel.name}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (onNotificationClick != null) {
                            IconButton(
                                onClick = { onNotificationClick() },
                                modifier = Modifier
                                    .size(32.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "En Lecture : ${channel.name}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isPlaying) {
                            CustomPauseIcon(tint = Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                        if (isLandscape) {
                            IconButton(
                                onClick = {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                CustomReduceIcon(tint = Color.White)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                CustomFullscreenIcon(tint = Color.White)
                            }
                        }

                        IconButton(
                            onClick = { onIsMutedChange?.invoke(!isMuted) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            CustomVolumeIcon(
                                isMuted = isMuted,
                                tint = if (isMuted) Color(0xFFEF4444) else Color.White
                            )
                        }

                        IconButton(
                            onClick = { setShowPlayerSettingsValue(!showPlayerSettingsValue) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Parametres",
                                tint = if (showPlayerSettingsValue) Color.White else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (showPlayerSettingsValue) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                setShowPlayerSettingsValue(false)
                            }
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(280.dp)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = true,
                                    onClick = {} // Intercept clicks so card content click doesn't close it
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                // En-tête fixe en haut
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Paramètres de lecture",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    IconButton(
                                        onClick = { setShowPlayerSettingsValue(false) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fermer",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Contenu défilant sous l'en-tête fixe
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Vitesse : x",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
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
                                            onOptionSelected = { onPlaybackSpeedChange?.invoke(it) }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Sourdine (Muet)",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Switch(
                                            checked = isMuted,
                                            onCheckedChange = { onIsMutedChange?.invoke(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                                            )
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Orientation",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
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

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Taille lecteur flottant",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        ProgressiveLineSelector(
                                            options = listOf(
                                                "small" to "Petit",
                                                "medium" to "Moyen",
                                                "large" to "Grand"
                                            ),
                                            selectedOption = floatingSize,
                                            onOptionSelected = { onFloatingSizeChange?.invoke(it) }
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Qualité Vidéo (ABR)",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
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

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Codec Vidéo",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        ProgressiveLineSelector(
                                            options = listOf(
                                                "H.265 (HEVC)" to "H.265\n(HEVC)",
                                                "AV1" to "AV1\n(Nouveau)",
                                                "H.264 Fallback" to "H.264\n(Compatibilité)"
                                            ),
                                            selectedOption = codecPref,
                                            onOptionSelected = { codecKey ->
                                                codecPref = codecKey
                                                playPrefs.edit().putString("codec_pref", codecKey).apply()
                                            }
                                        )
                                    }

                                    if (showHdWarningDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showHdWarningDialog = false },
                                            title = { Text("Attention", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                                            text = { Text("La HD consomme beaucoup sur votre réseau mobile.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp) },
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
                                                        Text("Activer", color = Color.White, fontWeight = FontWeight.Bold)
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
                        }
                    }
                }
            }
        }

        if (isPlayerFloating && showFloatingControlsValue) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { setShowFloatingControlsValue(false) }
            ) {
                IconButton(
                    onClick = {
                        setShowFloatingControlsValue(false)
                        onMaximize?.invoke()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                ) {
                    CustomMaximizeIcon(tint = Color.White)
                }
 
                IconButton(
                    onClick = {
                        setShowFloatingControlsValue(false)
                        onCloseFloating?.invoke()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
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
    inactiveColor: Color = Color.White.copy(alpha = 0.15f),
    labelColor: Color = Color.White
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


