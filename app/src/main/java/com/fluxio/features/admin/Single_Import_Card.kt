package com.fluxio.features.admin

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import com.fluxio.features.settings.StreamResolutionInfo
import com.fluxio.features.settings.extractStreamInfo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fluxio.shared.components.CustomImageImportIcon
import com.fluxio.shared.models.LiveChannel
import com.fluxio.features.player.VideoPlayerView
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelEditImportCard(
    name: String,
    onNameChange: (String) -> Unit,
    streamUrl: String,
    onStreamUrlChange: (String) -> Unit,
    logoUrl: String,
    onLogoUrlChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    country: String = "France",
    onCountryChange: (String) -> Unit = {},
    isVerificationStream: Boolean,
    onVerificationStreamChange: (Boolean) -> Unit,
    isVerificationLogo: Boolean,
    onVerificationLogoChange: (Boolean) -> Unit,
    categories: List<String>,
    allChannels: List<LiveChannel> = emptyList(),
    onCancel: () -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    var expandedCategory by remember { mutableStateOf(false) }
    var isPaid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isUploadingLogo by remember { mutableStateOf(false) }
    var duplicatePromptChannel by remember { mutableStateOf<LiveChannel?>(null) }

    val saveChannelAction: (String?) -> Unit = { existingTargetId ->
        val cleanLogoUrl = logoUrl.trim()
        val targetId = existingTargetId ?: ("custom_" + UUID.randomUUID().toString().take(8))
        val channel = LiveChannel(
            id = targetId,
            name = name.trim(),
            url = streamUrl.trim(),
            category = category,
            logoText = name.trim().take(2).uppercase(),
            description = description.trim(),
            logoUrl = cleanLogoUrl,
            country = country.ifBlank { "France" },
            isPaid = isPaid
        )
        AdminUtils.saveCustomChannel(context, channel)
        AdminUtils.saveChannelOverride(
            context = context,
            id = targetId,
            name = name.trim(),
            url = streamUrl.trim(),
            category = category,
            logoUrl = cleanLogoUrl,
            country = country.ifBlank { "France" },
            description = description.trim(),
            logoText = name.trim().take(2).uppercase(),
            isPaid = isPaid
        )
        Toast.makeText(context, if (existingTargetId != null) "Chaîne remplacée avec succès !" else "Chaîne '${name.trim()}' importée avec succès !", Toast.LENGTH_SHORT).show()
        onImportSuccess()
    }

    var streamTrackList by remember(streamUrl) { mutableStateOf<List<StreamResolutionInfo>?>(null) }
    var isLoadingStreamInfo by remember(streamUrl) { mutableStateOf(false) }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotBlank()) {
            isLoadingStreamInfo = true
            streamTrackList = extractStreamInfo(streamUrl)
            isLoadingStreamInfo = false
        } else {
            streamTrackList = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploadingLogo = true
            scope.launch {
                val cloudUrl = AdminUtils.uploadImageToStorage(context, it, "logos")
                isUploadingLogo = false
                if (cloudUrl != null) {
                    onLogoUrlChange(cloudUrl)
                    onVerificationLogoChange(true)
                    Toast.makeText(context, "Logo téléversé dans le cloud avec succès !", Toast.LENGTH_SHORT).show()
                } else {
                    val localPath = AdminUtils.copyUriToInternalStorage(context, it)
                    if (localPath != null) {
                        onLogoUrlChange(localPath)
                        onVerificationLogoChange(true)
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Retour à la liste",
                    tint = Color.White
                )
            }
            Text(
                text = "Configurer la chaîne",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nom de la chaîne", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { 
                        onStreamUrlChange(it) 
                        onVerificationStreamChange(false)
                    },
                    label = { Text("URL du flux (M3U8 / MP4)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isVerificationStream && streamUrl.trim().isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    ) {
                        VideoPlayerView(
                            url = streamUrl.trim(),
                            playbackSpeed = 1.0f,
                            isMuted = false,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { onVerificationStreamChange(false) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer le check",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (streamUrl.trim().isEmpty()) {
                                Toast.makeText(context, "Veuillez entrer une URL de flux d'abord", Toast.LENGTH_SHORT).show()
                            } else {
                                onVerificationStreamChange(true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Vérifier",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vérifier le flux de la chaîne", color = Color.White, fontSize = 13.sp)
                    }
                }

                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { 
                        onLogoUrlChange(it) 
                        if (it.isEmpty()) onVerificationLogoChange(false)
                    },
                    label = { Text("URL ou chemin du logo (optionnel)", color = Color.Gray) },
                    singleLine = true,
                    trailingIcon = {
                        if (isUploadingLogo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    imagePickerLauncher.launch("image/*")
                                }
                            ) {
                                CustomImageImportIcon(
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isVerificationLogo && logoUrl.trim().isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            val logoData = if (logoUrl.startsWith("/")) File(logoUrl) else logoUrl.trim()
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(logoData)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Aperçu du logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aperçu du logo",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Image chargée avec succès !",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { onVerificationLogoChange(false) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer l'aperçu",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                val selectedCats = remember(category) {
                    category.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
                
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
                                        onCategoryChange(updated.joinToString(","))
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
                                    .clickable { expandedCategory = true }
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
                                expanded = expandedCategory,
                                onDismissRequest = { expandedCategory = false },
                                modifier = Modifier
                                    .widthIn(min = 200.dp, max = 280.dp)
                                    .heightIn(max = 280.dp)
                                    .background(Color(0xFF1E1E1E))
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat, color = Color.White) },
                                        onClick = {
                                            expandedCategory = false
                                            val isAlreadySelected = selectedCats.any { it.equals(cat, ignoreCase = true) }
                                            if (isAlreadySelected) {
                                                Toast.makeText(context, "Cette catégorie est déjà sélectionnée.", Toast.LENGTH_SHORT).show()
                                            } else if (selectedCats.size >= 3) {
                                                Toast.makeText(context, "Vous pouvez sélectionner jusqu'à 3 catégories au maximum.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val updated = selectedCats + cat
                                                onCategoryChange(updated.joinToString(","))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

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
                                .background(if (!isPaid) Color.White.copy(alpha = 0.15f) else Color(0xFF1E1E1E))
                                .border(
                                    width = 1.5.dp,
                                    color = if (!isPaid) Color.White else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { isPaid = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Gratuit",
                                    color = if (!isPaid) Color.White else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tout public",
                                    color = if (!isPaid) Color.White.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPaid) Color.White.copy(alpha = 0.15f) else Color(0xFF1E1E1E))
                                .border(
                                    width = 1.5.dp,
                                    color = if (isPaid) Color.White else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { isPaid = true }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Payant",
                                    color = if (isPaid) Color.White else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Abonnement Premium",
                                    color = if (isPaid) Color.White.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = country,
                    onValueChange = { onCountryChange(it) },
                    label = { Text("Pays (ex: France)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { onDescriptionChange(it.take(1000)) },
                    label = { Text("Description (optionnel)", color = Color.Gray) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    supportingText = {
                        Text(
                            text = "${description.length} / 1000",
                            color = if (description.length >= 1000) Color.Red else Color.Gray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (streamUrl.isNotBlank()) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (name.trim().isEmpty() || streamUrl.trim().isEmpty()) {
                            Toast.makeText(context, "Le nom et l'URL du flux sont obligatoires.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val existingMatch = allChannels.firstOrNull { chan ->
                            chan.name.trim().equals(name.trim(), ignoreCase = true) ||
                            (streamUrl.trim().isNotBlank() && chan.url.trim().equals(streamUrl.trim(), ignoreCase = true))
                        }

                        if (existingMatch != null) {
                            duplicatePromptChannel = existingMatch
                        } else {
                            saveChannelAction(null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Importer la chaîne",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    if (duplicatePromptChannel != null) {
        AlertDialog(
            onDismissRequest = { duplicatePromptChannel = null },
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
                    text = "La chaîne '${duplicatePromptChannel!!.name}' existe déjà dans l'application. Voulez-vous la remplacer ?",
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
                            val existingId = duplicatePromptChannel!!.id
                            duplicatePromptChannel = null
                            saveChannelAction(existingId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remplacer la chaîne existante", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { duplicatePromptChannel = null },
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
