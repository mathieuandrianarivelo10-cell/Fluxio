package com.fluxio.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsNotificationsScreen(sharedPrefs: android.content.SharedPreferences) {
    var pushNotifications by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_notif_push_enabled", true))
    }
    var eventsEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_notif_events_enabled", true))
    }
    var subExpiryEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_notif_sub_expiry_enabled", true))
    }
    var subExpiryDelay by remember {
        mutableStateOf(sharedPrefs.getString("pref_notif_sub_expiry_delay", "1_day") ?: "1_day")
    }
    var dndEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_notif_dnd_enabled", false))
    }
    var dndStartHour by remember {
        mutableStateOf(sharedPrefs.getInt("pref_notif_dnd_start_hour", 22))
    }
    var dndEndHour by remember {
        mutableStateOf(sharedPrefs.getInt("pref_notif_dnd_end_hour", 7))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Notifications Push
        SettingsToggleItem(
            title = "Notifications Push",
            description = "Autoriser les alertes d'actualité",
            checked = pushNotifications,
            onCheckedChange = {
                pushNotifications = it
                sharedPrefs.edit().putBoolean("pref_notif_push_enabled", it).apply()
            },
            icon = null
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        // Événements
        SettingsToggleItem(
            title = "Événements",
            description = "Recevoir une notification quand un grand événement dans “À la une” démarre (Coupe du Monde, etc.).",
            checked = eventsEnabled,
            onCheckedChange = {
                eventsEnabled = it
                sharedPrefs.edit().putBoolean("pref_notif_events_enabled", it).apply()
            },
            icon = null
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        // Fin d'abonnement
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleItem(
                title = "Fin d’abonnement",
                description = "Alerte avant l’expiration du Premium.",
                checked = subExpiryEnabled,
                onCheckedChange = {
                    subExpiryEnabled = it
                    sharedPrefs.edit().putBoolean("pref_notif_sub_expiry_enabled", it).apply()
                },
                icon = null
            )

            if (subExpiryEnabled) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Délai de l'alerte",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            "1_day" to "1 jour avant",
                            "1_hour" to "1 heure avant"
                        ).forEach { (delayKey, delayLabel) ->
                            val isSelected = subExpiryDelay == delayKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFFE50914)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFE50914) else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        subExpiryDelay = delayKey
                                        sharedPrefs.edit().putString("pref_notif_sub_expiry_delay", delayKey).apply()
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = delayLabel,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        // Mode Ne pas déranger
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleItem(
                title = "Mode Ne pas déranger",
                description = "Plages horaires sans notifications.",
                checked = dndEnabled,
                onCheckedChange = {
                    dndEnabled = it
                    sharedPrefs.edit().putBoolean("pref_notif_dnd_enabled", it).apply()
                },
                icon = null
            )

            if (dndEnabled) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Plage horaire active",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Début",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        dndStartHour = (dndStartHour + 23) % 24
                                        sharedPrefs.edit().putInt("pref_notif_dnd_start_hour", dndStartHour).apply()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                ) {
                                    Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = String.format("%02dh00", dndStartHour),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        dndStartHour = (dndStartHour + 1) % 24
                                        sharedPrefs.edit().putInt("pref_notif_dnd_start_hour", dndStartHour).apply()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                ) {
                                    Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Fin",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        dndEndHour = (dndEndHour + 23) % 24
                                        sharedPrefs.edit().putInt("pref_notif_dnd_end_hour", dndEndHour).apply()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                ) {
                                    Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = String.format("%02dh00", dndEndHour),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        dndEndHour = (dndEndHour + 1) % 24
                                        sharedPrefs.edit().putInt("pref_notif_dnd_end_hour", dndEndHour).apply()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                ) {
                                    Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }


    }
}
