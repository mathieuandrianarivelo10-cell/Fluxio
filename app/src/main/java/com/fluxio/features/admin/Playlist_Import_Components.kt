package com.fluxio.features.admin

import android.net.Uri
import com.fluxio.features.iptv.normalizeGenre
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class CheckState {
    UNTESTED,
    TESTING,
    SUCCESS,
    FAILED
}

data class ParsedChannel(
    val id: String,
    val initialName: String,
    val initialUrl: String,
    val initialLogoUrl: String,
    val initialCategory: String,
    val nameState: MutableState<String>,
    val urlState: MutableState<String>,
    val logoUrlState: MutableState<String>,
    val categoryState: MutableState<String>,
    val isSelectedState: MutableState<Boolean>,
    val checkState: MutableState<CheckState>
)

fun parseM3uPlaylist(content: String): List<ParsedChannel> {
    val list = mutableListOf<ParsedChannel>()
    val lines = content.lines()
    var currentName = ""
    var currentLogoUrl = ""
    var currentCategory = "Divertissement / Humour"
    
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        
        if (trimmed.startsWith("#EXTINF:")) {
            currentName = trimmed.substringAfterLast(",").trim()
            val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(trimmed)
            currentLogoUrl = logoMatch?.groupValues?.get(1) ?: ""
            val groupMatch = Regex("""group-title="([^"]+)"""").find(trimmed)
            val matchedGroup = groupMatch?.groupValues?.get(1) ?: ""
            currentCategory = if (matchedGroup.isNotEmpty()) normalizeGenre(matchedGroup) else "Divertissement / Humour"
        } else if (!trimmed.startsWith("#")) {
            val url = trimmed
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtmp://") || url.startsWith("rtsp://")) {
                val name = if (currentName.isNotEmpty()) currentName else url.substringAfterLast("/").substringBefore("?")
                list.add(
                    ParsedChannel(
                        id = "m3u_" + UUID.randomUUID().toString().take(6),
                        initialName = name,
                        initialUrl = url,
                        initialLogoUrl = currentLogoUrl,
                        initialCategory = currentCategory,
                        nameState = mutableStateOf(name),
                        urlState = mutableStateOf(url),
                        logoUrlState = mutableStateOf(currentLogoUrl),
                        categoryState = mutableStateOf(currentCategory),
                        isSelectedState = mutableStateOf(true),
                        checkState = mutableStateOf(CheckState.UNTESTED)
                    )
                )
            }
            currentName = ""
            currentLogoUrl = ""
            currentCategory = "Divertissement / Humour"
        }
    }
    
    if (list.isEmpty()) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed.contains(",")) {
                val parts = trimmed.split(",", limit = 2)
                val name = parts[0].trim()
                val url = parts[1].trim()
                if (url.startsWith("http")) {
                    list.add(
                        ParsedChannel(
                            id = "csv_" + UUID.randomUUID().toString().take(6),
                            initialName = name,
                            initialUrl = url,
                            initialLogoUrl = "",
                            initialCategory = "Divertissement / Humour",
                            nameState = mutableStateOf(name),
                            urlState = mutableStateOf(url),
                            logoUrlState = mutableStateOf(""),
                            categoryState = mutableStateOf("Divertissement / Humour"),
                            isSelectedState = mutableStateOf(true),
                            checkState = mutableStateOf(CheckState.UNTESTED)
                        )
                    )
                }
            } else if (trimmed.startsWith("http")) {
                val name = trimmed.substringAfterLast("/").substringBefore("?")
                list.add(
                    ParsedChannel(
                        id = "raw_" + UUID.randomUUID().toString().take(6),
                        initialName = name,
                        initialUrl = trimmed,
                        initialLogoUrl = "",
                        initialCategory = "Divertissement / Humour",
                        nameState = mutableStateOf(name),
                        urlState = mutableStateOf(trimmed),
                        logoUrlState = mutableStateOf(""),
                        categoryState = mutableStateOf("Divertissement / Humour"),
                        isSelectedState = mutableStateOf(true),
                        checkState = mutableStateOf(CheckState.UNTESTED)
                    )
                )
            }
        }
    }
    return list
}

