package com.fluxio.features.iptv

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.fluxio.shared.models.*
import com.fluxio.features.player.*
import com.fluxio.features.admin.*
import com.fluxio.features.settings.*
import com.fluxio.shared.components.*
import com.fluxio.core.security.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fluxio.core.database.DatabaseRepository
import com.fluxio.core.database.FavoriteChannelEntity
import kotlin.math.roundToInt
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material.ripple.RippleAlpha

val LocalImmersiveMode = androidx.compose.runtime.compositionLocalOf { false }

data class InAppAlert(
    val id: String,
    val title: String,
    val message: String,
    val type: String, // "update", "warning", "info", "promo"
    val actionText: String? = null,
    val actionUrl: String? = null,
    val date: String = "Aujourd'hui"
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppContent(
    isDarkTheme: Boolean,
    onIsDarkThemeChange: (Boolean) -> Unit,
    immersiveModeEnabled: Boolean,
    onImmersiveModeToggle: (Boolean) -> Unit,
    biometricLockEnabled: Boolean,
    onBiometricLockToggle: (Boolean) -> Unit,
    sharedPrefs: SharedPreferences,
    userEmail: String,
    userName: String,
    onLogoutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localChannels = remember {
        emptyList<LiveChannel>()
    }

    var channelLoadState by remember { mutableStateOf<ChannelLoadState>(ChannelLoadState.Success(localChannels)) }
    var selectedChannel by remember {
        mutableStateOf(
            LiveChannel(
                id = "",
                name = "",
                url = "",
                category = "",
                logoText = "",
                description = ""
            )
        )
    }
    var triggerReload by remember { mutableIntStateOf(0) }



    var channelSortOrder by remember {
        mutableStateOf(sharedPrefs.getString("channel_sort_order", "logique") ?: "logique")
    }
    var triggerChannelTransform by remember { mutableIntStateOf(0) }

    val channels = remember(channelLoadState, channelSortOrder, triggerChannelTransform, triggerReload) {
        val rawList = when (val state = channelLoadState) {
            is ChannelLoadState.Success -> state.channels
            else -> localChannels
        }
        
        val deletedIds = sharedPrefs.getStringSet("deleted_channel_ids", emptySet()) ?: emptySet()
        val processedList = rawList
            .filter { !deletedIds.contains(it.id) }
            .map { channel ->
                val overriddenName = sharedPrefs.getString("name_override_${channel.id}", null)
                val overriddenUrl = sharedPrefs.getString("url_override_${channel.id}", null)
                val overriddenCategory = sharedPrefs.getString("category_override_${channel.id}", null)
                val overriddenLogoUrl = sharedPrefs.getString("logoUrl_override_${channel.id}", null)
                val overriddenCountry = sharedPrefs.getString("country_override_${channel.id}", null)
                val overriddenDescription = sharedPrefs.getString("description_override_${channel.id}", null)
                val overriddenLogoText = sharedPrefs.getString("logoText_override_${channel.id}", null)
                
                channel.copy(
                    name = overriddenName ?: channel.name,
                    url = overriddenUrl ?: channel.url,
                    category = overriddenCategory ?: channel.category,
                    logoUrl = overriddenLogoUrl ?: channel.logoUrl,
                    country = overriddenCountry ?: channel.country,
                    description = overriddenDescription ?: channel.description,
                    logoText = overriddenLogoText ?: channel.logoText
                )
            }
        
        val numberedList = assignChannelNumbers(processedList)
        
        when (channelSortOrder) {
            "alphabetique" -> {
                numberedList.sortedBy { it.name.lowercase() }
            }
            else -> {
                numberedList.sortedBy { it.channelNumber ?: 999 }
            }
        }
    }

    var adminFeaturedChannelIds by remember {
        mutableStateOf(sharedPrefs.getStringSet("admin_featured_ids", emptySet()) ?: emptySet())
    }
    var adminPublishedChannelIds by remember {
        mutableStateOf(sharedPrefs.getStringSet("admin_tv_channels_ids", emptySet()) ?: emptySet())
    }

    val isAdmin = remember(userEmail) {
        com.fluxio.core.security.SecurityUtils.isAdminEmail(userEmail)
    }

    val subscriptionType = remember(userEmail, triggerReload) {
        sharedPrefs.getString("subscription_type_$userEmail", "gratuit") ?: "gratuit"
    }

    val isPremiumUser = remember(subscriptionType, isAdmin) {
        subscriptionType == "premium" || isAdmin
    }

    val isChannelRestricted = remember(selectedChannel, subscriptionType, isAdmin) {
        selectedChannel.isPaid && !isAdmin && subscriptionType == "gratuit"
    }

    fun loadIndividualFirestoreDocs(fs: FirebaseFirestore) {
        fs.collection("catalogs").document("featured").get().addOnSuccessListener { featDoc ->
            val featList = featDoc?.get("ids") as? List<*>
            val fIds = featList?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
            
            fs.collection("catalogs").document("tv_channels").get().addOnSuccessListener { tvDoc ->
                val tvList = tvDoc?.get("ids") as? List<*>
                val pIds = tvList?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
                
                val localCustomIds = sharedPrefs.getStringSet("admin_tv_channels_ids", emptySet()) ?: emptySet()
                val customIds = localCustomIds.filter { it.startsWith("custom_") }.toSet()
                val finalPublishedIds = pIds + customIds
                
                adminFeaturedChannelIds = fIds
                adminPublishedChannelIds = finalPublishedIds
                sharedPrefs.edit()
                    .putStringSet("admin_featured_ids", fIds)
                    .putStringSet("admin_tv_channels_ids", finalPublishedIds)
                    .apply()
            }
        }
    }

    fun loadCatalogsWithFirestorePriority() {
        try {
            val fs = FirebaseFirestore.getInstance()
            
            fs.collection("catalogs").document("main").get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val featuredList = document.get("featured") as? List<*>
                        val tvChannelsList = document.get("tv_channels") as? List<*>
                        val fIds = featuredList?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
                        val pIds = tvChannelsList?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
                        
                        if (fIds.isNotEmpty() || pIds.isNotEmpty()) {
                            val localCustomIds = sharedPrefs.getStringSet("admin_tv_channels_ids", emptySet()) ?: emptySet()
                            val customIds = localCustomIds.filter { it.startsWith("custom_") }.toSet()
                            val finalPublishedIds = pIds + customIds
                            
                            adminFeaturedChannelIds = fIds
                            adminPublishedChannelIds = finalPublishedIds
                            sharedPrefs.edit()
                                .putStringSet("admin_featured_ids", fIds)
                                .putStringSet("admin_tv_channels_ids", finalPublishedIds)
                                .apply()
                            return@addOnSuccessListener
                        }
                    }
                    loadIndividualFirestoreDocs(fs)
                }
                .addOnFailureListener {
                    loadIndividualFirestoreDocs(fs)
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(userEmail) {
        if (userEmail.isNotEmpty()) {
            triggerReload++
        }
    }

    val onPublishCatalogs: (Set<String>, Set<String>) -> Unit = { featuredIds, tvChannelIds ->
        adminFeaturedChannelIds = featuredIds
        adminPublishedChannelIds = tvChannelIds
        sharedPrefs.edit()
            .putStringSet("admin_featured_ids", featuredIds)
            .putStringSet("admin_tv_channels_ids", tvChannelIds)
            .apply()
        try {
            val fs = FirebaseFirestore.getInstance()
            val docData = hashMapOf(
                "featured" to featuredIds.toList(),
                "tv_channels" to tvChannelIds.toList(),
                "last_updated" to System.currentTimeMillis()
            )
            fs.collection("catalogs").document("main").set(docData)
            fs.collection("catalogs").document("featured").set(mapOf("ids" to featuredIds.toList()))
            fs.collection("catalogs").document("tv_channels").set(mapOf("ids" to tvChannelIds.toList()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var updateInfo by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var hasNotifiedUpdateSystem by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showClearNotificationsDialog by remember { mutableStateOf(false) }
    var readNotificationIds by remember(userEmail) {
        mutableStateOf(sharedPrefs.getStringSet("read_notification_ids_$userEmail", emptySet()) ?: emptySet())
    }
    var initialReadNotificationIds by remember(userEmail) {
        mutableStateOf(sharedPrefs.getStringSet("read_notification_ids_$userEmail", emptySet()) ?: emptySet())
    }
    var notifiedNotificationIds by remember(userEmail) {
        mutableStateOf(sharedPrefs.getStringSet("notified_notification_ids_$userEmail", emptySet()) ?: emptySet())
    }
    var clearedNotificationIds by remember(userEmail) {
        mutableStateOf(sharedPrefs.getStringSet("cleared_notification_ids_$userEmail", emptySet()) ?: emptySet())
    }

    var firestoreNotifications by remember { mutableStateOf<List<InAppAlert>>(emptyList()) }

    LaunchedEffect(userEmail, triggerReload) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null && userEmail.isNotEmpty() && userEmail != "invite@fluxio.tv") {
            var registration: com.google.firebase.firestore.ListenerRegistration? = null
            try {
                registration = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("subscription_notifications")
                    .whereEqualTo("userId", uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            error.printStackTrace()
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.mapNotNull { doc ->
                                val id = doc.id
                                val rawTitle = doc.getString("title") ?: ""
                                val rawMessage = doc.getString("message") ?: ""
                                
                                val title = if (rawTitle == "Nouvel abonnement") {
                                    "Paiement effectué"
                                } else {
                                    rawTitle
                                }
                                
                                val message = if (rawTitle == "Nouvel abonnement") {
                                    "Votre paiement a été effectué avec succès et est en cours de vérification par notre équipe."
                                } else {
                                    rawMessage
                                }

                                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                val formatted = try {
                                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAt))
                                } catch (e: Exception) {
                                    "Récemment"
                                }
                                // Map it to InAppAlert
                                InAppAlert(
                                    id = id,
                                    title = title,
                                    message = message,
                                    type = "info",
                                    date = formatted
                                )
                            }
                            
                            // Check for any new un-notified notifications to trigger a sound and show push alert
                            val newAlerts = list.filter { !notifiedNotificationIds.contains(it.id) }
                            if (newAlerts.isNotEmpty()) {
                                newAlerts.forEach { alert ->
                                    com.fluxio.core.notification.NotificationHelper.showNotification(
                                        context = context,
                                        title = alert.title,
                                        message = alert.message
                                    )
                                }
                                val updatedNotifiedIds = notifiedNotificationIds + newAlerts.map { it.id }.toSet()
                                notifiedNotificationIds = updatedNotifiedIds
                                sharedPrefs.edit().putStringSet("notified_notification_ids_$userEmail", updatedNotifiedIds).apply()
                            }
                            
                            firestoreNotifications = list
                        }
                    }
                kotlinx.coroutines.awaitCancellation()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                registration?.remove()
            }
        } else {
            firestoreNotifications = emptyList()
        }
    }

    val subscriptionExpiresAt = remember(userEmail, triggerReload) {
        sharedPrefs.getLong("subscription_expires_at_$userEmail", 0L)
    }

    LaunchedEffect(subscriptionType, subscriptionExpiresAt) {
        if (!isAdmin && subscriptionType == "premium" && subscriptionExpiresAt > 0L) {
            val now = System.currentTimeMillis()
            val remainingMs = subscriptionExpiresAt - now
            if (sharedPrefs.getBoolean("pref_notif_sub_expiry_enabled", true)) {
                val subExpiryDelay = sharedPrefs.getString("pref_notif_sub_expiry_delay", "1_day") ?: "1_day"
                val thresholdMs = if (subExpiryDelay == "1_hour") 3600000L else 86400000L
                val lastNotifiedExpiry = sharedPrefs.getLong("last_notified_expiry_main_$userEmail", 0L)
                if (remainingMs in 1..thresholdMs && lastNotifiedExpiry != subscriptionExpiresAt) {
                    sharedPrefs.edit().putLong("last_notified_expiry_main_$userEmail", subscriptionExpiresAt).apply()
                    val delayLabel = if (subExpiryDelay == "1_hour") "1 heure" else "24 heures"
                    com.fluxio.core.notification.NotificationHelper.showNotification(
                        context,
                        "Expiration de l'abonnement",
                        "Attention, votre abonnement Premium expire dans moins de $delayLabel."
                    )
                }
            }
        }
    }

    val packageInfo = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val currentVersionCode = remember(packageInfo) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 1L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 1L
        }
    }

    val inAppNotifications: List<InAppAlert> = remember(updateInfo, subscriptionType, subscriptionExpiresAt) {
        val list = mutableListOf<InAppAlert>()
        val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        
        // 1. Update notification
        updateInfo?.let {
            val vName = it["versionName"] as? String ?: ""
            val updateTime = System.currentTimeMillis() - 1 * 3600000L // 1 hour ago
            val updateDate = formatter.format(java.util.Date(updateTime))
            list.add(
                InAppAlert(
                    id = "update",
                    title = "Nouvelle mise à jour disponible !",
                    message = "La version v$vName est disponible. Elle apporte de nouvelles fonctionnalités et corrections. Mettez à jour maintenant.",
                    type = "update",
                    actionText = "Télécharger",
                    actionUrl = it["apkUrl"] as? String,
                    date = updateDate
                )
            )
        }
        
        // Setup user's dynamic first open timestamp
        var firstOpen = sharedPrefs.getLong("first_open_time_$userEmail", 0L)
        if (firstOpen == 0L) {
            firstOpen = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000L
            sharedPrefs.edit().putLong("first_open_time_$userEmail", firstOpen).apply()
        }
        
        // 2. Subscription expiration notification
        if (subscriptionType == "premium" && subscriptionExpiresAt > 0L) {
            val now = System.currentTimeMillis()
            val remainingMs = subscriptionExpiresAt - now
            if (remainingMs > 0) {
                val formattedDate = formatter.format(java.util.Date(subscriptionExpiresAt))
                if (remainingMs < 86400000L) {
                    val warningTime = subscriptionExpiresAt - 23 * 3600000L
                    val warningDate = formatter.format(java.util.Date(if (warningTime in 1..System.currentTimeMillis()) warningTime else System.currentTimeMillis() - 30 * 60000L))
                    list.add(
                        InAppAlert(
                            id = "expiry_warning",
                            title = "Votre abonnement expire bientôt",
                            message = "Votre abonnement Premium prendra fin le $formattedDate. Renouvelez-le pour ne pas perdre l'accès à vos chaînes préférées.",
                            type = "warning",
                            actionText = "Renouveler",
                            date = warningDate
                        )
                    )
                } else {
                    val cachedTotalDuration = sharedPrefs.getLong("subscription_total_duration_$userEmail", 24 * 60 * 60 * 1000L)
                    val subStartTime = subscriptionExpiresAt - cachedTotalDuration
                    val activeDate = formatter.format(java.util.Date(if (subStartTime in 1..System.currentTimeMillis()) subStartTime else System.currentTimeMillis() - 2 * 3600000L))
                    list.add(
                        InAppAlert(
                            id = "premium_active",
                            title = "Abonnement Premium Actif",
                            message = "Votre abonnement est actif jusqu'au $formattedDate. Merci de faire partie de la communauté Fluxio !",
                            type = "info",
                            date = activeDate
                        )
                    )
                }
            }
        } else if (subscriptionType == "gratuit") {
            val promoTime = firstOpen + 24 * 60 * 60 * 1000L
            val promoDate = formatter.format(java.util.Date(if (promoTime < System.currentTimeMillis()) promoTime else System.currentTimeMillis() - 12 * 60 * 60 * 1000L))
            list.add(
                InAppAlert(
                    id = "premium_promo",
                    title = "Passez au Premium !",
                    message = "Débloquez plus de chaînes sportives, cinéma, sans aucune publicité. Abonnez-vous dès aujourd'hui !",
                    type = "promo",
                    actionText = "Découvrir",
                    date = promoDate
                )
            )
        }
        
        // No welcome notification as requested
        
        list
    }

    val combinedNotifications = remember(inAppNotifications, firestoreNotifications, clearedNotificationIds) {
        (inAppNotifications + firestoreNotifications).filter { !clearedNotificationIds.contains(it.id) }
    }

    val unreadNotificationsCount = remember(combinedNotifications, readNotificationIds) {
        combinedNotifications.count { !readNotificationIds.contains(it.id) }
    }

    var customProgramsList by remember { mutableStateOf(emptyList<CustomProgram>()) }

    LaunchedEffect(triggerReload) {
        try {
            FirebaseFirestore.getInstance().collection("app_updates").document("latest")
                .get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val latestCode = doc.getLong("versionCode") ?: 0L
                        if (latestCode > currentVersionCode) {
                            val info = mapOf(
                                "versionName" to (doc.getString("versionName") ?: ""),
                                "versionCode" to latestCode,
                                "releaseNotes" to (doc.getString("releaseNotes") ?: ""),
                                "isForceUpdate" to (doc.getBoolean("isForceUpdate") ?: false),
                                "apkUrl" to (doc.getString("apkUrl") ?: "")
                            )
                            updateInfo = info
                            showUpdateDialog = true

                            if (!hasNotifiedUpdateSystem) {
                                hasNotifiedUpdateSystem = true
                                val isForce = doc.getBoolean("isForceUpdate") ?: false
                                val vName = doc.getString("versionName") ?: ""
                                val apkUrl = doc.getString("apkUrl") ?: ""
                                val title = if (isForce) "Mise à jour obligatoire v$vName" else "Mise à jour disponible v$vName"
                                val message = "Une nouvelle version de Fluxio ($vName) est disponible."
                                com.fluxio.core.notification.NotificationHelper.showNotification(
                                    context = context,
                                    title = title,
                                    message = message,
                                    url = apkUrl.ifEmpty { null }
                                )
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Load local/cached channels immediately so they do not disappear during synchronization
        val initialList = mutableListOf<LiveChannel>()
        initialList.addAll(localChannels)
        initialList.addAll(AdminUtils.loadCustomChannels(context))
        channelLoadState = ChannelLoadState.Success(deduplicateChannels(initialList.toList()))

        loadCatalogsWithFirestorePriority()
        launch(Dispatchers.IO) {
            try {
                com.fluxio.features.admin.AdminSyncUtils.syncChannelOverridesFromFirebase(context)
                com.fluxio.features.admin.AdminSyncUtils.syncCustomChannelsFromFirebase(context)
                com.fluxio.features.admin.AdminSyncUtils.syncCustomProgramsFromFirebase(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                val combinedList = mutableListOf<LiveChannel>()
                combinedList.addAll(localChannels)
                combinedList.addAll(AdminUtils.loadCustomChannels(context))
                
                channelLoadState = ChannelLoadState.Success(deduplicateChannels(combinedList.toList()))
                
                try {
                    customProgramsList = AdminUtils.loadCustomPrograms(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val remoteSources = IptvSources.filter { !it.isLocal }
                val loadedChannels = mutableListOf<LiveChannel>()
                
                launch(Dispatchers.IO) {
                    for (source in remoteSources) {
                        try {
                            val content = fetchUrlText(source.url)
                            if (content.isNotEmpty()) {
                                val parsed = parseM3u(content, source.name)
                                if (parsed.isNotEmpty()) {
                                    loadedChannels.addAll(parsed)
                                }
                            }
                        } catch (e: Exception) {
                            // Fail silently
                        }
                    }
                    if (loadedChannels.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            combinedList.addAll(loadedChannels)
                            channelLoadState = ChannelLoadState.Success(deduplicateChannels(combinedList.toList()))
                        }
                    }
                }
            }
        }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0: Direct (Home), 1: Favoris, 2: Réglages
    var settingsInitialSubScreen by remember { mutableStateOf<String?>(null) }
    val isImportingChaine = activeTab == 3 && settingsInitialSubScreen == "Importer une chaîne"
    var isPlayerFloating by remember { mutableStateOf(false) }
    var isPlayerControlsVisible by remember { mutableStateOf(false) }
    var showDetailsOfPlaying by remember { mutableStateOf(false) }
    var showWorldCupScreen by remember { mutableStateOf(false) }

    BackHandler(
        enabled = showDetailsOfPlaying || showWorldCupScreen || showNotificationCenter || settingsInitialSubScreen != null || activeTab != 0
    ) {
        if (showDetailsOfPlaying) {
            showDetailsOfPlaying = false
        } else if (showWorldCupScreen) {
            showWorldCupScreen = false
        } else if (showNotificationCenter) {
            showNotificationCenter = false
        } else if (settingsInitialSubScreen != null) {
            settingsInitialSubScreen = null
        } else if (activeTab != 0) {
            activeTab = 0
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("Tous") }
    var selectedGenre by remember { mutableStateOf("Tous") }

    val dbRepository = remember { DatabaseRepository(context) }

    var favoriteSet by remember {
        mutableStateOf(sharedPrefs.getStringSet("favorite_ids", emptySet()) ?: emptySet())
    }

    LaunchedEffect(userEmail) {
        if (userEmail.isNotEmpty()) {
            dbRepository.getFavorites(userEmail).collect { entities ->
                val roomFavoriteIds = entities.map { it.channelId }.toSet()
                favoriteSet = roomFavoriteIds
            }
        }
    }

    val toggleFavorite: (String) -> Unit = { channelId ->
        val isFav = favoriteSet.contains(channelId)
        val updated = if (isFav) {
            favoriteSet - channelId
        } else {
            favoriteSet + channelId
        }
        favoriteSet = updated
        sharedPrefs.edit().putStringSet("favorite_ids", updated).apply()
        
        scope.launch {
            if (isFav) {
                dbRepository.deleteFavorite(channelId, userEmail)
            } else {
                dbRepository.insertFavorite(FavoriteChannelEntity(channelId, userEmail))
            }
            
            // Sync updated favorites list to Cloud (Firestore)
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null && userEmail.isNotEmpty() && userEmail != "invite@fluxio.tv") {
                try {
                    val userRef = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                    userRef.set(mapOf("favorites" to updated.toList()), com.google.firebase.firestore.SetOptions.merge())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    var likedSet by remember {
        mutableStateOf(sharedPrefs.getStringSet("liked_ids", emptySet()) ?: emptySet())
    }

    val channelLikesMap = remember { mutableStateMapOf<String, Int>() }

    val toggleLike: (String) -> Unit = { channelId ->
        val isCurrentlyLiked = likedSet.contains(channelId)
        val updated = if (isCurrentlyLiked) {
            likedSet - channelId
        } else {
            likedSet + channelId
        }
        likedSet = updated
        sharedPrefs.edit().putStringSet("liked_ids", updated).apply()

        val increment = if (isCurrentlyLiked) -1L else 1L
        
        // Mise à jour locale optimiste immédiate de la carte des likes
        val currentLocalCount = channelLikesMap[channelId] ?: (if (isCurrentlyLiked) 1 else 0)
        val newCount = (currentLocalCount + increment.toInt()).coerceAtLeast(0)
        channelLikesMap[channelId] = newCount

        try {
            val docRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("channel_likes")
                .document(channelId)
            
            docRef.set(
                mapOf("count" to com.google.firebase.firestore.FieldValue.increment(increment)),
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val getLikesCount: (String) -> Int = { channelId ->
        val countFromMap = channelLikesMap[channelId]
        if (countFromMap != null) {
            countFromMap
        } else {
            if (likedSet.contains(channelId)) 1 else 0
        }
    }

    var videoRotationMode by remember {
        mutableStateOf(sharedPrefs.getString("video_rotation_mode", "auto") ?: "auto")
    }

    val offlineChannels = remember { mutableStateListOf<String>() }
    var filterOffline by remember { mutableStateOf(true) }
    var floatingSize by remember {
        mutableStateOf(sharedPrefs.getString("floating_size", "medium") ?: "medium")
    }
    var showFloatingControls by remember { mutableStateOf(false) }

    var playbackSpeed by remember {
        mutableStateOf(sharedPrefs.getFloat("playback_speed", 1.0f))
    }
    var isMuted by remember {
        mutableStateOf(sharedPrefs.getBoolean("is_muted", false))
    }
    var playerVolume by remember {
        mutableStateOf(sharedPrefs.getFloat("player_volume", 1.0f))
    }

    LaunchedEffect(offlineChannels.toList(), channels) {
        while (true) {
            delay(10000)
            val copyOfOffline = offlineChannels.toList()
            for (channelId in copyOfOffline) {
                val chan = channels.find { it.id == channelId }
                if (chan != null) {
                    val isPlayable = checkChannelPlayability(chan.url)
                    if (isPlayable) {
                        offlineChannels.remove(channelId)
                    }
                }
            }
        }
    }

    val customChannelIds = remember(channels) {
        channels.filter { it.id.startsWith("custom_") }.map { it.id }.toSet()
    }

    val visibleChannels = remember(channels, filterOffline, offlineChannels.toList(), adminPublishedChannelIds, isAdmin, customChannelIds) {
        val baseList = if (filterOffline) {
            channels.filter { !offlineChannels.contains(it.id) }
        } else {
            channels
        }
        val parsedList = baseList.filter { channel ->
            if (channel.id.startsWith("custom_")) {
                true
            } else {
                val idLower = channel.id.lowercase().trim()
                val nameLower = channel.name.lowercase().trim()
                !(idLower == "horizon_welcome" ||
                  nameLower.contains("bienvenue sur horizon") ||
                  nameLower.contains("tv5monde") ||
                  nameLower.contains("tv5 monde") ||
                  nameLower == "france inter" ||
                  nameLower.startsWith("france inter") ||
                  nameLower == "france info" ||
                  nameLower.startsWith("france info") ||
                  nameLower.contains("radio frontieres") ||
                  nameLower.contains("radio frontière") ||
                  nameLower.contains("radio frontières") ||
                  nameLower.contains("clubbing tv france") ||
                  nameLower.contains("clubbing tv") ||
                  nameLower.contains("motorvision"))
            }
        }
        if (isAdmin) {
            parsedList
        } else {
            parsedList.filter { adminPublishedChannelIds.contains(it.id) || customChannelIds.contains(it.id) }
        }
    }

    LaunchedEffect(visibleChannels) {
        if ((selectedChannel.id == "" || selectedChannel.id == "horizon_welcome") && visibleChannels.isNotEmpty()) {
            val lastViewedId = sharedPrefs.getString("last_viewed_channel_id", "")
            val savedChannel = visibleChannels.find { it.id == lastViewedId }
            selectedChannel = savedChannel ?: visibleChannels.first()
        }
    }

    var isHomeSkeletonLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userEmail, channelLoadState) {
        if (userEmail.isNotBlank()) {
            when (channelLoadState) {
                is ChannelLoadState.Success -> {
                    isHomeSkeletonLoading = false
                }
                is ChannelLoadState.Error -> {
                    isHomeSkeletonLoading = false
                }
                is ChannelLoadState.Loading -> {
                    isHomeSkeletonLoading = true
                }
            }
        } else {
            isHomeSkeletonLoading = false
        }
    }

    val onVideoRotationModeChange: (String) -> Unit = { mode ->
        videoRotationMode = mode
        sharedPrefs.edit().putString("video_rotation_mode", mode).apply()
    }

    val activity = context as? androidx.fragment.app.FragmentActivity
    LaunchedEffect(videoRotationMode) {
        when (videoRotationMode) {
            "portrait" -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            "landscape" -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            else -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isFloatingEnlarged by remember { mutableStateOf(false) }
    var showPlayerSettings by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlayerFloating) {
        if (!isPlayerFloating) {
            offsetX = 0f
            offsetY = 0f
            isFloatingEnlarged = false
        }
    }

    LaunchedEffect(showPlayerSettings) {
        if (showPlayerSettings) {
            isPlayerFloating = false
        }
    }

    LaunchedEffect(selectedChannel) {
        isPlayerFloating = false
        val channelId = selectedChannel.id
        if (channelId.isNotEmpty()) {
            sharedPrefs.edit().putString("last_viewed_channel_id", channelId).apply()
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("channel_likes")
                    .document(channelId)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (doc != null && doc.exists()) {
                            val count = doc.getLong("count")?.toInt() ?: 0
                            channelLikesMap[channelId] = count
                        } else {
                            channelLikesMap[channelId] = 0
                        }
                    }
                    .addOnFailureListener {
                        channelLikesMap[channelId] = 0
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset.Zero
            }
        }
    }

    val favoriteChannels = remember(visibleChannels, favoriteSet) {
        visibleChannels.filter { favoriteSet.contains(it.id) }
    }

    val density = LocalDensity.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = statusBarPadding + 64.dp

    val playerSpacerHeight = if (isPlayerFloating || activeTab == 0 || activeTab == 3 || activeTab == 4) 0.dp else 240.dp

    if (com.fluxio.features.player.PipHelper.isInPipMode) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayerWithSubtitles(
                url = selectedChannel.url,
                channel = selectedChannel,
                videoRotationMode = videoRotationMode,
                onVideoRotationModeChange = onVideoRotationModeChange,
                modifier = Modifier.fillMaxSize(),
                playWhenReady = true,
                onPlayerError = {
                    if (!offlineChannels.contains(selectedChannel.id)) {
                        offlineChannels.add(selectedChannel.id)
                    }
                },
                showPlayerSettings = showPlayerSettings,
                onShowPlayerSettingsChange = { showPlayerSettings = it },
                playbackSpeed = playbackSpeed,
                onPlaybackSpeedChange = { 
                    playbackSpeed = it
                    sharedPrefs.edit().putFloat("playback_speed", it).apply()
                },
                isMuted = isMuted, playerVolume = playerVolume, onPlayerVolumeChange = { playerVolume = it; sharedPrefs.edit().putFloat("player_volume", it).apply() },
                onIsMutedChange = { 
                    isMuted = it
                    sharedPrefs.edit().putBoolean("is_muted", it).apply()
                }
            )
        }
        return
    }

    CompositionLocalProvider(LocalImmersiveMode provides immersiveModeEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isLandscape) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .then(if (LocalImmersiveMode.current) Modifier else Modifier.navigationBarsPadding())
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            )
                            CompositionLocalProvider(LocalRippleTheme provides NoRippleTheme) {
                                NavigationBar(
                                    containerColor = Color.Black,
                                    tonalElevation = 0.dp,
                                    windowInsets = WindowInsets(0, 0, 0, 0),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bottom_nav_bar")
                                        .height(72.dp)
                                ) {
                        // Tab 0: Accueil
                        NavigationBarItem(
                            selected = activeTab == 0,
                            onClick = { 
                                if (selectedChannel.id.isNotEmpty() && showDetailsOfPlaying) {
                                    showDetailsOfPlaying = false
                                }
                                activeTab = 0 
                            },
                            icon = {
                                CustomHomeMenuIcon(
                                    selected = activeTab == 0,
                                    tint = if (activeTab == 0) Color(0xFFE50914) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Accueil",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 0) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_home")
                        )

                        // Tab 1: TV
                        NavigationBarItem(
                            selected = activeTab == 1,
                            onClick = { 
                                activeTab = 1 
                            },
                            icon = {
                                CustomPlayMenuIcon(
                                    selected = activeTab == 1,
                                    tint = if (activeTab == 1) Color(0xFFE50914) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "TV",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 1) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_direct")
                        )

                        // Tab 2: Favoris
                        NavigationBarItem(
                            selected = activeTab == 2,
                            onClick = { 
                                if (selectedChannel.id.isNotEmpty() && showDetailsOfPlaying) {
                                    showDetailsOfPlaying = false
                                }
                                activeTab = 2 
                            },
                            icon = {
                                CustomStarMenuIcon(
                                    selected = activeTab == 2,
                                    tint = if (activeTab == 2) Color(0xFFE50914) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Favoris",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 2) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_favorites")
                        )

                        // Tab 3: Paramètres
                        NavigationBarItem(
                            selected = activeTab == 3,
                            onClick = { 
                                if (selectedChannel.id.isNotEmpty() && showDetailsOfPlaying) {
                                    showDetailsOfPlaying = false
                                }
                                activeTab = 3 
                            },
                            icon = {
                                CustomSettingsMenuIcon(
                                    selected = activeTab == 3,
                                    tint = if (activeTab == 3) Color(0xFFE50914) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Paramètres",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 3) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_settings")
                        )
                    }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayerWithSubtitles(
                    url = selectedChannel.url,
                    channel = selectedChannel,
                    videoRotationMode = videoRotationMode,
                    onVideoRotationModeChange = onVideoRotationModeChange,
                    modifier = Modifier.fillMaxSize(),
                    playWhenReady = true,
                    onPlayerError = {
                        if (!offlineChannels.contains(selectedChannel.id)) {
                            offlineChannels.add(selectedChannel.id)
                        }
                    },
                    showPlayerSettings = showPlayerSettings,
                    onShowPlayerSettingsChange = { showPlayerSettings = it },
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = { 
                        playbackSpeed = it
                        sharedPrefs.edit().putFloat("playback_speed", it).apply()
                    },
                    isMuted = isMuted,
                    onIsMutedChange = { 
                        isMuted = it
                        sharedPrefs.edit().putBoolean("is_muted", it).apply()
                    },
                    playerVolume = playerVolume,
                    onPlayerVolumeChange = {
                        playerVolume = it
                        sharedPrefs.edit().putFloat("player_volume", it).apply()
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .background(MaterialTheme.colorScheme.background)
                    .nestedScroll(nestedScrollConnection)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (activeTab != 0 && activeTab != 3 && activeTab != 4) {
                        Spacer(modifier = Modifier.height(playerSpacerHeight))
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                fadeIn() with fadeOut()
                            }
                        ) { tab ->
                            when (tab) {
                                0 -> {
                                    val adminFeaturedChannels = remember(channels, adminFeaturedChannelIds) {
                                        channels.filter { adminFeaturedChannelIds.contains(it.id) }
                                    }
                                    if (isHomeSkeletonLoading) {
                                        AccueilSkeletonScreen()
                                    } else {
                                        AccueilScreen(
                                            channels = visibleChannels,
                                            adminFeaturedChannels = adminFeaturedChannels,
                                            customPrograms = customProgramsList,
                                            favoriteSet = favoriteSet,
                                            onChannelSelect = { channel ->
                                                selectedChannel = channel
                                                activeTab = 1
                                                showDetailsOfPlaying = true
                                                isPlayerFloating = false
                                            },
                                            onToggleFavorite = toggleFavorite,
                                            onCategoryClick = { cat ->
                                                selectedGenre = cat
                                                showDetailsOfPlaying = false
                                                activeTab = 1
                                            },
                                            unreadCount = unreadNotificationsCount,
                                            onNotificationClick = {
                                                initialReadNotificationIds = readNotificationIds
                                                val currentIds = combinedNotifications.map { it.id }.toSet()
                                                val updatedReadIds = readNotificationIds + currentIds
                                                readNotificationIds = updatedReadIds
                                                sharedPrefs.edit().putStringSet("read_notification_ids_$userEmail", updatedReadIds).apply()
                                                showNotificationCenter = true
                                            },
                                            isPremiumUser = isPremiumUser,
                                            onLockedClick = {
                                                settingsInitialSubScreen = "Compte & Abonnement"
                                                activeTab = 3
                                            },
                                            onRefresh = { triggerReload++ }
                                        )
                                    }
                                }
                                1 -> {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        if (showDetailsOfPlaying) {
                                            ChannelDetailView(
                                                channel = selectedChannel,
                                                isPlaying = true,
                                                isFavorite = favoriteSet.contains(selectedChannel.id),
                                                onToggleFavorite = { toggleFavorite(selectedChannel.id) },
                                                onFullscreenToggle = {
                                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                },
                                                relatedChannels = visibleChannels.filter { it.category == selectedChannel.category && it.id != selectedChannel.id }.take(5),
                                                onChannelSelect = { 
                                                    selectedChannel = it
                                                    showDetailsOfPlaying = true
                                                },
                                                onBackClick = { showDetailsOfPlaying = false },
                                                isLiked = likedSet.contains(selectedChannel.id),
                                                likesCount = getLikesCount(selectedChannel.id),
                                                onLikeToggle = { toggleLike(selectedChannel.id) },
                                                sharedPrefs = sharedPrefs,
                                                favoriteSet = favoriteSet,
                                                onToggleFavoriteGlobal = toggleFavorite,
                                                isPremiumUser = isPremiumUser,
                                                onLockedClick = {
                                                    settingsInitialSubScreen = "Compte & Abonnement"
                                                    activeTab = 3
                                                }
                                            )
                                        } else {
                                            HomeTab(
                                                channels = visibleChannels,
                                                favoriteSet = favoriteSet,
                                                onBackClick = {
                                                    if (selectedGenre != "Tous") {
                                                        selectedGenre = "Tous"
                                                    } else if (searchQuery.isNotEmpty()) {
                                                        searchQuery = ""
                                                    } else {
                                                        activeTab = 0
                                                    }
                                                },
                                                selectedChannel = selectedChannel,
                                                searchQuery = searchQuery,
                                                onSearchQueryChange = { searchQuery = it },
                                                selectedCountry = selectedCountry,
                                                onCountryChange = { selectedCountry = it },
                                                selectedGenre = selectedGenre,
                                                onGenreChange = { selectedGenre = it },
                                                onChannelSelect = { 
                                                    selectedChannel = it
                                                    showDetailsOfPlaying = true 
                                                },
                                                onToggleFavorite = toggleFavorite,
                                                channelLoadState = channelLoadState,
                                                onRetryLoad = { triggerReload++ },
                                                isPremiumUser = isPremiumUser,
                                                onLockedClick = {
                                                    settingsInitialSubScreen = "Compte & Abonnement"
                                                    activeTab = 3
                                                },
                                                unreadCount = unreadNotificationsCount,
                                                onNotificationClick = {
                                                    initialReadNotificationIds = readNotificationIds
                                                    val currentIds = combinedNotifications.map { it.id }.toSet()
                                                    val updatedReadIds = readNotificationIds + currentIds
                                                    readNotificationIds = updatedReadIds
                                                    sharedPrefs.edit().putStringSet("read_notification_ids_$userEmail", updatedReadIds).apply()
                                                    showNotificationCenter = true
                                                }
                                            )
                                        }
                                    }
                                }
                                2 -> FavoritesTab(
                                    channels = visibleChannels,
                                    favoriteSet = favoriteSet,
                                    onChannelSelect = { 
                                        selectedChannel = it
                                        activeTab = 1
                                        showDetailsOfPlaying = true
                                        isPlayerFloating = false
                                    },
                                    onToggleFavorite = toggleFavorite,
                                    isPremiumUser = isPremiumUser,
                                    onLockedClick = {
                                        settingsInitialSubScreen = "Compte & Abonnement"
                                        activeTab = 3
                                    },
                                    unreadCount = unreadNotificationsCount,
                                    onNotificationClick = {
                                        initialReadNotificationIds = readNotificationIds
                                        val currentIds = combinedNotifications.map { it.id }.toSet()
                                        val updatedReadIds = readNotificationIds + currentIds
                                        readNotificationIds = updatedReadIds
                                        sharedPrefs.edit().putStringSet("read_notification_ids_$userEmail", updatedReadIds).apply()
                                        showNotificationCenter = true
                                    }
                                )
                                3 -> {
                                    CompositionLocalProvider(LocalRippleTheme provides NoRippleTheme) {
                                        SettingsTab(
                                            isDarkTheme = isDarkTheme,
                                            onIsDarkThemeChange = onIsDarkThemeChange,
                                            immersiveModeEnabled = immersiveModeEnabled,
                                            onImmersiveModeToggle = onImmersiveModeToggle,
                                            biometricLockEnabled = biometricLockEnabled,
                                            onBiometricLockToggle = onBiometricLockToggle,
                                            filterOfflineEnabled = filterOffline,
                                            onFilterOfflineToggle = { filterOffline = it },
                                            offlineChannelsCount = offlineChannels.size,
                                            onResetOfflineChannels = { offlineChannels.clear() },
                                            onLogoutClick = onLogoutClick,
                                            onDeleteAccountClick = onDeleteAccountClick,
                                            userEmail = userEmail,
                                            userName = userName,
                                            isAdmin = isAdmin,
                                            allChannels = channels,
                                            adminFeaturedChannelIds = adminFeaturedChannelIds,
                                            adminPublishedChannelIds = adminPublishedChannelIds,
                                            onPublishCatalogs = onPublishCatalogs,
                                            playbackSpeed = playbackSpeed,
                                            onPlaybackSpeedChange = { 
                                                playbackSpeed = it
                                                sharedPrefs.edit().putFloat("playback_speed", it).apply()
                                            },
                                            isMuted = isMuted,
                                            onIsMutedChange = { 
                                                isMuted = it
                                                sharedPrefs.edit().putBoolean("is_muted", it).apply()
                                            },
                                            playerVolume = playerVolume,
                                            onPlayerVolumeChange = {
                                                playerVolume = it
                                                sharedPrefs.edit().putFloat("player_volume", it).apply()
                                            },
                                            videoRotationMode = videoRotationMode,
                                            onVideoRotationModeChange = onVideoRotationModeChange,
                                            floatingSize = floatingSize,
                                            onFloatingSizeChange = { 
                                                floatingSize = it
                                                sharedPrefs.edit().putString("floating_size", it).apply()
                                            },
                                            sharedPrefs = sharedPrefs,
                                            channelSortOrder = channelSortOrder,
                                            onChannelSortOrderChange = { channelSortOrder = it },
                                            triggerChannelTransform = triggerChannelTransform,
                                            onTriggerChannelTransformChange = { triggerChannelTransform = it },
                                            onRefreshChannels = { triggerReload++ },
                                            initialSubScreen = settingsInitialSubScreen,
                                            onSubScreenChange = { settingsInitialSubScreen = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Single Video Player overlaid on top of content
                if (!isImportingChaine && (isPlayerFloating || (activeTab != 0 && activeTab != 3 && activeTab != 4))) {
                    val configuration = LocalConfiguration.current
                    val maxAllowedWidth = (configuration.screenWidthDp - 24).dp
                    
                    val clickBonusWidth = if (isFloatingEnlarged) 80.dp else 0.dp
                    val clickBonusHeight = if (isFloatingEnlarged) 55.dp else 0.dp

                    val rawWidth = when (floatingSize) {
                        "small" -> if (showFloatingControls) 270.dp else 180.dp
                        "large" -> if (showFloatingControls) 400.dp else 280.dp
                        else -> if (showFloatingControls) 340.dp else 220.dp
                    } + clickBonusWidth
                    val baseWidth = if (rawWidth > maxAllowedWidth) maxAllowedWidth else rawWidth

                    val rawHeight = when (floatingSize) {
                        "small" -> if (showFloatingControls) 165.dp else 110.dp
                        "large" -> if (showFloatingControls) 245.dp else 175.dp
                        else -> if (showFloatingControls) 210.dp else 135.dp
                    } + clickBonusHeight
                    val maxAllowedHeight = (configuration.screenHeightDp - 64).dp
                    val baseHeight = if (rawHeight > maxAllowedHeight) maxAllowedHeight else rawHeight

                    val targetWidth = if (isPlayerFloating) baseWidth else configuration.screenWidthDp.dp
                    val targetHeight = if (isPlayerFloating) baseHeight else 240.dp

                    val animatedWidth by animateDpAsState(
                        targetValue = targetWidth,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "widthAnimation"
                    )
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "heightAnimation"
                    )
                    val animatedCornerRadius by animateDpAsState(
                        targetValue = if (isPlayerFloating) 16.dp else 0.dp,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "cornerAnimation"
                    )
                    val animatedPadding by animateDpAsState(
                        targetValue = if (isPlayerFloating) 16.dp else 0.dp,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "paddingAnimation"
                    )
                    val animatedHorBias by animateFloatAsState(
                        targetValue = if (isPlayerFloating) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "horBiasAnimation"
                    )
                    val animatedVerBias by animateFloatAsState(
                        targetValue = if (isPlayerFloating) 1f else -1f,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "verBiasAnimation"
                    )
                    val animatedOffsetX by animateFloatAsState(
                        targetValue = offsetX,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "offsetXAnimation"
                    )
                    val animatedOffsetY by animateFloatAsState(
                        targetValue = offsetY,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "offsetYAnimation"
                    )
                    val animatedBorderWidth by animateDpAsState(
                        targetValue = if (isPlayerFloating) 1.dp else 0.dp,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "borderWidthAnimation"
                    )
                    val animatedBorderColor by animateColorAsState(
                        targetValue = if (isPlayerFloating) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        label = "borderColorAnimation"
                    )

                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val dragModifier = Modifier
                                .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                                .then(
                                    if (isPlayerFloating) {
                                        Modifier.pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                offsetX += dragAmount.x
                                                offsetY += dragAmount.y
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )

                            Box(
                                modifier = dragModifier
                                    .width(animatedWidth)
                                    .height(animatedHeight)
                                    .align(BiasAlignment(animatedHorBias, animatedVerBias))
                                    .padding(animatedPadding.coerceAtLeast(0.dp))
                                    .clip(RoundedCornerShape(animatedCornerRadius.coerceAtLeast(0.dp)))
                                    .background(Color.Black)
                                    .clickable {
                                        if (isPlayerFloating) {
                                            isFloatingEnlarged = !isFloatingEnlarged
                                            showFloatingControls = !showFloatingControls
                                        } else {
                                            showDetailsOfPlaying = true
                                        }
                                    }
                                    .border(
                                        width = animatedBorderWidth.coerceAtLeast(0.dp),
                                        color = animatedBorderColor,
                                        shape = RoundedCornerShape(animatedCornerRadius.coerceAtLeast(0.dp))
                                    )
                            ) {
                                if (isChannelRestricted) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF0D0E11).copy(alpha = 0.95f))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Chaîne Payante",
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = "CHAÎNE PREMIUM",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Text(
                                                text = "Cette chaîne est réservée aux abonnés Premium. Veuillez vous abonner dans les réglages pour y accéder.",
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 16.sp
                                            )
                                            Button(
                                                onClick = {
                                                    activeTab = 2
                                                    showDetailsOfPlaying = false
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "S'abonner maintenant",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    VideoPlayerWithSubtitles(
                                        url = selectedChannel.url,
                                        channel = selectedChannel,
                                        videoRotationMode = videoRotationMode,
                                        onVideoRotationModeChange = onVideoRotationModeChange,
                                        modifier = Modifier.fillMaxSize(),
                                        playWhenReady = true,
                                        onPlayerError = {
                                            if (!offlineChannels.contains(selectedChannel.id)) {
                                                offlineChannels.add(selectedChannel.id)
                                            }
                                        },
                                        showPlayerSettings = showPlayerSettings,
                                        onShowPlayerSettingsChange = { showPlayerSettings = it },
                                        playbackSpeed = playbackSpeed,
                                        onPlaybackSpeedChange = { 
                                            playbackSpeed = it
                                            sharedPrefs.edit().putFloat("playback_speed", it).apply()
                                        },
                                        isMuted = isMuted, playerVolume = playerVolume, onPlayerVolumeChange = { playerVolume = it; sharedPrefs.edit().putFloat("player_volume", it).apply() },
                                        onIsMutedChange = { 
                                            isMuted = it
                                            sharedPrefs.edit().putBoolean("is_muted", it).apply()
                                        },
                                        onBackClick = {
                                            if (showDetailsOfPlaying) {
                                                showDetailsOfPlaying = false
                                            } else {
                                                if (selectedGenre != "Tous") {
                                                    selectedGenre = "Tous"
                                                } else if (searchQuery.isNotEmpty()) {
                                                    searchQuery = ""
                                                } else {
                                                    activeTab = 0
                                                }
                                            }
                                        },
                                        onNotificationClick = if (!showDetailsOfPlaying) {
                                            {
                                                initialReadNotificationIds = readNotificationIds
                                                val currentIds = combinedNotifications.map { it.id }.toSet()
                                                val updatedReadIds = readNotificationIds + currentIds
                                                readNotificationIds = updatedReadIds
                                                sharedPrefs.edit().putStringSet("read_notification_ids_$userEmail", updatedReadIds).apply()
                                                showNotificationCenter = true
                                            }
                                        } else null,
                                        showDetailsOfPlaying = showDetailsOfPlaying,
                                        isPlayerFloating = isPlayerFloating
                                    )
                                }

                                if (isPlayerFloating && showFloatingControls) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    ) {
                                        IconButton(
                                            onClick = { isPlayerFloating = false },
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .size(32.dp)
                                                .padding(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Fermer",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { 
                                                showDetailsOfPlaying = true
                                                isPlayerFloating = false
                                            },
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(36.dp)
                                        ) {
                                            CustomMaximizeIcon(tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUpdateDialog) {
        val info = updateInfo
        if (info != null) {
            val isForce = info["isForceUpdate"] as? Boolean ?: false
            val vName = info["versionName"] as? String ?: ""
            val notes = info["releaseNotes"] as? String ?: ""
            val apkUrl = info["apkUrl"] as? String ?: ""

            AlertDialog(
                onDismissRequest = {
                    if (!isForce) {
                        showUpdateDialog = false
                    }
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = !isForce,
                    dismissOnClickOutside = !isForce
                ),
                containerColor = Color.Black,
                titleContentColor = Color.White,
                textContentColor = Color.LightGray,
                title = {
                    Text(
                        text = if (isForce) "Mise à jour obligatoire !" else "Mise à jour disponible",
                        fontWeight = FontWeight.Bold,
                        color = if (isForce) Color(0xFFE50914) else Color.White,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Une nouvelle version (v$vName) est disponible pour Fluxio.",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (notes.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Notes de version :",
                                    color = Color(0xFFE50914),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notes,
                                    color = Color.LightGray,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (apkUrl.isNotEmpty()) {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(apkUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Impossible d'ouvrir le lien de téléchargement", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                text = "Mettre à jour maintenant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        if (!isForce) {
                            Button(
                                onClick = { showUpdateDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.LightGray
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(
                                    text = "Plus tard",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            )
        }
    } // Closes Scaffold content
    } // Closes Box container

    if (showNotificationCenter) {
        androidx.activity.compose.BackHandler {
            showNotificationCenter = false
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B0B0D)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (LocalImmersiveMode.current) Modifier else Modifier.navigationBarsPadding())
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showNotificationCenter = false }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Centre de Notifications",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Restez au courant des nouveautés et de l'état de votre compte.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (combinedNotifications.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aucune notification pour le moment.",
                                    color = Color.Gray,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else {
                        items(combinedNotifications) { alert: InAppAlert ->
                            val isRead = initialReadNotificationIds.contains(alert.id)
                            
                            val leftBarColor = when (alert.type) {
                                "update" -> Color(0xFFE50914)
                                "warning" -> Color(0xFFFFA000)
                                "promo" -> Color(0xFFE50914)
                                else -> Color(0xFF7F8C8D)
                            }.copy(alpha = if (isRead) 0.3f else 1.0f)

                            val titleColor = if (isRead) Color.White.copy(alpha = 0.7f) else Color.White
                            val messageColor = if (isRead) Color.LightGray.copy(alpha = 0.6f) else Color.LightGray

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max)
                                    .background(Color.Black, RoundedCornerShape(12.dp))
                                    .border(0.5.dp, Color.White, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(16.dp)
                                ) {
                                    // Header of card (Type badge, date, read status)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when (alert.type) {
                                                "update" -> "Mise à jour"
                                                "warning" -> "Alerte"
                                                "promo" -> "Offre"
                                                else -> "Info"
                                            }.uppercase(),
                                            color = leftBarColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = alert.date,
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                            if (isRead) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Vue",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = alert.title,
                                        color = titleColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = alert.message,
                                        color = messageColor,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                    if (alert.actionText != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                if (alert.type == "update" && alert.actionUrl != null) {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(alert.actionUrl))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Impossible d'ouvrir le lien de téléchargement", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else if (alert.type == "promo" || alert.type == "warning") {
                                                    settingsInitialSubScreen = "Compte & Abonnement"
                                                    activeTab = 3
                                                    showNotificationCenter = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFE50914),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(
                                                text = alert.actionText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (combinedNotifications.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showClearNotificationsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                text = "Vider toutes les notifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (showClearNotificationsDialog) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showClearNotificationsDialog = false }
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                            color = Color(0xFF16161A)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Vider les notifications",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Text(
                                    text = "Voulez-vous vraiment supprimer toutes vos notifications de manière irréversible ?",
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            showClearNotificationsDialog = false
                                            val currentIds = combinedNotifications.map { it.id }.toSet()
                                            val updatedCleared = clearedNotificationIds + currentIds
                                            clearedNotificationIds = updatedCleared
                                            sharedPrefs.edit().putStringSet("cleared_notification_ids_$userEmail", updatedCleared).apply()
                                            
                                            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                            if (uid != null) {
                                                try {
                                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                        .collection("subscription_notifications")
                                                        .whereEqualTo("userId", uid)
                                                        .get()
                                                        .addOnSuccessListener { snapshot ->
                                                            if (snapshot != null && !snapshot.isEmpty) {
                                                                val batch = com.google.firebase.firestore.FirebaseFirestore.getInstance().batch()
                                                                snapshot.documents.forEach { doc ->
                                                                    batch.delete(doc.reference)
                                                                }
                                                                batch.commit()
                                                            }
                                                        }
                                                } catch (e: java.lang.Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE50914), // Rouge
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("Supprimer", fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Button(
                                        onClick = { showClearNotificationsDialog = false },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("Annuler", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

object NoRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor() = Color.Transparent

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(0.0f, 0.0f, 0.0f, 0.0f)
}
