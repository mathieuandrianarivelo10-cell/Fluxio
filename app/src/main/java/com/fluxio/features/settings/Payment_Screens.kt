package com.fluxio.features.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.telephony.SmsMessage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun PaymentScreen(
    pkg: Triple<String, String, String>,
    initialPhone: String,
    registeredPhone: String,
    onBack: () -> Unit,
    onPaymentSuccess: (String, String, String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var phoneInput by remember { mutableStateOf(initialPhone) }
    val detectedOperator = remember(phoneInput) { detectOperator(phoneInput) }

    var validationError by remember { mutableStateOf<String?>(null) }
    var isWaitingForSms by remember { mutableStateOf(false) }
    var receivedSmsDetails by remember { mutableStateOf<String?>(null) }
    var detectedRef by remember { mutableStateOf<String?>(null) }
    var isSuccessState by remember { mutableStateOf(false) }

    val amount = pkg.second.replace("\\D".toRegex(), "")
    val ussdCode = when (detectedOperator) {
        "Mvola" -> "#111*1*2*0342736487*${amount}*2*0#"
        "Orange Money" -> "#144*1*1*0379205911*0379205911*1*${amount}*2#"
        "Airtel Money" -> "*436*1*2*0332962062*0332962062*${amount}#"
        else -> null
    }

    val launchPaymentFlow = {
        if (ussdCode != null) {
            try {
                val hasCallPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CALL_PHONE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasCallPermission) {
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(ussdCode)}"))
                    context.startActivity(intent)
                } else {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(ussdCode)}"))
                    context.startActivity(intent)
                }
                isWaitingForSms = true
                validationError = null
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(ussdCode)}"))
                    context.startActivity(intent)
                    isWaitingForSms = true
                    validationError = null
                } catch (ex: Exception) {
                    Toast.makeText(context, "Erreur lors du lancement de l'USSD : ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val hasRequiredPermissions = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_SMS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
    androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECEIVE_SMS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
    androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CALL_PHONE
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        launchPaymentFlow()
    }

    DisposableEffect(isWaitingForSms, detectedOperator) {
        val intentFilter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.provider.Telephony.SMS_RECEIVED") {
                    val bundle = intent.extras
                    if (bundle != null) {
                        try {
                            val pdus = bundle.get("pdus") as? Array<*>
                            if (pdus != null) {
                                for (pdu in pdus) {
                                    val sms = SmsMessage.createFromPdu(pdu as ByteArray)
                                    val sender = sms.originatingAddress ?: ""
                                    val body = sms.messageBody ?: ""
                                    
                                    if (isTransactionSms(sender, body, detectedOperator ?: "")) {
                                        val ref = extractTransactionRef(body)
                                        receivedSmsDetails = body
                                        detectedRef = ref
                                        isSuccessState = true
                                        isWaitingForSms = false
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        if (isWaitingForSms) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, intentFilter)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(isWaitingForSms) {
        if (isWaitingForSms) {
            while (isWaitingForSms) {
                delay(3000)
                scanInboxForTransaction(context, detectedOperator ?: "") { sender, body ->
                    val ref = extractTransactionRef(body)
                    receivedSmsDetails = body
                    detectedRef = ref
                    isSuccessState = true
                    isWaitingForSms = false
                }
            }
        }
    }

    if (isSuccessState) {
        SuccessScreen(
            operator = detectedOperator ?: "Inconnu",
            ref = detectedRef ?: "N/A",
            details = receivedSmsDetails ?: "Paiement réussi.",
            onFinish = {
                onPaymentSuccess(phoneInput, detectedRef ?: "N/A", receivedSmsDetails ?: "Paiement réussi.")
            }
        )
    } else if (isWaitingForSms) {
        WaitingScreen(
            ussdCode = ussdCode ?: "",
            operator = detectedOperator ?: "",
            amount = amount,
            onCancel = { isWaitingForSms = false },
            onManualCheck = {
                scanInboxForTransaction(context, detectedOperator ?: "") { sender, body ->
                    val ref = extractTransactionRef(body)
                    receivedSmsDetails = body
                    detectedRef = ref
                    isSuccessState = true
                    isWaitingForSms = false
                }
            },
            onLaunchUssd = { launchPaymentFlow() }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    keyboardController?.hide()
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Paiement Mobile Money",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Abonnement : " + pkg.first,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pkg.third,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Montant à payer :",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Text(
                            text = pkg.second,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Numéro de téléphone",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '+' }
                            if (filtered.length <= 13) {
                                phoneInput = filtered
                            }
                        },
                        placeholder = { Text("Ex: 034 12 345 67", color = Color.White.copy(alpha = 0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (detectedOperator != null) "Méthode de paiement :" else "Méthodes acceptées :",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detectedOperator ?: "Non détecté",
                            color = if (detectedOperator != null) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            validationError?.let { err ->
                Text(
                    text = err,
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isButtonEnabled = detectedOperator != null && phoneInput.length >= 9

                Button(
                    onClick = {
                        if (isButtonEnabled) {
                            keyboardController?.hide()
                            
                            val cleanInput = phoneInput.replace("\\D".toRegex(), "")
                            val cleanRegistered = registeredPhone.replace("\\D".toRegex(), "")
                            val isMatching = cleanInput.isNotEmpty() && cleanRegistered.isNotEmpty() &&
                                    (cleanInput.takeLast(9) == cleanRegistered.takeLast(9))
                            
                            if (!isMatching) {
                                validationError = "Le numéro saisi ne correspond pas à celui enregistré lors de l'inscription (${registeredPhone.ifBlank { "Aucun" }})."
                            } else {
                                validationError = null
                                if (hasRequiredPermissions) {
                                    launchPaymentFlow()
                                } else {
                                    permissionsLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_SMS,
                                            android.Manifest.permission.RECEIVE_SMS,
                                            android.Manifest.permission.CALL_PHONE
                                        )
                                    )
                                }
                            }
                        }
                    },
                    enabled = isButtonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE50914),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE50914).copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Confirmer le paiement", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        keyboardController?.hide()
                        onBack()
                    },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Retour aux offres", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WaitingScreen(
    ussdCode: String,
    operator: String,
    amount: String,
    onCancel: () -> Unit,
    onManualCheck: () -> Unit,
    onLaunchUssd: () -> Unit
) {
    val userEmail = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "" }
    val isAdmin = remember(userEmail) { com.fluxio.core.security.SecurityUtils.isAdminEmail(userEmail) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 4.dp,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Attente de confirmation...",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(alphaAnim)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Instructions :",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAdmin) {
                        "1. Le code USSD suivant a été lancé sur votre téléphone. Si ce n'est pas le cas, composez-le manuellement en appuyant sur le bouton ci-dessous :"
                    } else {
                        "1. Le paiement a été initié automatiquement sur votre téléphone. Si ce n'est pas le cas, appuyez sur le bouton ci-dessous pour lancer l'appel :"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(8.dp))
                        .clickable { onLaunchUssd() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Lancer l'appel de paiement",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAdmin) ussdCode else "Lancer l'appel de paiement ($operator)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Text(
                    text = if (isAdmin) {
                        "Appuyez sur le code ci-dessus pour lancer ou relancer directement l'appel USSD."
                    } else {
                        "Appuyez sur le bouton ci-dessus pour lancer ou relancer directement l'appel."
                    },
                    color = Color(0xFFFCA5A5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "2. Entrez votre code secret mobile money sur la boîte de dialogue USSD qui s'affiche pour valider le montant de $amount Ar.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        
        Button(
            onClick = onManualCheck,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE50914),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Vérifier la réception du SMS", fontWeight = FontWeight.Bold, color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        OutlinedButton(
            onClick = onCancel,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Annuler l'achat", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AnimatedCheckmark(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF10B981),
    durationMillis: Int = 800
) {
    val checkProgress = remember { Animatable(0f) }
    val circleProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        circleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis / 2, easing = LinearEasing)
        )
        checkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis / 2, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = (width.coerceAtMost(height) / 2f) - 4.dp.toPx()
        val centerX = width / 2f
        val centerY = height / 2f

        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius,
            center = Offset(centerX, centerY)
        )

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * circleProgress.value,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            size = Size(radius * 2, radius * 2),
            topLeft = Offset(centerX - radius, centerY - radius)
        )

        if (checkProgress.value > 0f) {
            val path = Path()
            val startX = centerX - radius * 0.4f
            val startY = centerY
            
            val cornerX = centerX - radius * 0.1f
            val cornerY = centerY + radius * 0.3f
            
            val endX = centerX + radius * 0.45f
            val endY = centerY - radius * 0.35f

            path.moveTo(startX, startY)
            
            if (checkProgress.value <= 0.4f) {
                val p = checkProgress.value / 0.4f
                val currentX = startX + (cornerX - startX) * p
                val currentY = startY + (cornerY - startY) * p
                path.lineTo(currentX, currentY)
            } else {
                path.lineTo(cornerX, cornerY)
                val p = (checkProgress.value - 0.4f) / 0.6f
                val currentX = cornerX + (endX - cornerX) * p
                val currentY = cornerY + (endY - cornerY) * p
                path.lineTo(currentX, currentY)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun CheckinAnimationDialog(
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        com.fluxio.core.notification.Subscription_Sound_Player.playSuccessSong()
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(0.5.dp, Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedCheckmark(
                    modifier = Modifier.size(90.dp),
                    color = Color(0xFF10B981)
                )

                Text(
                    text = "Paiement Validé !",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Votre transaction a été traitée avec succès. Votre accès Premium est prêt !",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Continuer", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SuccessScreen(
    operator: String,
    ref: String,
    details: String,
    onFinish: () -> Unit
) {
    var showCheckinDialog by remember { mutableStateOf(true) }

    if (showCheckinDialog) {
        CheckinAnimationDialog(onDismiss = { showCheckinDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedCheckmark(
            modifier = Modifier.size(100.dp),
            color = Color(0xFF10B981)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Paiement Validé !",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Votre transaction a été interceptée et validée.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(0.5.dp, Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Opérateur", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(operator, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                Divider(color = Color.White.copy(alpha = 0.08f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Référence", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(ref, color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                Divider(color = Color.White.copy(alpha = 0.08f))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Message de confirmation", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color.White, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = details,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Valider", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

fun isTransactionSms(sender: String, body: String, operator: String): Boolean {
    val cleanSender = sender.lowercase().trim()
    val cleanBody = body.lowercase()
    
    return when (operator) {
        "Airtel Money" -> {
            cleanSender.contains("4783566639") || 
            cleanSender.contains("airtel") ||
            cleanBody.contains("airtel")
        }
        "Mvola" -> {
            cleanSender.contains("8652") || 
            cleanSender.contains("mvola") ||
            cleanBody.contains("mvola")
        }
        "Orange Money" -> {
            cleanSender.contains("7264366639") || 
            cleanSender.contains("orange") ||
            cleanBody.contains("orange")
        }
        else -> false
    }
}

fun extractTransactionRef(body: String): String {
    val patterns = listOf(
        "ref\\s*:\\s*([A-Za-z0-9._-]+)",
        "reference\\s*:\\s*([A-Za-z0-9._-]+)",
        "référence\\s*:\\s*([A-Za-z0-9._-]+)",
        "txid\\s*:\\s*([A-Za-z0-9._-]+)",
        "id\\s*:\\s*([A-Za-z0-9._-]+)",
        "trans\\s*:\\s*([A-Za-z0-9._-]+)",
        "([A-Z0-9]{10,20})"
    )
    for (pattern in patterns) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val match = regex.find(body)
        if (match != null) {
            return match.groups[1]?.value ?: match.value
        }
    }
    return "N/A"
}

fun scanInboxForTransaction(context: Context, operator: String, onMatched: (String, String) -> Unit) {
    try {
        val uri = Uri.parse("content://sms/inbox")
        val cursor = context.contentResolver.query(
            uri,
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC LIMIT 15"
        )
        cursor?.use { c ->
            val addressIndex = c.getColumnIndex("address")
            val bodyIndex = c.getColumnIndex("body")
            val dateIndex = c.getColumnIndex("date")
            
            if (addressIndex >= 0 && bodyIndex >= 0 && dateIndex >= 0) {
                val currentTime = System.currentTimeMillis()
                while (c.moveToNext()) {
                    val sender = c.getString(addressIndex) ?: ""
                    val body = c.getString(bodyIndex) ?: ""
                    val date = c.getLong(dateIndex)
                    
                    if (currentTime - date < 5 * 60 * 1000) {
                        if (isTransactionSms(sender, body, operator)) {
                            onMatched(sender, body)
                            break
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun detectOperator(phone: String): String? {
    val clean = phone.replace("\\s".toRegex(), "").trim()
    return when {
        clean.startsWith("034") || clean.startsWith("038") -> "Mvola"
        clean.startsWith("032") || clean.startsWith("037") -> "Orange Money"
        clean.startsWith("033") || clean.startsWith("035") -> "Airtel Money"
        clean.startsWith("34") || clean.startsWith("38") -> "Mvola"
        clean.startsWith("32") || clean.startsWith("37") -> "Orange Money"
        clean.startsWith("33") || clean.startsWith("35") -> "Airtel Money"
        clean.startsWith("+26134") || clean.startsWith("+26138") -> "Mvola"
        clean.startsWith("+26132") || clean.startsWith("+26137") -> "Orange Money"
        clean.startsWith("+26133") || clean.startsWith("+26135") -> "Airtel Money"
        else -> null
    }
}
