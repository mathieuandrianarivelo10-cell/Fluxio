package com.fluxio.features.admin

import android.content.Context
import com.fluxio.shared.models.LiveChannel
import org.json.JSONArray
import org.json.JSONObject
import com.fluxio.core.database.DatabaseRepository
import com.fluxio.core.database.CustomProgramEntity
import kotlinx.coroutines.flow.first
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AdminSyncUtils {

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }

    suspend fun syncChannelOverridesFromFirebase(context: Context) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("channel_overrides").get().awaitTask()
            if (snapshot != null && !snapshot.isEmpty) {
                val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                val editor = sharedPrefs.edit()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: continue
                    val name = doc.getString("name") ?: ""
                    val url = doc.getString("url") ?: ""
                    val category = doc.getString("category") ?: ""
                    val logoUrl = doc.getString("logoUrl") ?: ""
                    val country = doc.getString("country") ?: ""
                    val description = doc.getString("description") ?: ""
                    val logoText = doc.getString("logoText") ?: ""
                    
                    editor.putString("name_override_$id", name)
                    editor.putString("url_override_$id", url)
                    editor.putString("category_override_$id", category)
                    editor.putString("logoUrl_override_$id", logoUrl)
                    editor.putString("country_override_$id", country)
                    editor.putString("description_override_$id", description)
                    editor.putString("logoText_override_$id", logoText)
                }
                editor.apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncCustomChannelsFromFirebase(context: Context) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            // Try priority read from Cloud Firestore, fall back to Realtime Database on exception
            val snapshot = try {
                firestore.collection("custom_channels").get().awaitTask()
            } catch (firestoreEx: Exception) {
                firestoreEx.printStackTrace()
                null
            }

            if (snapshot != null) {
                val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                val channelsJsonStr = sharedPrefs.getString("custom_channels_json", "[]") ?: "[]"
                val localArray = JSONArray(channelsJsonStr)
                val localMap = mutableMapOf<String, JSONObject>()
                
                for (i in 0 until localArray.length()) {
                    val obj = localArray.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isNotEmpty()) {
                        localMap[id] = obj
                    }
                }

                var hasChanges = false

                // Proactively delete any local channels matching AB1
                val localKeysToPurge = localMap.keys.filter { key ->
                    key.equals("AB1", ignoreCase = true) ||
                    key.equals("custom_AB1", ignoreCase = true) ||
                    key.contains("ab1", ignoreCase = true) ||
                    (localMap[key]?.optString("name")?.contains("AB1", ignoreCase = true) ?: false)
                }
                if (localKeysToPurge.isNotEmpty()) {
                    for (key in localKeysToPurge) {
                        localMap.remove(key)
                    }
                    hasChanges = true
                }

                val remoteIds = mutableSetOf<String>()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: continue
                    val name = doc.getString("name") ?: ""
                    
                    if (id.equals("AB1", ignoreCase = true) ||
                        id.equals("custom_AB1", ignoreCase = true) ||
                        id.contains("ab1", ignoreCase = true) ||
                        name.contains("AB1", ignoreCase = true)
                    ) {
                        try {
                            firestore.collection("custom_channels").document(doc.id).delete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        continue
                    }
                    
                    remoteIds.add(id)
                    val url = doc.getString("url") ?: ""
                    val category = doc.getString("category") ?: ""
                    val logoText = doc.getString("logoText") ?: ""
                    val description = doc.getString("description") ?: ""
                    val logoUrl = doc.getString("logoUrl") ?: ""
                    val country = doc.getString("country") ?: ""
                    val isPaid = doc.getBoolean("isPaid") ?: false

                    val localObj = localMap[id]
                    if (localObj == null) {
                        val newObj = JSONObject().apply {
                            put("id", id)
                            put("name", name)
                            put("url", url)
                            put("category", category)
                            put("logoText", logoText)
                            put("description", description)
                            put("logoUrl", logoUrl)
                            put("country", country)
                            put("isPaid", isPaid)
                        }
                        localMap[id] = newObj
                        hasChanges = true
                    } else {
                        if (localObj.optString("name") != name ||
                            localObj.optString("url") != url ||
                            localObj.optString("category") != category ||
                            localObj.optString("description") != description ||
                            localObj.optString("logoUrl") != logoUrl ||
                            localObj.optBoolean("isPaid") != isPaid
                        ) {
                            localObj.put("name", name)
                            localObj.put("url", url)
                            localObj.put("category", category)
                            localObj.put("description", description)
                            localObj.put("logoUrl", logoUrl)
                            localObj.put("isPaid", isPaid)
                            hasChanges = true
                        }
                    }
                }

                // Delete local channels that have been removed from remote database
                val keysToRemove = localMap.keys.filter { !remoteIds.contains(it) }
                if (keysToRemove.isNotEmpty()) {
                    for (key in keysToRemove) {
                        localMap.remove(key)
                    }
                    hasChanges = true
                }

                if (hasChanges || localArray.length() == 0) {
                    val updatedArray = JSONArray()
                    for (obj in localMap.values) {
                        updatedArray.put(obj)
                    }
                    sharedPrefs.edit().putString("custom_channels_json", updatedArray.toString()).apply()
                }

                val currentPublishedIds = sharedPrefs.getStringSet("admin_tv_channels_ids", emptySet()) ?: emptySet()
                val updatedPublishedIds = currentPublishedIds.toMutableSet()
                updatedPublishedIds.addAll(remoteIds)
                val filteredPublishedIds = updatedPublishedIds.filter { !it.startsWith("custom_") || remoteIds.contains(it) }.toSet()
                sharedPrefs.edit().putStringSet("admin_tv_channels_ids", filteredPublishedIds).apply()
            } else {
                // Fallback: load custom channels from Realtime Database as specified in rule 6
                try {
                    val rtdb = com.google.firebase.database.FirebaseDatabase.getInstance()
                    val rtdbSnapshot = rtdb.getReference("custom_channels").get().awaitTask()
                    if (rtdbSnapshot.exists()) {
                        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
                        val channelsJsonStr = sharedPrefs.getString("custom_channels_json", "[]") ?: "[]"
                        val localArray = JSONArray(channelsJsonStr)
                        val localMap = mutableMapOf<String, JSONObject>()
                        
                        for (i in 0 until localArray.length()) {
                            val obj = localArray.getJSONObject(i)
                            val id = obj.optString("id", "")
                            if (id.isNotEmpty()) {
                                localMap[id] = obj
                            }
                        }

                        var hasChanges = false

                        // Proactively delete local channels matching AB1
                        val localKeysToPurge = localMap.keys.filter { key ->
                            key.equals("AB1", ignoreCase = true) ||
                            key.equals("custom_AB1", ignoreCase = true) ||
                            key.contains("ab1", ignoreCase = true) ||
                            (localMap[key]?.optString("name")?.contains("AB1", ignoreCase = true) ?: false)
                        }
                        if (localKeysToPurge.isNotEmpty()) {
                            for (key in localKeysToPurge) {
                                localMap.remove(key)
                            }
                            hasChanges = true
                        }

                        val remoteIds = mutableSetOf<String>()
                        
                        for (child in rtdbSnapshot.children) {
                            val id = child.key ?: continue
                            val name = child.child("name").value?.toString() ?: ""
                            
                            if (id.equals("AB1", ignoreCase = true) ||
                                id.equals("custom_AB1", ignoreCase = true) ||
                                id.contains("ab1", ignoreCase = true) ||
                                name.contains("AB1", ignoreCase = true)
                            ) {
                                try {
                                    rtdb.getReference("custom_channels").child(id).removeValue()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                continue
                            }
                            
                            remoteIds.add(id)
                            val url = child.child("url").value?.toString() ?: ""
                            val category = child.child("category").value?.toString() ?: ""
                            val logoText = child.child("logoText").value?.toString() ?: ""
                            val description = child.child("description").value?.toString() ?: ""
                            val logoUrl = child.child("logoUrl").value?.toString() ?: ""
                            val country = child.child("country").value?.toString() ?: ""
                            val isPaid = child.child("isPaid").value as? Boolean ?: false

                            val localObj = localMap[id]
                            if (localObj == null) {
                                val newObj = JSONObject().apply {
                                    put("id", id)
                                    put("name", name)
                                    put("url", url)
                                    put("category", category)
                                    put("logoText", logoText)
                                    put("description", description)
                                    put("logoUrl", logoUrl)
                                    put("country", country)
                                    put("isPaid", isPaid)
                                }
                                localMap[id] = newObj
                                hasChanges = true
                            } else {
                                if (localObj.optString("name") != name ||
                                    localObj.optString("url") != url ||
                                    localObj.optString("category") != category ||
                                    localObj.optString("description") != description ||
                                    localObj.optString("logoUrl") != logoUrl ||
                                    localObj.optBoolean("isPaid") != isPaid
                                ) {
                                    localObj.put("name", name)
                                    localObj.put("url", url)
                                    localObj.put("category", category)
                                    localObj.put("description", description)
                                    localObj.put("logoUrl", logoUrl)
                                    localObj.put("isPaid", isPaid)
                                    hasChanges = true
                                }
                            }
                        }

                        // Delete local channels that have been removed from remote database
                        val keysToRemove = localMap.keys.filter { !remoteIds.contains(it) }
                        if (keysToRemove.isNotEmpty()) {
                            for (key in keysToRemove) {
                                localMap.remove(key)
                            }
                            hasChanges = true
                        }

                        if (hasChanges || localArray.length() == 0) {
                            val updatedArray = JSONArray()
                            for (obj in localMap.values) {
                                updatedArray.put(obj)
                            }
                            sharedPrefs.edit().putString("custom_channels_json", updatedArray.toString()).apply()
                        }

                        val currentPublishedIds = sharedPrefs.getStringSet("admin_tv_channels_ids", emptySet()) ?: emptySet()
                        val updatedPublishedIds = currentPublishedIds.toMutableSet()
                        updatedPublishedIds.addAll(remoteIds)
                        val filteredPublishedIds = updatedPublishedIds.filter { !it.startsWith("custom_") || remoteIds.contains(it) }.toSet()
                        sharedPrefs.edit().putStringSet("admin_tv_channels_ids", filteredPublishedIds).apply()
                    }
                } catch (rtdbEx: Exception) {
                    rtdbEx.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncCustomProgramsFromFirebase(context: Context) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("custom_programs").get().awaitTask()
            if (snapshot != null) {
                val repo = DatabaseRepository(context)
                
                // First, proactively delete any local programs that match AB1
                val customChannels = AdminUtils.loadCustomChannels(context)
                val ab1ChannelIds = customChannels.filter { chan ->
                    chan.id.equals("AB1", ignoreCase = true) ||
                    chan.id.equals("custom_AB1", ignoreCase = true) ||
                    chan.id.contains("ab1", ignoreCase = true) ||
                    chan.name.contains("AB1", ignoreCase = true)
                }.map { it.id }.toMutableSet()
                
                ab1ChannelIds.add("AB1")
                ab1ChannelIds.add("custom_AB1")
                ab1ChannelIds.add("8")
                ab1ChannelIds.add("custom_8")

                val initialLocalPrograms = repo.getAllPrograms().first()
                for (localProg in initialLocalPrograms) {
                    if (ab1ChannelIds.contains(localProg.channelId) ||
                        localProg.channelId.equals("AB1", ignoreCase = true) ||
                        localProg.channelId.equals("custom_AB1", ignoreCase = true) ||
                        localProg.channelId.contains("ab1", ignoreCase = true) ||
                        localProg.programName.contains("AB1", ignoreCase = true)
                    ) {
                        repo.deleteProgram(localProg.id)
                    }
                }

                val localPrograms = repo.getAllPrograms().first()
                val remoteIds = mutableSetOf<Long>()
                val remoteDeterministicKeys = mutableSetOf<String>()

                for (doc in snapshot.documents) {
                    val channelId = doc.getString("channelId") ?: continue
                    val programName = doc.getString("programName") ?: ""
                    
                    // If it matches AB1, delete it from Firestore and skip syncing
                    if (ab1ChannelIds.contains(channelId) ||
                        channelId.equals("AB1", ignoreCase = true) ||
                        channelId.equals("custom_AB1", ignoreCase = true) ||
                        channelId.contains("ab1", ignoreCase = true) ||
                        programName.contains("AB1", ignoreCase = true)
                    ) {
                        try {
                            firestore.collection("custom_programs").document(doc.id).delete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        continue
                    }
                    
                    if (programName.contains("FIFA World Cup 2026")) {
                        continue
                    }
                    
                    val startTime = doc.getString("startTime") ?: ""
                    val endTime = doc.getString("endTime") ?: ""
                    val day = doc.getString("day") ?: ""
                    val description = doc.getString("description") ?: ""
                    val imageUrl = doc.getString("imageUrl") ?: ""
                    
                    val docIdStr = doc.id
                    val parsedId = docIdStr.toLongOrNull()
                    val deterministicKey = "${channelId}_${programName}_${startTime}"

                    if (parsedId != null) {
                        remoteIds.add(parsedId)
                    } else {
                        remoteDeterministicKeys.add(deterministicKey)
                    }

                    val localMatch = if (parsedId != null) {
                        localPrograms.find { it.id == parsedId }
                    } else {
                        localPrograms.find { 
                            it.channelId == channelId && 
                            it.programName == programName && 
                            it.startTime == startTime 
                        }
                    }

                    if (localMatch == null) {
                        repo.insertProgram(
                            CustomProgramEntity(
                                id = parsedId ?: 0L,
                                channelId = channelId,
                                programName = programName,
                                startTime = startTime,
                                endTime = endTime,
                                day = day,
                                description = description,
                                imageUrl = imageUrl
                            )
                        )
                    } else {
                        if (localMatch.channelId != channelId ||
                            localMatch.programName != programName ||
                            localMatch.startTime != startTime ||
                            localMatch.endTime != endTime ||
                            localMatch.day != day ||
                            localMatch.description != description ||
                            localMatch.imageUrl != imageUrl
                        ) {
                            repo.insertProgram(
                                CustomProgramEntity(
                                    id = localMatch.id,
                                    channelId = channelId,
                                    programName = programName,
                                    startTime = startTime,
                                    endTime = endTime,
                                    day = day,
                                    description = description,
                                    imageUrl = imageUrl
                                )
                            )
                        }
                    }
                }

                // Delete local programs that are no longer present in the remote snapshot
                for (localProg in localPrograms) {
                    val deterministicKey = "${localProg.channelId}_${localProg.programName}_${localProg.startTime}"
                    val existsInRemote = remoteIds.contains(localProg.id) || remoteDeterministicKeys.contains(deterministicKey)
                    if (!existsInRemote) {
                        repo.deleteProgram(localProg.id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
