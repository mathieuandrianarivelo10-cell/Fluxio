package com.fluxio.shared.models

data class LiveChannel(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val logoText: String,
    val description: String,
    val logoUrl: String = "",
    val country: String = "",
    val channelNumber: Int? = null,
    val isPaid: Boolean = false
) {
    val initials: String
        get() {
            if (logoText.isNotEmpty()) return logoText
            val words = name.trim().split("\\s+".toRegex())
            return when {
                words.isEmpty() -> "TV"
                words.size == 1 -> words[0].take(2).uppercase()
                else -> (words[0].take(1) + words[1].take(1)).uppercase()
            }
        }

    fun getBackdropUrl(): String {
        if (logoUrl.isNotEmpty()) {
            return logoUrl
        }
        return when (id) {
            "f24_fr" -> "https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=600&auto=format&fit=crop"
            "f24_en" -> "https://images.unsplash.com/photo-1495020689067-958852a6565d?q=80&w=600&auto=format&fit=crop"
            "nasa_tv" -> "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=600&auto=format&fit=crop"
            "aljazeera_en" -> "https://images.unsplash.com/photo-1526470608268-f674ce90ebd4?q=80&w=600&auto=format&fit=crop"
            "redbull_tv" -> "https://images.unsplash.com/photo-1517649763962-0c623066013b?q=80&w=600&auto=format&fit=crop"
            "bbb_hd" -> "https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=600&auto=format&fit=crop"
            "sintel_hd" -> "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=600&auto=format&fit=crop"
            "tears_hd" -> "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?q=80&w=600&auto=format&fit=crop"
            "shop_channel_jp" -> "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?q=80&w=600&auto=format&fit=crop"
            "qvc_japan" -> "https://images.unsplash.com/photo-1441986300917-64674bd600d8?q=80&w=600&auto=format&fit=crop"
            "nhk_world_jp" -> "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?q=80&w=600&auto=format&fit=crop"
            "weathernews_jp" -> "https://images.unsplash.com/photo-1530908268418-ec1c8a462227?q=80&w=600&auto=format&fit=crop"
            "tokyo_mx" -> "https://images.unsplash.com/photo-1540959733332-eab4deceeaf7?q=80&w=600&auto=format&fit=crop"
            else -> {
                val cat = category.lowercase()
                when {
                    cat.contains("info") || cat.contains("news") -> "https://images.unsplash.com/photo-1495020689067-958852a6565d?q=80&w=600&auto=format&fit=crop"
                    cat.contains("ciné") || cat.contains("movies") || cat.contains("films") -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=600&auto=format&fit=crop"
                    cat.contains("sport") -> "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?q=80&w=600&auto=format&fit=crop"
                    cat.contains("music") || cat.contains("musique") -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop"
                    cat.contains("kid") || cat.contains("jeunesse") -> "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=600&auto=format&fit=crop"
                    cat.contains("doc") -> "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=600&auto=format&fit=crop"
                    cat.contains("espace") || cat.contains("space") -> "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=600&auto=format&fit=crop"
                    cat.contains("divert") || cat.contains("show") -> "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop"
                    else -> "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop"
                }
            }
        }
    }
}

sealed class ChannelLoadState {
    object Loading : ChannelLoadState()
    data class Success(val channels: List<LiveChannel>) : ChannelLoadState()
    data class Error(val message: String) : ChannelLoadState()
}

data class IptvSource(
    val name: String,
    val url: String,
    val isLocal: Boolean = false
)

val IptvSources = emptyList<IptvSource>()
