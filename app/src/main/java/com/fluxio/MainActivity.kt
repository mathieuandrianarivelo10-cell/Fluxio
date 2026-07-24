package com.fluxio

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- OUR IMPORTED COMPONENTS & MODELS & UTILS ---
import com.fluxio.shared.models.*
import com.fluxio.core.security.*
import com.fluxio.features.player.*
import com.fluxio.shared.theme.*
import com.fluxio.shared.components.*
import com.fluxio.features.auth.*
import com.fluxio.features.iptv.*
import com.fluxio.features.admin.*

class MainActivity : FragmentActivity() {

    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force fresh build and install trigger
        enableEdgeToEdge()

        sharedPrefs = getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)

        val securityPrefs = getSharedPreferences("horizon_security", Context.MODE_PRIVATE)
        securityPrefs.edit().putString("device_status", "active").apply()

        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        val immersiveDefault = sharedPrefs.getBoolean("immersive_mode", true)
        setImmersiveMode(immersiveDefault)

        setContent {
            var isDarkTheme by remember {
                mutableStateOf(true)
            }
            FluxioTheme(darkTheme = isDarkTheme) {
                val context = LocalContext.current
                
                val currentUser = remember {
                    try {
                        FirebaseAuth.getInstance().currentUser
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        null
                    }
                }
                var isUserLoggedIn by remember {
                    val loggedIn = sharedPrefs.getBoolean("is_logged_in", false) || currentUser != null
                    if (loggedIn && !sharedPrefs.getBoolean("is_logged_in", false)) {
                        sharedPrefs.edit()
                            .putBoolean("is_logged_in", true)
                            .putString("user_email", currentUser?.email ?: "")
                            .putString("user_name", currentUser?.displayName ?: "")
                            .apply()
                    }
                    mutableStateOf(loggedIn)
                }
                var userEmail by remember {
                    mutableStateOf(sharedPrefs.getString("user_email", "")?.ifEmpty { null } ?: currentUser?.email ?: "")
                }
                var userName by remember {
                    mutableStateOf(sharedPrefs.getString("user_name", "")?.ifEmpty { null } ?: currentUser?.displayName ?: "")
                }
                var immersiveModeEnabled by remember { 
                    mutableStateOf(sharedPrefs.getBoolean("immersive_mode", true)) 
                }
                var biometricLockEnabled by remember { 
                    mutableStateOf(sharedPrefs.getBoolean("biometric_enabled", false)) 
                }
                var isUnlocked by remember { 
                    mutableStateOf(!biometricLockEnabled) 
                }
                var hasCompletedProfile by remember(isUserLoggedIn, userEmail) {
                    mutableStateOf(
                        if (!isUserLoggedIn || userEmail.isBlank() || userEmail == "invite@fluxio.tv") {
                            true
                        } else {
                            val name = sharedPrefs.getString("profile_account_name_$userEmail", "")
                            val phone = sharedPrefs.getString("profile_phone_$userEmail", "")
                            if (!name.isNullOrBlank() && !phone.isNullOrBlank()) {
                                true
                            } else {
                                // Default to true initially to let LaunchedEffect fetch the existing profile from Firestore.
                                // If no profile is found in the cloud, the LaunchedEffect will set hasCompletedProfile = false.
                                true
                            }
                        }
                    )
                }

                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(2000)
                    showSplash = false
                }

                var deviceStatus by remember { mutableStateOf("active") }
                var isUserBlocked by remember { mutableStateOf(false) }
                var needsDeviceVerification by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    try {
                        SecurityUtils.checkDeviceStatus(context) { status ->
                            deviceStatus = status
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }

                LaunchedEffect(isUserLoggedIn, userEmail) {
                    try {
                        if (isUserLoggedIn && userEmail.isNotEmpty() && userEmail != "invite@fluxio.tv" && !SecurityUtils.isAdminEmail(userEmail)) {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            SecurityUtils.checkUserDeviceMatch(context, userEmail, uid) { isMatch, needsVerification ->
                                needsDeviceVerification = needsVerification
                            }
                        } else {
                            needsDeviceVerification = false
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }

                LaunchedEffect(isUserLoggedIn, userEmail) {
                    try {
                        if (isUserLoggedIn && userEmail.isNotBlank() && userEmail != "invite@fluxio.tv") {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid != null) {
                                try {
                                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    if (com.fluxio.core.security.SecurityUtils.isAdminEmail(userEmail)) {
                                        sharedPrefs.edit().putString("user_role", "admin").apply()
                                        val adminMap = mapOf(
                                            "uid" to uid,
                                            "email" to userEmail,
                                            "role" to "admin"
                                        )
                                        firestore.collection("users").document(uid).set(adminMap, com.google.firebase.firestore.SetOptions.merge())
                                    }
                                    firestore.collection("users").document(uid).addSnapshotListener { doc, error ->
                                        if (error != null) {
                                            val name = sharedPrefs.getString("profile_account_name_$userEmail", "")
                                            val phone = sharedPrefs.getString("profile_phone_$userEmail", "")
                                            hasCompletedProfile = !name.isNullOrBlank() && !phone.isNullOrBlank()
                                            return@addSnapshotListener
                                        }
                                        if (doc != null && doc.exists()) {
                                            isUserBlocked = doc.getBoolean("isBlocked") == true || doc.getString("status") == "blocked"
                                            val accountName = doc.getString("accountName") ?: doc.getString("name") ?: doc.getString("displayName")
                                            val phoneNumber = doc.getString("phoneNumber")
                                            
                                            // Fetch and restore subscription status
                                            val serverSub = doc.getString("subscriptionType") ?: "gratuit"
                                            val serverExpiry = doc.getLong("subscriptionExpiresAt") ?: 0L
                                            val serverTotalDuration = doc.getLong("subscriptionTotalDuration") ?: (24 * 60 * 60 * 1000L)
                                            val nameChangeAttemptsCount = doc.getLong("nameChangeAttemptsCount")?.toInt() ?: 0
                                            val nameChangeCooldownStart = doc.getLong("nameChangeCooldownStart") ?: 0L
                                            
                                            val editor = sharedPrefs.edit()
                                                .putString("subscription_type_$userEmail", serverSub)
                                                .putLong("subscription_expires_at_$userEmail", serverExpiry)
                                                .putLong("subscription_total_duration_$userEmail", serverTotalDuration)
                                                .putInt("name_change_attempts_count_$userEmail", nameChangeAttemptsCount)
                                                .putLong("name_change_cooldown_start_$userEmail", nameChangeCooldownStart)
                                            
                                            if (!accountName.isNullOrBlank() && !phoneNumber.isNullOrBlank()) {
                                                editor.putString("profile_account_name_$userEmail", accountName)
                                                    .putString("profile_phone_$userEmail", phoneNumber)
                                                userName = accountName
                                                hasCompletedProfile = true
                                            } else {
                                                hasCompletedProfile = false
                                            }
                                            editor.apply()
                                            
                                            // Restore favorites from Firestore
                                            val remoteFavorites = (doc.get("favorites") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                            if (remoteFavorites.isNotEmpty()) {
                                                sharedPrefs.edit().putStringSet("favorite_ids", remoteFavorites.toSet()).apply()
                                                val dbRepository = com.fluxio.core.database.DatabaseRepository(context)
                                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                                    for (favId in remoteFavorites) {
                                                        dbRepository.insertFavorite(com.fluxio.core.database.FavoriteChannelEntity(favId, userEmail))
                                                    }
                                                }
                                            }
                                        } else {
                                            hasCompletedProfile = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                try {
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("online_presence")
                                        .document(uid)
                                        .set(mapOf("status" to "online", "lastSeen" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                try {
                                    com.google.firebase.database.FirebaseDatabase.getInstance()
                                        .getReference("online_presence")
                                        .child(uid)
                                        .setValue(mapOf("status" to "online", "lastSeen" to System.currentTimeMillis()))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }

                LaunchedEffect(immersiveModeEnabled) {
                    setImmersiveMode(immersiveModeEnabled)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("file:///android_asset/photos/logo_original.png")
                                    .error(com.fluxio.R.drawable.logo_original)
                                    .fallback(com.fluxio.R.drawable.logo_original)
                                    .build(),
                                contentDescription = "Logo de l'application",
                                modifier = Modifier.size(240.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(if (!isUserLoggedIn) 16.dp else 0.dp)
                            ) {
                            MainAppContent(
                                isDarkTheme = isDarkTheme,
                                onIsDarkThemeChange = { enabled ->
                                    isDarkTheme = enabled
                                    sharedPrefs.edit().putBoolean("dark_theme", enabled).apply()
                                },
                                immersiveModeEnabled = immersiveModeEnabled,
                                onImmersiveModeToggle = { enabled ->
                                    immersiveModeEnabled = enabled
                                    sharedPrefs.edit().putBoolean("immersive_mode", enabled).apply()
                                },
                                biometricLockEnabled = biometricLockEnabled,
                                onBiometricLockToggle = { enabled ->
                                    BiometricAuthHelper.triggerBiometricPrompt(
                                        activity = this@MainActivity,
                                        onSuccess = {
                                            biometricLockEnabled = enabled
                                            sharedPrefs.edit().putBoolean("biometric_enabled", enabled).apply()
                                            if (!enabled) {
                                                isUnlocked = true
                                            }
                                            Toast.makeText(context, if (enabled) "Verrouillage activé" else "Verrouillage désactivé", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = {
                                            Toast.makeText(context, "Action annulée ou échouée", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                sharedPrefs = sharedPrefs,
                                userEmail = if (isUserLoggedIn) userEmail else "invite@fluxio.tv",
                                userName = if (isUserLoggedIn) userName else "Utilisateur Fluxio",
                                onLogoutClick = {
                                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                    if (uid != null) {
                                        try {
                                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                .collection("online_presence")
                                                .document(uid)
                                                .set(mapOf("status" to "offline", "lastSeen" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
                                        } catch (e: Exception) { e.printStackTrace() }

                                        try {
                                            com.google.firebase.database.FirebaseDatabase.getInstance()
                                                .getReference("online_presence")
                                                .child(uid)
                                                .setValue(mapOf("status" to "offline", "lastSeen" to System.currentTimeMillis()))
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                    FirebaseAuth.getInstance().signOut()
                                    sharedPrefs.edit()
                                        .putBoolean("is_logged_in", false)
                                        .putString("user_email", "")
                                        .putString("user_name", "")
                                        .putString("user_role", "")
                                        .apply()
                                    isUserLoggedIn = false
                                },
                                onDeleteAccountClick = {
                                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                    val uid = currentUser?.uid
                                    val email = currentUser?.email ?: userEmail
                                    if (uid != null) {
                                        try {
                                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                .collection("online_presence")
                                                .document(uid)
                                                .set(mapOf("status" to "offline"), com.google.firebase.firestore.SetOptions.merge())
                                        } catch (e: Exception) { e.printStackTrace() }

                                        try {
                                            com.google.firebase.database.FirebaseDatabase.getInstance()
                                                .getReference("online_presence")
                                                .child(uid)
                                                .setValue(mapOf("status" to "offline", "lastSeen" to System.currentTimeMillis()))
                                        } catch (e: Exception) { e.printStackTrace() }
                                        
                                        try {
                                            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).delete()
                                        } catch (e: Exception) { e.printStackTrace() }

                                        // Clear double account / device check records for this user
                                        try {
                                            val deviceId = com.fluxio.core.security.SecurityUtils.getDeviceID(this@MainActivity)
                                            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("devices").document(deviceId).delete()
                                        } catch (e: Exception) { e.printStackTrace() }

                                        try {
                                            val secPrefs = getSharedPreferences("horizon_security", Context.MODE_PRIVATE)
                                            secPrefs.edit().remove("user_device_$email").apply()
                                        } catch (e: Exception) { e.printStackTrace() }

                                        currentUser.delete()
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    Toast.makeText(this@MainActivity, "Compte supprimé avec succès", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Compte local nettoyé. Suppression Auth: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                                }
                                                FirebaseAuth.getInstance().signOut()
                                                sharedPrefs.edit()
                                                    .putBoolean("is_logged_in", false)
                                                    .putString("user_email", "")
                                                    .putString("user_name", "")
                                                    .putString("user_role", "")
                                                    .putBoolean("biometric_enabled", false)
                                                    .apply()
                                                biometricLockEnabled = false
                                                isUnlocked = true
                                                isUserLoggedIn = false
                                            }
                                    } else {
                                        Toast.makeText(this@MainActivity, "Impossible de supprimer : non connecté", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = !isUserLoggedIn,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            OnboardingSignInScreen(
                                onSignInSuccess = { email, name ->
                                    val cleanEmail = email.trim()
                                    val isAdmin = com.fluxio.core.security.SecurityUtils.isAdminEmail(cleanEmail)
                                    val role = if (isAdmin) "admin" else "user"
                                    
                                    userEmail = cleanEmail
                                    userName = name
                                    sharedPrefs.edit()
                                        .putBoolean("is_logged_in", true)
                                        .putString("user_email", cleanEmail)
                                        .putString("user_name", name)
                                        .putString("user_role", role)
                                        .apply()
                                    
                                    // Enregistrer les données dans Firestore (sans effacer les données existantes)
                                    try {
                                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "demo_user_${System.currentTimeMillis()}"
                                        val userMap = hashMapOf<String, Any>(
                                            "uid" to uid,
                                            "email" to email,
                                            "displayName" to name,
                                            "name" to name,
                                            "accountName" to name,
                                            "role" to role
                                        )
                                        FirebaseFirestore.getInstance().collection("users").document(uid)
                                            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    
                                    isUserLoggedIn = true
                                }
                            )
                        }

                        LaunchedEffect(isUserLoggedIn, biometricLockEnabled, isUnlocked) {
                            if (isUserLoggedIn && biometricLockEnabled && !isUnlocked) {
                                BiometricAuthHelper.triggerBiometricPrompt(
                                    activity = this@MainActivity,
                                    onSuccess = { isUnlocked = true },
                                    onFailure = { 
                                        Toast.makeText(context, "Authentification échouée", Toast.LENGTH_SHORT).show() 
                                    }
                                )
                            }
                        }

                        if (isUserLoggedIn && biometricLockEnabled && !isUnlocked) {
                            BiometricLockScreen(
                                onUnlockClick = {
                                    BiometricAuthHelper.triggerBiometricPrompt(
                                        activity = this@MainActivity,
                                        onSuccess = { isUnlocked = true },
                                        onFailure = { 
                                            Toast.makeText(context, "Authentification échouée", Toast.LENGTH_SHORT).show() 
                                        }
                                    )
                                }
                            )
                        }

                        if (isUserLoggedIn && !hasCompletedProfile && userEmail.isNotBlank() && userEmail != "invite@fluxio.tv") {
                            AccountDetailsDialog(
                                userEmail = userEmail,
                                sharedPrefs = sharedPrefs,
                                onSaveSuccess = { _, _ ->
                                    hasCompletedProfile = true
                                }
                            )
                        }

                        if (isUserLoggedIn && isUserBlocked && !com.fluxio.core.security.SecurityUtils.isAdminEmail(userEmail)) {
                            // User Blocked Screen
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .clickable(enabled = true, onClick = {}),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Compte suspendu",
                                        tint = com.fluxio.shared.theme.RedPrimary,
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Votre compte a été suspendu",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Cet utilisateur a été bloqué par l'administration pour violation de nos conditions d'utilisation.\n\nVeuillez contacter le support si vous pensez qu'il s'agit d'une erreur.",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (false) { // deviceStatus == "blocked"
                            BlockedDeviceScreen()
                        }

                        if (false) { // deviceStatus == "suspended"
                            SuspendedDeviceScreen()
                        }

                        if (isUserLoggedIn && needsDeviceVerification) {
                            NewDeviceVerificationScreen(
                                userEmail = userEmail,
                                onVerificationConfirmed = {
                                    needsDeviceVerification = false
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isEnabled = sharedPrefs.getBoolean("immersive_mode", true)
        setImmersiveMode(isEnabled)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (com.fluxio.features.player.PipHelper.isVideoPlaying) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.fluxio.features.player.PipHelper.isInPipMode = isInPictureInPictureMode
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val isEnabled = sharedPrefs.getBoolean("immersive_mode", true)
            setImmersiveMode(isEnabled)
        }
    }

    private fun setImmersiveMode(enable: Boolean) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = if (enable) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }

            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (windowInsetsController != null) {
                if (enable) {
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            // Fallback and layout stability flag reinforcement to prevent "flattening"
            val flags = if (enable) {
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            } else {
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
            window.decorView.systemUiVisibility = flags
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

}
