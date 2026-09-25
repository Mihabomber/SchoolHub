package com.school.hub.sync

import com.school.hub.core.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SyncApi {
    @GET("api/classes/{code}/{collection}")
    suspend fun pull(
        @Path("code") code: String,
        @Path("collection") collection: String,
        @Query("since") since: Long,
    ): PullResponse

    @POST("api/classes/{code}/{collection}")
    suspend fun push(
        @Path("code") code: String,
        @Path("collection") collection: String,
        @Body body: PushRequest,
    ): PushResponse
}

/** Синхронизация всех коллекций через REST-сервер (папка server/). */
class CloudSync(
    private val collections: List<SyncCollection>,
    private val settings: SettingsStore,
) {
    private var cachedUrl: String? = null
    private var cachedApi: SyncApi? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Synchronized
    private fun api(rawUrl: String): SyncApi {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url += "/"
        if (url != cachedUrl || cachedApi == null) {
            cachedApi = Retrofit.Builder()
                .baseUrl(url)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create(SyncJson.gson))
                .build()
                .create(SyncApi::class.java)
            cachedUrl = url
        }
        return cachedApi!!
    }

    suspend fun sync(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = settings.serverUrl.value
            require(url.isNotBlank()) { "Сервер не настроен" }
            val api = api(url)
            val code = settings.effectiveClassCode
            var sent = 0
            var received = 0
            for (c in collections) {
                val dirty = c.exportDirty()
                if (dirty.isNotEmpty()) {
                    dirty.chunked(20).forEach { chunk -> api.push(code, c.name, PushRequest(chunk)) }
                    c.markClean(dirty)
                    sent += dirty.size
                }
                val res = api.pull(code, c.name, settings.lastSync(c.name))
                received += c.merge(res.items.orEmpty(), markDirty = false)
                settings.setLastSync(c.name, res.serverTime)
            }
            "отправлено $sent, получено $received"
        }
    }
}
