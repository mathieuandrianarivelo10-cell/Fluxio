package com.fluxio.core.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fluxio.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "fluxio_alerts"
    private const val CHANNEL_NAME = "Alertes Fluxio"
    private const val CHANNEL_DESC = "Notifications et alertes de l'application Fluxio"

    @SuppressLint("MissingPermission")
    fun showNotification(context: Context, title: String, message: String, url: String? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = 0xFFE50914.toInt()
                setSound(defaultSoundUri, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = if (!url.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            pendingIntentFlags
        )

        // Use a generic system drawable or launcher icon if available
        val smallIcon = android.R.drawable.ic_dialog_info

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    fun sendWelcomeNotificationIfNeeded(uid: String) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("subscription_notifications")
            .whereEqualTo("userId", uid)
            .whereEqualTo("title", "Bienvenue sur Fluxio !")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot == null || querySnapshot.isEmpty) {
                    val notifId = java.util.UUID.randomUUID().toString()
                    val notifMap = hashMapOf<String, Any>(
                        "id" to notifId,
                        "userId" to uid,
                        "title" to "Bienvenue sur Fluxio !",
                        "message" to "Bienvenue sur Fluxio, votre plateforme de streaming IPTV ! Profitez de vos flux de diffusion en direct.",
                        "createdAt" to System.currentTimeMillis()
                    )
                    firestore.collection("subscription_notifications")
                        .document(notifId)
                        .set(notifMap)
                }
            }
    }
}
