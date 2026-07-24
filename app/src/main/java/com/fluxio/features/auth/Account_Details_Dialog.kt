package com.fluxio.features.auth

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import com.fluxio.shared.components.shimmerEffect
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.foundation.BorderStroke

import android.view.Gravity
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountDetailsDialog(
    userEmail: String,
    sharedPrefs: SharedPreferences,
    onSaveSuccess: (accountName: String, phoneNumber: String) -> Unit
) {
    val cardHeightFraction = 0.65f

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var accountName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isCheckingBackend by remember { mutableStateOf(true) }
    val isDarkTheme = true

    val phoneNumberHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            try {
                val rawPhoneNumber = Identity.getSignInClient(context).getPhoneNumberFromIntent(result.data)
                val cleaned = rawPhoneNumber.filter { it.isDigit() }
                val formatted = if (cleaned.startsWith("261") && cleaned.length > 3) {
                    "0" + cleaned.substring(3)
                } else if (cleaned.length == 9 && (cleaned.startsWith("32") || cleaned.startsWith("33") || cleaned.startsWith("34") || cleaned.startsWith("38") || cleaned.startsWith("37") || cleaned.startsWith("35"))) {
                    "0$cleaned"
                } else {
                    cleaned
                }
                if (formatted.length <= 10) {
                    phoneNumber = formatted
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Echec de la recuperation", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(userEmail) {
        if (userEmail.isBlank() || userEmail == "invite@fluxio.tv") {
            isCheckingBackend = false
            return@LaunchedEffect
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            var found = false
            try {
                FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener { document ->
                        val remoteName = document.getString("accountName")
                        val remotePhone = document.getString("phoneNumber")
                        if (!remoteName.isNullOrBlank() && !remotePhone.isNullOrBlank()) {
                            sharedPrefs.edit()
                                .putString("profile_account_name_$userEmail", remoteName)
                                .putString("profile_phone_$userEmail", remotePhone)
                                .apply()
                            onSaveSuccess(remoteName, remotePhone)
                            found = true
                        }
                        isCheckingBackend = false
                    }
                    .addOnFailureListener {
                        isCheckingBackend = false
                    }
            } catch (e: Exception) {
                isCheckingBackend = false
            }
        } else {
            isCheckingBackend = false
        }
    }

    if (!isCheckingBackend) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .pointerInput(Unit) {},
            contentAlignment = Alignment.BottomCenter
        ) {
                val allowedPrefixes = listOf("034", "038", "032", "037", "033", "035")
                val isPhonePrefixValid = allowedPrefixes.any { phoneNumber.startsWith(it) }
                val isPhoneValid = phoneNumber.length == 10 && isPhonePrefixValid
                val isFormValid = accountName.trim().isNotEmpty() && isPhoneValid

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(cardHeightFraction)
                        .imePadding()
                        .border(
                            1.dp, 
                            if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f), 
                            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .testTag("account_details_dialog"),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color.Black else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    Text(
                        text = "Complétez votre profil",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Nom du Compte") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("account_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF59E0B).copy(alpha = 0.1f)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Votre numero de telephone est necessaire pour vos paiements d'abonnement. Vous pouvez le saisir manuellement ou le detecter automatiquement.",
                                fontSize = 12.sp,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { input ->
                            if (input.length <= 10 && input.all { it.isDigit() }) {
                                phoneNumber = input
                            }
                        },
                        label = { Text("Numero de Telephone") },
                        placeholder = { Text("Ex: 0321234567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("phone_number_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )

                    Button(
                        onClick = {
                            try {
                                val request = GetPhoneNumberHintIntentRequest.builder().build()
                                Identity.getSignInClient(context)
                                    .getPhoneNumberHintIntent(request)
                                    .addOnSuccessListener { pendingIntent ->
                                        try {
                                            val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                            phoneNumberHintLauncher.launch(intentSenderRequest)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erreur de selection", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "Saisie manuelle requise", Toast.LENGTH_SHORT).show()
                                    }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Service non disponible", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remplir automatiquement", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (!isFormValid) return@Button
                            keyboardController?.hide()
                            isSaving = true
                            val finalPhoneNumber = if (phoneNumber.startsWith("0")) {
                                "+261${phoneNumber.drop(1)}"
                            } else {
                                "+261$phoneNumber"
                            }
                            
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid != null) {
                                // Verification de l'unicite du numero de telephone
                                FirebaseFirestore.getInstance().collection("registered_phones").document(finalPhoneNumber).get()
                                    .addOnSuccessListener { document ->
                                        if (document != null && document.exists()) {
                                            val registeredUid = document.getString("uid")
                                            if (!registeredUid.isNullOrEmpty() && registeredUid != uid) {
                                                isSaving = false
                                                Toast.makeText(context, "Ce numero de telephone est deja associe a un autre compte.", Toast.LENGTH_LONG).show()
                                                return@addOnSuccessListener
                                            }
                                        }
                                        
                                        // Enregistrement des informations de profil et liaison du telephone
                                        sharedPrefs.edit()
                                            .putString("profile_account_name_$userEmail", accountName)
                                            .putString("profile_phone_$userEmail", finalPhoneNumber)
                                            .apply()
                                        
                                        val userMap = hashMapOf<String, Any>(
                                            "accountName" to accountName,
                                            "name" to accountName,
                                            "displayName" to accountName,
                                            "phoneNumber" to finalPhoneNumber
                                        )
                                        val phoneMap = hashMapOf<String, Any>(
                                            "uid" to uid
                                        )
                                        
                                        try {
                                            // Firestore
                                            val firestore = FirebaseFirestore.getInstance()
                                            firestore.collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                            firestore.collection("registered_phones").document(finalPhoneNumber).set(phoneMap)
                                        } catch (e: Exception) {
                                            // Ignored
                                        }
                                        
                                        isSaving = false
                                        onSaveSuccess(accountName, finalPhoneNumber)
                                        Toast.makeText(context, "Profil enregistré !", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        // En cas d'erreur de connexion, nous permettons l'enregistrement local
                                        sharedPrefs.edit()
                                            .putString("profile_account_name_$userEmail", accountName)
                                            .putString("profile_phone_$userEmail", finalPhoneNumber)
                                            .apply()
                                        
                                        val userMap = hashMapOf<String, Any>(
                                            "accountName" to accountName,
                                            "name" to accountName,
                                            "displayName" to accountName,
                                            "phoneNumber" to finalPhoneNumber
                                        )
                                        try {
                                            FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                        } catch (e: Exception) {
                                            // Ignored
                                        }
                                        
                                        isSaving = false
                                        onSaveSuccess(accountName, finalPhoneNumber)
                                        Toast.makeText(context, "Profil enregistré !", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                sharedPrefs.edit()
                                    .putString("profile_account_name_$userEmail", accountName)
                                    .putString("profile_phone_$userEmail", finalPhoneNumber)
                                    .apply()
                                isSaving = false
                                onSaveSuccess(accountName, finalPhoneNumber)
                                Toast.makeText(context, "Profil enregistré !", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = isFormValid && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_profile_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isFormValid) Color.White else Color.White.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Enregistrer",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
