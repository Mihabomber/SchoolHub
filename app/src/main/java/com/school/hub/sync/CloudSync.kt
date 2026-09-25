package com.school.hub.sync

import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
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
    @GET("api/classes/{code}/cheats")
    suspend fun pull(@Path("code") code: String, @Query("since") since: Long): PullResponse

    @POST("api/classes/{code}/cheats")
    suspend fun push(@Path("code") code: String, @Body body: PushRequest): PushResponse
}

/** Синхронизация через простой REST-сервер (см. папку server/). */
class CloudSync(
    private val repo: CheatSheetRepository,
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
                .addConverterFactory(GsonConverterFactory.create())
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

            val dirty = repo.exportDirty()
            if (dirty.isNotEmpty()) {
                dirty.chunked(20).forEach { chunk -> api.push(code, PushRequest(chunk)) }
                repo.markClean(dirty)
            }
            val res = api.pull(code, settings.lastServerSync)
            val changed = repo.mergeRemote(res.items.orEmpty(), markDirty = false)
            settings.lastServerSync = res.serverTime
            "отправлено ${dirty.size}, получено $changed"
        }
    }
}
