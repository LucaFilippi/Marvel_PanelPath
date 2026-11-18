package com.lucafilippi.panelpath.data

import com.google.gson.annotations.SerializedName
import com.lucafilippi.panelpath.security.TokenStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class Page<T>(val count: Int = 0, val next: String? = null, val results: List<T> = emptyList())
data class PublisherDto(val id: Int = 0, val name: String = "")
data class SeriesDto(val id: Int, val series: String? = null, @SerializedName("year_began") val yearBegan: Int? = null, val publisher: PublisherDto? = null, @SerializedName("issue_count") val issueCount: Int? = null, val desc: String? = null)
data class IssueSeriesDto(val id: Int = 0, val name: String? = null, val publisher: PublisherDto? = null)
data class IssueDto(val id: Int, val series: IssueSeriesDto? = null, val number: String? = null, val issue: String? = null, val image: String? = null, @SerializedName("cover_date") val coverDate: String? = null, val desc: String? = null)

interface MetronApi {
    @GET("publisher/") suspend fun publishers(@Query("name") name: String = "Marvel", @Query("page") page: Int = 1): Page<PublisherDto>
    @GET("series/") suspend fun series(@Query("q") query: String, @Query("publisher_name") publisher: String = "Marvel", @Query("page") page: Int = 1): Page<SeriesDto>
    @GET("series/{id}/") suspend fun seriesDetail(@Path("id") id: Int): SeriesDto
    @GET("series/{id}/issue_list/") suspend fun issues(@Path("id") seriesId: Int, @Query("page") page: Int = 1): Page<IssueDto>
    @GET("issue/") suspend fun searchIssues(@Query("series_name") query: String, @Query("publisher_name") publisher: String = "Marvel", @Query("page") page: Int = 1): Page<IssueDto>
    @GET("issue/{id}/") suspend fun issue(@Path("id") id: Int): IssueDto
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val message: String) : ApiResult<Nothing>
}

class MetronRepository(private val tokenStore: TokenStore) {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).followRedirects(false)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val token = tokenStore.read()
            val builder = request.newBuilder().header("User-Agent", "PanelPath/0.4.4")
            if (request.url.isHttps && request.url.host == "metron.cloud" && !token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            chain.proceed(builder.build())
        }).build()
    private val api = Retrofit.Builder().baseUrl("https://metron.cloud/api/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(MetronApi::class.java)

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        if (tokenStore.read().isNullOrBlank()) ApiResult.Failure("Add your Metron token in Settings to search online.")
        else ApiResult.Success(block())
    } catch (e: HttpException) {
        ApiResult.Failure(when (e.code()) { 401, 403 -> "Token rejected by Metron. Check or replace it in Settings."; 429 -> "Metron rate limit reached. Try again later."; in 500..599 -> "Metron is temporarily unavailable."; else -> "Metron request failed (HTTP ${e.code()})." })
    } catch (_: SocketTimeoutException) { ApiResult.Failure("Connection timed out. Try again.") }
      catch (_: IOException) { ApiResult.Failure("No connection to Metron. Check your internet access.") }
      catch (e: CancellationException) { throw e }
      catch (_: Exception) { ApiResult.Failure("Could not read the Metron response.") }

    suspend fun validate(candidate: String): ApiResult<Unit> {
        val safeClient = client.newBuilder().addInterceptor(Interceptor { chain ->
            val original = chain.request()
            chain.proceed(if (original.url.isHttps && original.url.host == "metron.cloud") original.newBuilder().header("Authorization", "Bearer $candidate").build() else original)
        }).build()
        val validationApi = Retrofit.Builder().baseUrl("https://metron.cloud/api/").client(safeClient)
            .addConverterFactory(GsonConverterFactory.create()).build().create(MetronApi::class.java)
        return try { validationApi.publishers(); ApiResult.Success(Unit) }
        catch (e: HttpException) { ApiResult.Failure(if (e.code() == 401 || e.code() == 403) "Metron rejected this token." else if (e.code() == 429) "Metron rate limit reached. Try again later." else "Metron returned HTTP ${e.code()}.") }
        catch (_: IOException) { ApiResult.Failure("Could not connect to Metron. Check your connection.") }
        catch (_: Exception) { ApiResult.Failure("Could not validate this token.") }
    }
    suspend fun series(q: String, page: Int) = call { api.series(q, page = page) }
    suspend fun searchIssues(q: String, page: Int) = call { api.searchIssues(q, page = page) }
    suspend fun seriesDetail(id: Int) = call { api.seriesDetail(id) }
    suspend fun issues(seriesId: Int, page: Int) = call { api.issues(seriesId, page) }
    suspend fun issue(id: Int) = call { api.issue(id) }
}
