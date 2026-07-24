package com.fluxio.features.admin

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fluxio.shared.models.LiveChannel
import com.fluxio.features.iptv.COMMON_CATEGORIES
import com.fluxio.features.settings.StreamResolutionInfo
import com.fluxio.features.settings.extractStreamInfo
import com.fluxio.features.settings.getFallbackStreamInfo
import com.fluxio.shared.theme.*
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.fluxio.shared.components.CustomImageImportIcon
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun GererChainesView(
    allChannels: List<LiveChannel>,
    adminFeaturedChannelIds: Set<String>,
    adminPublishedChannelIds: Set<String>,
    onPublishCatalogs: (Set<String>, Set<String>) -> Unit,
    onBack: () -> Unit,
    onRefreshChannels: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE) }
    
    var deletedChannelIds by remember {
        mutableStateOf(sharedPrefs.getStringSet("deleted_channel_ids", emptySet()) ?: emptySet())
    }
    var searchQuery by remember { mutableStateOf("") }
    var editingChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var channelToDelete by remember { mutableStateOf<LiveChannel?>(null) }
    var duplicatePromptData by remember { mutableStateOf<DuplicatePromptData?>(null) }

    var selectedChannelListTab by remember { mutableStateOf("operational") }

    val filteredChannels = remember(allChannels, searchQuery, deletedChannelIds) {
        val q = searchQuery.lowercase().trim()
        val baseList = allChannels.filter { it.id != "horizon_welcome" && !deletedChannelIds.contains(it.id) }
        if (q.isEmpty()) {
            baseList
        } else {
            baseList.filter {
                it.name.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
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
        label = "EditingChannelTransition",
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
        var editIsPublished by remember(channelToEdit.id) { mutableStateOf(adminPublishedChannelIds.contains(channelToEdit.id)) }
        var editIsFeatured by remember(channelToEdit.id) { mutableStateOf(adminFeaturedChannelIds.contains(channelToEdit.id)) }
        var editIsPaid by remember(channelToEdit.id) { mutableStateOf(channelToEdit.isPaid) }
        var isUploadingLogo by remember(channelToEdit.id) { mutableStateOf(false) }

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
                                onClick = {
                                    logoPickerLauncher.launch("image/*")
                                }
                            ) {
                                CustomImageImportIcon(
                                    tint = Color.White
                                )
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

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val performSaveAction: (String) -> Unit = { targetId ->
                                AdminUtils.saveChannelOverride(
                                    context = context,
                                    id = targetId,
                                    name = editName,
                                    url = editUrl,
                                    category = editCategory,
                                    logoUrl = editLogoUrl,
                                    country = editCountry,
                                    description = editDescription,
                                    logoText = editLogoText,
                                    isPaid = editIsPaid
                                )
                                
                                if (targetId.startsWith("custom_") || channelToEdit.id.startsWith("custom_")) {
                                    val updatedChannel = LiveChannel(
                                        id = targetId,
                                        name = editName,
                                        url = editUrl,
                                        category = editCategory,
                                        logoUrl = editLogoUrl,
                                        country = editCountry,
                                        description = editDescription,
                                        logoText = editLogoText,
                                        isPaid = editIsPaid
                                    )
                                    AdminUtils.updateCustomChannelInFirebase(updatedChannel)
                                }
                                AdminUtils.updateChannelPaidStatus(context, targetId, editIsPaid)
                                
                                val updatedPublished = if (editIsPublished) {
                                    adminPublishedChannelIds + targetId
                                } else {
                                    adminPublishedChannelIds - targetId
                                }
                                val updatedFeatured = if (editIsFeatured) {
                                    adminFeaturedChannelIds + targetId
                                } else {
                                    adminFeaturedChannelIds - targetId
                                }
                                onPublishCatalogs(updatedFeatured, updatedPublished)

                                editingChannel = null
                                onRefreshChannels()
                                Toast.makeText(context, "Chaîne enregistrée avec succès !", Toast.LENGTH_SHORT).show()
                            }

                            val duplicateChannel = allChannels.firstOrNull { existing ->
                                existing.id != channelToEdit.id &&
                                !deletedChannelIds.contains(existing.id) &&
                                (
                                    existing.name.trim().equals(editName.trim(), ignoreCase = true) ||
                                    (editUrl.isNotBlank() && existing.url.trim().equals(editUrl.trim(), ignoreCase = true))
                                )
                            }

                            if (duplicateChannel != null) {
                                duplicatePromptData = DuplicatePromptData(
                                    existingChannel = duplicateChannel,
                                    onReplace = {
                                        performSaveAction(duplicateChannel.id)
                                        duplicatePromptData = null
                                    }
                                )
                            } else {
                                performSaveAction(channelToEdit.id)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { editingChannel = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Annuler", color = Color.White)
                    }
                }
            }
        }
    } else {
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
                    text = "Modifier & Gérer les chaînes",
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
                    .padding(vertical = 12.dp)
            ) {
                val operationalChannels = remember(filteredChannels) {
                    filteredChannels.filter { channel ->
                        channel.logoUrl.isNotBlank() && channel.url.isNotBlank()
                    }
                }
                val problematicChannels = remember(filteredChannels) {
                    filteredChannels.filter { channel ->
                        channel.logoUrl.isBlank() || channel.url.isBlank()
                    }
                }

                val freeChannels = remember(filteredChannels) {
                    filteredChannels.filter { !it.isPaid }
                }
                val paidChannels = remember(filteredChannels) {
                    filteredChannels.filter { it.isPaid }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Statut des chaînes",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Nombre de chaînes :",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                            val existingCount = allChannels.filter { it.id != "horizon_welcome" && !deletedChannelIds.contains(it.id) }.size
                            Text(
                                text = "$existingCount",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chaînes supprimées :",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${deletedChannelIds.size}",
                                color = Color(0xFFE50914),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher une chaîne...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.White)
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

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontally scrollable menu bar for 4 tabs
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
                        val isSelected = selectedChannelListTab == tabId || (selectedChannelListTab == "gauche" && tabId == "operational") || (selectedChannelListTab == "droite" && tabId == "problematic")
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

                Spacer(modifier = Modifier.height(12.dp))

                val (currentList, currentTitle, emptyMsg) = when (selectedChannelListTab) {
                    "problematic", "droite" -> Triple(problematicChannels, "Chaînes à problème (${problematicChannels.size})", "Aucune chaîne à problème.")
                    "free" -> Triple(freeChannels, "Chaînes gratuites (${freeChannels.size})", "Aucune chaîne gratuite.")
                    "paid" -> Triple(paidChannels, "Chaînes payantes (${paidChannels.size})", "Aucune chaîne payante.")
                    else -> Triple(operationalChannels, "Chaînes opérationnelles (${operationalChannels.size})", "Aucune chaîne opérationnelle.")
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = currentTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (currentList.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                        ) {
                            Text(
                                text = emptyMsg,
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        currentList.forEach { channel ->
                            ChannelAdminRow(
                                channel = channel,
                                onEdit = { editingChannel = channel },
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
                    text = "Supprimer la chaîne",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Voulez-vous vraiment supprimer la chaîne '${channelToDelete!!.name}' ?",
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
                            if (chan.id.startsWith("custom_")) {
                                AdminUtils.deleteCustomChannel(context, chan.id)
                            }
                            
                            val currentDeleted = sharedPrefs.getStringSet("deleted_channel_ids", emptySet()) ?: emptySet()
                            val updatedDeleted = currentDeleted + chan.id
                            sharedPrefs.edit().putStringSet("deleted_channel_ids", updatedDeleted).apply()
                            
                            val currentDeletedCount = sharedPrefs.getInt("deleted_channels_count", 0)
                            sharedPrefs.edit().putInt("deleted_channels_count", currentDeletedCount + 1).apply()
                            
                            val updatedSet = deletedChannelIds + chan.id
                            deletedChannelIds = updatedSet
                            channelToDelete = null
                            onRefreshChannels()
                            Toast.makeText(context, "Chaîne supprimée avec succès !", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Supprimer", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { channelToDelete = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Annuler", color = Color.White)
                    }
                }
            },
            dismissButton = null,
            containerColor = Color(0xFF1E1E1E)
        )
    }


    if (duplicatePromptData != null) {
        AlertDialog(
            onDismissRequest = { duplicatePromptData = null },
            title = {
                Text(
                    text = "Chaîne existante détectée",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "La chaîne '${duplicatePromptData!!.existingChannel.name}' existe déjà dans l'application. Voulez-vous la remplacer ?",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            duplicatePromptData!!.onReplace()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remplacer la chaîne existante", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { duplicatePromptData = null },
                        modifier = Modifier.fillMaxWidth()
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

data class DuplicatePromptData(
    val existingChannel: LiveChannel,
    val onReplace: () -> Unit
)
