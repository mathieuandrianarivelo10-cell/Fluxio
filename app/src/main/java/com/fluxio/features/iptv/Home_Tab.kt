package com.fluxio.features.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Notifications
import com.fluxio.shared.models.ChannelLoadState
import com.fluxio.shared.models.LiveChannel
import com.fluxio.shared.components.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FilterList


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    channels: List<LiveChannel>,
    favoriteSet: Set<String>,
    selectedChannel: LiveChannel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCountry: String,
    onCountryChange: (String) -> Unit,
    selectedGenre: String,
    onGenreChange: (String) -> Unit,
    onChannelSelect: (LiveChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBackClick: () -> Unit,
    channelLoadState: ChannelLoadState,
    onRetryLoad: () -> Unit,
    isPremiumUser: Boolean = true,
    onLockedClick: () -> Unit = {},
    unreadCount: Int = 0,
    onNotificationClick: () -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRetryLoad()
            kotlinx.coroutines.delay(1500)
            pullToRefreshState.endRefresh()
        }
    }

    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var selectedDetailsChannel by remember { mutableStateOf<LiveChannel?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        when (channelLoadState) {
                is ChannelLoadState.Loading -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Search box skeleton
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .shimmerEffect()
                            )
                        }
                        // Categories skeleton
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                repeat(5) {
                                    Box(
                                        modifier = Modifier
                                            .width(80.dp)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .shimmerEffect()
                                    )
                                }
                            }
                        }
                        // Header skeleton
                        item {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
                            )
                        }
                        // Channel rows skeleton
                        items(6) {
                            EmptyChannelPlaceholder()
                        }
                    }
                }
                is ChannelLoadState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(50.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(channelLoadState.message, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRetryLoad,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }
                }
                is ChannelLoadState.Success -> {
                    val activeChannels = channels

                    val countries = remember(activeChannels) {
                        val distinct = activeChannels.map { it.country }.distinct().filter { it.isNotEmpty() }.sorted()
                        listOf("Tous") + distinct
                    }

                    val categories = remember(activeChannels) {
                        val distinct = activeChannels.flatMap { it.category.split(",") }.map { it.trim() }.distinct().filter { it.isNotEmpty() && !COMMON_CATEGORIES.contains(it) }.sorted()
                        listOf("Tous") + COMMON_CATEGORIES + distinct
                    }

                    val filteredChannels = remember(searchQuery, activeChannels, selectedGenre, selectedCountry) {
                        val query = searchQuery.normalize()
                        activeChannels.filter { channel ->
                            val matchesQuery = query.isEmpty() ||
                                    channel.name.normalize().contains(query) ||
                                    channel.description.normalize().contains(query)
                            val matchesGenre = selectedGenre == "Tous" ||
                                    channel.category.split(",").map { it.trim().lowercase() }.contains(selectedGenre.lowercase().trim())
                            val matchesCountry = selectedCountry == "Tous" ||
                                    channel.country.lowercase().trim() == selectedCountry.lowercase().trim()
                            matchesQuery && matchesGenre && matchesCountry
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Rechercher une chaîne...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onBackground)
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_field")
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CategorySelector(
                                        categories = categories,
                                        selectedCategory = selectedGenre,
                                        onCategorySelected = onGenreChange
                                    )
                                }
                                IconButton(
                                    onClick = { showCategorySheet = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = "Toutes les catégories",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Toutes les Chaînes (${filteredChannels.size})",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (filteredChannels.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucune chaîne disponible actuellement",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(filteredChannels, key = { it.id }) { channel ->
                                ChannelListRow(
                                    channel = channel,
                                    isFavorite = favoriteSet.contains(channel.id),
                                    onChannelSelect = { selectedDetailsChannel = channel },
                                    onToggleFavorite = { onToggleFavorite(channel.id) },
                                    isPremiumUser = isPremiumUser,
                                    onLockedClick = onLockedClick
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    if (showCategorySheet) {
                        CategoryBottomSheet(
                            categories = categories,
                            selectedCategory = selectedGenre,
                            activeChannels = channels,
                            onCategorySelected = {
                                onGenreChange(it)
                                showCategorySheet = false
                            },
                            onDismiss = { showCategorySheet = false }
                        )
                    }

                    selectedDetailsChannel?.let { channel ->
                        ChannelDetailBottomSheet(
                            channel = channel,
                            isFavorite = favoriteSet.contains(channel.id),
                            onToggleFavorite = { onToggleFavorite(channel.id) },
                            onPlayClick = {
                                onChannelSelect(channel)
                                selectedDetailsChannel = null
                            },
                            isPremiumUser = isPremiumUser,
                            onLockedClick = onLockedClick,
                            onDismiss = { selectedDetailsChannel = null }
                        )
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

@Composable
fun ChannelGridCard(
    channel: LiveChannel,
    isFavorite: Boolean,
    onChannelSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background == Color(0xFFF8F9FA) || MaterialTheme.colorScheme.background == Color(0xFFFFFFFF) || MaterialTheme.colorScheme.background == Color.White
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = if (isLightTheme) CardDefaults.cardElevation(defaultElevation = 3.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (isLightTheme) it
                else it.border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            }
            .clickable { onChannelSelect() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
            ) {
                com.fluxio.shared.components.AdaptiveAsyncImage(
                    imageUrl = channel.getBackdropUrl(),
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    initials = channel.initials,
                    fallbackTextSize = 24.sp, imageType = com.fluxio.shared.components.ImageType.PROGRAM_BANNER
                )
                
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onToggleFavorite() }
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color(0xFFFFD600) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = channel.country,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Column(modifier = Modifier.padding(10.dp)) {
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.height(2.dp))
                com.fluxio.shared.components.DynamicCategoryTicker(
                    categoryString = channel.category,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun GenreListRow(
    genreName: String,
    channels: List<LiveChannel>,
    onClick: () -> Unit
) {
    val cleanName = remember(genreName) { genreName.lowercase().trim() }

    val (title, subtitle, icon) = remember(cleanName) {
        when (cleanName) {
            "information continue" -> Triple("Information continue", "Informations en continu", Icons.Outlined.Newspaper)
            "sport" -> Triple("Sport", "Tous les évènements en direct", Icons.Outlined.SportsSoccer)
            "cinéma / séries" -> Triple("Cinéma / Séries", "Les meilleurs films 24/7", Icons.Outlined.Movie)
            "patrimoine / archives" -> Triple("Patrimoine / Archives", "Archives et documentaires historiques", Icons.Outlined.AccountBalance)
            "jeunesse" -> Triple("Jeunesse", "Des programmes pour toute la famille", Icons.Outlined.Toys)
            "documentaires / découverte" -> Triple("Documentaires / Découverte", "Documentaires et reportages", Icons.Outlined.TravelExplore)
            "musicale / clips" -> Triple("Musicale / Clips", "Clips musicaux et concerts", Icons.Outlined.MusicNote)
            "divertissement / humour" -> Triple("Divertissement / Humour", "Humour et émissions variées", Icons.Outlined.SentimentVerySatisfied)
            "cuisine / gastronomie" -> Triple("Cuisine / Gastronomie", "Gastronomie et art culinaire", Icons.Outlined.Restaurant)
            "féminine / lifestyle" -> Triple("Féminine / Lifestyle", "Mode, beauté et bien-être", Icons.Outlined.Spa)
            "éducative / culturelle" -> Triple("Éducative / Culturelle", "Programmes culturels et éducatifs", Icons.Outlined.School)
            "religieuse / spiritualité" -> Triple("Religieuse / Spiritualité", "Émissions religieuses et spirituelles", Icons.Outlined.Church)
            "parlementaire / politique" -> Triple("Parlementaire / Politique", "Actualité parlementaire et débats", Icons.Outlined.Gavel)
            "météo" -> Triple("Météo", "Prévisions météorologiques en temps réel", Icons.Outlined.WbCloudy)
            "télé-achat / shopping" -> Triple("Télé-achat / Shopping", "Télé-achat et bonnes affaires", Icons.Outlined.ShoppingCart)
            "jeux vidéo / high-tech" -> Triple("Jeux vidéo / High-tech", "Jeux vidéo et nouvelles technologies", Icons.Outlined.SportsEsports)
            "animaux / nature" -> Triple("Animaux / Nature", "Animaux, faune et flore sauvage", Icons.Outlined.Pets)
            else -> Triple(genreName, "Parcourir la catégorie", Icons.Outlined.Tv)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121214))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFE50914),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
