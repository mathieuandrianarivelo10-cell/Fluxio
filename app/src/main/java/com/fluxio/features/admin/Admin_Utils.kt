package com.fluxio.features.admin

import android.content.Context
import com.fluxio.shared.models.LiveChannel
import org.json.JSONArray
import org.json.JSONObject
import com.fluxio.core.database.DatabaseRepository
import com.fluxio.core.database.CustomProgramEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.fluxio.features.iptv.StreamObfuscator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

object AdminUtils {

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }
    fun saveCustomChannel(context: Context, channel: LiveChannel) {
        val obfuscatedUrl = if (channel.url.startsWith("enc:")) {
            channel.url
        } else {
            StreamObfuscator.obfuscate(channel.url)
        }

        val obfuscatedChannel = channel.copy(url = obfuscatedUrl)

        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        val channelsJsonStr = sharedPrefs.getString("custom_channels_json", "[]") ?: "[]"
        val jsonArray = JSONArray(channelsJsonStr)
        val jsonObject = JSONObject().apply {
            put("id", obfuscatedChannel.id)
            put("name", obfuscatedChannel.name)
            put("url", obfuscatedChannel.url)
            put("category", obfuscatedChannel.category)
            put("logoText", obfuscatedChannel.logoText)
            put("description", obfuscatedChannel.description)
            put("logoUrl", obfuscatedChannel.logoUrl)
            put("country", obfuscatedChannel.country)
            put("isPaid", obfuscatedChannel.isPaid)
        }
        jsonArray.put(jsonObject)
        sharedPrefs.edit().putString("custom_channels_json", jsonArray.toString()).apply()

        val currentPublishedIds = sharedPrefs.getStringSet("admin_tv_channels_ids", emptySet()) ?: emptySet()
        val updatedPublishedIds = currentPublishedIds + obfuscatedChannel.id
        sharedPrefs.edit().putStringSet("admin_tv_channels_ids", updatedPublishedIds).apply()

        try {
            val firestore = FirebaseFirestore.getInstance()
            val channelMap = hashMapOf(
                "id" to obfuscatedChannel.id,
                "name" to obfuscatedChannel.name,
                "url" to obfuscatedChannel.url,
                "category" to obfuscatedChannel.category,
                "logoText" to obfuscatedChannel.logoText,
                "description" to obfuscatedChannel.description,
                "logoUrl" to obfuscatedChannel.logoUrl,
                "country" to obfuscatedChannel.country,
                "isPaid" to obfuscatedChannel.isPaid,
                "importedAt" to System.currentTimeMillis()
            )
            firestore.collection("custom_channels").document(obfuscatedChannel.id).set(channelMap)
            
            // Mirror to Realtime Database child /custom_channels/{id}
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("custom_channels")
                    .child(obfuscatedChannel.id)
                    .setValue(channelMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            firestore.collection("catalogs").document("tv_channels").get().addOnSuccessListener { document ->
                val list = document.get("ids") as? List<*>
                val currentIds = list?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()
                currentIds.add(obfuscatedChannel.id)
                firestore.collection("catalogs").document("tv_channels").set(mapOf("ids" to currentIds.toList()))
            }
            
            firestore.collection("catalogs").document("main").get().addOnSuccessListener { document ->
                val tvChannelsList = if (document != null && document.exists()) document.get("tv_channels") as? List<*> else null
                val currentIds = tvChannelsList?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()
                currentIds.add(obfuscatedChannel.id)
                
                val featuredList = if (document != null && document.exists()) document.get("featured") as? List<*> else null
                val featuredIds = featuredList?.mapNotNull { it?.toString() }?.toList() ?: emptyList()
                
                val docData = hashMapOf(
                    "featured" to featuredIds,
                    "tv_channels" to currentIds.toList(),
                    "last_updated" to System.currentTimeMillis()
                )
                firestore.collection("catalogs").document("main").set(docData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateCustomChannelInFirebase(obfuscatedChannel: LiveChannel) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val channelMap = hashMapOf(
                "id" to obfuscatedChannel.id,
                "name" to obfuscatedChannel.name,
                "url" to obfuscatedChannel.url,
                "category" to obfuscatedChannel.category,
                "logoText" to obfuscatedChannel.logoText,
                "description" to obfuscatedChannel.description,
                "logoUrl" to obfuscatedChannel.logoUrl,
                "country" to obfuscatedChannel.country,
                "isPaid" to obfuscatedChannel.isPaid,
                "importedAt" to System.currentTimeMillis()
            )
            firestore.collection("custom_channels").document(obfuscatedChannel.id).set(channelMap)
            
            // Mirror to Realtime Database child /custom_channels/{id}
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("custom_channels")
                    .child(obfuscatedChannel.id)
                    .setValue(channelMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCustomChannels(context: Context): List<LiveChannel> {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        val channelsJsonStr = sharedPrefs.getString("custom_channels_json", "[]") ?: "[]"
        val list = mutableListOf<LiveChannel>()
        try {
            val jsonArray = JSONArray(channelsJsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LiveChannel(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        url = obj.optString("url", ""),
                        category = obj.optString("category", ""),
                        logoText = obj.optString("logoText", ""),
                        description = obj.optString("description", ""),
                        logoUrl = obj.optString("logoUrl", ""),
                        country = obj.optString("country", ""),
                        isPaid = obj.optBoolean("isPaid", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun deleteCustomChannel(context: Context, channelId: String) {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        val channelsJsonStr = sharedPrefs.getString("custom_channels_json", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(channelsJsonStr)
            val updatedArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("id") != channelId) {
                    updatedArray.put(obj)
                }
            }
            sharedPrefs.edit().putString("custom_channels_json", updatedArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val currentPublished = sharedPrefs.getStringSet("admin_tv_channels_ids", emptySet()) ?: emptySet()
            if (currentPublished.contains(channelId)) {
                sharedPrefs.edit().putStringSet("admin_tv_channels_ids", currentPublished - channelId).apply()
            }
            val currentFeatured = sharedPrefs.getStringSet("admin_featured_ids", emptySet()) ?: emptySet()
            if (currentFeatured.contains(channelId)) {
                sharedPrefs.edit().putStringSet("admin_featured_ids", currentFeatured - channelId).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("custom_channels").document(channelId).delete()

            // Delete from Realtime Database as well
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("custom_channels")
                    .child(channelId)
                    .removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            firestore.collection("catalogs").document("tv_channels").get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val list = document.get("ids") as? List<*>
                    val currentIds = list?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()
                    if (currentIds.remove(channelId)) {
                        firestore.collection("catalogs").document("tv_channels").set(mapOf("ids" to currentIds.toList()))
                    }
                }
            }

            firestore.collection("catalogs").document("featured").get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val list = document.get("ids") as? List<*>
                    val currentIds = list?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()
                    if (currentIds.remove(channelId)) {
                        firestore.collection("catalogs").document("featured").set(mapOf("ids" to currentIds.toList()))
                    }
                }
            }

            firestore.collection("catalogs").document("main").get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val tvChannelsList = document.get("tv_channels") as? List<*>
                    val currentTvIds = tvChannelsList?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()
                    val featuredList = document.get("featured") as? List<*>
                    val currentFeaturedIds = featuredList?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()

                    val removedTv = currentTvIds.remove(channelId)
                    val removedFeat = currentFeaturedIds.remove(channelId)
                    if (removedTv || removedFeat) {
                        val docData = hashMapOf(
                            "featured" to currentFeaturedIds.toList(),
                            "tv_channels" to currentTvIds.toList(),
                            "last_updated" to System.currentTimeMillis()
                        )
                        firestore.collection("catalogs").document("main").set(docData)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }



    suspend fun saveCustomProgram(
        context: Context,
        channelId: String,
        programName: String,
        startTime: String,
        endTime: String,
        day: String,
        description: String,
        imageUrl: String = "",
        id: Long = 0
    ) {
        val repo = DatabaseRepository(context)
        val savedId = repo.insertProgram(
            CustomProgramEntity(
                id = id,
                channelId = channelId,
                programName = programName,
                startTime = startTime,
                endTime = endTime,
                day = day,
                description = description,
                imageUrl = imageUrl
            )
        )

        // Save Custom Program to Cloud Firestore
        val cloudId = if (id != 0L) id.toString() else savedId.toString()
        try {
            val firestore = FirebaseFirestore.getInstance()
            val programMap = hashMapOf(
                "id" to cloudId,
                "channelId" to channelId,
                "programName" to programName,
                "startTime" to startTime,
                "endTime" to endTime,
                "day" to day,
                "description" to description,
                "imageUrl" to imageUrl,
                "savedAt" to System.currentTimeMillis()
            )
            firestore.collection("custom_programs").document(cloudId).set(programMap).awaitTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadCustomPrograms(context: Context): List<CustomProgram> {
        val repo = DatabaseRepository(context)
        val rawList = repo.getAllPrograms().first()
        val cleanList = rawList.filter { !it.programName.contains("FIFA World Cup 2026") }
        if (cleanList.size < rawList.size) {
            rawList.forEach { 
                if (it.programName.contains("FIFA World Cup 2026")) {
                    repo.deleteProgram(it.id)
                }
            }
        }
        return cleanList.map { CustomProgram(it.channelId, it.programName, it.startTime, it.endTime, it.day, it.description, it.imageUrl, it.id) }
    }

    suspend fun deleteCustomProgram(
        context: Context,
        channelId: String,
        programName: String,
        startTime: String,
        day: String,
        id: Long = 0L
    ) {
        val repo = DatabaseRepository(context)
        if (id != 0L) {
            repo.deleteProgram(id)
        } else {
            repo.deleteProgramByName(channelId, programName)
        }

        // Also delete from Cloud Firestore using both schemes and query-based matching for complete clean-up
        try {
            val firestore = FirebaseFirestore.getInstance()
            
            // 1. Delete by numeric/string doc ID if available
            if (id != 0L) {
                firestore.collection("custom_programs").document(id.toString()).delete().awaitTask()
            }
            
            // 2. Delete by deterministic ID formats
            val oldDeterministicId = "${channelId}_${programName}_${startTime}".replace("/", "_").replace(" ", "_").replace(":", "_")
            firestore.collection("custom_programs").document(oldDeterministicId).delete().awaitTask()
            
            val cleanDeterministicId = "${channelId}_${programName}_${startTime}"
            firestore.collection("custom_programs").document(cleanDeterministicId).delete().awaitTask()

            // 3. Delete by field-based query matching to ensure any old/weird document formats are thoroughly cleaned up
            val querySnapshot = firestore.collection("custom_programs")
                .whereEqualTo("channelId", channelId)
                .whereEqualTo("programName", programName)
                .whereEqualTo("startTime", startTime)
                .get()
                .awaitTask()
            
            if (querySnapshot != null) {
                for (doc in querySnapshot.documents) {
                    firestore.collection("custom_programs").document(doc.id).delete().awaitTask()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun copyUriToInternalStorage(context: Context, uri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "logo_${System.currentTimeMillis()}.png"
            val file = java.io.File(context.filesDir, fileName)
            val outputStream = java.io.FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateChannelPaidStatus(context: Context, channelId: String, isPaid: Boolean) {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        val channelsJsonStr = sharedPrefs.getString("custom_channels_json", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(channelsJsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("id") == channelId) {
                    obj.put("isPaid", isPaid)
                    break
                }
            }
            sharedPrefs.edit().putString("custom_channels_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("custom_channels").document(channelId).update("isPaid", isPaid)
            
            // Mirror paid status to Realtime Database child /custom_channels/{id}/isPaid
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("custom_channels")
                    .child(channelId)
                    .child("isPaid")
                    .setValue(isPaid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveChannelOverride(
        context: Context,
        id: String,
        name: String,
        url: String,
        category: String,
        logoUrl: String,
        country: String,
        description: String,
        logoText: String,
        isPaid: Boolean
    ) {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("name_override_$id", name)
            .putString("url_override_$id", url)
            .putString("category_override_$id", category)
            .putString("logoUrl_override_$id", logoUrl)
            .putString("country_override_$id", country)
            .putString("description_override_$id", description)
            .putString("logoText_override_$id", logoText)
            .apply()

        val obfuscatedUrl = if (url.startsWith("enc:")) url else StreamObfuscator.obfuscate(url)

        val overrideMap = hashMapOf(
            "id" to id,
            "name" to name,
            "url" to obfuscatedUrl,
            "category" to category,
            "logoUrl" to logoUrl,
            "country" to country,
            "description" to description,
            "logoText" to logoText,
            "isPaid" to isPaid,
            "updatedAt" to System.currentTimeMillis()
        )

        try {
            FirebaseFirestore.getInstance().collection("channel_overrides").document(id).set(overrideMap)
            
            // Mirror channel overrides to Realtime Database child /channel_overrides/{id}
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("channel_overrides")
                    .child(id)
                    .setValue(overrideMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getImgBBApiKey(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        val savedKey = sharedPrefs.getString("imgbb_api_key", "") ?: ""
        return if (savedKey.isNotEmpty()) savedKey else "6daf44c10bfbb28d9e67b5695572e365"
    }

    fun saveImgBBApiKey(context: Context, key: String) {
        val sharedPrefs = context.getSharedPreferences("horizon_iptv", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("imgbb_api_key", key).apply()
    }

    suspend fun uploadImageToStorage(context: Context, uri: android.net.Uri, folder: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Try ImgBB if API key is present
            val apiKey = getImgBBApiKey(context).trim()
            if (apiKey.isNotEmpty()) {
                try {
                    val contentResolver = context.contentResolver
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val bytes = inputStream.use { it.readBytes() }
                        val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .build()

                        val requestBody = okhttp3.MultipartBody.Builder()
                            .setType(okhttp3.MultipartBody.FORM)
                            .addFormDataPart("key", apiKey)
                            .addFormDataPart("image", base64Image)
                            .build()

                        val request = okhttp3.Request.Builder()
                            .url("https://api.imgbb.com/1/upload")
                            .post(requestBody)
                            .build()

                        val result = client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val responseBody = response.body?.string()
                                if (responseBody != null) {
                                    val jsonObject = org.json.JSONObject(responseBody)
                                    val dataObj = jsonObject.getJSONObject("data")
                                    dataObj.getString("url")
                                } else null
                            } else null
                        }
                        if (result != null) return@withContext result
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Try Firebase Storage
            val fbUrl = uploadImageToFirebaseStorage(uri, folder)
            if (fbUrl != null) {
                return@withContext fbUrl
            }

            // 3. Try Catbox as a robust, zero-config anonymous cloud fallback
            val catboxUrl = uploadToCatbox(context, uri)
            if (catboxUrl != null) {
                return@withContext catboxUrl
            }

            // 4. Try FreeImage.host as an extremely reliable, zero-config public cloud fallback
            val freeImageUrl = uploadToFreeImageHost(context, uri)
            if (freeImageUrl != null) {
                return@withContext freeImageUrl
            }

            null
        }
    }

    private suspend fun uploadImageToFirebaseStorage(uri: android.net.Uri, folder: String): String? {
        return try {
            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
            val fileRef = storageRef.child("$folder/img_${System.currentTimeMillis()}.png")
            fileRef.putFile(uri).awaitTask()
            val downloadUri = fileRef.downloadUrl.awaitTask()
            downloadUri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun uploadToCatbox(context: Context, uri: android.net.Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.use { it.readBytes() }

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val mediaType = "image/png".toMediaTypeOrNull()
            val fileBody = bytes.toRequestBody(mediaType)

            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", "image.png", fileBody)
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val urlStr = response.body?.string()?.trim()
                    if (urlStr != null && urlStr.startsWith("http")) {
                        urlStr
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun uploadToFreeImageHost(context: Context, uri: android.net.Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.use { it.readBytes() }
            val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("key", "6d207e02198a847aa98d0a2a901485a5") // FreeImage.host public API key
                .addFormDataPart("action", "upload")
                .addFormDataPart("source", base64Image)
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://freeimage.host/api/1/upload")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = org.json.JSONObject(responseBody)
                        val imageObj = jsonObject.getJSONObject("image")
                        imageObj.getString("url")
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


}

data class CustomProgram(
    val channelId: String,
    val programName: String,
    val startTime: String,
    val endTime: String,
    val day: String,
    val description: String,
    val imageUrl: String = "",
    val id: Long = 0
)
