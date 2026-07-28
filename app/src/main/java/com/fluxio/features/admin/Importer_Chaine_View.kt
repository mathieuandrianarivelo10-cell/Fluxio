package com.fluxio.features.admin

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import com.fluxio.features.iptv.COMMON_CATEGORIES
import com.fluxio.features.player.VideoPlayerView
import com.fluxio.shared.components.CustomImageImportIcon
import com.fluxio.features.iptv.normalizeGenre
import com.fluxio.features.iptv.sortFrenchChannelsFirst
import com.fluxio.features.settings.StreamResolutionInfo
import com.fluxio.features.settings.extractStreamInfo
import com.fluxio.shared.models.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val logoRegex = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE)
private val groupRegex = Regex("""group-title="([^"]+)"""", RegexOption.IGNORE_CASE)
private val countryRegex = Regex("""tvg-country="([^"]+)"""", RegexOption.IGNORE_CASE)

suspend fun fetchM3uContentFromUrl(urlStr: String): String {
    return withContext(Dispatchers.IO) {
        var currentUrl = urlStr
        var redirects = 0
        var connection: HttpURLConnection? = null
        
        while (redirects < 5) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
            connection.instanceFollowRedirects = true

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == 307 || 
                status == 308) {
                val newUrl = connection.getHeaderField("Location")
                if (!newUrl.isNullOrEmpty()) {
                    currentUrl = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                        newUrl
                    } else {
                        val base = URL(currentUrl)
                        URL(base, newUrl).toString()
                    }
                    connection.disconnect()
                    redirects++
                } else {
                    break
                }
            } else {
                break
            }
        }

        val conn = connection ?: throw IllegalStateException("Impossible de se connecter à l'URL")
        val encoding = conn.contentEncoding
        val inputStream = if (encoding != null && encoding.equals("gzip", ignoreCase = true)) {
            java.util.zip.GZIPInputStream(conn.inputStream)
        } else if (encoding != null && encoding.equals("deflate", ignoreCase = true)) {
            java.util.zip.InflaterInputStream(conn.inputStream)
        } else {
            conn.inputStream
        }

        inputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { it.readText() }
    }
}

fun parseM3uToLiveChannels(content: String): List<LiveChannel> {
    val list = mutableListOf<LiveChannel>()
    var currentName = ""
    var currentLogoUrl = ""
    var currentCategory = "Divertissement / Humour"
    var currentCountry = "France"

    val markdownLinkRegex = Regex("""\[([^\]]+)\]\((https?://[^\s\)]+)\)""")
    val urlRegex = Regex("""(https?|rtmp|rtsp)://[^\s"'>|]+""", RegexOption.IGNORE_CASE)

    content.lineSequence().forEach { rawLine ->
        val trimmed = rawLine.trim()
        if (trimmed.isNotEmpty()) {
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                currentName = trimmed.substringAfterLast(",").trim()
                currentLogoUrl = logoRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: ""

                val matchedGroup = groupRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: ""
                currentCategory = if (matchedGroup.isNotEmpty()) normalizeGenre(matchedGroup) else "Divertissement / Humour"

                val matchedCountry = countryRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: ""
                if (matchedCountry.isNotEmpty()) {
                    currentCountry = matchedCountry
                }
            } else if (!trimmed.startsWith("#") && !trimmed.startsWith("<!--")) {
                val mdMatch = markdownLinkRegex.find(trimmed)
                if (mdMatch != null) {
                    val name = mdMatch.groupValues[1].trim()
                    val url = mdMatch.groupValues[2].trim()
                    if (!url.endsWith(".png", ignoreCase = true) &&
                        !url.endsWith(".jpg", ignoreCase = true) &&
                        !url.endsWith(".svg", ignoreCase = true)
                    ) {
                        val safeId = "custom_import_" + UUID.randomUUID().toString().take(8)
                        list.add(
                            LiveChannel(
                                id = safeId,
                                name = name,
                                url = url,
                                category = currentCategory,
                                logoText = name.take(2).uppercase(),
                                description = "Chaîne importée",
                                logoUrl = currentLogoUrl,
                                country = currentCountry,
                                isPaid = false
                            )
                        )
                    }
                } else {
                    val urlMatch = urlRegex.find(trimmed)
                    if (urlMatch != null) {
                        val url = urlMatch.value.trim()
                        if (!url.endsWith(".png", ignoreCase = true) &&
                            !url.endsWith(".jpg", ignoreCase = true) &&
                            !url.endsWith(".jpeg", ignoreCase = true) &&
                            !url.endsWith(".svg", ignoreCase = true) &&
                            !url.contains("w3.org")
                        ) {
                            var name = currentName
                            if (name.isEmpty() && trimmed.startsWith("|")) {
                                val parts = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                                val nonUrlPart = parts.firstOrNull { !it.startsWith("http", ignoreCase = true) && !it.contains("---") }
                                if (nonUrlPart != null) {
                                    name = nonUrlPart
                                }
                            }
                            if (name.isEmpty()) {
                                name = url.substringAfterLast("/").substringBefore("?").substringBefore("#")
                                if (name.isBlank() || name.length < 2) name = "Chaîne " + (list.size + 1)
                            }
                            val safeId = "custom_import_" + UUID.randomUUID().toString().take(8)
                            list.add(
                                LiveChannel(
                                    id = safeId,
                                    name = name,
                                    url = url,
                                    category = currentCategory,
                                    logoText = name.take(2).uppercase(),
                                    description = "Chaîne importée",
                                    logoUrl = currentLogoUrl,
                                    country = currentCountry,
                                    isPaid = false
                                )
                            )
                        }
                    }
                }
                currentName = ""
                currentLogoUrl = ""
                currentCategory = "Divertissement / Humour"
                currentCountry = "France"
            }
        }
    }
    return list
}