suspend fun checkStreamConnectivity(streamUrl: String): Boolean {
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(streamUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            val responseCode = connection.responseCode
            if (responseCode in 200..399) {
                return@withContext true
            }
        } catch (e: Exception) {
            // fall through to GET
        } finally {
            connection?.disconnect()
        }

        try {
            val url = URL(streamUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            val responseCode = connection.responseCode
            responseCode in 200..399
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}

@Composable
fun ParsedChannelCard(
    item: ParsedChannel,
    onPlayPreview: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isSelectedState.value,
                    onCheckedChange = { item.isSelectedState.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.White,
                        uncheckedColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                if (item.logoUrlState.value.isNotEmpty()) {
                    AsyncImage(
                        model = item.logoUrlState.value,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(
                    modifier = Modifier.weight(1.0f)
                ) {
                    BasicTextField(
                        value = item.nameState.value,
                        onValueChange = { item.nameState.value = it },
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.urlState.value,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    val statusColor = when (item.checkState.value) {
                        CheckState.UNTESTED -> Color.Gray
                        CheckState.TESTING -> Color.White
                        CheckState.SUCCESS -> Color(0xFF4CAF50)
                        CheckState.FAILED -> Color(0xFFF44336)
                    }
                    val statusText = when (item.checkState.value) {
                        CheckState.UNTESTED -> "Non checké"
                        CheckState.TESTING -> "Vérification..."
                        CheckState.SUCCESS -> "En ligne"
                        CheckState.FAILED -> "Hors ligne"
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onVerify,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Vérifier la connectivité", tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vérifier", color = Color.White, fontSize = 10.sp)
                    }

                    Button(
                        onClick = onPlayPreview,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Checker la vidéo", tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Checker vidéo", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

enum class DetectedStreamType {
    EMPTY,
    SINGLE_STREAM,
    PLAYLIST,
    UNSURE_HTTP,
    RAW_TEXT
}

fun detectStreamType(text: String): DetectedStreamType {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return DetectedStreamType.EMPTY
    
    if (trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF") || (trimmed.lines().size > 2 && trimmed.contains("http"))) {
        return DetectedStreamType.PLAYLIST
    }
    
    if (trimmed.endsWith(".m3u", ignoreCase = true) || trimmed.contains(".m3u?", ignoreCase = true)) {
        return DetectedStreamType.PLAYLIST
    }
    
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtmp://") || trimmed.startsWith("rtsp://")) {
        val path = trimmed.substringBefore("?")
        if (path.endsWith(".m3u8", ignoreCase = true) || 
            path.endsWith(".mp4", ignoreCase = true) || 
            path.endsWith(".ts", ignoreCase = true) || 
            path.endsWith(".mkv", ignoreCase = true) ||
            path.endsWith(".mov", ignoreCase = true) ||
            path.endsWith(".flv", ignoreCase = true) ||
            path.endsWith(".avi", ignoreCase = true) ||
            path.endsWith(".webm", ignoreCase = true)) {
            return DetectedStreamType.SINGLE_STREAM
        }
        return DetectedStreamType.UNSURE_HTTP
    }
    
    return DetectedStreamType.RAW_TEXT
}

suspend fun downloadUrlWithRedirects(urlStr: String): String {
    return withContext(Dispatchers.IO) {
        var currentUrl = urlStr.trim()
        var connection: HttpURLConnection? = null
        var redirectCount = 0
        val maxRedirects = 10
        
        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308) {
                val newUrl = connection.getHeaderField("Location")
                if (newUrl != null && newUrl.isNotEmpty()) {
                    currentUrl = if (newUrl.startsWith("http")) {
                        newUrl
                    } else {
                        val baseUri = Uri.parse(currentUrl)
                        val scheme = baseUri.scheme ?: "http"
                        val host = baseUri.host ?: ""
                        val port = if (baseUri.port != -1) ":${baseUri.port}" else ""
                        "$scheme://$host$port$newUrl"
                    }
                    redirectCount++
                    connection.disconnect()
                    continue
                }
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                return@withContext content
            } else {
                connection.disconnect()
                throw Exception("HTTP Error $responseCode")
            }
        }
        throw Exception("Too many redirects")
    }
}

fun parseSpecialUrl(url: String): ParsedChannel? {
    val trimmed = url.trim()
    if (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) {
        var name = "Flux YouTube"
        val lowercaseUrl = trimmed.lowercase()
        if (lowercaseUrl.contains("/c/")) {
            val part = trimmed.substringAfter("/c/").substringBefore("/live").substringBefore("/")
            if (part.isNotEmpty()) {
                name = part.replaceFirstChar { it.uppercase() } + " (YouTube)"
            }
        } else if (lowercaseUrl.contains("/channel/")) {
            val part = trimmed.substringAfter("/channel/").substringBefore("/live").substringBefore("/")
            if (part.isNotEmpty()) {
                name = "YouTube Channel ($part)"
            }
        } else if (lowercaseUrl.contains("/user/")) {
            val part = trimmed.substringAfter("/user/").substringBefore("/live").substringBefore("/")
            if (part.isNotEmpty()) {
                name = part.replaceFirstChar { it.uppercase() } + " (YouTube)"
            }
        } else if (lowercaseUrl.contains("/@")) {
            val part = trimmed.substringAfter("/@").substringBefore("/live").substringBefore("/")
            if (part.isNotEmpty()) {
                name = part.replaceFirstChar { it.uppercase() } + " (YouTube)"
            }
        } else if (lowercaseUrl.contains("franceinfo")) {
            name = "Franceinfo (YouTube)"
        }
        
        val category = when {
            lowercaseUrl.contains("franceinfo") || lowercaseUrl.contains("news") || lowercaseUrl.contains("info") || lowercaseUrl.contains("actu") -> "Actualités / Infos"
            lowercaseUrl.contains("sport") || lowercaseUrl.contains("foot") -> "Sport"
            else -> "Divertissement / Humour"
        }
        val logoUrl = "https://www.youtube.com/favicon.ico"
        return ParsedChannel(
            id = "yt_" + UUID.randomUUID().toString().take(6),
            initialName = name,
            initialUrl = trimmed,
            initialLogoUrl = logoUrl,
            initialCategory = category,
            nameState = mutableStateOf(name),
            urlState = mutableStateOf(trimmed),
            logoUrlState = mutableStateOf(logoUrl),
            categoryState = mutableStateOf(category),
            isSelectedState = mutableStateOf(true),
            checkState = mutableStateOf(CheckState.UNTESTED)
        )
    }
    
    if (trimmed.contains("dailymotion.com") || trimmed.contains("dai.ly")) {
        val videoId = trimmed.substringAfterLast("/")
        var name = "Dailymotion Live ($videoId)"
        if (trimmed.contains("france24") || trimmed.contains("x3b68jn")) {
            name = "France 24 (Dailymotion)"
        }
        val logoUrl = "https://www.dailymotion.com/favicon.ico"
        val category = when {
            trimmed.contains("france24") || trimmed.contains("news") || trimmed.contains("info") -> "Actualités / Infos"
            else -> "Divertissement / Humour"
        }
        return ParsedChannel(
            id = "dm_" + UUID.randomUUID().toString().take(6),
            initialName = name,
            initialUrl = trimmed,
            initialLogoUrl = logoUrl,
            initialCategory = category,
            nameState = mutableStateOf(name),
            urlState = mutableStateOf(trimmed),
            logoUrlState = mutableStateOf(logoUrl),
            categoryState = mutableStateOf(category),
            isSelectedState = mutableStateOf(true),
            checkState = mutableStateOf(CheckState.UNTESTED)
        )
    }
    return null
}

suspend fun parseImportInput(inputText: String): List<ParsedChannel> {
    val trimmedInput = inputText.trim()
    if (trimmedInput.isEmpty()) return emptyList()

    // Case 1: Xtream Codes format
    val xtreamMatch = Regex("""(https?://[^|]+)\s*\|\s*([^|]+)\s*\|\s*([^|]+)""").find(trimmedInput)
    if (xtreamMatch != null) {
        val host = xtreamMatch.groupValues[1].trim()
        val username = xtreamMatch.groupValues[2].trim()
        val password = xtreamMatch.groupValues[3].trim()
        val m3uUrl = "$host/get.php?username=$username&password=$password&output=m3u_plus"
        return try {
            val content = downloadUrlWithRedirects(m3uUrl)
            parseM3uPlaylist(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Case 2: Special single link (YouTube / Dailymotion)
    val specialChannel = parseSpecialUrl(trimmedInput)
    if (specialChannel != null) {
        return listOf(specialChannel)
    }

    // Case 3: HTTP/HTTPS link
    if (trimmedInput.startsWith("http://") || trimmedInput.startsWith("https://")) {
        return try {
            val content = downloadUrlWithRedirects(trimmedInput)
            val parsed = parseM3uPlaylist(content)
            if (parsed.isNotEmpty()) {
                parsed
            } else {
                val nameFromUrl = trimmedInput.substringAfterLast("/").substringBefore("?").replace(".m3u8", "", ignoreCase = true).replace(".mp4", "", ignoreCase = true)
                val finalName = if (nameFromUrl.isEmpty()) "Flux Direct" else nameFromUrl
                listOf(
                    ParsedChannel(
                        id = "single_" + UUID.randomUUID().toString().take(6),
                        initialName = finalName,
                        initialUrl = trimmedInput,
                        initialLogoUrl = "",
                        initialCategory = "Divertissement / Humour",
                        nameState = mutableStateOf(finalName),
                        urlState = mutableStateOf(trimmedInput),
                        logoUrlState = mutableStateOf(""),
                        categoryState = mutableStateOf("Divertissement / Humour"),
                        isSelectedState = mutableStateOf(true),
                        checkState = mutableStateOf(CheckState.UNTESTED)
                    )
                )
            }
        } catch (e: Exception) {
            val nameFromUrl = trimmedInput.substringAfterLast("/").substringBefore("?").replace(".m3u8", "", ignoreCase = true).replace(".mp4", "", ignoreCase = true)
            val finalName = if (nameFromUrl.isEmpty()) "Flux Direct" else nameFromUrl
            listOf(
                ParsedChannel(
                    id = "single_" + UUID.randomUUID().toString().take(6),
                    initialName = finalName,
                    initialUrl = trimmedInput,
                    initialLogoUrl = "",
                    initialCategory = "Divertissement / Humour",
                    nameState = mutableStateOf(finalName),
                    urlState = mutableStateOf(trimmedInput),
                    logoUrlState = mutableStateOf(""),
                    categoryState = mutableStateOf("Divertissement / Humour"),
                    isSelectedState = mutableStateOf(true),
                    checkState = mutableStateOf(CheckState.UNTESTED)
                )
            )
        }
    }

    // Case 4: Raw text input (M3U content)
    return parseM3uPlaylist(trimmedInput)
}

