package com.fluxio.features.iptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.fluxio.shared.models.LiveChannel
import com.fluxio.shared.components.FeaturedCarousel
import com.fluxio.shared.components.ChannelListRow
import com.fluxio.features.admin.CustomProgram
import androidx.compose.animation.*
import java.text.SimpleDateFormat
import java.util.Locale

data class FeaturedBannerItem(
    val title: String,
    val description: String,
    val imageUrl: String,
    val onClick: () -> Unit,
    val channel: LiveChannel? = null,
    val program: CustomProgram? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccueilScreen(
    channels: List<LiveChannel>,
    adminFeaturedChannels: List<LiveChannel>,
    customPrograms: List<CustomProgram> = emptyList(),
    favoriteSet: Set<String>,
    onChannelSelect: (LiveChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    unreadCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    isPremiumUser: Boolean = true,
    onLockedClick: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
            kotlinx.coroutines.delay(1500)
            pullToRefreshState.endRefresh()
        }
    }

    var showRegarderTvSheet by remember { mutableStateOf(false) }
    var selectedSheetChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var selectedSheetProgramName by remember { mutableStateOf<String?>(null) }

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000) // Update every 10 seconds to keep programs up-to-date and responsive
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val ongoingAndUpcomingPrograms = remember(customPrograms, channels, currentTimeMillis) {
        val now = currentTimeMillis
        val locale = java.util.Locale.FRANCE
        val sdfDate = java.text.SimpleDateFormat("dd/MM/yyyy", locale)
        val sdfTime = java.text.SimpleDateFormat("HH:mm", locale)
        val sdfDayOfWeek = java.text.SimpleDateFormat("EEEE", locale)

        val todayDateStr = sdfDate.format(java.util.Date(now))
        val currentTimeStr = sdfTime.format(java.util.Date(now))
        val todayDayOfWeek = sdfDayOfWeek.format(java.util.Date(now))

        val currentLocale = java.util.Locale.getDefault()
        val sdfDayOfWeekDefault = java.text.SimpleDateFormat("EEEE", currentLocale)
        val todayDayOfWeekDefault = sdfDayOfWeekDefault.format(java.util.Date(now))
        
        // Exclude any programs associated with AB1 (as is done for activePrograms)
        val filteredCustomPrograms = customPrograms.filter { program ->
            val programChannelIds = program.channelId.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val targetChannel = channels.find { programChannelIds.contains(it.id) }
            val isAb1Channel = targetChannel != null && (
                targetChannel.name.contains("AB1", ignoreCase = true) ||
                targetChannel.id.contains("ab1", ignoreCase = true) ||
                targetChannel.id == "8"
            )
            val isAb1Id = programChannelIds.any { id ->
                id.equals("AB1", ignoreCase = true) ||
                id.equals("custom_AB1", ignoreCase = true) ||
                id.contains("ab1", ignoreCase = true) ||
                id == "8"
            }
            val isAb1Name = program.programName.contains("AB1", ignoreCase = true)
            
            !(isAb1Channel || isAb1Id || isAb1Name)
        }

        filteredCustomPrograms.map { program ->
            val dayStr = program.day.trim()
            val startTimeStr = program.startTime.trim()
            val endTimeStr = program.endTime.trim()

            var isLive = false
            var isUpcoming = false

            // Try the standard precise datetime parsing first
            var parsingSuccess = false
            try {
                val startStr: String
                val endStr: String
                if (dayStr.startsWith("Du ") && dayStr.contains(" au ")) {
                    val parts = dayStr.removePrefix("Du ").split(" au ")
                    startStr = parts.getOrNull(0)?.trim() ?: dayStr
                    endStr = parts.getOrNull(1)?.trim() ?: dayStr
                } else {
                    startStr = dayStr
                    endStr = dayStr
                }

                val sdfDateTime = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", locale)
                val parsedStart = sdfDateTime.parse("$startStr $startTimeStr")
                val parsedEnd = sdfDateTime.parse("$endStr $endTimeStr")

                if (parsedStart != null && parsedEnd != null) {
                    parsingSuccess = true
                    val startEpoch = parsedStart.time
                    val endEpoch = parsedEnd.time

                    if (now in startEpoch..endEpoch) {
                        isLive = true
                    } else if (now < startEpoch) {
                        isUpcoming = true
                    }
                }
            } catch (e: Exception) {
                // Ignore, will fallback to text-based matching below
            }

            if (!parsingSuccess) {
                // Time range check
                val isTimeActive = currentTimeStr >= startTimeStr && currentTimeStr <= endTimeStr

                // Day check
                val isDayActive = if (dayStr.startsWith("Du ") && dayStr.contains(" au ")) {
                    val parts = dayStr.removePrefix("Du ").split(" au ")
                    val startStr = parts.getOrNull(0)?.trim() ?: dayStr
                    val endStr = parts.getOrNull(1)?.trim() ?: dayStr
                    try {
                        val parsedStart = sdfDate.parse(startStr)
                        val parsedEnd = sdfDate.parse(endStr)
                        val todayDate = sdfDate.parse(todayDateStr)
                        if (parsedStart != null && parsedEnd != null && todayDate != null) {
                            !todayDate.before(parsedStart) && !todayDate.after(parsedEnd)
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    dayStr.equals(todayDateStr, ignoreCase = true) ||
                    dayStr.equals(todayDayOfWeek, ignoreCase = true) ||
                    dayStr.lowercase().contains(todayDayOfWeek.lowercase()) ||
                    todayDayOfWeek.lowercase().contains(dayStr.lowercase()) ||
                    dayStr.equals(todayDayOfWeekDefault, ignoreCase = true) ||
                    dayStr.lowercase().contains(todayDayOfWeekDefault.lowercase()) ||
                    todayDayOfWeekDefault.lowercase().contains(dayStr.lowercase())
                }

                isLive = isDayActive && isTimeActive

                // Upcoming check
                val isToday = if (dayStr.startsWith("Du ") && dayStr.contains(" au ")) {
                    val parts = dayStr.removePrefix("Du ").split(" au ")
                    val startStr = parts.getOrNull(0)?.trim() ?: dayStr
                    val endStr = parts.getOrNull(1)?.trim() ?: dayStr
                    try {
                        val parsedStart = sdfDate.parse(startStr)
                        val parsedEnd = sdfDate.parse(endStr)
                        val todayDate = sdfDate.parse(todayDateStr)
                        parsedStart != null && parsedEnd != null && todayDate != null && !todayDate.before(parsedStart) && !todayDate.after(parsedEnd)
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    dayStr.equals(todayDateStr, ignoreCase = true) ||
                    dayStr.equals(todayDayOfWeek, ignoreCase = true) ||
                    dayStr.lowercase().contains(todayDayOfWeek.lowercase()) ||
                    todayDayOfWeek.lowercase().contains(dayStr.lowercase()) ||
                    dayStr.equals(todayDayOfWeekDefault, ignoreCase = true) ||
                    dayStr.lowercase().contains(todayDayOfWeekDefault.lowercase()) ||
                    todayDayOfWeekDefault.lowercase().contains(dayStr.lowercase())
                }

                isUpcoming = if (isToday) {
                    currentTimeStr < startTimeStr
                } else {
                    if (dayStr.startsWith("Du ") && dayStr.contains(" au ")) {
                        val parts = dayStr.removePrefix("Du ").split(" au ")
                        val startStr = parts.getOrNull(0)?.trim() ?: dayStr
                        try {
                            val parsedStart = sdfDate.parse(startStr)
                            val todayDate = sdfDate.parse(todayDateStr)
                            parsedStart != null && todayDate != null && todayDate.before(parsedStart)
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        try {
                            val parsedDay = sdfDate.parse(dayStr)
                            val todayDate = sdfDate.parse(todayDateStr)
                            parsedDay != null && todayDate != null && todayDate.before(parsedDay)
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
            }

            Triple(program, isLive, isUpcoming)
        }.filter { it.second || it.third }
        .sortedWith(compareByDescending<Triple<CustomProgram, Boolean, Boolean>> { it.second } // Live first
            .thenByDescending { it.third } // Upcoming second
            .thenBy {
                try {
                    val dayStr = it.first.day.trim()
                    val startStr = if (dayStr.startsWith("Du ") && dayStr.contains(" au ")) {
                        val parts = dayStr.removePrefix("Du ").split(" au ")
                        parts.getOrNull(0)?.trim() ?: dayStr
                    } else {
                        dayStr
                    }
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", locale).parse("$startStr ${it.first.startTime.trim()}")?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
        )
    }

    val allLivePrograms = remember(ongoingAndUpcomingPrograms) {
        ongoingAndUpcomingPrograms.filter { it.second }
    }

    val activePrograms = remember(allLivePrograms) {
        allLivePrograms.map { it.first }
    }

    val featuredItems = remember(adminFeaturedChannels, activePrograms, channels) {
        val list = mutableListOf<FeaturedBannerItem>()
        if (activePrograms.isNotEmpty()) {
            activePrograms.forEach { program ->
                val programChannelIds = program.channelId.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val targetChannel = channels.find { programChannelIds.contains(it.id) }
                val bgUrl = if (program.imageUrl.isNotEmpty()) {
                    if (program.imageUrl.startsWith("/")) {
                        val file = java.io.File(program.imageUrl)
                        if (file.exists()) {
                            program.imageUrl
                        } else {
                            targetChannel?.getBackdropUrl() ?: ""
                        }
                    } else {
                        program.imageUrl
                    }
                } else {
                    targetChannel?.getBackdropUrl() ?: ""
                }
                list.add(
                    FeaturedBannerItem(
                        title = program.programName,
                        description = program.description.ifEmpty {
                            if (targetChannel != null) {
                                if (programChannelIds.size > 1) "Retrouvez ce programme en direct sur plusieurs chaînes."
                                else "Retrouvez ce programme en direct sur ${targetChannel.name}."
                            } else "Retrouvez ce programme en direct sur Fluxio."
                        },
                        imageUrl = bgUrl,
                        onClick = {
                            if (programChannelIds.size > 1) {
                                selectedSheetProgramName = program.programName
                                showRegarderTvSheet = true
                            } else {
                                targetChannel?.let { onChannelSelect(it) }
                            }
                        },
                        channel = targetChannel,
                        program = program
                    )
                )
            }
        }
        if (list.isEmpty()) {
            adminFeaturedChannels.forEach { channel ->
                list.add(
                    FeaturedBannerItem(
                        title = channel.name,
                        description = channel.description.ifEmpty { "Suivez ${channel.name} en direct sur Fluxio." },
                        imageUrl = channel.getBackdropUrl(),
                        onClick = { onChannelSelect(channel) },
                        channel = channel
                    )
                )
            }
        }
        list
    }

    var currentIndex by remember(featuredItems.size) { mutableStateOf(0) }

    LaunchedEffect(featuredItems.size) {
        if (featuredItems.size > 1) {
            while (true) {
                kotlinx.coroutines.delay(30000L)
                currentIndex = (currentIndex + 1) % featuredItems.size
            }
        }
    }

    val currentItem = featuredItems.getOrNull(currentIndex) ?: featuredItems.firstOrNull()

    val liveProgramsList = remember(ongoingAndUpcomingPrograms, currentItem) {
        val currentProgramId = currentItem?.program?.id
        ongoingAndUpcomingPrograms.filter { it.second && it.first.id != currentProgramId }
    }
    val upcomingProgramsList = remember(ongoingAndUpcomingPrograms, currentItem) {
        val currentProgramId = currentItem?.program?.id
        ongoingAndUpcomingPrograms.filter { it.third && !it.second && it.first.id != currentProgramId }
    }

    var topVisitedChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var isLoadingTopVisited by remember { mutableStateOf(true) }
    var showAllCategories by remember { mutableStateOf(false) }

    LaunchedEffect(channels) {
        if (channels.isEmpty()) return@LaunchedEffect
        isLoadingTopVisited = true
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("view_statistics")
                .orderBy("totalViews", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener { result ->
                    val topIds = result.documents.mapNotNull { it.getString("channelId") }
                    val sortedList = mutableListOf<LiveChannel>()
                    topIds.forEach { id ->
                        channels.find { it.id == id }?.let { sortedList.add(it) }
                    }
                    if (sortedList.size < 5) {
                        val remaining = channels.filter { chan -> !sortedList.any { it.id == chan.id } }
                        sortedList.addAll(remaining.take(5 - sortedList.size))
                    }
                    topVisitedChannels = sortedList.take(5)
                    isLoadingTopVisited = false
                }
                .addOnFailureListener {
                    topVisitedChannels = channels.take(5)
                    isLoadingTopVisited = false
                }
        } catch (e: Exception) {
            topVisitedChannels = channels.take(5)
            isLoadingTopVisited = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        if (currentItem != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    AnimatedContent(
                        targetState = currentIndex,
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(1500)) { width -> width } + fadeIn(animationSpec = androidx.compose.animation.core.tween(1500))).togetherWith(
                                slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(1500)) { width -> -width } + fadeOut(animationSpec = androidx.compose.animation.core.tween(1500))
                            )
                        },
                        label = "carousel_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetIndex ->
                        val item = featuredItems.getOrNull(targetIndex) ?: featuredItems.firstOrNull()
                        if (item != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                var isExpanded by remember(targetIndex) { mutableStateOf(false) }
                                val imgData = remember(item.imageUrl) {
                                    if (item.imageUrl.startsWith("/")) {
                                        java.io.File(item.imageUrl)
                                    } else {
                                        item.imageUrl
                                    }
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imgData)
                                        .error(android.R.drawable.ic_menu_gallery)
                                        .crossfade(true)
                                        .size(1280, 720)
                                        .precision(coil.size.Precision.AUTOMATIC)
                                        .build(),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                val backgroundColor = MaterialTheme.colorScheme.background
                                // Vertical gradient to fade background at the bottom
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    backgroundColor.copy(alpha = 0.5f),
                                                    backgroundColor
                                                )
                                            )
                                        )
                                )
                                // Horizontal gradient for text contrast on the left (elegant dark transparent variants)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.85f),
                                                    Color.Black.copy(alpha = 0.6f),
                                                    Color.Black.copy(alpha = 0.2f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                // Banner Contents
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(horizontal = 24.dp, vertical = 24.dp)
                                        .animateContentSize(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 25.sp,
                                        modifier = Modifier.fillMaxWidth(0.65f)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Column(
                                        modifier = Modifier.fillMaxWidth(0.65f)
                                    ) {
                                        Text(
                                            text = item.description,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.description.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (isExpanded) "Afficher moins" else "Afficher plus",
                                                color = Color(0xFFE50914),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textDecoration = TextDecoration.Underline,
                                                modifier = Modifier.clickable { isExpanded = !isExpanded }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Button(
                                        onClick = {
                                            val hasMatchingProgram = activePrograms.any { it.programName.equals(item.title, ignoreCase = true) }
                                            if (hasMatchingProgram) {
                                                selectedSheetProgramName = item.title
                                                selectedSheetChannel = item.channel
                                                showRegarderTvSheet = true
                                            } else {
                                                if (item.channel != null) {
                                                    onChannelSelect(item.channel)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE50914),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Regarder la TV",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Page indicators at the bottom center
                    if (featuredItems.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            featuredItems.forEachIndexed { index, _ ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (index == currentIndex) Color(0xFFE50914)
                                            else Color.White.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }

                    // Top Notification Icon Overlay
                    IconButton(
                        onClick = onNotificationClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(36.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNotificationClick,
                        modifier = Modifier
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chaînes populaires",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isLoadingTopVisited && topVisitedChannels.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(topVisitedChannels) { index, channel ->
                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable {
                                        if (channel.isPaid && !isPremiumUser) {
                                            onLockedClick()
                                        } else {
                                            onChannelSelect(channel)
                                        }
                                    },
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                ) {
                                    if (channel.logoUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(channel.logoUrl)
                                                .crossfade(true).size(256, 256).precision(coil.size.Precision.AUTOMATIC)
                                                .build(),
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp)
                                                .align(Alignment.Center)
                                        )
                                    } else {
                                        Text(
                                            text = channel.initials,
                                            color = Color.Black,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .background(Color(0xFFE50914), CircleShape)
                                            .align(Alignment.TopStart),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = channel.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    com.fluxio.shared.components.DynamicCategoryTicker(
                                        categoryString = channel.category,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(5) {
                            Box(
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Programmes TV",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (upcomingProgramsList.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun programme n'est planifié pour le moment.",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(upcomingProgramsList) { (program, isLive, isUpcoming) ->
                            ProgramCard(
                                program = program,
                                isLive = isLive,
                                isUpcoming = isUpcoming,
                                channels = channels,
                                onChannelSelect = onChannelSelect,
                                onProgramClick = { prog ->
                                    selectedSheetProgramName = prog.programName
                                    showRegarderTvSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Explorer par catégorie",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val genresList = if (showAllCategories) COMMON_CATEGORIES else COMMON_CATEGORIES.take(4)
                    genresList.forEach { genre ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            GenreListRow(
                                genreName = genre,
                                channels = channels,
                                onClick = { onCategoryClick(genre) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (showAllCategories) "Afficher moins" else "Afficher toutes les catégories",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier
                            .clickable { showAllCategories = !showAllCategories }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Custom bottom sheet overlay
    AnimatedVisibility(
        visible = showRegarderTvSheet,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val sheetChannels = remember(channels, selectedSheetChannel, selectedSheetProgramName, customPrograms) {
            val progName = selectedSheetProgramName
            if (progName != null) {
                val targetChannelIds = customPrograms
                    .filter { it.programName.equals(progName, ignoreCase = true) }
                    .flatMap { it.channelId.split(",").map { id -> id.trim() }.filter { id -> id.isNotEmpty() } }
                    .toSet()
                channels.filter { targetChannelIds.contains(it.id) }
            } else {
                val selChannel = selectedSheetChannel
                if (selChannel != null && channels.any { it.id == selChannel.id }) {
                    val category = selChannel.category
                    val sameCategory = channels.filter { it.category == category && it.id != selChannel.id }
                    listOf(selChannel) + sameCategory
                } else {
                    channels
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showRegarderTvSheet = false
                    selectedSheetProgramName = null
                }
        ) {
            AnimatedVisibility(
                visible = showRegarderTvSheet,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable(enabled = false) {}
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF121212))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (LocalImmersiveMode.current) Modifier else Modifier.navigationBarsPadding())
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 12.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp).padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Choisir une chaîne",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedSheetProgramName != null) {
                                    Text(
                                        text = "Programme : $selectedSheetProgramName",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                } else if (selectedSheetChannel != null) {
                                    Text(
                                        text = "Catégorie : ${selectedSheetChannel?.category}",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                        ) {
                            items(sheetChannels, key = { it.id }) { channel ->
                                ChannelListRow(
                                    channel = channel,
                                    isFavorite = favoriteSet.contains(channel.id),
                                    onChannelSelect = {
                                        showRegarderTvSheet = false
                                        selectedSheetProgramName = null
                                        onChannelSelect(channel)
                                    },
                                    onToggleFavorite = {
                                        onToggleFavorite(channel.id)
                                    },
                                    isPremiumUser = isPremiumUser,
                                    onLockedClick = onLockedClick
                                )
                            }
                        }
                    }
                }
            }
        }
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f),
            containerColor = Color(0xFF222326),
            contentColor = Color(0xFFE50914)
        )
    }
}
}

@Composable
private fun ProgramCard(
    program: CustomProgram,
    isLive: Boolean,
    isUpcoming: Boolean,
    channels: List<LiveChannel>,
    onChannelSelect: (LiveChannel) -> Unit,
    onProgramClick: (CustomProgram) -> Unit
) {
    val programChannelIds = remember(program.channelId) {
        program.channelId.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val targetChannel = remember(channels, programChannelIds) {
        channels.find { programChannelIds.contains(it.id) }
    }
    val bgUrl = if (program.imageUrl.isNotEmpty()) {
        if (program.imageUrl.startsWith("/")) {
            val file = java.io.File(program.imageUrl)
            if (file.exists()) {
                program.imageUrl
            } else {
                targetChannel?.getBackdropUrl() ?: ""
            }
        } else {
            program.imageUrl
        }
    } else {
        targetChannel?.getBackdropUrl() ?: ""
    }

    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable {
                if (programChannelIds.size > 1) {
                    onProgramClick(program)
                } else {
                    targetChannel?.let { onChannelSelect(it) }
                }
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val imgData = remember(bgUrl) {
                    if (bgUrl.startsWith("/")) java.io.File(bgUrl) else bgUrl
                }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imgData)
                        .error(android.R.drawable.ic_menu_gallery)
                        .crossfade(true)
                        .size(400, 225)
                        .precision(coil.size.Precision.AUTOMATIC)
                        .build(),
                    contentDescription = program.programName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = when {
                            isLive -> "DIRECT"
                            isUpcoming -> "À VENIR"
                            else -> "TERMINÉ"
                        },
                        color = when {
                            isLive -> Color(0xFFE50914) // Rouge vif pour le direct
                            isUpcoming -> Color(0xFF1D4ED8) // Bleu royal profond pour à venir
                            else -> Color(0xFF4B5563) // Gris ardoise élégant pour terminé
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = program.programName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${program.startTime} - ${program.endTime}",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = program.day,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

