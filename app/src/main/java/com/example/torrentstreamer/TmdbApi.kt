package com.example.torrentstreamer

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.Interceptor
import okhttp3.OkHttpClient

data class TmdbSearchResponse(val results: List<TmdbResult>)

data class TmdbResult(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val media_type: String? = null,
    val vote_average: Double? = null,
    val genre_ids: List<Int>? = null,
    val origin_country: List<String>? = null
)

data class TmdbDetails(
    val runtime: Int? = null,
    val episode_run_time: List<Int>? = null,
    val credits: TmdbCredits? = null
)

data class TmdbCredits(val crew: List<TmdbCrewMember>)
data class TmdbCrewMember(val name: String, val job: String)

interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("language") language: String = "uk-UA",
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbSearchResponse

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits",
        @Query("language") language: String = "uk-UA"
    ): TmdbDetails

    @GET("tv/{id}")
    suspend fun getTvDetails(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits",
        @Query("language") language: String = "uk-UA"
    ): TmdbDetails

    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
        const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

        fun create(): TmdbApi {
            // Найнадійніший рефлексивний витяг ключа без compile-time залежності від BuildConfig
            val apiKey = try {
                val clazz = Class.forName("com.example.torrentstreamer.BuildConfig")
                val field = clazz.getField("TMDB_API_KEY")
                field.get(null) as String
            } catch (_: Exception) {
                ""
            }

            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("accept", "application/json")
                    .build()
                chain.proceed(request)
            }

            val client = TorrServerManager.getSharedClient().newBuilder()
                .addInterceptor(authInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(TmdbApi::class.java)
        }
    }
}