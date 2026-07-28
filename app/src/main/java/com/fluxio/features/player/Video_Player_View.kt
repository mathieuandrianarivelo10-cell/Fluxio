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
    onPlayerError: (() -> Unit)? = null,
    onTracksDetected: ((Set<Int>) -> Unit)? = null
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
                    "HD (720p)" -> 0.80f * codecMultiplier
                    "Full HD (1080p)" -> 1.50f * codecMultiplier
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
        // Nettoyage préventif du cache sur nouvelle tentative
        if (retryCount > 0) {
            try {
                context.cacheDir.resolve("media3_cache").deleteRecursively()
            } catch (_: Exception) {}
        }

        val sanitizedUrl = cleanUrl.trim().replace(" ", "%20")
        val targetUrl = if (retryCount > 0 && sanitizedUrl.matches(Regex("""https?://[^/]+/[^/]+/[^/]+/\d+(\.ts)?$"""))) {
            if (sanitizedUrl.endsWith(".ts")) {
                sanitizedUrl.dropLast(3) + ".m3u8"
            } else {
                "$sanitizedUrl.m3u8"
            }
        } else {
            sanitizedUrl
        }

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVSmarters/3.1.51 (Linux; Android 12) VLC/3.0.18 LibVLC/3.0.18")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive",
                    "Accept-Encoding" to "identity"
                )
            )

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            setConstantBitrateSeekingEnabled(false)
        }

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        val isHls = targetUrl.contains(".m3u8", ignoreCase = true) ||
                targetUrl.contains("/m3u8", ignoreCase = true) ||
                targetUrl.contains("type=m3u8", ignoreCase = true) ||
                targetUrl.contains("output=hls", ignoreCase = true) ||
                targetUrl.contains("/hls", ignoreCase = true)

        val mediaItem = if (isHls) {
            MediaItem.Builder()
                .setUri(targetUrl)
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.02f)
                        .setMinPlaybackSpeed(0.98f)
                        .setTargetOffsetMs(2000)
                        .build()
                )
                .build()
        } else {
            // Auto-détection du container (TS, MP4, FLV, MKV) par ExoPlayer sniffer
            // Ne pas forcer MimeTypes.VIDEO_MP2T pour éviter les blocages si le format varie
            MediaItem.fromUri(targetUrl)
        }

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
        val parametersBuilder = trackSelector.buildUponParameters()
            .setExceedVideoConstraintsIfNecessary(true)
            .setExceedAudioConstraintsIfNecessary(true)
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setAllowMultipleAdaptiveSelections(true)

        when (manualQualityMode) {
            "Ajustement auto (Plus basse rés.)" -> {
                parametersBuilder.setForceLowestBitrate(true)
            }
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
            "HD (720p)" -> {
                parametersBuilder.setMaxVideoSize(1280, 720)
            }
            "Full HD (1080p)", "HD (720p/1080p)" -> {
                parametersBuilder.setMaxVideoSize(1920, 1080)
            }
            "Audio uniquement" -> {
                parametersBuilder.setMaxVideoSize(1, 1)
            }
            else -> {
                // Mode Auto : Sélection automatique fluide sans brider le débit
            }
        }
        trackSelector.parameters = parametersBuilder.build()

        val loadControlBuilder = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        loadControlBuilder.setBufferDurationsMs(
            5000,  // minBufferMs : 5s de buffer minimal
            50000, // maxBufferMs : 50s
            1500,  // bufferForPlaybackMs : 1.5s avant de démarrer
            3000   // bufferForPlaybackAfterRebufferMs : 3s après rebuffer
        )
        loadControlBuilder.setPrioritizeTimeOverSizeThresholds(false)
        loadControlBuilder.setBackBuffer(10000, true)
        val loadControl = loadControlBuilder.build()

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setAllowedVideoJoiningTimeMs(5000)
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build().apply {
                setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
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
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val heights = mutableSetOf<Int>()
                var lowestGroupIndex = -1
                var lowestTrackIndex = -1
                var minHeight = Int.MAX_VALUE

                for (gIdx in 0 until tracks.groups.size) {
                    val group = tracks.groups[gIdx]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                        for (tIdx in 0 until group.length) {
                            val format = group.getTrackFormat(tIdx)
                            if (format.height > 0) {
                                heights.add(format.height)
                                if (format.height < minHeight) {
                                    minHeight = format.height
                                    lowestGroupIndex = gIdx
                                    lowestTrackIndex = tIdx
                                }
                            }
                        }
                    }
                }
                if (heights.isNotEmpty()) {
                    onTracksDetected?.invoke(heights)
                }
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.height > 0) {
                    onTracksDetected?.invoke(setOf(videoSize.height))
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
                    if (playWhenReady) {
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
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
                        keepScreenOn = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
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
