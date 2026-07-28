package com.fluxio.features.admin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.models.LiveChannel
import com.google.firebase.firestore.FirebaseFirestore

data class ChannelStat(
    val channel: LiveChannel,
    val share: Float,
    val viewers: String,
    val bandwidth: String,
    val trend: String,
    val isPositiveTrend: Boolean?,
    val quality: String,
    val bitrate: String,
    val server: String
)

@Composable
fun StatistiquesVisionnageView(
    allChannels: List<LiveChannel>,
    onBack: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(0) }
    var animateChart by remember { mutableStateOf(false) }

    val animationProgress by animateFloatAsState(
        targetValue = if (animateChart) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "chartAnimation"
    )

    LaunchedEffect(selectedPeriod) {
        animateChart = false
        animateChart = true
    }

    var totalUsers by remember { mutableStateOf(0) }
    var totalDevices by remember { mutableStateOf(0) }
    var activeDevicesCount by remember { mutableStateOf(0) }
    var isLoadingStats by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoadingStats = true
        var usersCount = 0
        var devicesCount = 0

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    usersCount = task.result.size()
                    totalUsers = usersCount
                }
                firestore.collection("devices").get().addOnCompleteListener { deviceTask ->
                    if (deviceTask.isSuccessful) {
                        val snapshot = deviceTask.result
                        devicesCount = snapshot.size()
                        totalDevices = devicesCount

                        var activeTemp = 0
                        snapshot.documents.forEach { doc ->
                            val status = doc.getString("status") ?: "active"
                            if (status == "active") {
                                activeTemp++
                            }
                        }
                        activeDevicesCount = activeTemp
                    }
                    isLoadingStats = false
                }
            }
        } catch (ex: Exception) {
            isLoadingStats = false
        }
    }

    var activeSessions by remember { mutableStateOf<Map<String, Map<String, Any>>>(emptyMap()) }

    DisposableEffect(Unit) {
        val firestore = FirebaseFirestore.getInstance()
        val listener = firestore.collection("active_sessions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val sessions = mutableMapOf<String, Map<String, Any>>()
                snapshot?.documents?.forEach { doc ->
                    val key = doc.id
                    val data = doc.data
                    if (data != null) {
                        @Suppress("UNCHECKED_CAST")
                        sessions[key] = data as Map<String, Any>
                    }
                }
                activeSessions = sessions
            }
        onDispose {
            listener.remove()
        }
    }

    val activeChannels = remember(allChannels) {
        allChannels.filter { it.id != "horizon_welcome" }
    }

    val activeViewers = remember(activeSessions, selectedPeriod) {
        val baseLive = activeSessions.size
        if (selectedPeriod == 0) {
            baseLive
        } else {
            if (baseLive == 0) {
                0
            } else {
                baseLive
            }
        }
    }

    val maxViewers = remember(activeSessions, selectedPeriod) {
        val baseLive = activeSessions.size
        if (baseLive == 0) {
            0
        } else {
            when (selectedPeriod) {
                0 -> baseLive
                1 -> (baseLive * 1.5).toInt().coerceAtLeast(baseLive)
                else -> (baseLive * 2.2).toInt().coerceAtLeast(baseLive)
            }
        }
    }

    val bandwidthStr = remember(activeViewers, selectedPeriod) {
        if (activeViewers == 0) {
            "0.0 Mbps"
        } else {
            if (selectedPeriod == 0) {
                val mbps = activeViewers * 4.5
                String.format("%.1f Mbps", mbps)
            } else {
                val totalBytes = (activeViewers * 4.5 * 1024 * 1024) * when (selectedPeriod) {
                    1 -> 7.8
                    else -> 32.4
                }
                val tbValue = (totalBytes * 3600 * 24) / (8.0 * 1024 * 1024 * 1024 * 1024)
                if (tbValue >= 1.0) {
                    String.format("%.1f TB", tbValue)
                } else {
                    String.format("%.1f GB", tbValue * 1024.0)
                }
            }
        }
    }

    val statsList = remember(selectedPeriod, activeChannels, activeViewers, activeSessions) {
        if (activeChannels.isEmpty()) {
            emptyList<ChannelStat>()
        } else {
            val liveCounts = activeChannels.associateWith { channel ->
                activeSessions.values.count { it["channelId"] == channel.id }
            }

            val sortedChannels = activeChannels.sortedWith(
                compareByDescending<LiveChannel> { liveCounts[it] ?: 0 }
                    .thenBy { it.name }
            ).take(5)

            val totalLiveViewers = activeSessions.size

            sortedChannels.mapIndexed { index, channel ->
                val channelLiveViewers = liveCounts[channel] ?: 0
                val share = if (totalLiveViewers > 0) {
                    channelLiveViewers.toFloat() / totalLiveViewers
                } else {
                    0f
                }

                val multiplier = when (selectedPeriod) {
                    0 -> 1.0
                    1 -> 3.2
                    else -> 7.5
                }

                val scaledViewers = if (channelLiveViewers > 0) {
                    (channelLiveViewers * multiplier).toInt().coerceAtLeast(1)
                } else {
                    0
                }

                val viewersStr = "$scaledViewers"
                val mbps = scaledViewers * 4.5
                val bandwidthVal = String.format("%.1f Mbps", mbps)

                val trend = if (channelLiveViewers > 0) "+${(10 + index)}%" else "Stable"
                val isPositive = if (channelLiveViewers > 0) true else null

                ChannelStat(
                    channel = channel,
                    share = share,
                    viewers = viewersStr,
                    bandwidth = bandwidthVal,
                    trend = trend,
                    isPositiveTrend = isPositive,
                    quality = "1080p (60fps)",
                    bitrate = "4.5 Mbps",
                    server = when (index % 3) {
                        0 -> "Edge-Primary"
                        1 -> "Edge-Secondary"
                        else -> "Edge-Backup"
                    }
                )
            }
        }
    }

    var selectedIndex by remember(selectedPeriod) { mutableStateOf(0) }
    val selectedStat = remember(statsList, selectedIndex) {
        statsList.getOrNull(selectedIndex) ?: statsList.firstOrNull()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White
                )
            }
            Text(
                text = "Statistiques de visionnage",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val periods = listOf("Aujourd'hui", "7 derniers jours", "30 derniers jours")
            periods.forEachIndexed { index, periodName ->
                val isSelected = selectedPeriod == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = Color(0xFF161616),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedPeriod = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = periodName,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(2.dp)
                                .background(
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }

        if (isLoadingStats) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (selectedPeriod == 0) "Spectateurs Actifs" else "Spectateurs Max",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (selectedPeriod == 0) "$activeViewers" else "$maxViewers",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (totalUsers == 0) "Aucun utilisateur" else "Sur $totalUsers inscrits",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (selectedPeriod == 0) "Bande Passante" else "Volume Global",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = bandwidthStr,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Moyenne active",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = when (selectedPeriod) {
                                    0 -> "Courbe d'audience (24h)"
                                    1 -> "Audience moyenne par jour"
                                    else -> "Audience hebdomadaire"
                                },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            ) {
                                val width = size.width
                                val height = size.height

                                val total = if (totalUsers > 0) totalUsers else 5
                                val ratio = (activeViewers.toFloat() / total.toFloat()).coerceIn(0.05f, 1.0f)
                                val basePoints = when (selectedPeriod) {
                                    0 -> {
                                        listOf(0.15f, 0.25f, 0.2f, 0.45f, 0.6f, 0.5f, 1.0f).map { it * ratio }
                                    }
                                    1 -> {
                                        listOf(0.4f, 0.45f, 0.6f, 0.55f, 0.7f, 0.65f, 0.8f).map { it * ratio }
                                    }
                                    else -> {
                                        listOf(0.3f, 0.35f, 0.4f, 0.38f, 0.5f, 0.55f, 0.48f, 0.6f, 0.65f, 0.75f).map { it * ratio }
                                    }
                                }

                                val scaleMultiplier = if (activeViewers == 0) 0f else 1f
                                val points = basePoints.map { it * scaleMultiplier }
                                val step = width / (points.size - 1)

                                val path = Path().apply {
                                    val startY = height - (points[0] * height * 0.8f * animationProgress)
                                    moveTo(0f, startY)
                                    for (i in 1 until points.size) {
                                        val nextX = i * step
                                        val nextY = height - (points[i] * height * 0.8f * animationProgress)
                                        lineTo(nextX, nextY)
                                    }
                                }

                                drawPath(
                                    path = path,
                                    color = Color.White,
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                for (i in points.indices) {
                                    val cx = i * step
                                    val cy = height - (points[i] * height * 0.8f * animationProgress)
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4.dp.toPx(),
                                        center = Offset(cx, cy)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                when (selectedPeriod) {
                                    0 -> {
                                        Text("06:00", color = Color.Gray, fontSize = 10.sp)
                                        Text("12:00", color = Color.Gray, fontSize = 10.sp)
                                        Text("18:00", color = Color.Gray, fontSize = 10.sp)
                                        Text("23:00", color = Color.Gray, fontSize = 10.sp)
                                    }
                                    1 -> {
                                        Text("Lun", color = Color.Gray, fontSize = 10.sp)
                                        Text("Mer", color = Color.Gray, fontSize = 10.sp)
                                        Text("Ven", color = Color.Gray, fontSize = 10.sp)
                                        Text("Dim", color = Color.Gray, fontSize = 10.sp)
                                    }
                                    else -> {
                                        Text("Sem 1", color = Color.Gray, fontSize = 10.sp)
                                        Text("Sem 2", color = Color.Gray, fontSize = 10.sp)
                                        Text("Sem 3", color = Color.Gray, fontSize = 10.sp)
                                        Text("Sem 4", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Chaînes actives et populaires",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (activeChannels.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucune chaîne configurée dans le catalogue",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                statsList.forEachIndexed { idx, stat ->
                                    val isSelected = selectedIndex == idx
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedIndex = idx }
                                            .background(
                                                color = if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stat.channel.name,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "${(stat.share * 100).toInt()}% d'audience",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = stat.share * animationProgress,
                                            modifier = Modifier.fillMaxWidth().height(6.dp),
                                            color = Color.White,
                                            trackColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                selectedStat?.let { stat ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Détails du flux : ${stat.channel.name}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Statut", color = Color.Gray, fontSize = 11.sp)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                                            )
                                            Text("Actif", color = Color.White, fontSize = 13.sp)
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Résolution", color = Color.Gray, fontSize = 11.sp)
                                        Text(stat.quality, color = Color.White, fontSize = 13.sp)
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Bitrate", color = Color.Gray, fontSize = 11.sp)
                                        Text(stat.bitrate, color = Color.White, fontSize = 13.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Serveur d'origine", color = Color.Gray, fontSize = 11.sp)
                                        Text(stat.server, color = Color.White, fontSize = 13.sp)
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (selectedPeriod == 0) "Spectateurs" else "Pics de spectateurs", color = Color.Gray, fontSize = 11.sp)
                                        Text(stat.viewers, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Tendance", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            text = stat.trend,
                                            color = when (stat.isPositiveTrend) {
                                                true -> Color(0xFF4CAF50)
                                                false -> Color(0xFFF44336)
                                                else -> Color.Gray
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun activePeriodMultiplier(period: Int): Double {
    return when (period) {
        0 -> 1.0
        1 -> 2.8
        else -> 6.5
    }
}
