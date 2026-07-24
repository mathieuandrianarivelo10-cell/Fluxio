package com.fluxio.features.iptv

import android.content.SharedPreferences
import android.util.Base64
import com.fluxio.shared.models.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

val COMMON_CATEGORIES = listOf(
    "Information continue",
    "Sport",
    "Cinéma / Séries",
    "Patrimoine / Archives",
    "Jeunesse",
    "Documentaires / Découverte",
    "Musicale / Clips",
    "Divertissement / Humour",
    "Cuisine / Gastronomie",
    "Féminine / Lifestyle",
    "Éducative / Culturelle",
    "Religieuse / Spiritualité",
    "Parlementaire / Politique",
    "Météo",
    "Télé-achat / Shopping",
    "Jeux vidéo / High-tech",
    "Animaux / Nature"
)

object StreamObfuscator {
    private const val KEY = 0x5F

    fun obfuscate(input: String): String {
        val xorBytes = input.toByteArray(Charsets.UTF_8).map { (it.toInt() xor KEY).toByte() }.toByteArray()
        return "enc:" + Base64.encodeToString(xorBytes, Base64.NO_WRAP)
    }

    fun deobfuscate(input: String?): String {
        if (input == null) return ""
        if (!input.startsWith("enc:")) return input
        val cleanInput = input.substring(4)
        return try {
            val decodedBytes = Base64.decode(cleanInput, Base64.NO_WRAP)
            val decryptedBytes = decodedBytes.map { (it.toInt() xor KEY).toByte() }.toByteArray()
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            input
        }
    }
}

fun normalizeChannelName(name: String): String {
    var norm = name.lowercase()
        .replace("+", "plus")
        .replace("&", "and")
        .trim()
    norm = norm.replace(Regex("\\b(hd|sd|fhd|direct|1080p|720p|hevc|h264|backup|fr|us|ca|uk)\\b"), "")
    norm = norm.replace(Regex("[^a-z0-9 ]"), "")
    norm = norm.replace(Regex("\\s+"), " ")
    return norm.trim()
}

fun isValidStreamUrl(url: String): Boolean {
    val cleanUrl = StreamObfuscator.deobfuscate(url)
    val lower = cleanUrl.lowercase().trim()
    if (lower.isEmpty()) return false
    if (!lower.startsWith("http://") && !lower.startsWith("https://") && 
        !lower.startsWith("rtmp://") && !lower.startsWith("rtsp://") && 
        !lower.startsWith("mms://")) {
        return false
    }
    if (lower.contains("://127.0.0.1") || lower.contains("://localhost") || 
        lower.contains("://192.168.") || lower.contains("://10.") || 
        lower.contains("://172.16.")) {
        return false
    }
    if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".zip") || 
        lower.endsWith(".rar") || lower.endsWith(".txt") || lower.endsWith(".pdf") || 
        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
        lower.endsWith(".gif")) {
        return false
    }
    if (lower.contains("fluxio.tv") || lower.contains("invalid.url") || 
        lower.contains("yourdomain.com") || lower.contains("stream-url-here")) {
        return false
    }
    return true
}

fun deduplicateChannels(channels: List<LiveChannel>): List<LiveChannel> {
    val map = LinkedHashMap<String, LiveChannel>()
    val mapByUrl = LinkedHashMap<String, LiveChannel>()
    
    for (chan in channels) {
        if (!isValidStreamUrl(chan.url)) continue

        val normName = normalizeChannelName(chan.name)
        val normUrl = chan.url.lowercase().trim()
        
        val existingByName = map[normName]
        val existingByUrl = mapByUrl[normUrl]
        
        if (existingByName == null && existingByUrl == null) {
            map[normName] = chan
            mapByUrl[normUrl] = chan
        } else {
            val existing = existingByName ?: existingByUrl!!
            var keepNew = false
            
            val isExistingLocal = existing.id.startsWith("local_") || existing.id.startsWith("horizon_")
            val isNewLocal = chan.id.startsWith("local_") || chan.id.startsWith("horizon_")
            if (isNewLocal && !isExistingLocal) {
                keepNew = true
            } else if (!isNewLocal && isExistingLocal) {
                keepNew = false
            } else {
                val existingHasLogo = existing.logoUrl.isNotEmpty()
                val newHasLogo = chan.logoUrl.isNotEmpty()
                if (newHasLogo && !existingHasLogo) {
                    keepNew = true
                } else if (!newHasLogo && existingHasLogo) {
                    keepNew = false
                } else {
                    val existingIsHttps = existing.url.startsWith("https://", ignoreCase = true)
                    val newIsHttps = chan.url.startsWith("https://", ignoreCase = true)
                    if (newIsHttps && !existingIsHttps) {
                        keepNew = true
                    }
                }
            }
            
            if (keepNew) {
                map.remove(normalizeChannelName(existing.name))
                mapByUrl.remove(existing.url.lowercase().trim())
                
                map[normName] = chan
                mapByUrl[normUrl] = chan
            }
        }
    }
    return map.values.toList()
}

