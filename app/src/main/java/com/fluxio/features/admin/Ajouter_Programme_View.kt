package com.fluxio.features.admin
 
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.components.CustomImageImportIcon
import com.fluxio.shared.models.LiveChannel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.horizontalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjouterProgrammeView(
    allChannels: List<LiveChannel>,
    onBack: () -> Unit,
    onRefreshChannels: () -> Unit = {},
    programToEdit: CustomProgram? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val todayStr = remember {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        sdf.format(cal.time)
    }

    var selectedChannelIds by remember(programToEdit) {
        mutableStateOf(
            programToEdit?.channelId?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        )
    }

    var isPaid by remember(programToEdit) {
        mutableStateOf(
            if (programToEdit != null) {
                val ids = programToEdit.channelId.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                allChannels.any { it.id in ids && it.isPaid }
            } else {
                false
            }
        )
    }

    LaunchedEffect(selectedChannelIds, allChannels) {
        if (selectedChannelIds.isNotEmpty()) {
            val hasPaidChannel = selectedChannelIds.any { id ->
                allChannels.find { it.id == id }?.isPaid == true
            }
            if (hasPaidChannel) {
                isPaid = true
            }
        }
    }

    var programName by remember { mutableStateOf(programToEdit?.programName ?: "") }
    var startTime by remember { mutableStateOf(programToEdit?.startTime ?: "") }
    var endTime by remember { mutableStateOf(programToEdit?.endTime ?: "") }

    val initialStartDate = remember(programToEdit) {
        val day = programToEdit?.day ?: ""
        if (day.startsWith("Du ") && day.contains(" au ")) {
            day.removePrefix("Du ").split(" au ").getOrNull(0)?.trim() ?: todayStr
        } else {
            day.ifEmpty { todayStr }
        }
    }
    val initialEndDate = remember(programToEdit) {
        val day = programToEdit?.day ?: ""
        if (day.startsWith("Du ") && day.contains(" au ")) {
            day.removePrefix("Du ").split(" au ").getOrNull(1)?.trim() ?: todayStr
        } else {
            day.ifEmpty { todayStr }
        }
    }

    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }
    var description by remember { mutableStateOf(programToEdit?.description ?: "") }
    var imageUrl by remember { mutableStateOf(programToEdit?.imageUrl ?: "") }
    var isUploadingImage by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            isUploadingImage = true
            scope.launch {
                val cloudUrl = AdminUtils.uploadImageToStorage(context, it, "programs")
                isUploadingImage = false
                if (cloudUrl != null) {
                    imageUrl = cloudUrl
                    Toast.makeText(context, "Image enregistrée dans le cloud avec succès !", Toast.LENGTH_SHORT).show()
                } else {
                    val localPath = AdminUtils.copyUriToInternalStorage(context, it)
                    if (localPath != null) {
                        imageUrl = localPath
                        Toast.makeText(context, "Stocké localement (pour le Cloud, configurez ImgBB dans Paramètres).", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Erreur lors de l'importation de l'image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var expandedChannel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChannels = remember(allChannels, searchQuery) {
        if (searchQuery.isEmpty()) {
            allChannels
        } else {
            allChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    fun showDatePicker(initialDate: String, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        if (initialDate.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                val parsedDate = sdf.parse(initialDate)
                if (parsedDate != null) {
                    calendar.time = parsedDate
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        DatePickerDialog(
            context,
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                onDateSelected(sdf.format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            context,
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, hourOfDay, minute ->
                val formattedTime = String.format("%02d:%02d", hourOfDay, minute)
                onTimeSelected(formattedTime)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
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
                text = if (programToEdit != null) "Édition de programme" else "Ajouter un programme",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = programName,
                    onValueChange = { programName = it },
                    label = { Text("Nom de l'événement ou du programme", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (allChannels.isEmpty()) {
                    Text(
                        text = "Veuillez importer des chaînes pour pouvoir leur ajouter des programmes.",
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expandedChannel,
                        onExpandedChange = { expandedChannel = !expandedChannel }
                    ) {
                        val displayValue = if (selectedChannelIds.isNotEmpty() && searchQuery.isEmpty()) " " else searchQuery
                        OutlinedTextField(
                            value = displayValue,
                            onValueChange = { newVal ->
                                searchQuery = if (newVal.startsWith(" ")) {
                                    newVal.substring(1)
                                } else {
                                    newVal
                                }
                                expandedChannel = true
                            },
                            label = { Text("Chaîne concernée", color = Color.Gray) },
                            placeholder = {
                                if (selectedChannelIds.isEmpty()) {
                                    Text("Rechercher des chaînes...", color = Color.Gray.copy(alpha = 0.6f))
                                }
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedChannel) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedChannel,
                            onDismissRequest = { expandedChannel = false },
                            modifier = Modifier.background(Color(0xFF1E1E1E))
                        ) {
                            if (filteredChannels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Aucune chaîne trouvée", color = Color.Gray) },
                                    onClick = {}
                                )
                            } else {
                                filteredChannels.forEach { ch ->
                                    val isAlreadySelected = selectedChannelIds.contains(ch.id)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(ch.name, color = if (isAlreadySelected) Color.Gray else Color.White)
                                                if (isAlreadySelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Sélectionné",
                                                        tint = Color(0xFFE50914),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            if (isAlreadySelected) {
                                                selectedChannelIds = selectedChannelIds.filter { it != ch.id }
                                            } else {
                                                selectedChannelIds = selectedChannelIds + ch.id
                                            }
                                            searchQuery = ""
                                            expandedChannel = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedChannelIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chaînes sélectionnées (${selectedChannelIds.size}) :",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            selectedChannelIds.forEach { chId ->
                                val ch = allChannels.find { it.id == chId }
                                val chName = ch?.name ?: if (chId.isNotEmpty()) {
                                    chId.removePrefix("custom_").replace("_", " ")
                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRANCE) else it.toString() }
                                } else ""

                                if (chName.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(start = 12.dp)
                                    ) {
                                        Text(
                                            text = chName,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clickable {
                                                    selectedChannelIds = selectedChannelIds.filter { it != chId }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Supprimer",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedChannelIds.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Type d'accès de la chaîne sélectionnée",
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
                }

                OutlinedTextField(
                    value = startDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date de début", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showDatePicker(startDate) { startDate = it }
                                focusManager.clearFocus()
                            }
                        }
                )

                OutlinedTextField(
                    value = startTime,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Heure de début", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showTimePicker { startTime = it }
                                focusManager.clearFocus()
                            }
                        }
                )

                OutlinedTextField(
                    value = endDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date de fin", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showDatePicker(endDate) { endDate = it }
                                focusManager.clearFocus()
                            }
                        }
                )

                OutlinedTextField(
                    value = endTime,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Heure de fin", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showTimePicker { endTime = it }
                                focusManager.clearFocus()
                            }
                        }
                )

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Lien de l'image (URL ou importé)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    trailingIcon = {
                        if (isUploadingImage) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
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
                    modifier = Modifier.fillMaxWidth()
                )

                if (imageUrl.isNotEmpty()) {
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
                            val imgData = if (imageUrl.startsWith("/")) File(imageUrl) else imageUrl.trim()
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imgData)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Aperçu de l'image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aperçu de l'image",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { imageUrl = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Supprimer l'image",
                                tint = Color.Gray
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(150) },
                    label = { Text("Description du programme", color = Color.Gray) },
                    minLines = 3,
                    supportingText = {
                        Text(
                            text = "${description.length} / 150",
                            color = if (description.length == 150) Color.Red else Color.Gray,
                            fontSize = 11.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (programToEdit != null) {
                    Button(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Supprimer le programme",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        if (selectedChannelIds.isEmpty()) {
                            Toast.makeText(context, "Veuillez sélectionner au moins une chaîne.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (programName.trim().isEmpty() || startTime.trim().isEmpty() || endTime.trim().isEmpty()) {
                            Toast.makeText(context, "Le nom, l'heure de début et de fin sont requis.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val dayValue = if (startDate == endDate) startDate else "Du $startDate au $endDate"
                        val channelIdValue = selectedChannelIds.joinToString(",")

                        scope.launch {
                            selectedChannelIds.forEach { chId ->
                                AdminUtils.updateChannelPaidStatus(context, chId, isPaid)
                            }
                            AdminUtils.saveCustomProgram(
                                context = context,
                                channelId = channelIdValue,
                                programName = programName.trim(),
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                day = dayValue,
                                description = description.trim(),
                                imageUrl = imageUrl.trim(),
                                id = programToEdit?.id ?: 0L
                            )
                            onRefreshChannels()
                            Toast.makeText(context, if (programToEdit != null) "Modifications enregistrées avec succès !" else "Programme '${programName.trim()}' enregistré avec succès !", Toast.LENGTH_SHORT).show()
                            onBack()
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
                        text = if (programToEdit != null) "Enregistrer les modifications" else "Publier le programme",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation && programToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = "Supprimer le programme",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Voulez-vous vraiment supprimer le programme '${programToEdit.programName}' ?",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            containerColor = Color(0xFF161616),
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                AdminUtils.deleteCustomProgram(
                                    context = context,
                                    channelId = programToEdit.channelId,
                                    programName = programToEdit.programName,
                                    startTime = programToEdit.startTime,
                                    day = programToEdit.day,
                                    id = programToEdit.id
                                )
                                onRefreshChannels()
                                Toast.makeText(context, "Programme supprimé avec succès !", Toast.LENGTH_SHORT).show()
                                showDeleteConfirmation = false
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Supprimer", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { showDeleteConfirmation = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Annuler", color = Color.White)
                    }
                }
            }
        )
    }
}
