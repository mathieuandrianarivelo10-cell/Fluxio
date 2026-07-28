package com.fluxio.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.telephony.TelephonyManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

object SecurityUtils {

    fun hasDotOrPlusInLocalPart(email: String): Boolean {
        val parts = email.split("@")
        if (parts.size < 2) return false
        val localPart = parts[0]
        return localPart.contains(".") || localPart.contains("+")
    }

    fun getNormalizedEmail(email: String): String {
        val trimmed = email.trim().lowercase()
        val parts = trimmed.split("@")
        if (parts.size < 2) return trimmed
        val localPart = parts[0]
        val domain = parts[1]
        return if (domain == "gmail.com" || domain == "googlemail.com") {
            val cleanLocal = localPart.substringBefore("+").replace(".", "")
            "$cleanLocal@$domain"
        } else {
            val cleanLocal = localPart.substringBefore("+")
            "$cleanLocal@$domain"
        }
    }

    fun isAdminEmail(email: String): Boolean {
        if (email.isBlank()) return false
        val normalizedInput = getNormalizedEmail(email)
        val admin1 = getNormalizedEmail(com.fluxio.BuildConfig.ADMIN_EMAIL_1)
        val admin2 = getNormalizedEmail(com.fluxio.BuildConfig.ADMIN_EMAIL_2)
        val fallback1 = getNormalizedEmail("rmihaja44@gmail.com")
        val fallback2 = getNormalizedEmail("mathieuandrianarivelo10@gmail.com")
        return (admin1.isNotEmpty() && normalizedInput == admin1) ||
               (admin2.isNotEmpty() && normalizedInput == admin2) ||
               (normalizedInput == fallback1) ||
               (normalizedInput == fallback2)
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun getDeviceID(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("horizon_security", Context.MODE_PRIVATE)
        var cachedId = sharedPrefs.getString("cached_device_id", "") ?: ""
        if (cachedId.isNotEmpty()) {
            return cachedId
        }

        var imei: String? = null
        try {
            imei = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            imei = null
        }

        if (imei.isNullOrEmpty()) {
            imei = UUID.randomUUID().toString().replace("-", "").take(16)
        }

        sharedPrefs.edit().putString("cached_device_id", imei).apply()
        return imei
    }

    fun checkDeviceStatus(context: Context, onResult: (String) -> Unit) {
        val deviceId = getDeviceID(context)
        val sharedPrefs = context.getSharedPreferences("horizon_security", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("device_status", "active").apply()

        try {
            val db = FirebaseFirestore.getInstance()
            val updateMap = mapOf("status" to "active")
            db.collection("devices").document(deviceId).set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener {
                    onResult("active")
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onResult("active")
        }
    }

    fun registerDeviceAndAccount(
        context: Context,
        email: String,
        onRegistered: (Boolean, String) -> Unit
    ) {
        val deviceId = getDeviceID(context)
        val cleanEmail = getNormalizedEmail(email)
        val sharedPrefs = context.getSharedPreferences("horizon_security", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("device_status", "active").apply()

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("devices").document(deviceId).get()
                .addOnSuccessListener { document ->
                    val accountsList = mutableListOf<String>()
                    if (document != null && document.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val existingAccounts = document.get("accounts") as? List<String>
                        if (existingAccounts != null) {
                            accountsList.addAll(existingAccounts)
                        }
                    }

                    if (!accountsList.contains(cleanEmail)) {
                        accountsList.add(cleanEmail)
                    }

                    val deviceMap = hashMapOf<String, Any>(
                        "deviceId" to deviceId,
                        "status" to "active",
                        "accounts" to accountsList,
                        "accountsCount" to accountsList.size
                    )

                    try {
                        firestore.collection("devices").document(deviceId).set(deviceMap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    onRegistered(true, "active")
                }
                .addOnFailureListener {
                    onRegistered(true, "active")
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onRegistered(true, "active")
        }
    }

    fun checkUserDeviceMatch(
        context: Context,
        email: String,
        uid: String,
        onMatchResult: (isMatch: Boolean, needsVerification: Boolean) -> Unit
    ) {
        val currentDeviceId = getDeviceID(context)
        val sharedPrefs = context.getSharedPreferences("horizon_security", Context.MODE_PRIVATE)
        
        if (uid.isEmpty()) {
            val localRegisteredDevice = sharedPrefs.getString("user_device_$email", "") ?: ""
            if (localRegisteredDevice.isEmpty()) {
                sharedPrefs.edit().putString("user_device_$email", currentDeviceId).apply()
                onMatchResult(true, false)
            } else if (localRegisteredDevice == currentDeviceId) {
                onMatchResult(true, false)
            } else {
                onMatchResult(false, true)
            }
            return
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val registeredDevice = document.getString("device_id")
                        if (registeredDevice.isNullOrEmpty()) {
                            try {
                                firestore.collection("users").document(uid).update("device_id", currentDeviceId)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            onMatchResult(true, false)
                        } else if (registeredDevice == currentDeviceId) {
                            onMatchResult(true, false)
                        } else {
                            onMatchResult(false, true)
                        }
                    } else {
                        onMatchResult(true, false)
                    }
                }
                .addOnFailureListener {
                    val localRegisteredDevice = sharedPrefs.getString("user_device_$email", "") ?: ""
                    if (localRegisteredDevice.isEmpty()) {
                        sharedPrefs.edit().putString("user_device_$email", currentDeviceId).apply()
                        onMatchResult(true, false)
                    } else if (localRegisteredDevice == currentDeviceId) {
                        onMatchResult(true, false)
                    } else {
                        onMatchResult(false, true)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            val localRegisteredDevice = sharedPrefs.getString("user_device_$email", "") ?: ""
            if (localRegisteredDevice.isEmpty()) {
                sharedPrefs.edit().putString("user_device_$email", currentDeviceId).apply()
                onMatchResult(true, false)
            } else if (localRegisteredDevice == currentDeviceId) {
                onMatchResult(true, false)
            } else {
                onMatchResult(false, true)
            }
        }
    }

    fun updateRegisteredDevice(context: Context, email: String, uid: String) {
        val currentDeviceId = getDeviceID(context)
        val sharedPrefs = context.getSharedPreferences("horizon_security", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("user_device_$email", currentDeviceId).apply()

        if (uid.isEmpty()) return

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).update("device_id", currentDeviceId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
