package com.fluxio.features.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsAccountScreen(
    userEmail: String,
    userName: String,
    biometricLockEnabled: Boolean,
    onBiometricLockToggle: (Boolean) -> Unit,
    onDeleteAccountClick: () -> Unit = {},
    onPaymentModeChange: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember(userEmail) { context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE) }
    
    var accountName by remember(userEmail) { mutableStateOf(sharedPrefs.getString("profile_account_name_$userEmail", "") ?: "") }
    var phoneNumber by remember(userEmail) { mutableStateOf(sharedPrefs.getString("profile_phone_$userEmail", "") ?: "") }
    var currentEmail by remember(userEmail) { mutableStateOf(userEmail) }
    var showEditDialog by remember { mutableStateOf(false) }
    var subscriptionType by remember(userEmail) {
        val cachedType = sharedPrefs.getString("subscription_type_$userEmail", "gratuit") ?: "gratuit"
        val cachedExpiry = sharedPrefs.getLong("subscription_expires_at_$userEmail", 0L)
        if (cachedType == "premium" && cachedExpiry > System.currentTimeMillis()) {
            mutableStateOf("premium")
        } else {
            mutableStateOf("gratuit")
        }
    }
    val isAdmin = remember(userEmail) { com.fluxio.core.security.SecurityUtils.isAdminEmail(userEmail) }
    val effectiveSubscriptionType = remember(subscriptionType, isAdmin) {
        if (isAdmin) "premium" else subscriptionType
    }
    var subscriptionExpiresAt by remember(userEmail) {
        val cachedExpiry = sharedPrefs.getLong("subscription_expires_at_$userEmail", 0L)
        mutableStateOf(cachedExpiry)
    }
    var subscriptionTotalDuration by remember(userEmail) {
        val cachedDuration = sharedPrefs.getLong("subscription_total_duration_$userEmail", 0L)
        mutableStateOf(cachedDuration)
    }
    var isSyncingSub by remember { mutableStateOf(false) }
    var selectedPackageForPayment by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LaunchedEffect(selectedPackageForPayment) {
        onPaymentModeChange(selectedPackageForPayment != null)
    }

    fun syncSubscriptionWithServer() {
        if (isSyncingSub) return
        isSyncingSub = true
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    isSyncingSub = false
                    if (document != null && document.exists()) {
                        val serverSub = document.getString("subscriptionType") ?: "gratuit"
                        val serverExpiry = document.getLong("subscriptionExpiresAt") ?: 0L
                        val serverTotalDuration = document.getLong("subscriptionTotalDuration") ?: (24 * 60 * 60 * 1000L)
                        
                        sharedPrefs.edit()
                            .putString("subscription_type_$userEmail", serverSub)
                            .putLong("subscription_expires_at_$userEmail", serverExpiry)
                            .putLong("subscription_total_duration_$userEmail", serverTotalDuration)
                            .apply()
                            
                        subscriptionType = serverSub
                        subscriptionExpiresAt = serverExpiry
                        subscriptionTotalDuration = serverTotalDuration
                        
                        Toast.makeText(context, "Abonnement actualise", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Aucune donnee trouvee", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    isSyncingSub = false
                    Toast.makeText(context, "Echec de la synchronisation: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            isSyncingSub = false
            Toast.makeText(context, "Veuillez vous connecter", Toast.LENGTH_SHORT).show()
        }
    }

    fun savePhoneAndSubscription(phone: String, newSub: String, durationDays: Int = 0) {
        subscriptionType = newSub
        phoneNumber = phone
        val durationMs = if (durationDays > 0) durationDays * 24 * 60 * 60 * 1000L else 24 * 60 * 60 * 1000L
        val expiryTime = if (newSub == "premium" && durationDays > 0) {
            System.currentTimeMillis() + durationMs
        } else {
            0L
        }
        subscriptionExpiresAt = expiryTime
        subscriptionTotalDuration = durationMs
        sharedPrefs.edit()
            .putString("subscription_type_$userEmail", newSub)
            .putString("profile_phone_$userEmail", phone)
            .putLong("subscription_expires_at_$userEmail", expiryTime)
            .putLong("subscription_total_duration_$userEmail", durationMs)
            .apply()
        
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val updates = hashMapOf<String, Any>(
                "subscriptionType" to newSub,
                "phoneNumber" to phone,
                "subscriptionExpiresAt" to expiryTime,
                "subscriptionTotalDuration" to durationMs
            )
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    var nameAttemptsCount by remember { mutableStateOf(sharedPrefs.getInt("name_change_attempts_count_$userEmail", 0)) }
    var nameCooldownStart by remember { mutableStateOf(sharedPrefs.getLong("name_change_cooldown_start_$userEmail", 0L)) }

    val currentTime = System.currentTimeMillis()
    val cooldownDuration = 30L * 24 * 60 * 60 * 1000L
    val isLocked = nameAttemptsCount >= 2 && (currentTime - nameCooldownStart < cooldownDuration)

    val daysRemaining = remember(nameCooldownStart) {
        if (nameCooldownStart > 0L) {
            val diffMs = (nameCooldownStart + cooldownDuration) - System.currentTimeMillis()
            val diffDays = diffMs / (24 * 60 * 60 * 1000L)
            diffDays.coerceAtLeast(1)
        } else {
            30L
        }
    }

    LaunchedEffect(userEmail) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val serverAttempts = document.getLong("nameChangeAttemptsCount")?.toInt()
                        val serverCooldownStart = document.getLong("nameChangeCooldownStart")
                        var serverSub = document.getString("subscriptionType") ?: "gratuit"
                        var serverExpiry = document.getLong("subscriptionExpiresAt") ?: 0L
                        var serverTotalDuration = document.getLong("subscriptionTotalDuration") ?: (24 * 60 * 60 * 1000L)
                        
                        val finalAttempts = maxOf(serverAttempts ?: 0, nameAttemptsCount)
                        val finalCooldown = maxOf(serverCooldownStart ?: 0L, nameCooldownStart)
                        
                        sharedPrefs.edit()
                            .putInt("name_change_attempts_count_$userEmail", finalAttempts)
                            .putLong("name_change_cooldown_start_$userEmail", finalCooldown)
                            .putString("subscription_type_$userEmail", serverSub)
                            .putLong("subscription_expires_at_$userEmail", serverExpiry)
                            .putLong("subscription_total_duration_$userEmail", serverTotalDuration)
                            .apply()
                            
                        nameAttemptsCount = finalAttempts
                        nameCooldownStart = finalCooldown
                        subscriptionType = serverSub
                        subscriptionExpiresAt = serverExpiry
                        subscriptionTotalDuration = serverTotalDuration
                    }
                }
        }
    }

    LaunchedEffect(nameAttemptsCount, nameCooldownStart, userEmail) {
        val currTime = System.currentTimeMillis()
        if (nameAttemptsCount >= 2 && nameCooldownStart > 0L && (currTime - nameCooldownStart >= cooldownDuration)) {
            sharedPrefs.edit()
                .putInt("name_change_attempts_count_$userEmail", 0)
                .putLong("name_change_cooldown_start_$userEmail", 0L)
                .apply()
            nameAttemptsCount = 0
            nameCooldownStart = 0L
            
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val updates = hashMapOf<String, Any>(
                    "nameChangeAttemptsCount" to 0,
                    "nameChangeCooldownStart" to 0L
                )
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update(updates)
            }
        }
    }

    fun updateSubscription(newSub: String) {
        subscriptionType = newSub
        subscriptionExpiresAt = 0L
        sharedPrefs.edit()
            .putString("subscription_type_$userEmail", newSub)
            .putLong("subscription_expires_at_$userEmail", 0L)
            .apply()
        
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val updates = hashMapOf<String, Any>(
                "subscriptionType" to newSub,
                "subscriptionExpiresAt" to 0L
            )
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    LaunchedEffect(subscriptionType, subscriptionExpiresAt, isAdmin) {
        if (!isAdmin && subscriptionType == "premium" && subscriptionExpiresAt > 0L) {
            val checkExpiry = {
                if (System.currentTimeMillis() >= subscriptionExpiresAt) {
                    updateSubscription("gratuit")
                    Toast.makeText(context, "Votre abonnement Premium est terminé.", Toast.LENGTH_LONG).show()
                }
            }
            checkExpiry()
            while (!isAdmin && subscriptionType == "premium" && subscriptionExpiresAt > 0L) {
                kotlinx.coroutines.delay(10000)
                checkExpiry()
            }
        }
    }

    fun saveProfileData(
        uid: String,
        name: String,
        email: String,
        phone: String,
        emailChanged: Boolean,
        nameChanged: Boolean,
        onComplete: () -> Unit
    ) {
        val oldEmail = currentEmail
        val userMap = hashMapOf<String, Any>(
            "accountName" to name,
            "name" to name,
            "displayName" to name,
            "phoneNumber" to phone,
            "email" to email
        )
        
        if (nameChanged) {
            val newAttempts = nameAttemptsCount + 1
            val newCooldownStart = if (newAttempts >= 2) System.currentTimeMillis() else nameCooldownStart
            
            userMap["nameChangeAttemptsCount"] = newAttempts
            userMap["nameChangeCooldownStart"] = newCooldownStart
            
            sharedPrefs.edit()
                .putInt("name_change_attempts_count_$userEmail", newAttempts)
                .putLong("name_change_cooldown_start_$userEmail", newCooldownStart)
                .apply()
                
            nameAttemptsCount = newAttempts
            nameCooldownStart = newCooldownStart
        }
        
        val firestore = FirebaseFirestore.getInstance()
        
        firestore.collection("users").document(uid)
            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
        
        if (emailChanged) {
            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(oldEmail)
                    .addOnCompleteListener {}
                
                val user = FirebaseAuth.getInstance().currentUser
                try {
                    user?.verifyBeforeUpdateEmail(email)
                } catch (e: NoSuchMethodError) {
                    user?.updateEmail(email)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            Toast.makeText(
                context,
                "Modification reussie ! Un lien de confirmation a ete envoye a l'ancienne adresse e-mail ($oldEmail).",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(context, "Profil mis a jour !", Toast.LENGTH_SHORT).show()
        }
        
        sharedPrefs.edit()
            .putString("profile_account_name_$oldEmail", name)
            .putString("profile_phone_$oldEmail", phone)
            .putString("profile_account_name_$email", name)
            .putString("profile_phone_$email", phone)
            .apply()
            
        accountName = name
        currentEmail = email
        onComplete()
    }

    AnimatedContent(
        targetState = selectedPackageForPayment,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 300)
                ) with slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300)
                )
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300)
                ) with slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 300)
                )
            }
        },
        label = "PaymentScreenTransition"
    ) { pkg ->
        if (pkg == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Details du Profil",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = com.fluxio.R.drawable.ic_edit_profile),
                                    contentDescription = "Modifier le profil",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Nom du Compte", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            Text(text = accountName.ifBlank { userName }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Adresse E-mail", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            Text(text = currentEmail, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        
                        if (phoneNumber.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Telephone", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                val maskedPhone = remember(phoneNumber) {
                                    val trimmed = phoneNumber.trim()
                                    if (trimmed.length >= 6) {
                                        val start = trimmed.take(4)
                                        val end = trimmed.takeLast(2)
                                        "$start******$end"
                                    } else {
                                        "******"
                                    }
                                }
                                Text(text = maskedPhone, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                if (effectiveSubscriptionType == "gratuit") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tarifs des Abonnements",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val packages = listOf(
                                Triple("1 Jour", "500 Ar", "Idéal pour tester l'expérience"),
                                Triple("3 Jours", "1200 Ar", "Parfait pour le week-end"),
                                Triple("7 Jours", "2500 Ar", "Meilleure valeur hebdomadaire")
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                packages.forEach { pkgItem ->
                                    val isSelected = selectedPackageForPayment == pkgItem
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFFE50914).copy(alpha = 0.15f) else Color(0xFF161616))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color(0xFFE50914) else Color.White.copy(alpha = 0.05f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedPackageForPayment = pkgItem }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1.0f)) {
                                            Text(
                                                text = pkgItem.first,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = pkgItem.third,
                                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .width(85.dp)
                                                .background(if (isSelected) Color(0xFFE50914) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .border(1.dp, if (isSelected) Color(0xFFE50914) else Color.White, RoundedCornerShape(8.dp))
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pkgItem.second,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Abonnement Actuel",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "PREMIUM",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Accès illimité à toutes les chaînes internationales en HD / 4K.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            val expiryDateText = remember(subscriptionExpiresAt, isAdmin) {
                                if (isAdmin) {
                                    "Illimitée"
                                } else if (subscriptionExpiresAt > 0L) {
                                    try {
                                        val sdf = java.text.SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", java.util.Locale.FRANCE)
                                        sdf.format(java.util.Date(subscriptionExpiresAt))
                                    } catch (e: Exception) {
                                        "N/A"
                                    }
                                } else {
                                    "Indéfinie"
                                }
                            }

                            var remainingSecs by remember { mutableStateOf(0L) }
                            val topText = remember(remainingSecs, isAdmin) {
                                if (isAdmin) {
                                    "Abonnement"
                                } else if (remainingSecs > 0L) {
                                    val totalSeconds = remainingSecs / 1000L
                                    val totalMinutes = totalSeconds / 60L
                                    val totalHours = totalMinutes / 60L
                                    val totalDays = totalHours / 24L

                                    val months = totalDays / 30L
                                    val remainingDaysAfterMonths = totalDays % 30L
                                    val weeks = remainingDaysAfterMonths / 7L
                                    val days = remainingDaysAfterMonths % 7L

                                    "${months} Mois ${weeks} Sem. ${days} J"
                                } else {
                                    "0 Mois 0 Sem. 0 J"
                                }
                            }

                            val bottomText = remember(remainingSecs, isAdmin) {
                                if (isAdmin) {
                                    "Illimité"
                                } else if (remainingSecs > 0L) {
                                    val totalSeconds = remainingSecs / 1000L
                                    val totalMinutes = totalSeconds / 60L
                                    val totalHours = totalMinutes / 60L

                                    val hours = totalHours % 24L
                                    val minutes = totalMinutes % 60L
                                    val seconds = totalSeconds % 60L

                                    "${hours}h ${minutes}m ${seconds}s"
                                } else {
                                    "0h 0m 0s"
                                }
                            }

                            val progressFraction = remember(remainingSecs, subscriptionTotalDuration, isAdmin) {
                                if (isAdmin) {
                                    1.0f
                                } else if (remainingSecs > 0L && subscriptionTotalDuration > 0L) {
                                    (remainingSecs.toFloat() / subscriptionTotalDuration.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            }
                            LaunchedEffect(subscriptionExpiresAt, isAdmin) {
                                if (isAdmin) return@LaunchedEffect
                                while (true) {
                                    remainingSecs = (subscriptionExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
                                    kotlinx.coroutines.delay(1000)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularCountdownTimer(
                                    modifier = Modifier.size(220.dp),
                                    progress = progressFraction,
                                    topText = topText,
                                    bottomText = bottomText,
                                    onRefresh = { syncSubscriptionWithServer() }
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Statut", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Text("Actif", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Date d'expiration", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Text(expiryDateText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                if (phoneNumber.isNotBlank() && !isAdmin) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Numéro de transaction", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                        Text(phoneNumber, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            if (!isAdmin) {
                                Spacer(modifier = Modifier.height(16.dp))

                                var showCancelConfirm by remember { mutableStateOf(false) }
                                
                                OutlinedButton(
                                    onClick = { showCancelConfirm = true },
                                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text("Résilier l'abonnement", fontWeight = FontWeight.Bold)
                                }
                                
                                if (showCancelConfirm) {
                                    AlertDialog(
                                        onDismissRequest = { showCancelConfirm = false },
                                        title = {
                                            Text(
                                                text = "Résilier l'abonnement",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        containerColor = Color.Black,
                                        text = {
                                            Text(
                                                text = "Êtes-vous sûr de vouloir résilier votre abonnement Premium et repasser en mode gratuit ?",
                                                color = Color.LightGray,
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
                                                        updateSubscription("gratuit")
                                                        showCancelConfirm = false
                                                        Toast.makeText(context, "Abonnement résilié", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Confirmer la résiliation", color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                                
                                                TextButton(
                                                    onClick = { showCancelConfirm = false },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Conserver mon abonnement", color = Color.White.copy(alpha = 0.6f))
                                                }
                                            }
                                        },
                                        dismissButton = null
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsToggleItem(
                    title = "Protection Biometrique",
                    description = "Verrouiller l'acces a l'application",
                    checked = biometricLockEnabled,
                    onCheckedChange = onBiometricLockToggle,
                    icon = Icons.Default.Lock
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Zone de danger",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "La suppression de votre compte est définitive. Toutes vos données, vos favoris et votre abonnement seront supprimés de façon permanente.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Supprimer", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        if (showDeleteConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirmDialog = false },
                                title = {
                                    Text(
                                        text = "Supprimer définitivement le compte ?",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                containerColor = Color.Black,
                                text = {
                                    Text(
                                        text = "Cette action est irréversible. Toutes vos informations personnelles, votre historique d'abonnement et vos favoris seront effacés définitivement.",
                                        color = Color.LightGray,
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
                                                showDeleteConfirmDialog = false
                                                onDeleteAccountClick()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Oui, supprimer mon compte", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        OutlinedButton(
                                            onClick = { showDeleteConfirmDialog = false },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Annuler", color = Color.White)
                                        }
                                    }
                                },
                                dismissButton = null
                            )
                        }
                    }
                }
            }
        } else {
            PaymentScreen(
                pkg = pkg,
                initialPhone = "",
                registeredPhone = phoneNumber,
                onBack = { selectedPackageForPayment = null },
                onPaymentSuccess = { phoneUsed, ref, smsDetails ->
                    val days = when (pkg.first) {
                        "1 Jour" -> 1
                        "3 Jours" -> 3
                        "7 Jours" -> 7
                        else -> 1
                    }
                    savePhoneAndSubscription(phoneUsed, "premium", days)

                    val notifId = java.util.UUID.randomUUID().toString()
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val notifData = hashMapOf<String, Any>(
                        "id" to notifId,
                        "userId" to uid,
                        "title" to "Nouvel abonnement",
                        "createdAt" to System.currentTimeMillis(),
                        "name" to (accountName.ifBlank { userName.ifBlank { userEmail } }),
                        "phone" to phoneUsed,
                        "ref" to ref,
                        "message" to smsDetails,
                        "timestamp" to System.currentTimeMillis()
                    )
                    FirebaseFirestore.getInstance().collection("subscription_notifications")
                        .document(notifId)
                        .set(notifData)

                    selectedPackageForPayment = null
                    Toast.makeText(context, "Abonnement activé avec succès !", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (showEditDialog) {
        var editAccountName by remember { mutableStateOf(accountName.ifBlank { userName }) }
        var isSavingData by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSavingData) showEditDialog = false },
            title = {
                Text(
                    text = "Modifier le profil",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = Color.Black,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val attemptsLeft = (2 - nameAttemptsCount).coerceAtLeast(0)
                    
                    OutlinedTextField(
                        value = editAccountName,
                        onValueChange = { if (!isLocked) editAccountName = it },
                        label = { Text("Nom du Compte") },
                        enabled = !isLocked,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White.copy(alpha = 0.5f),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            disabledBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            disabledLabelColor = Color.White.copy(alpha = 0.3f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isLocked) {
                        Text(
                            text = "Vous avez épuisé vos 2 tentatives de changement de nom. Vous devez attendre $daysRemaining jour(s) avant de pouvoir le modifier de nouveau.",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "Tentatives de changement de nom restantes : $attemptsLeft/2. Après 2 changements, vous devrez attendre 30 jours.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val trimmedName = editAccountName.trim()
                            val currentName = accountName.ifBlank { userName }.trim()
                            val nameChanged = trimmedName != currentName

                            if (trimmedName.isBlank()) {
                                Toast.makeText(context, "Le nom ne peut pas être vide.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (nameChanged && isLocked) {
                                Toast.makeText(context, "Vous devez attendre la fin de la période de 30 jours.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid == null) {
                                if (nameChanged) {
                                    val newAttempts = nameAttemptsCount + 1
                                    val newCooldownStart = if (newAttempts >= 2) System.currentTimeMillis() else nameCooldownStart
                                    sharedPrefs.edit()
                                        .putString("profile_account_name_$userEmail", trimmedName)
                                        .putInt("name_change_attempts_count_$userEmail", newAttempts)
                                        .putLong("name_change_cooldown_start_$userEmail", newCooldownStart)
                                        .apply()
                                    nameAttemptsCount = newAttempts
                                    nameCooldownStart = newCooldownStart
                                } else {
                                    sharedPrefs.edit()
                                        .putString("profile_account_name_$userEmail", trimmedName)
                                        .apply()
                                }
                                accountName = trimmedName
                                showEditDialog = false
                                return@Button
                            }

                            isSavingData = true
                            saveProfileData(
                                uid = uid,
                                name = trimmedName,
                                email = currentEmail,
                                phone = phoneNumber,
                                emailChanged = false,
                                nameChanged = nameChanged,
                                onComplete = {
                                    isSavingData = false
                                    showEditDialog = false
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            disabledContainerColor = Color.Black.copy(alpha = 0.4f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        enabled = !isSavingData && (!isLocked || (editAccountName.trim() == accountName.ifBlank { userName }.trim())),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSavingData) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Enregistrer", color = Color.White)
                        }
                    }

                    TextButton(
                        onClick = { showEditDialog = false },
                        enabled = !isSavingData,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Annuler", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
fun CircularCountdownTimer(
    modifier: Modifier = Modifier,
    progress: Float,
    topText: String,
    bottomText: String,
    onRefresh: () -> Unit,
    progressColor: Color = Color(0xFFE50914),
    trackColor: Color = Color(0xFF2E3138)
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val safeProgress = progress.coerceIn(0f, 1f)
            val consumedProgress = 1f - safeProgress
            
            // Draw the elapsed / consumed portion in gray
            if (consumedProgress > 0f) {
                drawArc(
                    color = Color.Gray,
                    startAngle = -90f,
                    sweepAngle = consumedProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            
            // Draw the remaining portion in progress color (Red)
            if (safeProgress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f + (consumedProgress * 360f),
                    sweepAngle = safeProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = topText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(1.5f, 1.5f),
                        blurRadius = 3f
                    )
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = bottomText,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(1.5f, 1.5f),
                        blurRadius = 3f
                    )
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualiser",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
