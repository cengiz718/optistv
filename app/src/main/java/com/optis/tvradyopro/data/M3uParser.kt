package com.optis.tvradyopro.data

import com.optis.tvradyopro.model.Channel

object M3uParser {

    private val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    private val DEFAULT_REFERER = "https://vavoo.to/"
    private val DEFAULT_ORIGIN = "https://vavoo.to"

    private val LOGO_REGEX = Regex("tvg-logo=\"([^\"]*)\"")
    private val GROUP_REGEX = Regex("group-title=\"([^\"]*)\"")
    private val NAME_REGEX = Regex("tvg-name=\"([^\"]*)\"")

    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()

        var globalUserAgent = DEFAULT_UA
        var globalReferer = DEFAULT_REFERER
        var globalOrigin = DEFAULT_ORIGIN

        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup: String = "Genel"
        var itemUserAgent: String? = null
        var itemReferer: String? = null
        var itemOrigin: String? = null

        var index = 0
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXT-X-USER-AGENT:") -> {
                    globalUserAgent = line.substringAfter("#EXT-X-USER-AGENT:").trim()
                }
                line.startsWith("#EXT-X-REFERER:") -> {
                    globalReferer = line.substringAfter("#EXT-X-REFERER:").trim()
                }
                line.startsWith("#EXT-X-ORIGIN:") -> {
                    globalOrigin = line.substringAfter("#EXT-X-ORIGIN:").trim()
                }
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> {
                    itemUserAgent = line.substringAfter("=").trim()
                }
                line.startsWith("#EXTVLCOPT:http-referrer=") -> {
                    itemReferer = line.substringAfter("=").trim()
                }
                line.startsWith("#EXTVLCOPT:http-origin=") -> {
                    itemOrigin = line.substringAfter("=").trim()
                }
                line.startsWith("#EXTINF:") -> {
                    val logoMatch = LOGO_REGEX.find(line)
                    currentLogo = logoMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

                    val groupMatch = GROUP_REGEX.find(line)
                    currentGroup = groupMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "Genel"

                    val commaIdx = line.lastIndexOf(',')
                    val titleFromComma = if (commaIdx != -1 && commaIdx + 1 < line.length) {
                        line.substring(commaIdx + 1).trim()
                    } else ""

                    val nameMatch = NAME_REGEX.find(line)
                    currentName = titleFromComma.ifBlank { nameMatch?.groupValues?.get(1)?.trim() ?: "Kanal ${index + 1}" }
                }
                !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://")) -> {
                    val name = currentName ?: "Kanal ${index + 1}"
                    val parts = line.split("|", limit = 2)
                    val cleanUrl = parts[0].trim()
                    var pipeUa: String? = null
                    var pipeRef: String? = null
                    var pipeOrig: String? = null

                    if (parts.size > 1) {
                        parts[1].split("&").forEach { param ->
                            val kv = param.split("=", limit = 2)
                            if (kv.size == 2) {
                                when (kv[0].lowercase()) {
                                    "user-agent" -> pipeUa = kv[1]
                                    "referer", "referrer" -> pipeRef = kv[1]
                                    "origin" -> pipeOrig = kv[1]
                                }
                            }
                        }
                    }

                    val isRadio = detectRadio(name, currentGroup)
                    val smartCat = classifyChannel(name, currentGroup, isRadio)

                    channels.add(
                        Channel(
                            id = "${index}_${name.hashCode()}",
                            name = name,
                            streamUrl = cleanUrl,
                            logoUrl = currentLogo,
                            originalGroup = currentGroup,
                            smartCategory = smartCat,
                            isRadio = isRadio,
                            userAgent = pipeUa ?: itemUserAgent ?: globalUserAgent,
                            referer = pipeRef ?: itemReferer ?: globalReferer,
                            origin = pipeOrig ?: itemOrigin ?: globalOrigin
                        )
                    )
                    index++
                    currentName = null
                    currentLogo = null
                    itemUserAgent = null
                    itemReferer = null
                    itemOrigin = null
                }
            }
        }
        return channels
    }

    private fun detectRadio(name: String, group: String): Boolean {
        val upper = "$name $group".uppercase()
        val radioKeywords = listOf(
            "RADYO", "RADIO", " FM", "FM ", "POWERTURK", "POWER LOVE", "POWER DANCE",
            "NUMBER 1 ASK", "NUMBER 1 DAMAR", "NUMBER 1 DANCE", "PAL STATION",
            "RETRO T", "NOSTALJI", "AKUSTIK", "FASIL", "SLOW KARADENIZ",
            "AHMET KAYA", "MUSLUM GURSES", "SEZEN AKSU", "TARKAN", "ZARA",
            "CENGIZ KURTOGLU", "MAHSUN KIRMIZIGUL", "YILDIZ TILBE", "ZERRIN OZER",
            "SELDA BAGCAN", "SONER ARICA", "VINTAGE MUSIC"
        )
        return radioKeywords.any { upper.contains(it) }
    }

    private fun classifyChannel(name: String, originalGroup: String, isRadio: Boolean): String {
        val uName = name.uppercase()
        val uGroup = originalGroup.uppercase()

        if (uGroup.contains("GERMANY") || uGroup.contains("ALMANYA")) return "Almanya"
        if (isRadio) return "Radyo & Müzik"

        return when {
            listOf("SPOR", "SPORT", "BEIN", "EXXEN", "TIVIBU", "TABII", "NBA", "FB TV", "GS TV", "GALATASARAY", "TJK", "TAY", "IDMAN", "SARAN").any { uName.contains(it) } -> "Spor"
            listOf("HABER", "NEWS", "NTV", "CNN", "HALK", "SOZCU", "SZC", "TELE 1", "KRT", "TRT", "A HABER", "BLOOMBERG", "EKOTURK", "EKO TURK", "TBMM", "ULUSAL", "LIDER", "FLASH", "ÜLKE", "ULKE", "BENGU", "TÜRKHABER").any { uName.contains(it) && !uName.contains("BELGESEL") && !uName.contains("COCUK") && !uName.contains("MUZIK") } -> "Haber & Ulusal"
            listOf("SINEMA", "S NEMA", "MOVIE", "CINE", "FILM", "SALON", "BOX", "FIX", "PROTURK", "MAX ", "ENO ", "YESILCAM", "YEŞILÇAM", "FX", "DIZI").any { uName.contains(it) } -> "Sinema & Dizi"
            listOf("COCUK", "ÇOCUK", "OCUK", "CARTOON", "DISNEY", "NICK", "BABY", "MINIKA", "M N KA", "PEPE", "NILOYA", "KELOGLAN", "KRAL SAKIR", "POKEMON", "BOOMERANG", "MASAL", "EBA", "DA VINC", "CBEEBIES", "DUCK", "PISI", "KUKULI").any { uName.contains(it) } -> "Çocuk & Eğitim"
            listOf("BELGESEL", "NAT GEO", "NATIONAL", "DISCOVERY", "HISTORY", "VIASAT", "BBC EARTH", "LOVE NATURE", "ANIMAL", "YABAN", "CIFTCI", "AGRO", "TARIH", "DOCU", "IZ TV").any { uName.contains(it) } -> "Belgesel & Doğa"
            listOf("ATV", "KANAL D", "SHOW", "STAR", "NOW", "FOX", "TV 8", "TV8", "BEYAZ", "360", "TEVE", "KANAL 7").any { uName.contains(it) } -> "Haber & Ulusal"
            else -> "Yerel & Diğer"
        }
    }
}
