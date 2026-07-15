package com.example.torrentstreamer

object TorrentTitleParser {
    private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val SEASON_EPISODE_REGEX = Regex("""[Ss](\d{1,2})[Ee](\d{1,3})""")
    private val JUNK_TAGS = listOf(
        "1080p", "720p", "2160p", "4k", "hdrip", "webrip", "web-dl", "webdl",
        "bluray", "brrip", "dvdrip", "hdtv", "x264", "x265", "h264", "h265",
        "hevc", "aac", "dts", "ac3", "5.1", "amzn", "nf", "proper", "repack",
        "extended", "unrated", "remastered", "multi", "dual"
    )

    data class ParsedTitle(
        val cleanTitle: String,
        val year: Int?,
        val isSeries: Boolean,
        val season: Int?,
        val episode: Int?
    )

    fun parse(rawName: String): ParsedTitle {
        var name = rawName
            .substringBeforeLast(".") // Прибираємо розширення файлу, якщо є
            .replace(Regex("""[._]"""), " ") // Крапки/підкреслення -> пробіли
            .replace(Regex("""[\[\](){}]"""), " ") // Дужки -> пробіли

        val seasonMatch = SEASON_EPISODE_REGEX.find(name)
        val isSeries = seasonMatch != null
        val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
        val episode = seasonMatch?.groupValues?.get(2)?.toIntOrNull()

        val year = YEAR_REGEX.find(name)?.value?.toIntOrNull()

        // Все, що після року, сезону чи першого тегу якості — обрізаємо
        val cutIndex = listOfNotNull(
            year?.let { name.indexOf(it.toString()) }.takeIf { it != null && it > 0 },
            seasonMatch?.range?.first?.takeIf { it > 0 },
            JUNK_TAGS.map { tag -> name.indexOf(tag, ignoreCase = true) }
                .filter { it > 0 }.minOrNull()
        ).minOrNull()

        val cleanTitle = (if (cutIndex != null) name.substring(0, cutIndex) else name)
            .replace(Regex("""[-–]$"""), "")
            .trim()
            .replace(Regex("""\s+"""), " ")

        return ParsedTitle(cleanTitle, year, isSeries, season, episode)
    }
}