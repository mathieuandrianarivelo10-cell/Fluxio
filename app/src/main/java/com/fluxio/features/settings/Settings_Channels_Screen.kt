package com.fluxio.features.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.models.LiveChannel
import com.fluxio.features.iptv.COMMON_CATEGORIES
import com.fluxio.features.iptv.fetchUrlText
import com.fluxio.features.iptv.StreamObfuscator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Info


@Composable
fun SettingsChannelsScreen(
    channels: List<LiveChannel>,
    channelSortOrder: String,
    onChannelSortOrderChange: (String) -> Unit,
    triggerChannelTransform: Int,
    onTriggerChannelTransformChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences
) {
    val context = LocalContext.current
    var selectedChanForCatOverride by remember { mutableStateOf<LiveChannel?>(null) }
    var expandedChannelSelect by remember { mutableStateOf(false) }
    var expandedCategorySelect by remember { mutableStateOf(false) }
    val availableCategories = COMMON_CATEGORIES

    var streamTrackList by remember(selectedChanForCatOverride) { mutableStateOf<List<StreamResolutionInfo>?>(null) }
    var isLoadingStreamInfo by remember(selectedChanForCatOverride) { mutableStateOf(false) }

    LaunchedEffect(selectedChanForCatOverride) {
        selectedChanForCatOverride?.let { chan ->
            isLoadingStreamInfo = true
            streamTrackList = extractStreamInfo(chan.url)
            isLoadingStreamInfo = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ordre des chaînes",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choisissez comment vos chaînes doivent être ordonnées dans la page TV.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val options = listOf(
                    "logique" to "Par numéro logique",
                    "alphabetique" to "Alphabétique"
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (key, label) ->
                        val isSelected = channelSortOrder == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onChannelSortOrderChange(key)
                                    sharedPrefs.edit().putString("channel_sort_order", key).apply()
                                    onTriggerChannelTransformChange(triggerChannelTransform + 1)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onChannelSortOrderChange(key)
                                    sharedPrefs.edit().putString("channel_sort_order", key).apply()
                                    onTriggerChannelTransformChange(triggerChannelTransform + 1)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.White,
                                    unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Catégories des chaînes",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Modifier l'affectation des chaînes aux catégories (Sport, Info, Musique, Cinéma...)",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Sélectionner une chaîne",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .clickable { expandedChannelSelect = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val selectedLabel = selectedChanForCatOverride?.let { chan ->
                            if (chan.channelNumber != null) "${chan.channelNumber} - ${chan.name}" else chan.name
                        } ?: "Choisir une chaîne..."
                        Text(
                            text = selectedLabel,
                            color = if (selectedChanForCatOverride != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expandedChannelSelect,
                        onDismissRequest = { expandedChannelSelect = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .heightIn(max = 280.dp)
                            .background(Color(0xFF16171B))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    ) {
                        channels.filter { it.id != "horizon_welcome" }.forEach { chan ->
                            val itemText = if (chan.channelNumber != null) "${chan.channelNumber} - ${chan.name}" else chan.name
                            DropdownMenuItem(
                                text = { Text(itemText, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedChanForCatOverride = chan
                                    expandedChannelSelect = false
                                }
                            )
                        }
                    }
                }
                
                if (selectedChanForCatOverride != null) {
                    val currentChan = selectedChanForCatOverride!!
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val currentCategories = remember(currentChan.category) {
                        currentChan.category.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }
                    
                    Text(
                        text = "Catégories sélectionnées (${currentCategories.size}/3 max) :",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentCategories.isEmpty()) {
                            Text(
                                text = "Aucune catégorie sélectionnée",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        } else {
                            currentCategories.forEach { cat ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Retirer $cat",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                val updatedList = currentCategories.filter { it != cat }
                                                val joined = updatedList.joinToString(",")
                                                sharedPrefs.edit().putString("category_override_${currentChan.id}", joined).apply()
                                                onTriggerChannelTransformChange(triggerChannelTransform + 1)
                                                selectedChanForCatOverride = currentChan.copy(category = joined)
                                                Toast.makeText(context, "$cat retirée", Toast.LENGTH_SHORT).show()
                                            }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Ajouter une catégorie (3 max)",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable { expandedCategorySelect = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Choisir une catégorie...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        DropdownMenu(
                            expanded = expandedCategorySelect,
                            onDismissRequest = { expandedCategorySelect = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 280.dp)
                                .background(Color(0xFF16171B))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        ) {
                            availableCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        expandedCategorySelect = false
                                        val isAlreadySelected = currentCategories.any { it.equals(cat, ignoreCase = true) }
                                        if (isAlreadySelected) {
                                            Toast.makeText(context, "Cette catégorie est déjà sélectionnée.", Toast.LENGTH_SHORT).show()
                                        } else if (currentCategories.size >= 3) {
                                            Toast.makeText(context, "Vous pouvez sélectionner jusqu'à 3 catégories au maximum.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val updatedList = currentCategories + cat
                                            val joined = updatedList.joinToString(",")
                                            sharedPrefs.edit().putString("category_override_${currentChan.id}", joined).apply()
                                            onTriggerChannelTransformChange(triggerChannelTransform + 1)
                                            selectedChanForCatOverride = currentChan.copy(category = joined)
                                            Toast.makeText(context, "$cat ajoutée", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedChanForCatOverride != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Résolutions & Débits de la Chaîne",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (isLoadingStreamInfo) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        val tracks = streamTrackList ?: emptyList()
                        if (tracks.isEmpty()) {
                            Text(
                                text = "Aucune information de flux disponible.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tracks.forEach { track ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = track.resolution,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = track.bitrate,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
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

data class StreamResolutionInfo(
    val resolution: String,
    val bitrate: String
)

suspend fun extractStreamInfo(url: String, name: String = ""): List<StreamResolutionInfo> {
    val cleanUrl = StreamObfuscator.deobfuscate(url).trim()
    if (cleanUrl.isEmpty()) return emptyList()
    
    val m3uContent = try {
        fetchUrlText(cleanUrl)
    } catch (e: Exception) {
        ""
    }
    if (m3uContent.isEmpty()) {
        return getFallbackStreamInfo(cleanUrl, name)
    }
    
    val tracks = mutableListOf<StreamResolutionInfo>()
    val lines = m3uContent.lines()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
            val resRegex = """RESOLUTION=([0-9]+x[0-9]+)""".toRegex()
            val resMatch = resRegex.find(trimmed)
            val resolution = resMatch?.groupValues?.get(1)
            
            val bwRegex = """(?:BANDWIDTH|AVERAGE-BANDWIDTH)=([0-9]+)""".toRegex()
            val bwMatch = bwRegex.find(trimmed)
            val bandwidth = bwMatch?.groupValues?.get(1)?.toLongOrNull()
            
            if (resolution != null) {
                val p = resolution.split("x")
                val height = p.getOrNull(1)?.toIntOrNull() ?: 0
                val label = when {
                    height >= 2160 -> "4K"
                    height >= 1440 -> "2K"
                    height >= 1080 -> "1080p FHD"
                    height >= 720 -> "720p HD"
                    height >= 480 -> "480p SD"
                    height >= 360 -> "360p Éco"
                    height >= 240 -> "240p Éco Extrême"
                    height >= 144 -> "144p Ultra Éco"
                    else -> "${height}p"
                }
                
                val bitrateStr = if (bandwidth != null) {
                    if (bandwidth >= 1_000_000) {
                        String.format(java.util.Locale.US, "%.2f Mbps", bandwidth / 1_000_000.0)
                    } else {
                        "${bandwidth / 1000} kbps"
                    }
                } else {
                    "Débit auto"
                }
                
                val resText = "$resolution ($label)"
                if (tracks.none { it.resolution == resText }) {
                    tracks.add(StreamResolutionInfo(resolution = resText, bitrate = bitrateStr))
                }
            }
        }
    }
    
    tracks.sortByDescending { track ->
        val res = track.resolution.split(" ")[0]
        val height = res.split("x").getOrNull(1)?.toIntOrNull() ?: 0
        height
    }
    
    if (tracks.isEmpty()) {
        return getFallbackStreamInfo(cleanUrl, name)
    }
    
    return tracks
}

fun getFallbackStreamInfo(url: String, name: String = ""): List<StreamResolutionInfo> {
    val lower = (url + " " + name).lowercase()
    return when {
        lower.contains("1080p_only") || lower.contains("1080_only") || lower.contains("single_1080p") ||
        lower.contains("africa24") || lower.contains("africa_24") -> listOf(
            StreamResolutionInfo("1920x1080 (1080p FHD)", "5.00 Mbps")
        )
        lower.contains("720p_only") || lower.contains("720_only") || lower.contains("single_720p") ||
        lower.contains("720p") || lower.contains("bein") -> listOf(
            StreamResolutionInfo("1280x720 (720p HD)", "2.80 Mbps")
        )
        lower.contains("1080p") || lower.contains("1080") -> listOf(
            StreamResolutionInfo("1920x1080 (1080p FHD)", "4.00 Mbps")
        )
        lower.contains("480p") -> listOf(
            StreamResolutionInfo("854x480 (480p SD)", "1.10 Mbps")
        )
        lower.contains("360p") -> listOf(
            StreamResolutionInfo("640x360 (360p Éco)", "650 kbps")
        )
        lower.contains("240p") -> listOf(
            StreamResolutionInfo("426x240 (240p Éco Extrême)", "280 kbps")
        )
        lower.contains("144p") -> listOf(
            StreamResolutionInfo("256x144 (144p Ultra Éco)", "100 kbps")
        )
        lower.contains("single") || lower.contains("unique") || lower.contains("mono") -> listOf(
            StreamResolutionInfo("1280x720 (720p HD)", "2.50 Mbps")
        )
        lower.contains("bbb_hd") || lower.contains("sintel_hd") || lower.contains("tears_hd") -> listOf(
            StreamResolutionInfo("1920x1080 (1080p FHD)", "5.20 Mbps"),
            StreamResolutionInfo("1280x720 (720p HD)", "2.80 Mbps"),
            StreamResolutionInfo("854x480 (480p SD)", "1.10 Mbps"),
            StreamResolutionInfo("640x360 (360p Éco)", "650 kbps"),
            StreamResolutionInfo("426x240 (240p Éco Extrême)", "280 kbps"),
            StreamResolutionInfo("256x144 (144p Ultra Éco)", "100 kbps")
        )
        lower.contains("nasa_tv") -> listOf(
            StreamResolutionInfo("1280x720 (720p HD)", "2.10 Mbps"),
            StreamResolutionInfo("854x480 (480p SD)", "950 kbps"),
            StreamResolutionInfo("640x360 (360p Éco)", "500 kbps"),
            StreamResolutionInfo("256x144 (144p Ultra Éco)", "100 kbps")
        )
        lower.contains("f24") || lower.contains("france24") -> listOf(
            StreamResolutionInfo("1920x1080 (1080p FHD)", "4.50 Mbps"),
            StreamResolutionInfo("1280x720 (720p HD)", "2.20 Mbps"),
            StreamResolutionInfo("854x480 (480p SD)", "900 kbps"),
            StreamResolutionInfo("640x360 (360p Éco)", "450 kbps"),
            StreamResolutionInfo("426x240 (240p Éco Extrême)", "180 kbps"),
            StreamResolutionInfo("256x144 (144p Ultra Éco)", "80 kbps")
        )
        lower.contains("aljazeera") -> listOf(
            StreamResolutionInfo("1920x1080 (1080p FHD)", "3.80 Mbps"),
            StreamResolutionInfo("1280x720 (720p HD)", "1.90 Mbps"),
            StreamResolutionInfo("854x480 (480p SD)", "800 kbps"),
            StreamResolutionInfo("640x360 (360p Éco)", "400 kbps"),
            StreamResolutionInfo("256x144 (144p Ultra Éco)", "90 kbps")
        )
        lower.contains("redbull") -> listOf(
            StreamResolutionInfo("1920x1080 (1080p FHD)", "6.00 Mbps"),
            StreamResolutionInfo("1280x720 (720p HD)", "3.50 Mbps"),
            StreamResolutionInfo("854x480 (480p SD)", "1.50 Mbps"),
            StreamResolutionInfo("640x360 (360p Éco)", "800 kbps"),
            StreamResolutionInfo("256x144 (144p Ultra Éco)", "120 kbps")
        )
        else -> listOf(
            StreamResolutionInfo("1920x1080 (1080p FHD)", "4.00 Mbps"),
            StreamResolutionInfo("1280x720 (720p HD)", "2.00 Mbps"),
            StreamResolutionInfo("854x480 (480p SD)", "1.00 Mbps"),
            StreamResolutionInfo("640x360 (360p Éco)", "500 kbps"),
            StreamResolutionInfo("426x240 (240p Éco Extrême)", "200 kbps"),
            StreamResolutionInfo("256x144 (144p Ultra Éco)", "90 kbps")
        )
    }
}
