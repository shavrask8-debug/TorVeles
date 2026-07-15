package com.example.torrentstreamer

import android.content.Context
import android.util.Log
import com.example.torrentstreamer.data.TorrentMetadata
import com.example.torrentstreamer.data.WatchHistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetadataFetcher(
    private val context: Context,
    private val tmdbApi: TmdbApi,
    private val dao: WatchHistoryDao
) {
    private val categoryPrefs = context.getSharedPreferences("torrent_categories", Context.MODE_PRIVATE)
    private val posterPrefs = context.getSharedPreferences("torrent_posters", Context.MODE_PRIVATE)

    suspend fun fetchAndCache(hash: String, torrentDisplayName: String): TorrentMetadata? = withContext(Dispatchers.IO) {
        val parsed = TorrentTitleParser.parse(torrentDisplayName)
        Log.d("MetadataFetcher", "Parsing search values: title='${parsed.cleanTitle}', year=${parsed.year}")

        val response = runCatching {
            tmdbApi.searchMulti(query = parsed.cleanTitle, year = parsed.year)
        }.getOrNull()

        if (response == null || response.results.isEmpty()) {
            Log.w("MetadataFetcher", "No multi-search matches found for: ${parsed.cleanTitle}")
            return@withContext null
        }

        // Пріоритезація типу контенту та фільтрація за найкращим рейтингом
        val bestMatch = response.results
            .filter { it.media_type == "movie" || it.media_type == "tv" }
            .filter { (it.media_type == "tv") == parsed.isSeries }
            .maxByOrNull { it.vote_average ?: 0.0 }
            ?: response.results.firstOrNull { it.media_type == "movie" || it.media_type == "tv" }
            ?: response.results.firstOrNull()
            ?: return@withContext null

        val details = runCatching {
            if (bestMatch.media_type == "tv") tmdbApi.getTvDetails(bestMatch.id)
            else tmdbApi.getMovieDetails(bestMatch.id)
        }.getOrNull()

        val directorName = details?.credits?.crew
            ?.firstOrNull { it.job == "Director" || it.job == "Creator" || it.job == "Producer" }
            ?.name

        val runtimeMinutes = details?.runtime ?: details?.episode_run_time?.firstOrNull()

        // Перевірка аніме: жанр Animation (16) + країна походження Японія (JP)
        val isAnimeType = bestMatch.genre_ids?.contains(16) == true &&
                bestMatch.origin_country?.contains("JP") == true

        val category = when {
            isAnimeType -> "Аніме"
            bestMatch.media_type == "tv" -> "Серіал"
            else -> "Фільм"
        }

        val posterUrlPath = bestMatch.poster_path?.let { "${TmdbApi.IMAGE_BASE}$it" } ?: ""

        val metadata = TorrentMetadata(
            hash = hash,
            title = bestMatch.title ?: bestMatch.name ?: parsed.cleanTitle,
            posterUrl = posterUrlPath,
            year = (bestMatch.release_date ?: bestMatch.first_air_date)?.take(4) ?: (parsed.year?.toString() ?: ""),
            director = directorName,
            runtime = runtimeMinutes?.let { "$it хв" },
            isSeries = bestMatch.media_type == "tv" || parsed.isSeries,
            isAnime = isAnimeType,
            fetchedAt = System.currentTimeMillis()
        )

        // Кешування результатів у Room
        dao.saveMetadata(metadata)

        // Збереження в SharedPreferences для миттєвого оновлення інтерфейсу без перезапуску
        categoryPrefs.edit().putString(hash, category).apply()
        if (posterUrlPath.isNotBlank()) {
            posterPrefs.edit().putString(hash, posterUrlPath).apply()
        }

        Log.i("MetadataFetcher", "Success metadata caching. Hash: $hash, cached title: ${metadata.title}")
        metadata
    }
}