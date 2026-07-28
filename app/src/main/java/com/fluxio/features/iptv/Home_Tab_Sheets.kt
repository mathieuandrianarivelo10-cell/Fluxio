package com.fluxio.features.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.models.LiveChannel
import com.fluxio.shared.components.AdaptiveAsyncImage
import com.fluxio.shared.components.ImageType

@Composable
fun CategoryBottomSheet(
    categories: List<String>,
    selectedCategory: String,
    activeChannels: List<LiveChannel>,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF121212))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable(enabled = false) {}
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
                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtrer par Catégorie",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.White
                        )
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
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = category.lowercase().trim() == selectedCategory.lowercase().trim()
                        val count = if (category == "Tous") {
                            activeChannels.size
                        } else {
                            activeChannels.count { it.category.split(",").map { c -> c.lowercase().trim() }.contains(category.lowercase().trim()) }
                        }
                        
                        val cleanName = category.lowercase().trim()
                        val (title, subtitle, icon) = when (cleanName) {
                            "tous" -> Triple("Toutes les chaînes", "Afficher l'intégralité du catalogue", Icons.Outlined.Tv)
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
                            else -> Triple(category, "Parcourir la catégorie", Icons.Outlined.Tv)
                        }

                        val itemModifier = if (isSelected) {
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { onCategorySelected(category) }
                                .padding(16.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1A1E))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onCategorySelected(category) }
                                .padding(16.dp)
                        }

                        Box(
                            modifier = itemModifier
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        color = if (isSelected) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$count ch.",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
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

@Composable
fun ChannelDetailBottomSheet(
    channel: LiveChannel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayClick: () -> Unit,
    isPremiumUser: Boolean,
    onLockedClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLocked = channel.isPaid && !isPremiumUser
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
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
                .clickable(enabled = false) {}
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
                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Détails de la Chaîne",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    ) {
                        AdaptiveAsyncImage(
                            imageUrl = if (channel.logoUrl.isNotEmpty()) channel.logoUrl else channel.getBackdropUrl(),
                            contentDescription = channel.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            initials = channel.initials,
                            fallbackTextSize = 36.sp,
                            imageType = ImageType.PROGRAM_BANNER
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (channel.country.isNotEmpty() && !channel.country.equals("Local", ignoreCase = true)) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = channel.country,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (channel.isPaid) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "PREMIUM",
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (channel.channelNumber != null) "${channel.channelNumber} - ${channel.name}" else channel.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "À PROPOS DE LA CHAÎNE",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = channel.description.ifEmpty { "Aucune description disponible pour cette chaîne." },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isLocked) {
                                    onLockedClick()
                                } else {
                                    onPlayClick()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLocked) Color(0xFFF59E0B) else Color(0xFFE50914)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isLocked) Color.Black else Color.White
                                )
                                Text(
                                    text = if (isLocked) "S'abonner pour Débloquer" else "Regarder la Chaîne",
                                    color = if (isLocked) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Button(
                            onClick = onToggleFavorite,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isFavorite) Color(0xFFFFD600) else Color.White
                                )
                                Text(
                                    text = if (isFavorite) "Retirer des Favoris" else "Ajouter aux Favoris",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
