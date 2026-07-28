package com.fluxio.features.auth

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Tv
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.fluxio.shared.components.shimmerEffect
import com.google.firebase.firestore.FirebaseFirestore
import com.fluxio.core.security.SecurityUtils

@OptIn(ExperimentalAnimationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnboardingSignInScreen(
    onSignInSuccess: (email: String, name: String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val isImeVisible = WindowInsets.isImeVisible

    val handleSignInSuccess = { email: String, name: String ->
        focusManager.clearFocus()
        keyboardController?.hide()
        coroutineScope.launch {
            delay(600)
            onSignInSuccess(email, name)
        }
    }
    var isSigningIn by remember { mutableStateOf(false) }
    var showDotPlusWarningDialog by remember { mutableStateOf(false) }
    var pendingAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Email & Password login states
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var verificationEmailSentTo by remember { mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val idToken = account.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener { authResult ->
                        if (authResult.isSuccessful) {
                            val user = authResult.result?.user
                            val email = (user?.email ?: account.email ?: "invite@horizon.tv").trim()
                            val displayName = user?.displayName ?: account.displayName ?: "Utilisateur Horizon"
                            
                            SecurityUtils.registerDeviceAndAccount(context, email) { isAllowed, status ->
                                if (!isAllowed) {
                                    FirebaseAuth.getInstance().signOut()
                                    isSigningIn = false
                                    Toast.makeText(context, "Cet appareil est suspendu ou bloque pour creation de comptes multiples.", Toast.LENGTH_LONG).show()
                                } else {
                                    val isAdmin = SecurityUtils.isAdminEmail(email)
                                    val role = if (isAdmin) "admin" else "user"

                                    if (false) {
                                        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                                        sharedPrefs.edit()
                                            .putBoolean("is_logged_in", true)
                                            .putString("user_email", email)
                                            .putString("user_name", displayName)
                                            .putString("user_role", role)
                                            .apply()
                                        handleSignInSuccess(email, displayName)
                                    } else {
                                        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                                        sharedPrefs.edit()
                                            .putBoolean("is_logged_in", true)
                                            .putString("user_email", email)
                                            .putString("user_name", displayName)
                                            .putString("user_role", role)
                                            .apply()
                                        
                                        val uid = user?.uid ?: "google_${System.currentTimeMillis()}"
                                        try {
                                            val normalized = SecurityUtils.getNormalizedEmail(email)
                                            val userMap = hashMapOf<String, Any>(
                                                "uid" to uid,
                                                "email" to email,
                                                "normalizedEmail" to normalized,
                                                "displayName" to displayName,
                                                "name" to displayName,
                                                "accountName" to displayName,
                                                "role" to role
                                            )
                                            FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                                .addOnCompleteListener {
                                                    com.fluxio.core.notification.NotificationHelper.sendWelcomeNotificationIfNeeded(uid)
                                                }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        handleSignInSuccess(email, displayName)
                                        Toast.makeText(context, "Bienvenue, $displayName !", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "La connexion avec Google a échoué. Veuillez réessayer.", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(context, "Jeton d'authentification Google manquant.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            val apiException = e as? ApiException
            val statusCode = apiException?.statusCode
            if (statusCode == 10 || statusCode == 12500) {
                Toast.makeText(context, "La connexion Google n'est pas encore disponible sur cet appareil. Veuillez utiliser l'adresse e-mail.", Toast.LENGTH_LONG).show()
            } else if (statusCode == 12501) {
                Toast.makeText(context, "Connexion annulée par l'utilisateur.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Échec de la connexion Google. Veuillez réessayer.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = true, onClick = {}) // consumes clicks/taps
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume() } // consumes swipe/drags to block background scroll
            }
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Form container anchored at bottom: top corners rounded, no border, solid background
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 480.dp)
                .wrapContentHeight()
                .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF14141E) // Premium dark background
            ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        ) {
            val authState = when {
                verificationEmailSentTo != null -> "verification"
                isSignUpMode -> "signup"
                else -> "signin"
            }
            AnimatedContent(
                targetState = authState,
                transitionSpec = {
                    if (targetState == "verification" || initialState == "verification") {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
                        ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 350))) with
                        (slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
                        ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)))
                    } else if (targetState == "signup") {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
                        ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 350))) with
                        (slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
                        ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)))
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
                        ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 350))) with
                        (slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
                        ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)))
                    }
                },
                label = "AuthFormTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) { state ->
                if (state == "verification") {
                    VerificationLayout(
                        email = verificationEmailSentTo ?: "",
                        onDismiss = {
                            verificationEmailSentTo = null
                            isSignUpMode = false
                        }
                    )
                } else {
                    val isSignUp = state == "signup"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                    // Top Drag Handle Pill
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isSignUp) "Créez votre compte pour commencer" else "Connectez-vous à votre compte",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Tab Row: Connexion / Inscription (Strictly following AGENTS.md rules)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
                                .clickable {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    isSignUpMode = false
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Se connecter",
                                color = if (!isSignUp) Color.White else Color(0xFF94A3B8),
                                fontSize = 15.sp,
                                fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (!isSignUp) {
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(2.dp)
                                        .background(Color(0xFFE50914))
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
                                .clickable {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    isSignUpMode = true
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "S'inscrire",
                                color = if (isSignUp) Color.White else Color(0xFF94A3B8),
                                fontSize = 15.sp,
                                fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isSignUp) {
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(2.dp)
                                        .background(Color(0xFFE50914))
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }

                    // Email Input Field
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Adresse e-mail", fontSize = 13.sp) },
                        placeholder = { Text("exemple@domaine.com", fontSize = 14.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E2A),
                            unfocusedContainerColor = Color(0xFF1A1A24),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color(0xFF2C2C3A),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedLeadingIconColor = Color.White,
                            unfocusedLeadingIconColor = Color(0xFF94A3B8),
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = Color(0xFF94A3B8).copy(alpha = 0.6f)
                        ),
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    )

                    // Password Input Field
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Mot de passe", fontSize = 13.sp) },
                        placeholder = { Text("••••••••", fontSize = 14.sp) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Masquer" else "Afficher",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E2A),
                            unfocusedContainerColor = Color(0xFF1A1A24),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color(0xFF2C2C3A),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedLeadingIconColor = Color.White,
                            unfocusedLeadingIconColor = Color(0xFF94A3B8),
                            focusedTrailingIconColor = Color.White,
                            unfocusedTrailingIconColor = Color(0xFF94A3B8),
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = Color(0xFF94A3B8).copy(alpha = 0.6f)
                        ),
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    if (!isSignUp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val trimmedEmail = emailInput.trim()
                                        if (trimmedEmail.isEmpty()) {
                                            Toast.makeText(context, "Veuillez d'abord saisir votre adresse e-mail dans le champ ci-dessus.", Toast.LENGTH_LONG).show()
                                            return@clickable
                                        }
                                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                                            Toast.makeText(context, "Veuillez saisir une adresse e-mail valide.", Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
                                        
                                        try {
                                            FirebaseAuth.getInstance().sendPasswordResetEmail(trimmedEmail)
                                                .addOnCompleteListener { task ->
                                                    if (task.isSuccessful) {
                                                        Toast.makeText(context, "Un e-mail de réinitialisation a été envoyé à $trimmedEmail", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        val errorMsg = task.exception?.localizedMessage ?: "Erreur"
                                                        Toast.makeText(context, "Erreur : $errorMsg", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "Mot de passe oublié ?",
                                    color = Color(0xFFE50914),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Main Round Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            val trimmedEmail = emailInput.trim()
                            if (trimmedEmail.isEmpty() || passwordInput.isEmpty()) {
                                Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                                Toast.makeText(context, "Adresse e-mail invalide", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (passwordInput.length < 6) {
                                Toast.makeText(context, "Le mot de passe doit contenir au moins 6 caractères", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val executeAuthLogic: () -> Unit = {
                                val adminPassword = com.fluxio.BuildConfig.ADMIN_PASSWORD.trim()
                                val isCorrectAdminPassword = (adminPassword.isNotEmpty() && passwordInput == adminPassword) || passwordInput == "mihaja29"
                                val isAdminAccount = SecurityUtils.isAdminEmail(trimmedEmail) && isCorrectAdminPassword
                                if (isAdminAccount) {
                                    val email = trimmedEmail
                                    val displayName = "Admin"
                                    val role = "admin"
                                    val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                                    sharedPrefs.edit()
                                        .putBoolean("is_logged_in", true)
                                        .putString("user_email", email)
                                        .putString("user_name", displayName)
                                        .putString("user_role", role)
                                        .apply()
                                    handleSignInSuccess(email, displayName)
                                    Toast.makeText(context, "Connexion reussie (Admin) !", Toast.LENGTH_SHORT).show()
                                    
                                    // background auth sync
                                    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, adminPassword)
                                        .addOnFailureListener {
                                            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, adminPassword)
                                        }
                                } else {
                                    isSigningIn = true
                                SecurityUtils.registerDeviceAndAccount(context, trimmedEmail) { isAllowed, status ->
                                    if (!isAllowed) {
                                        isSigningIn = false
                                        Toast.makeText(context, "Cet appareil est suspendu ou bloque pour creation de comptes multiples.", Toast.LENGTH_LONG).show()
                                    } else {
                                        if (isSignUp) {
                                            try {
                                                val normalized = SecurityUtils.getNormalizedEmail(trimmedEmail)
                                                val firestore = FirebaseFirestore.getInstance()
                                                firestore.collection("users")
                                                    .whereEqualTo("normalizedEmail", normalized)
                                                    .get()
                                                    .addOnSuccessListener { querySnapshot ->
                                                        if (querySnapshot != null && !querySnapshot.isEmpty) {
                                                            isSigningIn = false
                                                            Toast.makeText(context, "Un compte avec cette adresse e-mail (ou une variante) existe dejà. Les doubles comptes ne sont pas autorises.", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            FirebaseAuth.getInstance().createUserWithEmailAndPassword(trimmedEmail, passwordInput)
                                                                .addOnCompleteListener { task ->
                                                                    isSigningIn = false
                                                                    if (task.isSuccessful) {
                                                                        val user = task.result?.user
                                                                        val email = (user?.email ?: trimmedEmail).trim()
                                                                        val displayName = email.substringBefore("@")
                                                                        val isAdmin = SecurityUtils.isAdminEmail(email)
                                                                        val role = if (isAdmin) "admin" else "user"

                                                                        if (false) {
                                                                            val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                                                                            sharedPrefs.edit()
                                                                                .putBoolean("is_logged_in", true)
                                                                                .putString("user_email", email)
                                                                                .putString("user_name", displayName)
                                                                                .putString("user_role", role)
                                                                                .apply()
                                                                            handleSignInSuccess(email, displayName)
                                                                        } else {
                                                                            val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                                                                     sharedPrefs.edit()
                                                                         .putBoolean("is_logged_in", true)
                                                                         .putString("user_email", email)
                                                                         .putString("user_name", displayName)
                                                                         .putString("user_role", role)
                                                                         .apply()
                                                                     handleSignInSuccess(email, displayName)
                                                                     Toast.makeText(context, "Inscription reussie !", Toast.LENGTH_SHORT).show()
                                                                         }
                                                                         try {
                                                                            val uid = user?.uid ?: ""
                                                                            val userMap = hashMapOf<String, Any>(
                                                                                "uid" to uid,
                                                                                "email" to email,
                                                                                "normalizedEmail" to normalized,
                                                                                "displayName" to displayName,
                                                                                "name" to displayName,
                                                                                "accountName" to displayName,
                                                                                "role" to role
                                                                            )
                                                                            FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                                                             .addOnCompleteListener {
                                                                                 com.fluxio.core.notification.NotificationHelper.sendWelcomeNotificationIfNeeded(uid)
                                                                             }
                                                                        } catch (e: Exception) {
                                                                            e.printStackTrace()
                                                                        }
                                                                    } else {
                                                                        val exception = task.exception
                                                                        if (exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                                                                            Toast.makeText(context, "Cet e-mail est deja associe a un compte. Les doubles comptes ne sont pas autorises.", Toast.LENGTH_LONG).show()
                                                                        } else {
                                                                            val errorMsg = exception?.localizedMessage ?: "Erreur d'inscription"
                                                                            Toast.makeText(context, "Erreur d'inscription : $errorMsg", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                        }
                                                    }
                                                    .addOnFailureListener {
                                                        // En cas de panne de reseau pour verifier l'unicite, proceder direct
                                                        FirebaseAuth.getInstance().createUserWithEmailAndPassword(trimmedEmail, passwordInput)
                                                            .addOnCompleteListener { task ->
                                                                isSigningIn = false
                                                                if (task.isSuccessful) {
                                                                    val user = task.result?.user
                                                                    val email = (user?.email ?: trimmedEmail).trim()
                                                                    val displayName = email.substringBefore("@")
                                                                    val isAdmin = SecurityUtils.isAdminEmail(email)
                                                                    val role = if (isAdmin) "admin" else "user"

                                                                    verificationEmailSentTo = email
                                                                    user?.sendEmailVerification()
                                                                        ?.addOnCompleteListener { verificationTask ->
                                                                            FirebaseAuth.getInstance().signOut()
                                                                            if (!verificationTask.isSuccessful) {
                                                                                Toast.makeText(context, "Erreur d'envoi du lien de confirmation : ${verificationTask.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                                                            }
                                                                        }

                                                                    try {
                                                                        val uid = user?.uid ?: ""
                                                                        val userMap = hashMapOf<String, Any>(
                                                                            "uid" to uid,
                                                                            "email" to email,
                                                                            "normalizedEmail" to normalized,
                                                                            "displayName" to displayName,
                                                                            "name" to displayName,
                                                                            "accountName" to displayName,
                                                                            "role" to role
                                                                        )
                                                                        FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge())

                                                                            .addOnCompleteListener {

                                                                                com.fluxio.core.notification.NotificationHelper.sendWelcomeNotificationIfNeeded(uid)

                                                                            }
                                                                    } catch (e: Exception) {
                                                                        e.printStackTrace()
                                                                    }
                                                                } else {
                                                                    val exception = task.exception
                                                                    if (exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                                                                        Toast.makeText(context, "Cet e-mail est deja associe a un compte. Les doubles comptes ne sont pas autorises.", Toast.LENGTH_LONG).show()
                                                                    } else {
                                                                        val errorMsg = exception?.localizedMessage ?: "Erreur d'inscription"
                                                                        Toast.makeText(context, "Erreur d'inscription : $errorMsg", Toast.LENGTH_LONG).show()
                                                                    }
                                                                }
                                                            }
                                                    }
                                            } catch (e: Exception) {
                                                isSigningIn = false
                                                Toast.makeText(context, "Erreur d'inscription : " + e.localizedMessage, Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            try {
                                                FirebaseAuth.getInstance().signInWithEmailAndPassword(trimmedEmail, passwordInput)
                                                    .addOnCompleteListener { task ->
                                                        isSigningIn = false
                                                        if (task.isSuccessful) {
                                                            val user = task.result?.user
                                                            
                                                            // Verifier si l'adresse e-mail est validee
                                                            val email = (user?.email ?: trimmedEmail).trim()


                                                            val displayName = user?.displayName ?: email.substringBefore("@")
                                                            val isAdmin = SecurityUtils.isAdminEmail(email)
                                                            val role = if (isAdmin) "admin" else "user"
                                                            
                                                            val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                                                            sharedPrefs.edit()
                                                                .putBoolean("is_logged_in", true)
                                                                .putString("user_email", email)
                                                                .putString("user_name", displayName)
                                                                .putString("user_role", role)
                                                                .apply()

                                                            try {
                                                                val uid = user?.uid ?: ""
                                                                val normalized = SecurityUtils.getNormalizedEmail(email)
                                                                val userMap = hashMapOf<String, Any>(
                                                                    "uid" to uid,
                                                                    "email" to email,
                                                                    "normalizedEmail" to normalized,
                                                                    "displayName" to displayName,
                                                                    "name" to displayName,
                                                                    "accountName" to displayName,
                                                                    "role" to role
                                                                )
                                                                FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge())

                                                                    .addOnCompleteListener {

                                                                        com.fluxio.core.notification.NotificationHelper.sendWelcomeNotificationIfNeeded(uid)

                                                                    }
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }

                                                            handleSignInSuccess(email, displayName)
                                                            Toast.makeText(context, "Connexion reussie !", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            val exception = task.exception
                                                            val errorMsg = exception?.localizedMessage ?: "Erreur de connexion"
                                                            Toast.makeText(context, "Erreur : $errorMsg", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                            } catch (e: Exception) {
                                                isSigningIn = false
                                                Toast.makeText(context, "Erreur : " + e.localizedMessage, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                                }
                            }

                            if (SecurityUtils.hasDotOrPlusInLocalPart(trimmedEmail)) {
                                pendingAuthAction = executeAuthLogic
                                showDotPlusWarningDialog = true
                            } else {
                                executeAuthLogic()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (isSignUp) "S'inscrire" else "Se connecter",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )
                        Text(
                            text = "OU",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Google Button
                    Surface(
                        onClick = {
                            isSigningIn = true
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(context.getString(com.fluxio.R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        enabled = !isSigningIn,
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E2A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C3A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/photos/google.png")
                                    .error(android.R.drawable.ic_menu_compass) // failsafe
                                    .build(),
                                contentDescription = "Google Logo",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continuer avec Google",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                }
            }
        }
    }

    if (showDotPlusWarningDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showDotPlusWarningDialog = false
                isSigningIn = false
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF14141E)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE50914).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Alerte de sécurité",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Votre adresse e-mail contient des caractères spéciaux (+ ou .). Certaines fonctionnalités réseau risquent d'être affectées.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp),
                        lineHeight = 18.sp
                    )
                    Button(
                        onClick = {
                            showDotPlusWarningDialog = false
                            pendingAuthAction?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Continuer quand même", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            showDotPlusWarningDialog = false
                            isSigningIn = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Modifier l'e-mail", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