fun String.normalize(): String {
    val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase().trim()
}

suspend fun fetchUrlText(urlString: String): String = withContext(Dispatchers.IO) {
    val cleanUrl = StreamObfuscator.deobfuscate(urlString)
    try {
        val targetUrl = if (cleanUrl == "https://iptv-org.github.io/iptv/index.m3u") {
            "https://iptv-org.github.io/iptv/index.country.m3u"
        } else {
            cleanUrl
        }
        
        val response = com.fluxio.core.network.NetworkClient.apiService.fetchM3u(targetUrl)
        if (response.isSuccessful) {
            response.body()?.string() ?: ""
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

suspend fun checkChannelPlayability(urlString: String): Boolean = withContext(Dispatchers.IO) {
    val cleanUrl = StreamObfuscator.deobfuscate(urlString)
    if (cleanUrl.isEmpty()) return@withContext false
    var connection: HttpURLConnection? = null
    try {
        val url = URL(cleanUrl)
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        connection.instanceFollowRedirects = true
        
        val responseCode = connection.responseCode
        responseCode in 200..399
    } catch (e: Exception) {
        false
    } finally {
        connection?.disconnect()
    }
}

fun isFrenchChannel(channel: LiveChannel): Boolean {
    val nameLower = channel.name.lowercase()
    val catLower = channel.category.lowercase()
    val countryLower = channel.country.lowercase()
    
    val frenchCountries = setOf(
        "france", "belgique", "suisse", "luxembourg", "monaco", 
        "sénégal", "senegal", "madagascar", "cameroun", "cameroon", 
        "côte d'ivoire", "ivory_coast", "bénin", "benin", "togo", 
        "mali", "niger", "burkina faso", "république démocratique du congo", "congo",
        "haiti", "haïti", "martinique", "guadeloupe", "réunion", "reunion", "guyane", "polynésie", "polynesie"
    )
    
    if (frenchCountries.any { countryLower.contains(it) }) {
        return true
    }
    
    val frenchIndicators = listOf(
        "fr:", "(fr)", "[fr]", " fr ", "fr -", "french", "francais", "français"
    )
    if (frenchIndicators.any { nameLower.contains(it) }) {
        return true
    }
    
    if (catLower.contains("français") || catLower.contains("francais") || catLower.contains("french")) {
        return true
    }
    
    return false
}

suspend fun parseM3u(m3uContent: String, sourceName: String): List<LiveChannel> = withContext(Dispatchers.Default) {
    val channels = mutableListOf<LiveChannel>()
    val lines = m3uContent.lines()
    var currentInfoLine = ""
    var channelCount = 0

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("#EXTINF:")) {
            currentInfoLine = trimmed
        } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            if (currentInfoLine.isNotEmpty()) {
                val parsed = parseExtInf(currentInfoLine, trimmed, sourceName, channelCount++)
                if (parsed != null && isFrenchChannel(parsed)) {
                    channels.add(parsed)
                }
                currentInfoLine = ""
            }
        }
    }
    channels
}

val GENRE_KEYWORDS = setOf(
    "infos", "information", "actu", "journal", "presse", "sport", "sports", "foot", "cinéma", "cinema", "séries", "series", "film", "movie",
    "patrimoine", "archive", "archives", "retro", "vintage", "jeunesse", "enfant", "kid", "kids", "documentaires", "documentaire",
    "découverte", "decouverte", "discovery", "science", "musique", "musicale", "music", "clips", "clip", "divertissement", "humour",
    "comedy", "cuisine", "gastronomie", "food", "lifestyle", "féminine", "feminine", "mode", "éducative", "educative", "culturelle",
    "culture", "religieuse", "spiritualité", "spiritualite", "relig", "parlementaire", "politique", "météo", "meteo", "weather",
    "télé-achat", "tele-achat", "shopping", "jeux vidéo", "gaming", "game", "high-tech", "tech", "animaux", "animal", "nature"
)

