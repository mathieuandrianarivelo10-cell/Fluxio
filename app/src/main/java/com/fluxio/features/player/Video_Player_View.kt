package com.fluxio.features.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import com.fluxio.shared.theme.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fluxio.features.iptv.StreamObfuscator
import com.fluxio.shared.components.shimmerEffect

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerView(
    url: String,
    playbackSpeed: Float,
    isMuted: Boolean,
    playerVolume: Float = 1.0f,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    onPlayerError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    val cleanUrl = remember(url) {
        StreamObfuscator.deobfuscate(url).trim()
    }

    if (cleanUrl.isEmpty()) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aucune chaîne en cours de lecture",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    var retryCount by remember { mutableStateOf(0) }
    var hasError by remember(cleanUrl, retryCount) { mutableStateOf(false) }
    var isLoading by remember(cleanUrl, retryCount) { mutableStateOf(true) }

    var manualQualityMode by remember { mutableStateOf("Standard (480p)") }
    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        while (true) {
            manualQualityMode = sharedPrefs.getString("manual_quality_mode", "Standard (480p)") ?: "Standard (480p)"
            delay(1000)
        }
    }

    LaunchedEffect(playWhenReady, isLoading, hasError) {
        if (playWhenReady && !isLoading && !hasError) {
            val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
            while (true) {
                delay(2000)
                val qualityMode = sharedPrefs.getString("manual_quality_mode", "Standard (480p)") ?: "Standard (480p)"
                val codecPref = sharedPrefs.getString("codec_pref", "H.265 (HEVC)") ?: "H.265 (HEVC)"
                val codecMultiplier = if (codecPref == "H.265 (HEVC)" || codecPref == "AV1") 0.6f else 1.0f

                val mbPerSecond = when (qualityMode) {
                    "Ultra Éco (144p)" -> 0.04f * codecMultiplier
                    "Éco Extrême (240p)" -> 0.08f * codecMultiplier
                    "Éco (360p)" -> 0.15f * codecMultiplier
                    "Standard (480p)" -> 0.35f * codecMultiplier
                    "HD (720p/1080p)" -> 1.20f * codecMultiplier
                    "Audio uniquement" -> 0.03f
                    else -> 0.40f * codecMultiplier
                }

                val addedMB = mbPerSecond * 2f
                val crossedLimit = com.fluxio.core.network.NetworkManager.addDataUsage(context, addedMB)
                if (crossedLimit) {
                    android.widget.Toast.makeText(
                        context,
                        "Alerte : Vous avez consommé 80% de votre forfait ! Passez en mode Éco.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val exoPlayer = remember(cleanUrl, retryCount, manualQualityMode) {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )
        }

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        val isHls = cleanUrl.contains(".m3u8", ignoreCase = true) ||
                cleanUrl.contains("/m3u8", ignoreCase = true) ||
                cleanUrl.contains("type=m3u8", ignoreCase = true) ||
                cleanUrl.contains("/hls", ignoreCase = true) ||
                cleanUrl.contains(".ts", ignoreCase = true)

        val mediaItem = if (isHls) {
            MediaItem.Builder()
                .setUri(cleanUrl)
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                .build()
        } else {
            MediaItem.fromUri(cleanUrl)
        }

        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        val zappingBufferSec = sharedPrefs.getInt("zapping_buffer_seconds", 3)

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
        val parametersBuilder = trackSelector.buildUponParameters()
        when (manualQualityMode) {
            "Ultra Éco (144p)" -> {
                parametersBuilder.setMaxVideoSize(256, 144)
            }
            "Éco Extrême (240p)" -> {
                parametersBuilder.setMaxVideoSize(426, 240)
            }
            "Éco (360p)" -> {
                parametersBuilder.setMaxVideoSize(640, 360)
            }
            "Standard (480p)" -> {
                parametersBuilder.setMaxVideoSize(854, 480)
            }
            "HD (720p/1080p)" -> {
                parametersBuilder.setMaxVideoSize(1920, 1080)
            }
            "Audio uniquement" -> {
                parametersBuilder.setMaxVideoSize(1, 1)
            }
        }
        trackSelector.parameters = parametersBuilder.build()

        val loadControlBuilder = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        if (zappingBufferSec == 3) {
            loadControlBuilder.setBufferDurationsMs(
                3000,
                5000,
                1000,
                1500
            )
        } else {
            loadControlBuilder.setBufferDurationsMs(
                10000,
                15000,
                2500,
                5000
            )
        }
        val loadControl = loadControlBuilder.build()

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItem(mediaItem)
                prepare()
                this.playWhenReady = playWhenReady
                this.volume = if (isMuted) 0f else playerVolume
                this.setPlaybackSpeed(playbackSpeed)
            }
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(isMuted, playerVolume, exoPlayer) {
        exoPlayer.volume = if (isMuted) 0f else playerVolume
        if (!isMuted && playerVolume > 0f) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager != null) {
                    val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val isStreamMuted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)
                    } else {
                        false
                    }
                    if (currentVolume == 0 || isStreamMuted || currentVolume < maxVol / 3) {
                        val targetVol = if (maxVol > 0) (maxVol * 0.7f).toInt() else 10
                        audioManager.setStreamVolume(
                            android.media.AudioManager.STREAM_MUSIC,
                            targetVol,
                            android.media.AudioManager.FLAG_SHOW_UI
                        )
                    }
                }
            } catch (e: Exception) {
                // S'assurer qu'aucune restriction n'interrompt le flux principal
            }
        }
    }

    LaunchedEffect(playWhenReady, exoPlayer) {
        if (playWhenReady) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        com.fluxio.features.player.PipHelper.isVideoPlaying = playWhenReady
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        com.fluxio.features.player.PipHelper.isVideoPlaying = playWhenReady
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < 3) {
                    isLoading = true
                    scope.launch {
                        delay(2000)
                        retryCount++
                    }
                } else {
                    hasError = true
                    isLoading = false
                    onPlayerError?.invoke()
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    hasError = false
                    isLoading = false
                    retryCount = 0
                } else if (state == Player.STATE_BUFFERING) {
                    isLoading = true
                } else if (state == Player.STATE_ENDED) {
                    isLoading = false
                }
            }
        }
        exoPlayer.addListener(listener)

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    if (!com.fluxio.features.player.PipHelper.isInPipMode) {
                        exoPlayer.pause()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (playWhenReady) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            exoPlayer.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            com.fluxio.features.player.PipHelper.isVideoPlaying = false
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        if (manualQualityMode != "Audio uniquement") {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Audio uniquement",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Mode Audio Uniquement",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Flux vidéo désactivé • Économie de 80% des données mobiles",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (isLoading && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    if (retryCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connexion perdue. Tentative de reconnexion $retryCount/3...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Erreur",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Erreur de connexion ou problème de chaîne",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
