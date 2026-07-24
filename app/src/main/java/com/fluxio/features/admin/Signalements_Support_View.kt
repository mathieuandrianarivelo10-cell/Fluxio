package com.fluxio.features.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fluxio.shared.theme.RedPrimary

data class SupportReport(
    val id: String,
    val source: String,
    val issueType: String,
    val description: String,
    val time: String,
    var isResolved: Boolean = false,
    val replies: List<String> = emptyList()
)

fun loadSupportReports(context: Context): List<SupportReport> {
    val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
    val reportsStr = sharedPrefs.getString("support_reports_json", null)
    if (reportsStr == null) {
        return emptyList()
    }

    val list = mutableListOf<SupportReport>()
    try {
        val array = JSONArray(reportsStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            if (id == "1" || id == "2" || id == "3") {
                continue
            }
            val rList = mutableListOf<String>()
            val rArray = obj.optJSONArray("replies")
            if (rArray != null) {
                for (j in 0 until rArray.length()) {
                    rList.add(rArray.getString(j))
                }
            }
            list.add(
                SupportReport(
                    id = id,
                    source = obj.getString("source"),
                    issueType = obj.getString("issueType"),
                    description = obj.getString("description"),
                    time = obj.getString("time"),
                    isResolved = obj.optBoolean("isResolved", false),
                    replies = rList
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveSupportReports(context: Context, reports: List<SupportReport>) {
    val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
    try {
        val array = JSONArray()
        for (report in reports) {
            val obj = JSONObject().apply {
                put("id", report.id)
                put("source", report.source)
                put("issueType", report.issueType)
                put("description", report.description)
                put("time", report.time)
                put("isResolved", report.isResolved)
                val rArray = JSONArray()
                for (reply in report.replies) {
                    rArray.put(reply)
                }
                put("replies", rArray)
            }
            array.put(obj)
        }
        sharedPrefs.edit().putString("support_reports_json", array.toString()).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun addSupportReport(context: Context, source: String, issueType: String, description: String) {
    val current = loadSupportReports(context).toMutableList()
    val newReport = SupportReport(
        id = System.currentTimeMillis().toString(),
        source = source,
        issueType = issueType,
        description = description,
        time = "À l'instant",
        replies = emptyList()
    )
    current.add(0, newReport)
    saveSupportReports(context, current)
}
@Composable
fun SignalementsSupportView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var reports by remember {
        mutableStateOf(loadSupportReports(context))
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedReportIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var replyingReport by remember { mutableStateOf<SupportReport?>(null) }
    var replyText by remember { mutableStateOf("") }

    var showCreateNotificationDialog by remember { mutableStateOf(false) }
    var notificationTitle by remember { mutableStateOf("") }
    var notificationMessage by remember { mutableStateOf("") }
    var sendToAll by remember { mutableStateOf(true) }
    var selectedUsers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var usersList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isFetchingUsers by remember { mutableStateOf(false) }

    LaunchedEffect(showCreateNotificationDialog) {
        if (showCreateNotificationDialog) {
            isFetchingUsers = true
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val list = mutableListOf<Map<String, Any>>()
                        for (doc in snapshot.documents) {
                            val data = doc.data
                            if (data != null) {
                                val map = data.toMutableMap()
                                map["uid"] = doc.id
                                list.add(map)
                            }
                        }
                        usersList = list
                        isFetchingUsers = false
                    }
                    .addOnFailureListener {
                        isFetchingUsers = false
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                isFetchingUsers = false
            }
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
            if (isSelectionMode) {
                IconButton(onClick = {
                    isSelectionMode = false
                    selectedReportIds = emptySet()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Annuler la sélection",
                        tint = Color.White
                    )
                }
                Text(
                    text = "${selectedReportIds.size} sélectionné(s)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val allIds = reports.map { it.id }.toSet()
                val isAllSelected = selectedReportIds.containsAll(allIds) && allIds.isNotEmpty()
                TextButton(onClick = {
                    if (isAllSelected) {
                        selectedReportIds = emptySet()
                    } else {
                        selectedReportIds = allIds
                    }
                }) {
                    Text(
                        text = if (isAllSelected) "Aucun" else "Tous",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = {
                    if (selectedReportIds.isNotEmpty()) {
                        showDeleteConfirmDialog = true
                    } else {
                        Toast.makeText(context, "Aucun élément sélectionné", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = RedPrimary
                    )
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Signalements & Support",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    showCreateNotificationDialog = true
                    notificationTitle = ""
                    notificationMessage = ""
                    sendToAll = true
                    selectedUsers = emptyList()
                    searchQuery = ""
                }) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Créer une notification",
                        tint = Color.White
                    )
                }
                if (reports.isNotEmpty()) {
                    IconButton(onClick = {
                        isSelectionMode = true
                        selectedReportIds = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Mode sélection",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun signalement en attente",
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
                items(reports) { report ->
                    val isSelected = selectedReportIds.contains(report.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelectionMode) {
                                    val current = selectedReportIds.toMutableSet()
                                    if (current.contains(report.id)) {
                                        current.remove(report.id)
                                    } else {
                                        current.add(report.id)
                                    }
                                    selectedReportIds = current
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF1E293B) 
                                             else if (report.isResolved) Color(0xFF1E2822) 
                                             else Color(0xFF161616)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color.White
                            else if (report.isResolved) Color(0xFF2E4D3E) 
                            else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        val current = selectedReportIds.toMutableSet()
                                        if (checked) {
                                            current.add(report.id)
                                        } else {
                                            current.remove(report.id)
                                        }
                                        selectedReportIds = current
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color.White,
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color(0xFF0F172A)
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = report.source,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = report.time,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = report.issueType,
                                    color = if (report.isResolved) Color(0xFF4CAF50) else Color(0xFFE50914),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = report.description,
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                if (report.replies.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Réponses de l'assistance :",
                                            color = Color(0xFFE50914),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        report.replies.forEach { reply ->
                                            Text(
                                                text = reply,
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                if (!isSelectionMode) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                replyingReport = report
                                                replyText = ""
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = "Répondre",
                                                color = Color(0xFFE50914),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            if (!report.isResolved && !isSelectionMode) {
                                IconButton(
                                    onClick = {
                                        val updated = reports.map {
                                            if (it.id == report.id) it.copy(isResolved = true) else it
                                        }
                                        reports = updated
                                        saveSupportReports(context, updated)
                                        Toast.makeText(context, "Signalement marqué comme résolu", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Résoudre",
                                        tint = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        Dialog(onDismissRequest = { showDeleteConfirmDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Confirmer la suppression",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Voulez-vous vraiment supprimer les ${selectedReportIds.size} signalement(s) sélectionné(s) ?",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val remaining = reports.filter { !selectedReportIds.contains(it.id) }
                                reports = remaining
                                saveSupportReports(context, remaining)
                                selectedReportIds = emptySet()
                                isSelectionMode = false
                                showDeleteConfirmDialog = false
                                Toast.makeText(context, "Signalement(s) supprimé(s)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Supprimer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = false },
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Annuler", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    replyingReport?.let { report ->
        Dialog(onDismissRequest = { replyingReport = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Répondre au message",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "De : ${report.source}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = report.description,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 3
                        )
                    }

                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Votre réponse") },
                        placeholder = { Text("Écrivez votre réponse ici...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = RedPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedLabelColor = RedPrimary,
                            unfocusedLabelColor = Color.Gray,
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    val updated = reports.map {
                                        if (it.id == report.id) {
                                            it.copy(
                                                replies = it.replies + replyText.trim(),
                                                isResolved = true
                                            )
                                        } else it
                                    }
                                    reports = updated
                                    saveSupportReports(context, updated)
                                    Toast.makeText(context, "Réponse envoyée et signalement résolu !", Toast.LENGTH_SHORT).show()
                                    replyingReport = null
                                    replyText = ""
                                } else {
                                    Toast.makeText(context, "La réponse ne peut pas être vide", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Envoyer la réponse", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                replyingReport = null
                                replyText = ""
                            },
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Annuler", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (showCreateNotificationDialog) {
        Dialog(
            onDismissRequest = { showCreateNotificationDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F0F0F) // Premium immersive dark background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pinned Header / Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616))
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showCreateNotificationDialog = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Créer une notification",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Main Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Titre de la notification",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = notificationTitle,
                                onValueChange = { notificationTitle = it },
                                placeholder = { Text("Ex : Maintenance planifiée...", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = RedPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = RedPrimary,
                                    unfocusedLabelColor = Color.Gray,
                                    focusedContainerColor = Color(0xFF161616),
                                    unfocusedContainerColor = Color(0xFF161616)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Message de l'alerte",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = notificationMessage,
                                onValueChange = { notificationMessage = it },
                                placeholder = { Text("Écrivez le message de l'alerte ici...", color = Color.Gray) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = RedPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = RedPrimary,
                                    unfocusedLabelColor = Color.Gray,
                                    focusedContainerColor = Color(0xFF161616),
                                    unfocusedContainerColor = Color(0xFF161616)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Destinataires de la notification",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomChip(
                                    selected = sendToAll,
                                    text = "Tous les utilisateurs",
                                    modifier = Modifier.weight(1f),
                                    onClick = { sendToAll = true }
                                )

                                CustomChip(
                                    selected = !sendToAll,
                                    text = "Cibler des personnes (@)",
                                    modifier = Modifier.weight(1f),
                                    onClick = { sendToAll = false }
                                )
                            }
                        }

                        if (!sendToAll) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Rechercher un utilisateur (@nom ou e-mail)") },
                                    placeholder = { Text("Tapez @ pour voir tout le monde...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = RedPrimary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        focusedLabelColor = RedPrimary,
                                        unfocusedLabelColor = Color.Gray,
                                        focusedContainerColor = Color(0xFF161616),
                                        unfocusedContainerColor = Color(0xFF161616)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = Color.Gray
                                        )
                                    }
                                )

                                if (selectedUsers.isNotEmpty()) {
                                    Text(
                                        text = "Personnes mentionnées :",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        selectedUsers.forEach { user ->
                                            val name = user["name"] as? String ?: user["displayName"] as? String ?: user["email"] as? String ?: "Utilisateur"
                                            CustomSuggestionChip(
                                                text = "@$name",
                                                onRemove = {
                                                    selectedUsers = selectedUsers.filter { it["uid"] != user["uid"] }
                                                }
                                            )
                                        }
                                    }
                                }

                                if (isFetchingUsers) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    val cleanQuery = searchQuery.removePrefix("@").trim()
                                    val filtered = remember(usersList, searchQuery) {
                                        if (searchQuery.isEmpty()) {
                                            usersList.take(5)
                                        } else {
                                            usersList.filter {
                                                val name = (it["name"] as? String ?: it["displayName"] as? String ?: "").lowercase()
                                                val email = (it["email"] as? String ?: "").lowercase()
                                                name.contains(cleanQuery.lowercase()) || email.contains(cleanQuery.lowercase())
                                            }
                                        }
                                    }

                                    if (filtered.isEmpty()) {
                                        Text(
                                            text = "Aucun utilisateur trouvé",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(6.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                filtered.forEach { user ->
                                                    val isSelected = selectedUsers.any { it["uid"] == user["uid"] }
                                                    val name = user["name"] as? String ?: user["displayName"] as? String ?: "Inconnu"
                                                    val email = user["email"] as? String ?: ""
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (isSelected) {
                                                                    selectedUsers = selectedUsers.filter { it["uid"] != user["uid"] }
                                                                } else {
                                                                    selectedUsers = selectedUsers + user
                                                                }
                                                            }
                                                            .padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(text = name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                            if (email.isNotEmpty()) {
                                                                Text(text = email, color = Color.Gray, fontSize = 12.sp)
                                                            }
                                                        }
                                                        Icon(
                                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                                            contentDescription = "Selection",
                                                            tint = if (isSelected) RedPrimary else Color.Gray,
                                                            modifier = Modifier.size(20.dp)
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

                    // Pinned Footer / Bottom Actions
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                if (notificationTitle.isNotBlank() && notificationMessage.isNotBlank()) {
                                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    val recipients = if (sendToAll) usersList else selectedUsers

                                    if (recipients.isEmpty()) {
                                        Toast.makeText(context, "Aucun destinataire disponible", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    val chunks = recipients.chunked(500)
                                    var pendingBatches = chunks.size
                                    var errorsCount = 0

                                    for (chunk in chunks) {
                                        val batch = db.batch()
                                        for (user in chunk) {
                                            val uid = user["uid"] as? String ?: continue
                                            val docRef = db.collection("subscription_notifications").document()
                                            val data = hashMapOf(
                                                "userId" to uid,
                                                "title" to notificationTitle.trim(),
                                                "message" to notificationMessage.trim(),
                                                "createdAt" to System.currentTimeMillis()
                                            )
                                            batch.set(docRef, data)
                                        }
                                        batch.commit()
                                            .addOnSuccessListener {
                                                pendingBatches--
                                                if (pendingBatches == 0) {
                                                    if (errorsCount == 0) {
                                                        Toast.makeText(context, "Notification envoyée avec succès !", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Notification envoyée partiellement", Toast.LENGTH_SHORT).show()
                                                    }
                                                    showCreateNotificationDialog = false
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                e.printStackTrace()
                                                errorsCount++
                                                pendingBatches--
                                                if (pendingBatches == 0) {
                                                    Toast.makeText(context, "Erreur d'envoi : ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    }
                                } else {
                                    Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Envoyer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = { showCreateNotificationDialog = false },
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Annuler", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomChip(
    selected: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 12.dp)
            .defaultMinSize(minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (selected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(45.dp)
                    .height(2.dp)
                    .background(RedPrimary)
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun CustomSuggestionChip(
    text: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(BorderStroke(1.dp, RedPrimary.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp)
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Retirer",
            tint = Color.White,
            modifier = Modifier
                .size(14.dp)
                .clickable { onRemove() }
        )
    }
}