fun normalizeGenre(genre: String): String {
    val lower = genre.lowercase().trim()
    return when {
        lower.contains("meteo") || lower.contains("météo") || lower.contains("weather") -> "Météo"
        lower.contains("shopping") || lower.contains("achat") || lower.contains("boutique") || lower.contains("tele-achat") || lower.contains("télé-achat") -> "Télé-achat / Shopping"
        lower.contains("game") || lower.contains("gaming") || lower.contains("jeux video") || lower.contains("high-tech") || lower.contains("tech") || lower.contains("geek") -> "Jeux vidéo / High-tech"
        lower.contains("animal") || lower.contains("nature") || lower.contains("dog") || lower.contains("cat") || lower.contains("zoo") || lower.contains("wild") || lower.contains("safari") || lower.contains("chasse") || lower.contains("peche") || lower.contains("pêche") || lower.contains("fishing") -> "Animaux / Nature"
        lower.contains("parlem") || lower.contains("polit") || lower.contains("senat") || lower.contains("assemblée") || lower.contains("lcp") -> "Parlementaire / Politique"
        lower.contains("relig") || lower.contains("spirit") || lower.contains("kto") || lower.contains("bible") || lower.contains("chretien") || lower.contains("islam") || lower.contains("eglise") || lower.contains("dieu") -> "Religieuse / Spiritualité"
        lower.contains("educ") || lower.contains("éduc") || lower.contains("culture") || lower.contains("art") || lower.contains("arte") || lower.contains("savoir") || lower.contains("theatre") -> "Éducative / Culturelle"
        lower.contains("lifestyle") || lower.contains("mode") || lower.contains("fashion") || lower.contains("feminine") || lower.contains("féminine") || lower.contains("deco") || lower.contains("déco") || lower.contains("design") || lower.contains("maison") || lower.contains("beauté") || lower.contains("beauty") || lower.contains("wellness") || lower.contains("bien-être") -> "Féminine / Lifestyle"
        lower.contains("cuisine") || lower.contains("gastronomie") || lower.contains("food") || lower.contains("chef") || lower.contains("gourmand") || lower.contains("marmiton") || lower.contains("recette") || lower.contains("kitchen") -> "Cuisine / Gastronomie"
        lower.contains("patrimoine") || lower.contains("archive") || lower.contains("retro") || lower.contains("vintage") || lower.contains("ina") -> "Patrimoine / Archives"
        lower.contains("info") || lower.contains("news") || lower.contains("journal") || lower.contains("presse") || lower.contains("actu") -> "Information continue"
        lower.contains("sport") || lower.contains("foot") || lower.contains("racing") || lower.contains("golf") || lower.contains("tennis") -> "Sport"
        lower.contains("ciné") || lower.contains("cine") || lower.contains("movie") || lower.contains("film") || lower.contains("series") || lower.contains("séries") || lower.contains("fiction") || lower.contains("polar") -> "Cinéma / Séries"
        lower.contains("kid") || lower.contains("jeunesse") || lower.contains("enfant") || lower.contains("cart") || lower.contains("toon") || lower.contains("disney") || lower.contains("gulli") -> "Jeunesse"
        lower.contains("doc") || lower.contains("science") || lower.contains("discovery") || lower.contains("geo") || lower.contains("voyage") || lower.contains("planete") || lower.contains("ushuaia") || lower.contains("decouverte") || lower.contains("découverte") -> "Documentaires / Découverte"
        lower.contains("music") || lower.contains("musique") || lower.contains("hit") || lower.contains("clip") || lower.contains("clips") || lower.contains("rock") || lower.contains("pop") || lower.contains("jazz") || lower.contains("opera") -> "Musicale / Clips"
        else -> "Divertissement / Humour"
    }
}

