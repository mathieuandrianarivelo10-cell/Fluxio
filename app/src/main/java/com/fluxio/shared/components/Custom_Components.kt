package com.fluxio.shared.components

import com.fluxio.shared.models.LiveChannel
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import com.fluxio.features.admin.CustomProgram
import com.fluxio.shared.theme.*

enum class ImageType {
    CHANNEL_LOGO,
    PROGRAM_BANNER,
    DEFAULT
}

@Composable
fun AdaptiveAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier,
    initials: String,
    fallbackTextSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    imageType: ImageType = ImageType.DEFAULT
) {
    val context = LocalContext.current
    val isConnected = remember(context) { com.fluxio.core.network.NetworkManager.isConnected(context) }
    
    if (isConnected && imageUrl.isNotEmpty()) {
        val imgData = remember(imageUrl) {
            if (imageUrl.startsWith("/")) {
                java.io.File(imageUrl)
            } else {
                imageUrl
            }
        }
        val request = remember(imgData, imageType) {
            ImageRequest.Builder(context)
                .data(imgData)
                .crossfade(true)
                .apply {
                    when (imageType) {
                        ImageType.CHANNEL_LOGO -> {
                            size(256, 256)
                        }
                        ImageType.PROGRAM_BANNER -> {
                            size(1280, 720)
                        }
                        ImageType.DEFAULT -> {
                            // Coil uses automatic component measurement by default
                        }
                    }
                }
                .precision(coil.size.Precision.AUTOMATIC)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = fallbackTextSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val isLightTheme = MaterialTheme.colorScheme.background == Color(0xFFF8F9FA) || MaterialTheme.colorScheme.background == Color(0xFFFFFFFF) || MaterialTheme.colorScheme.background == Color.White
    val baseColor = if (isLightTheme) Color.Black else Color.White

    val shimmerColors = listOf(
        baseColor.copy(alpha = 0.05f),
        baseColor.copy(alpha = 0.15f),
        baseColor.copy(alpha = 0.05f),
    )

    this.background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnim.value, y = translateAnim.value)
        )
    )
}

// --- PLACEHOLDERS & CAROUSELS ---

@Composable
fun CategorySelector(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background == Color(0xFFF8F9FA) || MaterialTheme.colorScheme.background == Color(0xFFFFFFFF) || MaterialTheme.colorScheme.background == Color.White
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(selectedCategory) {
        val index = categories.indexOfFirst { it.lowercase().trim() == selectedCategory.lowercase().trim() }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories, key = { it }) { category ->
            val isSelected = category.lowercase().trim() == selectedCategory.lowercase().trim()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCategorySelected(category) }
                    .padding(vertical = 6.dp)
            ) {
                val textColor = if (isSelected) {
                    Color.White
                } else {
                    Color(0xFF475569)
                }
                Text(
                    text = category,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE50914))
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedCarousel(
    channels: List<LiveChannel>,
    customPrograms: List<CustomProgram> = emptyList(),
    onChannelSelect: (LiveChannel) -> Unit,
    favoriteSet: Set<String>,
    onToggleFavorite: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(customPrograms) { program ->
                var showDetails by remember { mutableStateOf(false) }
                val programChannelIds = remember(program.channelId) {
                    program.channelId.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
                val targetChannel = remember(channels, programChannelIds) {
                    channels.find { programChannelIds.contains(it.id) }
                }
                Box(
                    modifier = Modifier
                        .width(230.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SecondaryBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (!showDetails) showDetails = true }
                ) {
                    val context = LocalContext.current
                    val imgData = remember(program.imageUrl, targetChannel) {
                        if (program.imageUrl.isNotEmpty()) {
                            if (program.imageUrl.startsWith("/")) {
                                val file = java.io.File(program.imageUrl)
                                if (file.exists()) {
                                    file
                                } else {
                                    targetChannel?.getBackdropUrl() ?: ""
                                }
                            } else {
                                program.imageUrl.trim()
                            }
                        } else {
                            targetChannel?.getBackdropUrl() ?: ""
                        }
                    }
                    if (imgData != "") {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imgData)
                                .crossfade(true)
                                .build(),
                            contentDescription = program.programName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .let { if (showDetails) it.blur(10.dp) else it }
                                .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White, Color.Transparent)
                                        ),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                    )
                                }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SecondaryBg)
                                .let { if (showDetails) it.blur(10.dp) else it }
                        )
                    }

                    if (!showDetails) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (program.startTime.isNotEmpty()) program.startTime else "DIRECT",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = program.programName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (program.day.isNotEmpty()) {
                                Text(
                                    text = program.day,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (showDetails) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = true,
                                    onClick = {}
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showDetails = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fermer",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (targetChannel != null) {
                                            onChannelSelect(targetChannel)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Ouvrir",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 12.dp, end = 12.dp, top = 42.dp, bottom = 12.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = if (program.description.isNotEmpty()) program.description else "Aucune description fournie.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
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
fun ChannelListRow(
    channel: LiveChannel,
    isFavorite: Boolean,
    onChannelSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    isPremiumUser: Boolean = true,
    onLockedClick: () -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isLocked = channel.isPaid && !isPremiumUser

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable {
                if (isLocked) {
                    onLockedClick()
                } else {
                    onChannelSelect()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isLocked) 0.35f else 1.0f)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(160.dp)
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    AdaptiveAsyncImage(
                        imageUrl = channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        initials = channel.initials,
                        fallbackTextSize = 24.sp, imageType = ImageType.CHANNEL_LOGO
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = channel.initials,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    surfaceColor,
                                    surfaceColor.copy(alpha = 0.85f),
                                    surfaceColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    val displayName = if (channel.name.length > 18) {
                        channel.name.take(18) + " . . ."
                    } else {
                        channel.name
                    }
                    val displayText = if (channel.channelNumber != null) {
                        "${channel.channelNumber} - $displayName"
                    } else {
                        displayName
                    }
                    Text(
                        text = displayText,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DynamicCategoryTicker(
                            categoryString = channel.category,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (channel.country.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = channel.country,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Chaîne Premium Verrouillée",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun DynamicCategoryTicker(
    categoryString: String,
    modifier: Modifier = Modifier,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    val categories = remember(categoryString) {
        categoryString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    if (categories.isEmpty()) {
        Text(
            text = "Chaîne TV",
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }
    
    if (categories.size == 1) {
        Text(
            text = categories[0],
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }
    
    var activeIndex by remember(categoryString) { mutableStateOf(0) }
    
    LaunchedEffect(categories) {
        while (true) {
            kotlinx.coroutines.delay(4000L)
            activeIndex = (activeIndex + 1) % categories.size
        }
    }
    
    AnimatedContent(
        targetState = activeIndex,
        transitionSpec = {
            (slideInHorizontally { width -> width } + fadeIn())
                .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
        },
        label = "categoryTicker",
        modifier = modifier
    ) { targetIndex ->
        Text(
            text = categories[targetIndex],
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}