@Composable
fun ImporterChaineView(
    allChannels: List<LiveChannel> = emptyList(),
    adminFeaturedChannelIds: Set<String> = emptySet(),
    adminPublishedChannelIds: Set<String> = emptySet(),
    onPublishCatalogs: (Set<String>, Set<String>) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onRefreshChannels: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("horizon_iptv", android.content.Context.MODE_PRIVATE) }

    var playlistUrl by remember {
        mutableStateOf("https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u")
    }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzingProgressMessage by remember { mutableStateOf("") }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var parsedChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var isImportingAll by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedChannelListTab by remember { mutableStateOf("operational") }
    var editingChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var channelToDelete by remember { mutableStateOf<LiveChannel?>(null) }

    val lazyListState = rememberLazyListState()
    var lastInspectedChannelId by remember { mutableStateOf<String?>(null) }

    val analyzePlaylist: () -> Unit = {
        val rawUrls = playlistUrl.lines()
            .flatMap { it.split(",", ";") }
            .map { it.trim() }
            .filter { line ->
                line.startsWith("http://", ignoreCase = true) ||
                line.startsWith("https://", ignoreCase = true) ||
                line.startsWith("rtmp://", ignoreCase = true) ||
                line.startsWith("rtsp://", ignoreCase = true)
            }
            .distinct()

        if (rawUrls.isNotEmpty()) {
            isAnalyzing = true
            analysisError = null
            analyzingProgressMessage = "Analyse de ${rawUrls.size} flux..."
            scope.launch {
                try {
                    val allCombinedChannels = mutableListOf<LiveChannel>()
                    val failedUrls = mutableListOf<String>()
                    var successCount = 0

                    rawUrls.forEachIndexed { index, urlStr ->
                        analyzingProgressMessage = "Analyse (${index + 1}/${rawUrls.size})..."
                        try {
                            val rawText = fetchM3uContentFromUrl(urlStr)
                            val channels = parseM3uToLiveChannels(rawText)
                            allCombinedChannels.addAll(channels)
                            if (channels.isNotEmpty()) {
                                successCount++
                            }
                        } catch (e: Exception) {
                            failedUrls.add(urlStr)
                        }
                    }

                    val uniqueChannels = allCombinedChannels.distinctBy { it.url.trim() }
                    parsedChannels = uniqueChannels

                    if (uniqueChannels.isEmpty()) {
                        analysisError = "Aucune chaîne valide trouvée dans les ${rawUrls.size} lien(s)."
                    } else {
                        val msg = if (failedUrls.isEmpty()) {
                            "${uniqueChannels.size} chaînes analysées avec succès (${rawUrls.size} flux) !"
                        } else {
                            "${uniqueChannels.size} chaînes analysées ($successCount/${rawUrls.size} flux réussis, ${failedUrls.size} échec(s))."
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    analysisError = "Erreur d'analyse : ${e.localizedMessage ?: "Impossible de télécharger les playlists"}"
                } finally {
                    isAnalyzing = false
                    analyzingProgressMessage = ""
                }
            }
        } else {
            Toast.makeText(context, "Veuillez saisir au moins une URL valide.", Toast.LENGTH_SHORT).show()
        }
    }

    // Automatically analyze playlist on launch
    LaunchedEffect(Unit) {
        analyzePlaylist()
    }

    val filteredChannels = remember(parsedChannels, searchQuery) {
        val q = searchQuery.lowercase().trim()
        if (q.isEmpty()) {
            parsedChannels
        } else {
            parsedChannels.filter {
                it.name.lowercase().contains(q) ||
                        it.category.lowercase().contains(q)
            }
        }
    }

    val operationalChannels = remember(filteredChannels) {
        filteredChannels.filter { it.logoUrl.isNotBlank() && it.url.isNotBlank() }
    }

    val problematicChannels = remember(filteredChannels) {
        filteredChannels.filter { it.logoUrl.isBlank() || it.url.isBlank() }
    }

    val freeChannels = remember(filteredChannels) {
        filteredChannels.filter { !it.isPaid }
    }

    val paidChannels = remember(filteredChannels) {
        filteredChannels.filter { it.isPaid }
    }

    LaunchedEffect(editingChannel) {
        if (editingChannel == null && lastInspectedChannelId != null) {
            val targetId = lastInspectedChannelId
            var currentTabList = when (selectedChannelListTab) {
                "problematic" -> problematicChannels
                "free" -> freeChannels
                "paid" -> paidChannels
                else -> operationalChannels
            }
            if (!currentTabList.any { it.id == targetId }) {
                if (operationalChannels.any { it.id == targetId }) {
                    selectedChannelListTab = "operational"
                    currentTabList = operationalChannels
                } else if (problematicChannels.any { it.id == targetId }) {
                    selectedChannelListTab = "problematic"
                    currentTabList = problematicChannels
                } else if (freeChannels.any { it.id == targetId }) {
                    selectedChannelListTab = "free"
                    currentTabList = freeChannels
                } else if (paidChannels.any { it.id == targetId }) {
                    selectedChannelListTab = "paid"
                    currentTabList = paidChannels
                }
            }
            val index = currentTabList.indexOfFirst { it.id == targetId }
            if (index != -1) {
                val headerOffset = 4
                lazyListState.scrollToItem((headerOffset + index).coerceAtLeast(0))
            }
        }
    }

    AnimatedContent(
        targetState = editingChannel,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width } + fadeOut()
                )
            } else {
                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut()
                )
            }
        },
        label = "EditingChannelTransitionImport",
        modifier = Modifier.fillMaxSize()
    ) { targetChannel ->
        if (targetChannel != null) {
            val channelToEdit = targetChannel
            var editName by remember(channelToEdit.id) { mutableStateOf(channelToEdit.name) }
            var editUrl by remember(channelToEdit.id) { mutableStateOf(channelToEdit.url) }
            var editCategory by remember(channelToEdit.id) { mutableStateOf(channelToEdit.category) }
            var editCountry by remember(channelToEdit.id) { mutableStateOf(channelToEdit.country) }
            var editLogoUrl by remember(channelToEdit.id) { mutableStateOf(channelToEdit.logoUrl) }
            var editDescription by remember(channelToEdit.id) { mutableStateOf(channelToEdit.description) }
            var editLogoText by remember(channelToEdit.id) { mutableStateOf(channelToEdit.logoText) }
            var editIsPaid by remember(channelToEdit.id) { mutableStateOf(channelToEdit.isPaid) }
            var editIsPublished by remember(channelToEdit.id) { mutableStateOf(adminPublishedChannelIds.contains(channelToEdit.id)) }
            var editIsFeatured by remember(channelToEdit.id) { mutableStateOf(adminFeaturedChannelIds.contains(channelToEdit.id)) }
            var editShowQualityWarning by remember(channelToEdit.id) { mutableStateOf(sharedPrefs.getBoolean("show_quality_warning_${channelToEdit.id}", false)) }
            var isUploadingLogo by remember(channelToEdit.id) { mutableStateOf(false) }
            var isTestingStream by remember(channelToEdit.id) { mutableStateOf(false) }

            var streamTrackList by remember(editUrl) { mutableStateOf<List<StreamResolutionInfo>?>(null) }
            var isLoadingStreamInfo by remember(editUrl) { mutableStateOf(false) }

            LaunchedEffect(editUrl) {
                if (editUrl.isNotBlank()) {
                    isLoadingStreamInfo = true
                    streamTrackList = extractStreamInfo(editUrl)
                    isLoadingStreamInfo = false
                } else {
                    streamTrackList = null
                }
            }

            val logoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let {
                    isUploadingLogo = true
                    scope.launch {
                        val cloudUrl = AdminUtils.uploadImageToStorage(context, it, "logos")
                        isUploadingLogo = false
                        if (cloudUrl != null) {
                            editLogoUrl = cloudUrl
                            Toast.makeText(context, "Logo téléversé dans le cloud avec succès !", Toast.LENGTH_SHORT).show()
                        } else {
                            val localPath = AdminUtils.copyUriToInternalStorage(context, it)
                            if (localPath != null) {
                                editLogoUrl = localPath
                                Toast.makeText(context, "Stocké localement (pour le Cloud, configurez ImgBB dans Paramètres).", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Erreur lors de l'importation de l'image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { editingChannel = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Modifier la chaîne",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nom de la chaîne", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("URL de flux", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val selectedCats = remember(editCategory) {
                        editCategory.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    var showAddCategoryDropdown by remember { mutableStateOf(false) }

                    Text(
                        text = "Catégories sélectionnées (${selectedCats.size}/3 max) :",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedCats.forEach { cat ->
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
                                    contentDescription = "Retirer",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val updated = selectedCats.filter { it != cat }
                                            editCategory = updated.joinToString(",")
                                        }
                                )
                            }
                        }

                        if (selectedCats.size < 3) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { showAddCategoryDropdown = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Ajouter une catégorie",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                DropdownMenu(
                                    expanded = showAddCategoryDropdown,
                                    onDismissRequest = { showAddCategoryDropdown = false },
                                    modifier = Modifier
                                        .widthIn(min = 200.dp, max = 280.dp)
                                        .heightIn(max = 280.dp)
                                        .background(Color(0xFF1E1E1E))
                                ) {
                                    COMMON_CATEGORIES.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat, color = Color.White) },
                                            onClick = {
                                                showAddCategoryDropdown = false
                                                val isAlreadySelected = selectedCats.any { it.equals(cat, ignoreCase = true) }
                                                if (isAlreadySelected) {
                                                    Toast.makeText(context, "Cette catégorie est déjà sélectionnée.", Toast.LENGTH_SHORT).show()
                                                } else if (selectedCats.size >= 3) {
                                                    Toast.makeText(context, "Vous pouvez sélectionner jusqu'à 3 catégories au maximum.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val updated = selectedCats + cat
                                                    editCategory = updated.joinToString(",")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editCountry,
                        onValueChange = { editCountry = it },
                        label = { Text("Pays", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editLogoUrl,
                        onValueChange = { editLogoUrl = it },
                        label = { Text("URL du Logo", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White
                        ),
                        trailingIcon = {
                            if (isUploadingLogo) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { logoPickerLauncher.launch("image/*") }
                                ) {
                                    CustomImageImportIcon(tint = Color.White)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it.take(150) },
                        label = { Text("Description", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White
                        ),
                        supportingText = {
                            Text(
                                text = "${editDescription.length} / 150",
                                color = if (editDescription.length >= 150) Color.Red else Color.Gray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editLogoText,
                        onValueChange = { editLogoText = it },
                        label = { Text("Texte du Logo (initiales)", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Type d'accès de la chaîne",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (!editIsPaid) Color.White.copy(alpha = 0.15f) else Color(0xFF262626))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (!editIsPaid) Color.White else Color.Gray.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { editIsPaid = false }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Gratuit",
                                        color = if (!editIsPaid) Color.White else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Tout public",
                                        color = if (!editIsPaid) Color.White.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (editIsPaid) Color.White.copy(alpha = 0.15f) else Color(0xFF262626))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (editIsPaid) Color.White else Color.Gray.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { editIsPaid = true }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Payant",
                                        color = if (editIsPaid) Color.White else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Premium",
                                        color = if (editIsPaid) Color.White.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Publier la chaîne", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = editIsPublished,
                            onCheckedChange = { editIsPublished = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mettre en vedette (Home)", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = editIsFeatured,
                            onCheckedChange = { editIsFeatured = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Boîte de dialogue Qualité Vidéo", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = editShowQualityWarning,
                            onCheckedChange = { editShowQualityWarning = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (editUrl.isNotBlank()) {
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
                                            .height(80.dp),
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
                                                    Text(
                                                        text = track.resolution,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
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

                    // --- NOUVELLE FONCTIONNALITÉ: TESTER LA CHAÎNE ---
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (editUrl.isBlank()) {
                                Toast.makeText(context, "Veuillez saisir une URL de flux à tester.", Toast.LENGTH_SHORT).show()
                            } else {
                                isTestingStream = !isTestingStream
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTestingStream) Color(0xFF262626) else Color(0xFF161616)
                        ),
                        border = BorderStroke(1.dp, if (isTestingStream) Color(0xFFE50914) else Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isTestingStream) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTestingStream) "Fermer le test du lecteur" else "Tester la chaîne (Lecteur Vidéo)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isTestingStream && editUrl.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black),
                            border = BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Aperçu de la chaîne en direct",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { isTestingStream = false },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fermer",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                ) {
                                    VideoPlayerView(
                                        url = editUrl.trim(),
                                        playbackSpeed = 1.0f,
                                        isMuted = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val updated = LiveChannel(
                                    id = channelToEdit.id,
                                    name = editName,
                                    url = editUrl,
                                    category = editCategory,
                                    logoUrl = editLogoUrl,
                                    country = editCountry,
                                    description = editDescription,
                                    logoText = if (editLogoText.isNotBlank()) editLogoText else editName.take(2).uppercase(),
                                    isPaid = editIsPaid
                                )
                                parsedChannels = parsedChannels.map { if (it.id == channelToEdit.id) updated else it }
                                AdminUtils.saveCustomChannel(context, updated)
                                AdminUtils.updateChannelPaidStatus(context, channelToEdit.id, editIsPaid)
                                sharedPrefs.edit().putBoolean("show_quality_warning_${channelToEdit.id}", editShowQualityWarning).apply()
                                val currentWarningSet: Set<String> = sharedPrefs.getStringSet("quality_warning_channel_ids", emptySet()) ?: emptySet()
                                val updatedWarningSet = if (editShowQualityWarning) currentWarningSet + channelToEdit.id else currentWarningSet - channelToEdit.id
                                sharedPrefs.edit().putStringSet("quality_warning_channel_ids", updatedWarningSet).apply()

                                val updatedPublished = if (editIsPublished) {
                                    adminPublishedChannelIds + channelToEdit.id
                                } else {
                                    adminPublishedChannelIds - channelToEdit.id
                                }
                                val updatedFeatured = if (editIsFeatured) {
                                    adminFeaturedChannelIds + channelToEdit.id
                                } else {
                                    adminFeaturedChannelIds - channelToEdit.id
                                }
                                onPublishCatalogs(updatedFeatured, updatedPublished)

                                editingChannel = null
                                onRefreshChannels()
                                Toast.makeText(context, "Chaîne enregistrée et importée !", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Enregistrer et Importer", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { editingChannel = null },
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Annuler", color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                parsedChannels = parsedChannels.filter { it.id != channelToEdit.id }
                                editingChannel = null
                                Toast.makeText(context, "Chaîne retirée de la liste d'analyse", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Retirer de la liste", color = Color.Red)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("importer_chaine_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importer une chaîne",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("importer_chaine_title")
                    )
                }

                val (currentList, currentTitle, emptyMsg) = when (selectedChannelListTab) {
                    "problematic" -> Triple(problematicChannels, "Chaînes à problème (${problematicChannels.size})", "Aucune chaîne à problème détectée.")
                    "free" -> Triple(freeChannels, "Chaînes gratuites (${freeChannels.size})", "Aucune chaîne gratuite.")
                    "paid" -> Triple(paidChannels, "Chaînes payantes (${paidChannels.size})", "Aucune chaîne payante.")
                    else -> Triple(operationalChannels, "Chaînes opérationnelles (${operationalChannels.size})", "Aucune chaîne opérationnelle détectée.")
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        // Analysis Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Analyseur de Playlists Multi-Flux (M3U, M3U8, MD, XML)",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = playlistUrl,
                                    onValueChange = { playlistUrl = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                    label = { Text("URL(s) des flux", color = Color.Gray, fontSize = 14.sp) },
                                    placeholder = { Text("Entrez vos URL...", color = Color.Gray.copy(alpha = 0.5f), fontSize = 14.sp) },
                                    singleLine = false,
                                    maxLines = 8,
                                    leadingIcon = {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    },
                                    trailingIcon = if (playlistUrl.isNotEmpty()) {
                                        {
                                            IconButton(
                                                onClick = { playlistUrl = "" }
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                val validUrlCount = remember(playlistUrl) {
                                    playlistUrl.lines()
                                        .flatMap { it.split(",", ";") }
                                        .map { it.trim() }
                                        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) || it.startsWith("rtmp://", ignoreCase = true) || it.startsWith("rtsp://", ignoreCase = true) }
                                        .distinct()
                                        .size
                                }

                                Button(
                                    onClick = { analyzePlaylist() },
                                    enabled = !isAnalyzing && validUrlCount > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    if (isAnalyzing) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (analyzingProgressMessage.isNotEmpty()) analyzingProgressMessage else "Analyse en cours...",
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (validUrlCount > 1) "Analyser les $validUrlCount playlists / flux" else "Analyser la playlist",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (analysisError != null) {
                                    Text(
                                        text = analysisError!!,
                                        color = Color(0xFFE50914),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (parsedChannels.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Chaînes analysées dans le lien :",
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${parsedChannels.size}",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            parsedChannels = sortFrenchChannelsFirst(parsedChannels)
                                            Toast.makeText(context, "Chaînes triées avec succès : TNT et chaînes FR placées en tête !", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                                        border = BorderStroke(1.dp, Color(0xFFE50914)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFFE50914),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Trier les chaînes françaises (TNT FR en tête)",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            placeholder = { Text("Rechercher...", color = Color.Gray, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = { searchQuery = "" }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                focusedContainerColor = Color(0xFF161616),
                                unfocusedContainerColor = Color(0xFF161616)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Tabs row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            val tabList = listOf(
                                Triple("operational", "Chaînes opérationnelles (${operationalChannels.size})", operationalChannels),
                                Triple("problematic", "Chaînes à problème (${problematicChannels.size})", problematicChannels),
                                Triple("free", "Chaînes gratuites (${freeChannels.size})", freeChannels),
                                Triple("paid", "Chaînes payantes (${paidChannels.size})", paidChannels)
                            )

                            tabList.forEach { (tabId, label, _) ->
                                val isSelected = selectedChannelListTab == tabId
                                val indicatorWidthFraction by animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0f,
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                                    label = "tabIndicatorWidth"
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .clickable { selectedChannelListTab = tabId }
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(indicatorWidthFraction)
                                            .height(2.dp)
                                            .background(Color(0xFFE50914))
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp)
                        )
                    }

                    if (isAnalyzing) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    Text("Analyse du lien M3U/M3U8 en cours...", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    } else if (currentList.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                            ) {
                                Text(
                                    text = emptyMsg,
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = currentList,
                            key = { channel -> channel.id }
                        ) { channel ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                ChannelAdminRow(
                                    channel = channel,
                                    onEdit = {
                                        lastInspectedChannelId = channel.id
                                        editingChannel = channel
                                    },
                                    onDelete = { channelToDelete = channel }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (channelToDelete != null) {
        AlertDialog(
            onDismissRequest = { channelToDelete = null },
            title = {
                Text(
                    text = "Retirer la chaîne",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Voulez-vous retirer la chaîne '${channelToDelete!!.name}' de la liste d'analyse ?",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val chan = channelToDelete!!
                            parsedChannels = parsedChannels.filter { it.id != chan.id }
                            channelToDelete = null
                            Toast.makeText(context, "Chaîne retirée", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Retirer", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { channelToDelete = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Annuler", color = Color.White)
                    }
                }
            },
            dismissButton = null,
            containerColor = Color(0xFF1E1E1E)
        )
    }
}