fun normalizeCountry(country: String): String {
    val trimmed = country.trim()
    val lower = trimmed.lowercase()
    return when {
        lower == "france" -> "France"
        lower == "united states" || lower == "usa" || lower == "us" -> "États-Unis"
        lower == "united kingdom" || lower == "uk" -> "Royaume-Uni"
        lower == "germany" || lower == "deutschland" -> "Allemagne"
        lower == "spain" || lower == "españa" -> "Espagne"
        lower == "italy" || lower == "italia" -> "Italie"
        lower == "canada" -> "Canada"
        lower == "belgium" || lower == "belgique" -> "Belgique"
        lower == "switzerland" || lower == "suisse" -> "Suisse"
        lower == "brazil" || lower == "brasil" -> "Brésil"
        lower == "japan" -> "Japon"
        lower == "china" -> "Chine"
        lower == "russia" -> "Russie"
        lower == "india" -> "Inde"
        else -> trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

fun isRealCountry(name: String): Boolean {
    val lower = name.lowercase().trim()
    val countries = setOf(
        "france", "états-unis", "etats-unis", "united states", "usa", "us", "royaume-uni", "united kingdom", "uk", "allemagne", "germany", 
        "espagne", "spain", "italie", "italy", "canada", "belgique", "belgium", "suisse", "switzerland", "brésil", "brazil", "japon", "japan", 
        "chine", "china", "russie", "russia", "inde", "india", "portugal", "australie", "australia", "mexique", "mexico", "argentine", "argentina", 
        "colombie", "colombia", "maroc", "morocco", "algérie", "algerie", "algeria", "tunisie", "tunisia", "sénégal", "senegal", "turquie", "turkey", 
        "grèce", "greece", "suède", "sweden", "norvège", "norway", "danemark", "denmark", "finlande", "finland", "pays-bas", "netherlands", 
        "autriche", "austria", "pologne", "poland", "ukraine", "égypte", "egypt", "arabie saoudite", "saudi_arabia", "émirats arabes unis", 
        "uae", "viêt nam", "vietnam", "thaïlande", "thailand", "indonésie", "indonesia", "philippines", "corée du sud", "south_korea", 
        "afrique du sud", "south_africa", "nouvelle-zélande", "new_zealand", "chili", "chile", "pérou", "peru", "venezuela", "équateur", 
        "ecuador", "bolivie", "bolivia", "paraguay", "uruguay", "roumanie", "romania", "bulgarie", "bulgaria", "hongrie", "hungary", 
        "république tchèque", "czech", "slovaquie", "slovakia", "croatie", "croatia", "serbie", "serbia", "irlande", "ireland", 
        "singapour", "singapore", "malaisie", "malaysia", "cameroun", "cameroon", "côte d'ivoire", "ivory_coast", "madagascar", "angola", 
        "azerbaïdjan", "azerbaijan", "bahamas", "bahreïn", "bahrain", "bangladesh", "barbade", "barbados", "bélarus", "belarus", 
        "bosnie", "bosnia", "botswana", "brunéi", "brunei", "cambodge", "cambodia", "costa_rica", "cuba", "chypre", "cyprus", 
        "dominicaine", "dominican", "estonie", "estonia", "éthiopie", "ethiopia", "fidji", "fiji", "géorgie", "georgia", "ghana", 
        "guatemala", "haïti", "haiti", "honduras", "islande", "iceland", "iran", "iraq", "israël", "israel", "jamaïque", "jamaica", 
        "jordanie", "jordan", "kazakhstan", "kenya", "koweït", "kuwait", "lettonie", "latvia", "liban", "lebanon", "libye", "libya", 
        "lituanie", "lithuania", "luxembourg", "macédoine", "macedonia", "malte", "malta", "moldavie", "moldova", "monaco", "mongolie", 
        "mongolia", "népal", "nepal", "nicaragua", "nigéria", "nigeria", "oman", "pakistan", "panama", "qatar", "salvador", "slovénie", 
        "slovenia", "sri_lanka", "soudan", "sudan", "syrie", "syria", "taïwan", "taiwan", "tanzanie", "tanzania", "ouzbékistan", 
        "uzbekistan", "yémen", "yemen", "zambie", "zambia", "zimbabwe", "international"
    )
    return countries.contains(lower)
}

fun detectCountryFromName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("france") || lower.contains("fr ") || lower.contains(" fr") || lower.contains("français") || lower.contains("francais") -> "France"
        lower.contains("usa") || lower.contains("us ") || lower.contains("united states") -> "États-Unis"
        lower.contains("uk ") || lower.contains(" uk") || lower.contains("united kingdom") || lower.contains("british") -> "Royaume-Uni"
        lower.contains("spain") || lower.contains("espagne") || lower.contains(" es ") || lower.contains(" es") -> "Espagne"
        lower.contains("italy") || lower.contains("italie") || lower.contains("it ") -> "Italie"
        lower.contains("germany") || lower.contains("allemagne") || lower.contains("de ") -> "Allemagne"
        lower.contains("canada") || lower.contains("ca ") -> "Canada"
        else -> "International"
    }
}

