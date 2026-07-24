package com.fluxio.features.admin

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImporterMisesAJourView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()

    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var releaseNotes by remember { mutableStateOf("") }
    var isForceUpdate by remember { mutableStateOf(false) }

    var importMethod by remember { mutableStateOf("file") } // "file" or "url"
    var directApkUrl by remember { mutableStateOf("") }

    var selectedApkUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedApkName by remember { mutableStateOf("") }
    var selectedApkSize by remember { mutableStateOf(0L) }
    var uploadProgress by remember { mutableStateOf<Int?>(null) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        selectedApkUri = uri
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        if (nameIndex != -1) {
                            selectedApkName = c.getString(nameIndex)
                        }
                        if (sizeIndex != -1) {
                            selectedApkSize = c.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                selectedApkName = uri.lastPathSegment ?: "APK"
            }
        }
    }

    var isLoadingLatest by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }

    var currentVersionName by remember { mutableStateOf("Inconnu") }
    var currentVersionCode by remember { mutableStateOf("Inconnu") }
    var currentApkUrl by remember { mutableStateOf("Inconnu") }
    var currentReleaseNotes by remember { mutableStateOf("Aucune description") }
    var currentForceUpdate by remember { mutableStateOf(false) }

    fun loadLatestUpdateInfo() {
        isLoadingLatest = true
        firestore.collection("app_updates").document("latest")
            .get()
            .addOnSuccessListener { document ->
                isLoadingLatest = false
                if (document != null && document.exists()) {
                    currentVersionName = document.getString("versionName") ?: "Inconnu"
                    currentVersionCode = document.getLong("versionCode")?.toString() ?: "Inconnu"
                    currentApkUrl = document.getString("apkUrl") ?: "Inconnu"
                    currentReleaseNotes = document.getString("releaseNotes") ?: "Aucune description"
                    currentForceUpdate = document.getBoolean("isForceUpdate") ?: false
                }
            }
            .addOnFailureListener {
                isLoadingLatest = false
            }
    }

    LaunchedEffect(Unit) {
        loadLatestUpdateInfo()
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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White
                )
            }
            Text(
                text = "Importer des mises a jour",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mise a jour active",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { loadLatestUpdateInfo() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isLoadingLatest) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Nom de version", color = Color.Gray, fontSize = 11.sp)
                            Text(text = currentVersionName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Code de version", color = Color.Gray, fontSize = 11.sp)
                            Text(text = currentVersionCode, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column {
                        Text(text = "Lien de telechargement APK", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = currentApkUrl,
                            color = Color(0xFFFF0000),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    Column {
                        Text(text = "Notes de mise a jour", color = Color.Gray, fontSize = 11.sp)
                        Text(text = currentReleaseNotes, color = Color.White, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = if (currentForceUpdate) Color(0xFFFF0000) else Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentForceUpdate) "Mise a jour obligatoire" else "Mise a jour facultative",
                            color = if (currentForceUpdate) Color(0xFFFF0000) else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Text(
                text = "Publier une nouvelle mise a jour",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            OutlinedTextField(
                value = versionName,
                onValueChange = { versionName = it },
                label = { Text("Nom de version (ex: 2.5.2)", color = Color.Gray) },
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
                value = versionCode,
                onValueChange = { versionCode = it },
                label = { Text("Code de version (ex: 1043)", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isFileSelected = importMethod == "file"
                val fileIndicatorFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isFileSelected) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                    label = "fileIndicator"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { importMethod = "file" }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Téléverser APK (Storage)",
                        color = if (isFileSelected) Color.White else Color.Gray,
                        fontWeight = if (isFileSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fileIndicatorFraction)
                            .height(2.dp)
                            .background(Color(0xFFE50914))
                    )
                }

                val isUrlSelected = importMethod == "url"
                val urlIndicatorFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isUrlSelected) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                    label = "urlIndicator"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { importMethod = "url" }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Lien Direct APK (sans Storage)",
                        color = if (isUrlSelected) Color.White else Color.Gray,
                        fontWeight = if (isUrlSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(urlIndicatorFraction)
                            .height(2.dp)
                            .background(Color(0xFFE50914))
                    )
                }
            }

            if (importMethod == "file") {
                if (selectedApkUri == null) {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161616)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Selectionner le fichier APK depuis le telephone", color = Color.White, fontSize = 13.sp)
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE50914).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1213))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fichier selectionne :", color = Color(0xFFE50914), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(selectedApkName, color = Color.White, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                                if (selectedApkSize > 0) {
                                    val sizeInMb = selectedApkSize / (1024f * 1024f)
                                    Text(String.format("%.2f Mo", sizeInMb), color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            TextButton(
                                onClick = { filePickerLauncher.launch("*/*") }
                            ) {
                                Text("Modifier", color = Color(0xFFE50914), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                uploadProgress?.let { progress ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFE50914),
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "Envoi dans le cloud... $progress%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = directApkUrl,
                    onValueChange = { directApkUrl = it },
                    label = { Text("Lien de téléchargement direct (URL de l'APK)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Astuce : Vous pouvez héberger votre APK sur GitHub, GDrive (lien direct), Dropbox, Archive.org ou tout autre hébergeur et coller le lien direct ici.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            OutlinedTextField(
                value = releaseNotes,
                onValueChange = { releaseNotes = it },
                label = { Text("Notes de version / Description", color = Color.Gray) },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Forcer la mise a jour",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Les utilisateurs devront mettre a jour pour continuer a utiliser l'application.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isForceUpdate,
                    onCheckedChange = { isForceUpdate = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF0000),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF262626)
                    )
                )
            }

            Button(
                onClick = {
                    val vName = versionName.trim()
                    val vCodeStr = versionCode.trim()
                    val notes = releaseNotes.trim()

                    if (vName.isEmpty() || vCodeStr.isEmpty() || notes.isEmpty()) {
                        Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val codeVal = vCodeStr.toLongOrNull()
                    if (codeVal == null) {
                        Toast.makeText(context, "Le code de version doit etre un nombre entier valide", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (importMethod == "file") {
                        if (selectedApkUri == null) {
                            Toast.makeText(context, "Veuillez selectionner un fichier APK", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                    } else {
                        if (directApkUrl.trim().isEmpty()) {
                            Toast.makeText(context, "Veuillez saisir le lien de téléchargement direct", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                    }

                    isPublishing = true

                    if (importMethod == "url") {
                        val updateMap = hashMapOf<String, Any>(
                            "versionName" to vName,
                            "versionCode" to codeVal,
                            "apkUrl" to directApkUrl.trim(),
                            "releaseNotes" to notes,
                            "isForceUpdate" to isForceUpdate,
                            "timestamp" to System.currentTimeMillis()
                        )

                        firestore.collection("app_updates").document("latest")
                            .set(updateMap)
                            .addOnSuccessListener {
                                isPublishing = false
                                Toast.makeText(context, "Mise a jour publiee avec succes !", Toast.LENGTH_LONG).show()
                                versionName = ""
                                versionCode = ""
                                directApkUrl = ""
                                releaseNotes = ""
                                isForceUpdate = false
                                loadLatestUpdateInfo()
                            }
                            .addOnFailureListener { e ->
                                isPublishing = false
                                Toast.makeText(context, "Erreur publication Firestore : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        uploadProgress = 0
                        try {
                            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                            val apkRef = storageRef.child("app_updates/fluxio_${vName}_${codeVal}.apk")
                            val uploadTask = apkRef.putFile(selectedApkUri!!)

                            uploadTask.addOnProgressListener { taskSnapshot ->
                                if (taskSnapshot.totalByteCount > 0) {
                                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                                    uploadProgress = progress
                                }
                            }.addOnSuccessListener {
                                apkRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                    val finalApkUrl = downloadUri.toString()
                                    val updateMap = hashMapOf<String, Any>(
                                        "versionName" to vName,
                                        "versionCode" to codeVal,
                                        "apkUrl" to finalApkUrl,
                                        "releaseNotes" to notes,
                                        "isForceUpdate" to isForceUpdate,
                                        "timestamp" to System.currentTimeMillis()
                                    )

                                    firestore.collection("app_updates").document("latest")
                                        .set(updateMap)
                                        .addOnSuccessListener {
                                            isPublishing = false
                                            uploadProgress = null
                                            Toast.makeText(context, "Mise a jour publiee avec succes !", Toast.LENGTH_LONG).show()
                                            versionName = ""
                                            versionCode = ""
                                            selectedApkUri = null
                                            selectedApkName = ""
                                            selectedApkSize = 0L
                                            releaseNotes = ""
                                            isForceUpdate = false
                                            loadLatestUpdateInfo()
                                        }
                                        .addOnFailureListener { e ->
                                            isPublishing = false
                                            uploadProgress = null
                                            Toast.makeText(context, "Erreur publication Firestore : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                }.addOnFailureListener { e ->
                                    isPublishing = false
                                    uploadProgress = null
                                    Toast.makeText(context, "Erreur recuperation URL : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }.addOnFailureListener { e ->
                                isPublishing = false
                                uploadProgress = null
                                Toast.makeText(context, "Erreur televersement : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            isPublishing = false
                            uploadProgress = null
                            Toast.makeText(context, "Erreur inattendue : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE50914)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = "Publier la mise a jour",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
