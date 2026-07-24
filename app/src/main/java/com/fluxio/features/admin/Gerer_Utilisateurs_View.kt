package com.fluxio.features.admin

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxio.shared.theme.RedPrimary
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GererUtilisateursView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val firestore = remember { FirebaseFirestore.getInstance() }

    var usersList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filters: 0 = Tous, 1 = Actifs, 2 = Premium/VIP, 3 = Bloqués
    var selectedFilterTab by remember { mutableStateOf(0) }

    var selectedUserForEdit by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    var isSavingAction by remember { mutableStateOf(false) }

    // Fetch users function
    fun loadUsers() {
        isLoading = true
        firestore.collection("users")
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
                usersList = list.sortedBy { 
                    (it["displayName"] as? String ?: it["name"] as? String ?: "").lowercase() 
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                isLoading = false
                Toast.makeText(context, "Erreur lors du chargement: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun updateSubscription(uid: String, currentUser: Map<String, Any>, addDurationMs: Long, makeVip: Boolean) {
        if (uid.isEmpty()) return
        isSavingAction = true

        val currentType = currentUser["subscriptionType"] as? String ?: "gratuit"
        val currentExpiresAt = currentUser["subscriptionExpiresAt"] as? Long ?: 0L
        val now = System.currentTimeMillis()

        val (newType, newExpiry, newTotalDuration) = if (makeVip) {
            Triple("vip", Long.MAX_VALUE, Long.MAX_VALUE)
        } else {
            val baseTime = if (currentType == "premium" && currentExpiresAt > now) {
                currentExpiresAt
            } else {
                now
            }
            val targetExpiry = baseTime + addDurationMs
            Triple("premium", targetExpiry, addDurationMs)
        }

        val updateMap = mapOf(
            "subscriptionType" to newType,
            "subscriptionExpiresAt" to newExpiry,
            "subscriptionTotalDuration" to newTotalDuration
        )

        firestore.collection("users").document(uid)
            .update(updateMap)
            .addOnSuccessListener {
                isSavingAction = false
                selectedUserForEdit = null
                Toast.makeText(context, "Abonnement mis à jour avec succès !", Toast.LENGTH_SHORT).show()
                loadUsers()
            }
            .addOnFailureListener { e ->
                isSavingAction = false
                Toast.makeText(context, "Erreur lors de la mise à jour : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun resetSubscription(uid: String) {
        if (uid.isEmpty()) return
        isSavingAction = true

        val updateMap = mapOf(
            "subscriptionType" to "gratuit",
            "subscriptionExpiresAt" to 0L,
            "subscriptionTotalDuration" to 0L
        )

        firestore.collection("users").document(uid)
            .update(updateMap)
            .addOnSuccessListener {
                isSavingAction = false
                selectedUserForEdit = null
                Toast.makeText(context, "Abonnement réinitialisé à gratuit.", Toast.LENGTH_SHORT).show()
                loadUsers()
            }
            .addOnFailureListener { e ->
                isSavingAction = false
                Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun toggleBlockUser(uid: String, currentBlocked: Boolean) {
        if (uid.isEmpty()) return
        isSavingAction = true

        val newBlocked = !currentBlocked
        val updateMap = mapOf(
            "isBlocked" to newBlocked,
            "status" to if (newBlocked) "blocked" else "active"
        )

        firestore.collection("users").document(uid)
            .update(updateMap)
            .addOnSuccessListener {
                isSavingAction = false
                selectedUserForEdit = null
                val msg = if (newBlocked) "Utilisateur bloqué !" else "Utilisateur débloqué !"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                loadUsers()
            }
            .addOnFailureListener { e ->
                isSavingAction = false
                Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun deleteUserCompletely(uid: String, phoneNumber: String) {
        if (uid.isEmpty()) return
        isSavingAction = true

        firestore.collection("users").document(uid).delete()
            .addOnSuccessListener {
                if (phoneNumber.isNotEmpty()) {
                    firestore.collection("registered_phones").document(phoneNumber).delete()
                        .addOnCompleteListener {
                            isSavingAction = false
                            selectedUserForEdit = null
                            Toast.makeText(context, "Utilisateur supprimé !", Toast.LENGTH_SHORT).show()
                            loadUsers()
                        }
                } else {
                    isSavingAction = false
                    selectedUserForEdit = null
                    Toast.makeText(context, "Utilisateur supprimé !", Toast.LENGTH_SHORT).show()
                    loadUsers()
                }
            }
            .addOnFailureListener { e ->
                isSavingAction = false
                Toast.makeText(context, "Erreur lors de la suppression : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun purgeAllFakeUsers() {
        isSavingAction = true
        val fakeUsers = usersList.filter { user ->
            val uid = user["uid"] as? String ?: ""
            val name = (user["displayName"] as? String ?: user["name"] as? String ?: user["accountName"] as? String ?: "").lowercase().trim()
            val email = (user["email"] as? String ?: "").lowercase().trim()
            val phone = (user["phoneNumber"] as? String ?: "").lowercase().trim()
            val role = (user["role"] as? String ?: "user").lowercase().trim()

            if (role == "admin" || com.fluxio.core.security.SecurityUtils.isAdminEmail(email)) false
            else if (email == "antokofaha9faravohitra@gmail.com") false
            else {
                val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (uid == currentAuthUser?.uid || (currentAuthUser?.email?.isNotEmpty() == true && email == (currentAuthUser.email?.lowercase()?.trim() ?: ""))) {
                    false
                } else {
                    val fakeNamesAndEmails = listOf("test", "fake", "mock", "dummy", "guest", "temp", "invalid", "foo", "bar", "example", "simulation", "generate", "jean.dupont", "jeandupont", "dupont", "unknown", "inconnu")
                    val fakePhones = listOf("12345", "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999", "9876")

                    var isFake = false
                    for (term in fakeNamesAndEmails) {
                        if (name.contains(term) || email.contains(term)) {
                            isFake = true
                            break
                        }
                    }
                    if (!isFake) {
                        for (pattern in fakePhones) {
                            if (phone.contains(pattern)) {
                                isFake = true
                                break
                            }
                        }
                    }
                    if (!isFake && phone.isNotEmpty() && (phone.length < 5 || phone == "123" || phone == "test")) {
                        isFake = true
                    }
                    isFake
                }
            }
        }

        if (fakeUsers.isEmpty()) {
            isSavingAction = false
            Toast.makeText(context, "Aucun compte fictif détecté !", Toast.LENGTH_SHORT).show()
            return
        }

        val totalToDelete = fakeUsers.size
        var completedDeletes = 0
        var deletedCount = 0

        for (user in fakeUsers) {
            val uid = user["uid"] as? String ?: ""
            val phone = user["phoneNumber"] as? String ?: ""

            firestore.collection("users").document(uid).delete()
                .addOnSuccessListener {
                    if (phone.isNotEmpty()) {
                        firestore.collection("registered_phones").document(phone).delete()
                            .addOnCompleteListener {
                                completedDeletes++
                                deletedCount++
                                if (completedDeletes == totalToDelete) {
                                    isSavingAction = false
                                    Toast.makeText(context, "$deletedCount comptes fictifs supprimés avec succès !", Toast.LENGTH_LONG).show()
                                    loadUsers()
                                }
                            }
                    } else {
                        completedDeletes++
                        deletedCount++
                        if (completedDeletes == totalToDelete) {
                            isSavingAction = false
                            Toast.makeText(context, "$deletedCount comptes fictifs supprimés avec succès !", Toast.LENGTH_LONG).show()
                            loadUsers()
                        }
                    }
                }
                .addOnFailureListener {
                    completedDeletes++
                    if (completedDeletes == totalToDelete) {
                        isSavingAction = false
                        Toast.makeText(context, "$deletedCount comptes fictifs supprimés.", Toast.LENGTH_LONG).show()
                        loadUsers()
                    }
                }
        }
    }

    val fakeUsersCount = remember(usersList) {
        usersList.count { user ->
            val uid = user["uid"] as? String ?: ""
            val name = (user["displayName"] as? String ?: user["name"] as? String ?: user["accountName"] as? String ?: "").lowercase().trim()
            val email = (user["email"] as? String ?: "").lowercase().trim()
            val phone = (user["phoneNumber"] as? String ?: "").lowercase().trim()
            val role = (user["role"] as? String ?: "user").lowercase().trim()

            if (role == "admin" || com.fluxio.core.security.SecurityUtils.isAdminEmail(email)) false
            else if (email == "antokofaha9faravohitra@gmail.com") false
            else {
                val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (uid == currentAuthUser?.uid || (currentAuthUser?.email?.isNotEmpty() == true && email == (currentAuthUser.email?.lowercase()?.trim() ?: ""))) {
                    false
                } else {
                    val fakeNamesAndEmails = listOf("test", "fake", "mock", "dummy", "guest", "temp", "invalid", "foo", "bar", "example", "simulation", "generate", "jean.dupont", "jeandupont", "dupont", "unknown", "inconnu")
                    val fakePhones = listOf("12345", "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999", "9876")

                    var isFake = false
                    for (term in fakeNamesAndEmails) {
                        if (name.contains(term) || email.contains(term)) {
                            isFake = true
                            break
                        }
                    }
                    if (!isFake) {
                        for (pattern in fakePhones) {
                            if (phone.contains(pattern)) {
                                isFake = true
                                break
                            }
                        }
                    }
                    if (!isFake && phone.isNotEmpty() && (phone.length < 5 || phone == "123" || phone == "test")) {
                        isFake = true
                    }
                    isFake
                }
            }
        }
    }

    // Load users on launch
    LaunchedEffect(Unit) {
        loadUsers()
    }

    // Compute stats
    val totalUsers = usersList.size
    val premiumUsersCount = usersList.count { 
        val type = it["subscriptionType"] as? String ?: "gratuit"
        type == "premium" || type == "vip"
    }
    val blockedUsersCount = usersList.count { 
        it["isBlocked"] == true || it["status"] == "blocked"
    }

    // Filter users list based on search and selected tab
    val filteredUsers = remember(usersList, searchQuery, selectedFilterTab) {
        usersList.filter { user ->
            val name = (user["displayName"] as? String ?: user["name"] as? String ?: user["accountName"] as? String ?: "").lowercase()
            val email = (user["email"] as? String ?: "").lowercase()
            val phone = (user["phoneNumber"] as? String ?: "").lowercase()
            val matchesSearch = searchQuery.isEmpty() || name.contains(searchQuery.lowercase()) || email.contains(searchQuery.lowercase()) || phone.contains(searchQuery.lowercase())

            val subType = user["subscriptionType"] as? String ?: "gratuit"
            val isPremium = subType == "premium" || subType == "vip"
            val isBlocked = user["isBlocked"] == true || user["status"] == "blocked"

            val matchesFilter = when (selectedFilterTab) {
                1 -> !isBlocked // Actifs
                2 -> isPremium  // Abonnés
                3 -> isBlocked  // Bloqués
                else -> true    // Tous
            }

            matchesSearch && matchesFilter
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Gestion des Utilisateurs",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showPurgeConfirmDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Purger les comptes fakes",
                        tint = if (fakeUsersCount > 0) RedPrimary else Color.White
                    )
                }
                IconButton(
                    onClick = { loadUsers() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser",
                        tint = Color.White
                    )
                }
            }

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Total", color = Color.Gray, fontSize = 12.sp)
                        Text("$totalUsers", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Premium
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Abonnés", color = Color.Gray, fontSize = 12.sp)
                        Text("$premiumUsersCount", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Bloqués
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Bloqués", color = Color.Gray, fontSize = 12.sp)
                        Text("$blockedUsersCount", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (fakeUsersCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = RedPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$fakeUsersCount Comptes Fictifs Détectés",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Des comptes de test ou fictifs ont été détectés dans la base de données.",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showPurgeConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Purger",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher nom, e-mail, téléphone...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Rechercher", tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.Gray)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0xFF141414),
                    unfocusedContainerColor = Color(0xFF141414)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Tabs / Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterLabels = listOf("Tous", "Actifs", "Abonnés", "Bloqués")
                filterLabels.forEachIndexed { index, label ->
                    val isSelected = selectedFilterTab == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedFilterTab = index }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f) // clean underlined segment
                                .height(2.dp)
                                .background(if (isSelected) RedPrimary else Color.Transparent)
                        )
                    }
                }
            }

            // Users List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            } else if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun utilisateur trouvé",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredUsers) { user ->
                        UserRowItem(
                            user = user,
                            onEditClick = { selectedUserForEdit = user }
                        )
                    }
                }
            }
        }

        // Details / Actions dialog
        selectedUserForEdit?.let { user ->
            val uid = user["uid"] as? String ?: ""
            val name = user["displayName"] as? String ?: user["name"] as? String ?: user["accountName"] as? String ?: "Utilisateur"
            val email = user["email"] as? String ?: ""
            val phone = user["phoneNumber"] as? String ?: "Aucun numéro"
            val role = user["role"] as? String ?: "user"
            
            val subType = user["subscriptionType"] as? String ?: "gratuit"
            val expiresAt = user["subscriptionExpiresAt"] as? Long ?: 0L
            val isBlocked = user["isBlocked"] == true || user["status"] == "blocked"

            var freeDaysToGive by remember(uid) { mutableStateOf(1) }

            AlertDialog(
                onDismissRequest = { if (!isSavingAction) selectedUserForEdit = null },
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Détails de l'utilisateur",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { selectedUserForEdit = null },
                            enabled = !isSavingAction,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.Gray)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // User Profile summary Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isBlocked) Color.DarkGray else Color.Black,
                                        CircleShape
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = email,
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tél: $phone",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Status details card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Account Type
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Abonnement actuel", color = Color.Gray, fontSize = 13.sp)
                                val subText = when (subType) {
                                    "premium" -> "Premium"
                                    "vip" -> "VIP Permanent"
                                    else -> "Gratuit"
                                }
                                val subColor = when (subType) {
                                    "premium" -> Color(0xFF00C853)
                                    "vip" -> Color(0xFFFFD700)
                                    else -> Color.Gray
                                }
                                Text(subText, color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Expiry date
                            if (subType == "premium" && expiresAt > 0L) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Expire le", color = Color.Gray, fontSize = 13.sp)
                                    val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    val dateStr = format.format(Date(expiresAt))
                                    val isExpired = expiresAt < System.currentTimeMillis()
                                    Text(
                                        text = if (isExpired) "$dateStr (Expiré)" else dateStr,
                                        color = if (isExpired) RedPrimary else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Status Blocked / Active
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Statut du compte", color = Color.Gray, fontSize = 13.sp)
                                Text(
                                    text = if (isBlocked) "BLOQUÉ / SUSPENDU" else "ACTIF",
                                    color = if (isBlocked) RedPrimary else Color(0xFF00C853),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Role
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Rôle", color = Color.Gray, fontSize = 13.sp)
                                Text(
                                    text = role.uppercase(),
                                    color = if (role == "admin") Color(0xFF29B6F6) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // --- ACTION 1: Augmenter l'abonnement ---
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Augmenter l'abonnement",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // +1 Day button
                                ActionButton(
                                    text = "+1 Jour",
                                    onClick = {
                                        updateSubscription(uid, user, 1L * 24 * 60 * 60 * 1000L, false)
                                    },
                                    enabled = !isSavingAction,
                                    modifier = Modifier.weight(1f)
                                )

                                // +3 Days button
                                ActionButton(
                                    text = "+3 Jours",
                                    onClick = {
                                        updateSubscription(uid, user, 3L * 24 * 60 * 60 * 1000L, false)
                                    },
                                    enabled = !isSavingAction,
                                    modifier = Modifier.weight(1f)
                                )

                                // +7 Days button
                                ActionButton(
                                    text = "+7 Jours",
                                    onClick = {
                                        updateSubscription(uid, user, 7L * 24 * 60 * 60 * 1000L, false)
                                    },
                                    enabled = !isSavingAction,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Free days stepper option
                            Text(
                                text = "Donner des jours gratuits",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (freeDaysToGive > 1) freeDaysToGive-- },
                                        enabled = !isSavingAction && freeDaysToGive > 1,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Diminuer",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Text(
                                        text = "$freeDaysToGive ${if (freeDaysToGive > 1) "jours" else "jour"}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.widthIn(min = 60.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    IconButton(
                                        onClick = { freeDaysToGive++ },
                                        enabled = !isSavingAction,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Augmenter",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val durationMs = freeDaysToGive.toLong() * 24 * 60 * 60 * 1000L
                                        updateSubscription(uid, user, durationMs, false)
                                    },
                                    enabled = !isSavingAction,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = "Offrir",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Permanent VIP button
                            Button(
                                onClick = {
                                    updateSubscription(uid, user, 0, true)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isSavingAction && subType != "vip"
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Text("Accorder l'accès VIP Permanent", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        // --- ACTION 2: Bloquer & Supprimer ---
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Block/Unblock toggle button
                            Button(
                                onClick = {
                                    toggleBlockUser(uid, isBlocked)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBlocked) Color(0xFF00C853) else Color.Black
                                ),
                                border = if (isBlocked) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isSavingAction
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isBlocked) "Débloquer le compte" else "Bloquer l'utilisateur",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Reset/Remove premium button
                            Button(
                                onClick = {
                                    resetSubscription(uid)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isSavingAction && subType != "gratuit"
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Retirer l'abonnement",
                                        color = Color.LightGray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Delete completely button
                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isSavingAction
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                Text("Supprimer définitivement l'utilisateur", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }

        // Delete Confirmation dialog
        if (showDeleteConfirmDialog) {
            val user = selectedUserForEdit
            val name = user?.get("displayName") as? String ?: user?.get("name") as? String ?: "Utilisateur"
            val uid = user?.get("uid") as? String ?: ""
            val phone = user?.get("phoneNumber") as? String ?: ""

            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                title = {
                    Text("Supprimer l'utilisateur ?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Text(
                        text = "Voulez-vous vraiment supprimer définitivement $name ? Toutes ses données d'abonnement et de profil seront détruites de manière irréversible.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            deleteUserCompletely(uid, phone)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Supprimer", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Text("Annuler")
                    }
                }
            )
        }

        // Purge fakes confirmation dialog
        if (showPurgeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showPurgeConfirmDialog = false },
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.LightGray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Purger les comptes fictifs ?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Text(
                        text = "Voulez-vous vraiment supprimer définitivement tous les comptes contenant des données fictives ou de test (ex: noms/emails contenant 'test', 'fake', 'dummy' ou numéros de téléphone génériques) ?\n\nCette action est irréversible et nettoiera également leurs numéros de téléphone associés pour de futures réinscriptions saines.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPurgeConfirmDialog = false
                            purgeAllFakeUsers()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Oui, purger tout", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPurgeConfirmDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Text("Annuler")
                    }
                }
            )
        }
    }
}

@Composable
fun UserRowItem(
    user: Map<String, Any>,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = user["displayName"] as? String ?: user["name"] as? String ?: user["accountName"] as? String ?: "Utilisateur"
    val email = user["email"] as? String ?: ""
    val phone = user["phoneNumber"] as? String ?: ""
    val subType = user["subscriptionType"] as? String ?: "gratuit"
    val expiresAt = user["subscriptionExpiresAt"] as? Long ?: 0L
    val isBlocked = user["isBlocked"] == true || user["status"] == "blocked"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onEditClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        border = BorderStroke(
            width = 1.dp,
            color = if (isBlocked) RedPrimary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Avatar or Block indicator
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isBlocked) Color.Black else Color(0xFF22242B),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (isBlocked) RedPrimary else Color.White.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBlocked) {
                    Icon(Icons.Default.Block, contentDescription = "Bloqué", tint = RedPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = email,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (phone.isNotEmpty()) {
                    Text(
                        text = "Tél: $phone",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Subscription Badge and edit icon
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val badgeColor = when (subType) {
                    "premium" -> Color(0xFF00C853)
                    "vip" -> Color(0xFFFFD700)
                    else -> Color(0xFF333333)
                }
                val badgeTextColor = when (subType) {
                    "vip" -> Color.Black
                    else -> Color.White
                }
                val badgeText = when (subType) {
                    "premium" -> "PREMIUM"
                    "vip" -> "VIP"
                    else -> "GRATUIT"
                }

                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (subType == "premium" && expiresAt > 0L) {
                    val daysLeft = ((expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
                    Text(
                        text = if (expiresAt < System.currentTimeMillis()) "Expiré" else "$daysLeft j. restants",
                        color = if (expiresAt < System.currentTimeMillis()) RedPrimary else Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Gérer",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22242B)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp),
        enabled = enabled,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
