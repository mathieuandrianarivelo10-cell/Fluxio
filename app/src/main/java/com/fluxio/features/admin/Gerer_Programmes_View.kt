package com.fluxio.features.admin

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.models.LiveChannel
import com.fluxio.core.database.DatabaseRepository
import com.fluxio.core.database.CustomProgramEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

@Composable
fun GererProgrammesView(
    allChannels: List<LiveChannel>,
    onBack: () -> Unit,
    onRefreshChannels: () -> Unit = {},
    onEditProgram: (CustomProgram) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val repo = remember { DatabaseRepository(context) }
    val programsEntityList by repo.getAllPrograms().collectAsState(initial = emptyList())

    val programsList = remember(programsEntityList) {
        programsEntityList.map {
            CustomProgram(
                channelId = it.channelId,
                programName = it.programName,
                startTime = it.startTime,
                endTime = it.endTime,
                day = it.day,
                description = it.description,
                imageUrl = it.imageUrl,
                id = it.id
            )
        }
    }

    val channelMap = remember(allChannels) { allChannels.associateBy { it.id } }

    val now = remember { System.currentTimeMillis() }

    val classifiedPrograms = remember(programsList, now) {
        val ongoing = mutableListOf<CustomProgram>()
        val upcoming = mutableListOf<CustomProgram>()
        val past = mutableListOf<CustomProgram>()

        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

        for (program in programsList) {
            try {
                val dayStr = program.day.trim()
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

                val startTimeStr = program.startTime.trim()
                val endTimeStr = program.endTime.trim()

                val parsedStart = sdfDateTime.parse("$startStr $startTimeStr")
                val parsedEnd = sdfDateTime.parse("$endStr $endTimeStr")

                if (parsedStart != null && parsedEnd != null) {
                    val startEpoch = parsedStart.time
                    val endEpoch = parsedEnd.time

                    if (now in startEpoch..endEpoch) {
                        ongoing.add(program)
                    } else if (now < startEpoch) {
                        upcoming.add(program)
                    } else {
                        past.add(program)
                    }
                } else {
                    ongoing.add(program)
                }
            } catch (e: Exception) {
                ongoing.add(program)
            }
        }
        Triple(ongoing, upcoming, past)
    }

    val ongoingPrograms = classifiedPrograms.first
    val upcomingPrograms = classifiedPrograms.second
    val pastPrograms = classifiedPrograms.third

    var selectedTabIndex by remember { mutableStateOf(0) }
    var hasSetInitialTab by remember { mutableStateOf(false) }

    LaunchedEffect(ongoingPrograms, upcomingPrograms) {
        if (!hasSetInitialTab && (ongoingPrograms.isNotEmpty() || upcomingPrograms.isNotEmpty())) {
            selectedTabIndex = if (ongoingPrograms.isNotEmpty()) 0 else 1
            hasSetInitialTab = true
        }
    }

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
                text = "Gérer les programmes",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (programsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun programme enregistré",
                    color = Color.Gray,
                    fontSize = 15.sp
                )
            }
        } else {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFE50914),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "En cours (${ongoingPrograms.size})",
                            color = if (selectedTabIndex == 0) Color(0xFFE50914) else Color.Gray,
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "À venir (${upcomingPrograms.size})",
                            color = if (selectedTabIndex == 1) Color(0xFFE50914) else Color.Gray,
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            text = "Passés (${pastPrograms.size})",
                            color = if (selectedTabIndex == 2) Color(0xFFE50914) else Color.Gray,
                            fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentTabPrograms = when (selectedTabIndex) {
                0 -> ongoingPrograms
                1 -> upcomingPrograms
                else -> pastPrograms
            }

            if (currentTabPrograms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (selectedTabIndex) {
                            0 -> "Aucun programme en cours actuellement"
                            1 -> "Aucun programme à venir planifié"
                            else -> "Aucun programme passé enregistré"
                        },
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(currentTabPrograms) { program ->
                        ProgramAdminRow(
                            program = program,
                            onEdit = { onEditProgram(program) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramAdminRow(
    program: CustomProgram,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (program.imageUrl.isNotEmpty()) {
                val imgData = if (program.imageUrl.startsWith("/")) {
                    val file = File(program.imageUrl)
                    if (file.exists()) file else android.R.drawable.ic_menu_gallery
                } else {
                    program.imageUrl.trim()
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imgData)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Image du programme",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222222)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = program.programName.take(2).uppercase(),
                        color = Color.Gray,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Text(
                text = program.programName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Modifier",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
