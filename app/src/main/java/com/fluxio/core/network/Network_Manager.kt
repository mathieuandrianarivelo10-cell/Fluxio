package com.fluxio.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.SharedPreferences

object NetworkManager {

    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isMobileConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun getNetworkTypeName(context: Context): String {
        return when {
            isWifiConnected(context) -> "Wi-Fi"
            isMobileConnected(context) -> "Données mobiles"
            else -> "Déconnecté"
        }
    }

    fun addDataUsage(context: Context, megaBytes: Float): Boolean {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        val currentUsage = sharedPrefs.getFloat("data_consumed_today", 450.0f)
        val newUsage = currentUsage + megaBytes
        sharedPrefs.edit().putFloat("data_consumed_today", newUsage).apply()

        val dataLimit = sharedPrefs.getFloat("data_limit", 1000.0f)
        val threshold = dataLimit * 0.8f

        val wasBelow = currentUsage < threshold
        val isAbove = newUsage >= threshold

        return wasBelow && isAbove
    }
}