fun detectGenreFromName(name: String): String {
    return normalizeGenre(name)
}

fun cleanGeoBlocked(text: String): String {
    return text
        .replace(Regex("(?i)\\s*[\\[\\(]*\\s*geo[- ]*blocked\\s*[\\]\\)]*"), "")
        .replace(Regex("(?i)\\s*geo[- ]*blocked"), "")
        .replace(Regex("(?i)\\s*geoblocked"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    }

fun parseExtInf(infoLine: String, url: String, sourceName: String, index: Int): LiveChannel? {
    try {
        val commaIndex = infoLine.lastIndexOf(',')
        val rawName = if (commaIndex != -1 && commaIndex < infoLine.length - 1) {
            infoLine.substring(commaIndex + 1).trim()
        } else {
            "Chaîne $index"
        }

        var name = cleanGeoBlocked(rawName)
        name = name.replace(Regex("[-|:\\s]+$"), "").trim()
        if (name.isEmpty()) {
            name = rawName
        }

        var category = "Divertissement / Humour"
        val groupRegex = """group-title="([^"]+)"""".toRegex()
        val groupMatch = groupRegex.find(infoLine)
        if (groupMatch != null) {
            var matchedGroup = groupMatch.groupValues[1].trim()
            matchedGroup = cleanGeoBlocked(matchedGroup)
                .replace(Regex("[-|:\\s]+$"), "").trim()
            if (matchedGroup.isNotEmpty()) {
                category = matchedGroup
            }
        }

        var country = "International"
        var genre = "Divertissement / Humour"

        val matchedLower = category.lowercase()
        val isGenre = GENRE_KEYWORDS.any { matchedLower.contains(it) }

        if (isGenre) {
            genre = normalizeGenre(category)
            country = detectCountryFromName(name)
        } else {
            val normalizedC = normalizeCountry(category)
            if (isRealCountry(normalizedC)) {
                country = normalizedC
                genre = detectGenreFromName(name)
            } else {
                country = detectCountryFromName(name)
                genre = category.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        var logoUrl = ""
        val logoRegex = """tvg-logo="([^"]+)"""".toRegex()
        val logoMatch = logoRegex.find(infoLine)
        if (logoMatch != null) {
            logoUrl = logoMatch.groupValues[1]
        }

        var id = ""
        val idRegex = """tvg-id="([^"]+)"""".toRegex()
        val idMatch = idRegex.find(infoLine)
        if (idMatch != null) {
            id = idMatch.groupValues[1]
        }
        if (id.isEmpty()) {
            id = "${sourceName.lowercase().replace(Regex("[^a-z0-9]"), "_")}_$index"
        }

        val logoText = makeLogoText(name)

        return LiveChannel(
            id = id,
            name = name,
            url = url,
            category = genre,
            logoText = logoText,
            description = "Chaîne en direct (Pays : $country, Genre : $genre).",
            logoUrl = logoUrl,
            country = country
        )
    } catch (e: Exception) {
        return null
    }
}

fun makeLogoText(name: String): String {
    val clean = name.replace(Regex("[^a-zA-Z0-9 ]"), "")
    val words = clean.split(" ").filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> (words[0].take(1) + words[1].take(2)).uppercase()
    }
}

fun getMockCommentsForChannel(channelId: String): List<String> {
    return when (channelId) {
        "f24_fr" -> listOf(
            "Le direct fonctionne super bien, excellente qualité !",
            "Toujours informé avec France 24.",
            "La meilleure chaîne d'info en continu."
        )
        "f24_en" -> listOf(
            "Great stream quality, no buffering at all!",
            "France 24 is my go-to news source.",
            "Really clear audio and video stream."
        )
        "nasa_tv" -> listOf(
            "Wow, the space view is breath-taking!",
            "I love watching these live space walks.",
            "NASA TV stream is working flawlessly here."
        )
        "aljazeera_en" -> listOf(
            "High-quality reporting and live coverage.",
            "Excellent stream, zero lag.",
            "Great coverage of world events."
        )
        "redbull_tv" -> listOf(
            "This extreme sports stream is insane!",
            "Super smooth playback, perfect for action scenes!",
            "Red Bull TV has the best adrenaline-packed content."
        )
        "bbb_hd", "sintel_hd", "tears_hd" -> listOf(
            "Beautiful HD quality verification stream.",
            "The sound design is perfect.",
            "Great short movie!"
        )
        else -> listOf(
            "Superbe flux, ultra stable !",
            "Qualité d'image incroyable sur cette chaîne.",
            "Rien à dire, l'application est au top !"
        )
    }
}

fun getCommentsForChannel(channelId: String, sharedPrefs: SharedPreferences): List<String> {
    val saved = sharedPrefs.getString("comments_$channelId", null)
    if (saved != null) {
        return saved.split("|||").filter { it.isNotEmpty() }
    }
    return getMockCommentsForChannel(channelId)
}

fun addCommentToChannel(channelId: String, comment: String, sharedPrefs: SharedPreferences) {
    val current = getCommentsForChannel(channelId, sharedPrefs).toMutableList()
    current.add(comment)
    sharedPrefs.edit().putString("comments_$channelId", current.joinToString("|||")).apply()
}

fun getPrimaryCategory(channel: LiveChannel): String {
    return channel.category.split(",").firstOrNull()?.trim() ?: "Divertissement / Humour"
}

fun assignChannelNumbers(channels: List<LiveChannel>): List<LiveChannel> {
    val categorySlots = mapOf(
        "Information continue" to listOf(1..4, 29..32),
        "Sport" to listOf(5..7, 33..36),
        "Divertissement / Humour" to listOf(8..10, 41..43),
        "Cinéma / Séries" to listOf(11..13, 37..40),
        "Jeunesse" to listOf(14..15, 47..49),
        "Documentaires / Découverte" to listOf(16..17, 44..46),
        "Musicale / Clips" to listOf(18..18, 50..52),
        "Patrimoine / Archives" to listOf(19..19, 53..55),
        "Cuisine / Gastronomie" to listOf(20..20, 56..58),
        "Féminine / Lifestyle" to listOf(21..21, 59..61),
        "Éducative / Culturelle" to listOf(22..22, 62..64),
        "Animaux / Nature" to listOf(23..23, 65..67),
        "Jeux vidéo / High-tech" to listOf(24..24, 68..70),
        "Télé-achat / Shopping" to listOf(25..25, 71..73),
        "Météo" to listOf(26..26, 74..76),
        "Religieuse / Spiritualité" to listOf(27..27, 77..79),
        "Parlementaire / Politique" to listOf(28..28, 80..82)
    )

    val sortedChannels = channels.sortedWith(compareBy({ getPrimaryCategory(it) }, { it.name }, { it.id }))
    val assignedMap = mutableMapOf<String, Int>()
    
    val categoryChannels = sortedChannels.groupBy { getPrimaryCategory(it) }
    val leftovers = mutableListOf<LiveChannel>()
    
    for ((category, slotsList) in categorySlots) {
        val listForCat = categoryChannels[category] ?: emptyList()
        var indexInCat = 0
        val allSlots = slotsList.flatMap { it.toList() }
        
        for (channel in listForCat) {
            if (indexInCat < allSlots.size) {
                assignedMap[channel.id] = allSlots[indexInCat]
                indexInCat++
            } else {
                leftovers.add(channel)
            }
        }
    }
    
    for (channel in sortedChannels) {
        if (!categorySlots.containsKey(getPrimaryCategory(channel))) {
            leftovers.add(channel)
        }
    }
    
    var nextLeftoverSlot = 83
    for (channel in leftovers) {
        assignedMap[channel.id] = nextLeftoverSlot
        nextLeftoverSlot++
    }
    
    return channels.map { channel ->
        val number = assignedMap[channel.id] ?: 99
        channel.copy(channelNumber = number)
    }
}

fun formatCategoriesForCompactDisplay(categoryString: String): String {
    val list = categoryString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (list.isEmpty()) return "Chaîne TV"
    if (list.size <= 2) {
        return list.joinToString(", ")
    }
    return "${list[0]}, ${list[1]} (+${list.size - 2})"
}

