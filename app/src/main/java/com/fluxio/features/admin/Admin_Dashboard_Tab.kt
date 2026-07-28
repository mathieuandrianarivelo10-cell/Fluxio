package com.fluxio.features.admin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.models.LiveChannel

data class AdminMenu(val title: String, val subScreenKey: String)
data class AdminCategory(val name: String, val menus: List<AdminMenu>)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdminDashboardTab(
    userEmail: String,
    allChannels: List<LiveChannel>,
    adminFeaturedChannelIds: Set<String>,
    adminPublishedChannelIds: Set<String>,
    onPublishCatalogs: (Set<String>, Set<String>) -> Unit,
    onLogoutClick: () -> Unit,
    onRefreshChannels: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSubScreenChange: ((String?) -> Unit)? = null,
    initialSubScreen: String? = null
) {
    var activeSubScreen by remember(initialSubScreen) {
        mutableStateOf<String?>(if (initialSubScreen == "Administration") null else initialSubScreen)
    }
    var programToEdit by remember { mutableStateOf<CustomProgram?>(null) }
    var expandedCategory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeSubScreen) {
        onSubScreenChange?.invoke(activeSubScreen)
    }

    androidx.activity.compose.BackHandler(enabled = activeSubScreen != null || onBack != null) {
        if (activeSubScreen != null) {
            if (activeSubScreen == "Ajouter un programme" && programToEdit != null) {
                activeSubScreen = "Gérer les programmes"
                programToEdit = null
            } else {
                activeSubScreen = null
            }
        } else {
            onBack?.invoke()
        }
    }

    val categories = remember {
        listOf(
            AdminCategory(
                name = "Gestion des Chaînes",
                menus = listOf(
                    AdminMenu("Importer une chaîne", "Importer une chaîne"),
                    AdminMenu("Gérer les chaînes", "Gérer les chaînes")
                )
            ),
            AdminCategory(
                name = "Gestion des Programmes",
                menus = listOf(
                    AdminMenu("Ajouter un programme", "Ajouter un programme"),
                    AdminMenu("Gérer les programmes", "Gérer les programmes")
                )
            ),
            AdminCategory(
                name = "Analyse & Support",
                menus = listOf(
                    AdminMenu("Statistiques de visionnage", "Statistiques de visionnage"),
                    AdminMenu("Signalements & Support", "Signalements & Support"),
                    AdminMenu("Gestion des utilisateurs", "Gérer les utilisateurs")
                )
            ),
            AdminCategory(
                name = "Mises à jour",
                menus = listOf(
                    AdminMenu("Importer les mises à jour", "Importer les mises à jour")
                )
            ),
            AdminCategory(
                name = "Configuration Cloud",
                menus = listOf(
                    AdminMenu("Configuration ImgBB", "Configuration ImgBB")
                )
            )
        )
    }

    AnimatedContent(
        targetState = activeSubScreen,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                ) with slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                )
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                ) with slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                )
            }
        },
        label = "AdminNavigation"
    ) { screen ->
        if (screen == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Retour",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Administration",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { activeSubScreen = "Notifications" },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    categories.forEach { category ->
                        val isExpanded = expandedCategory == category.name
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (isExpanded) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded) Color(0xFF22242B) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCategory = if (isExpanded) null else category.name
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = category.name,
                                        color = if (isExpanded) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Réduire" else "Développer",
                                        tint = if (isExpanded) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 8.dp),
                                            color = if (isExpanded) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                        category.menus.forEach { menu ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { activeSubScreen = menu.subScreenKey }
                                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = menu.title,
                                                    color = if (isExpanded) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = "Ouvrir",
                                                    tint = if (isExpanded) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (screen) {
                        "Importer une chaîne" -> {
                            ImporterChaineView(
                                allChannels = allChannels,
                                adminFeaturedChannelIds = adminFeaturedChannelIds,
                                adminPublishedChannelIds = adminPublishedChannelIds,
                                onPublishCatalogs = onPublishCatalogs,
                                onBack = { activeSubScreen = null },
                                onRefreshChannels = onRefreshChannels
                            )
                        }
                        "Statistiques de visionnage" -> {
                            StatistiquesVisionnageView(
                                allChannels = allChannels,
                                onBack = { activeSubScreen = null }
                            )
                        }
                        "Ajouter un programme" -> {
                            AjouterProgrammeView(
                                allChannels = allChannels,
                                onBack = {
                                    if (programToEdit != null) {
                                        activeSubScreen = "Gérer les programmes"
                                    } else {
                                        activeSubScreen = null
                                    }
                                    programToEdit = null
                                },
                                onRefreshChannels = onRefreshChannels,
                                programToEdit = programToEdit
                            )
                        }
                        "Gérer les chaînes" -> {
                            GererChainesView(
                                allChannels = allChannels,
                                adminFeaturedChannelIds = adminFeaturedChannelIds,
                                adminPublishedChannelIds = adminPublishedChannelIds,
                                onPublishCatalogs = onPublishCatalogs,
                                onBack = { activeSubScreen = null },
                                onRefreshChannels = onRefreshChannels
                            )
                        }
                        "Signalements & Support" -> {
                            SignalementsSupportView(
                                onBack = { activeSubScreen = null }
                            )
                        }
                        "Gérer les utilisateurs" -> {
                            GererUtilisateursView(
                                onBack = { activeSubScreen = null }
                            )
                        }
                        "Gérer les programmes" -> {
                            GererProgrammesView(
                                allChannels = allChannels,
                                onBack = { activeSubScreen = null },
                                onRefreshChannels = onRefreshChannels,
                                onEditProgram = { program ->
                                    programToEdit = program
                                    activeSubScreen = "Ajouter un programme"
                                }
                            )
                        }
                        "Importer les mises à jour" -> {
                            ImporterMisesAJourView(
                                onBack = { activeSubScreen = null }
                            )
                        }
                        "Configuration ImgBB" -> {
                            ConfigurationImgBBView(
                                onBack = { activeSubScreen = null }
                            )
                        }
                        "Notifications" -> {
                            NotificationsAbonnementView(
                                onBack = { activeSubScreen = null }
                            )
                        }
                    }
                }
            }
        }
    }
}
