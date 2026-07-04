package com.erpcomplete.rfid.data

import android.content.Context
import com.erpcomplete.rfid.BuildConfig
import com.erpcomplete.rfid.data.local.AppDatabase
import com.erpcomplete.rfid.data.remote.ErpApiService
import com.erpcomplete.rfid.data.remote.RefreshTokenResponse
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.rfid.ZebraFirmwareRepository
import com.erpcomplete.rfid.sync.NetworkSyncMonitor
import com.erpcomplete.rfid.sync.SyncRepository
import com.erpcomplete.rfid.util.AppLog
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val authStore = AuthStore(appContext)
    val sessionCache = SessionCache(authStore)
    val apiSettingsStore = ApiSettingsStore(appContext)
    val localeSettingsStore = LocaleSettingsStore(appContext)
    val workflowDraftStore = WorkflowDraftStore(appContext)

    init {
        authStore.attachSessionCache(sessionCache)
    }

    val deviceId: String
        get() = authStore.deviceUuidBlocking()

    private val isRefreshing = AtomicBoolean(false)

    private val baseUrlInterceptor = Interceptor { chain ->
        val request = chain.request()
        val configured = apiSettingsStore.baseUrlBlocking().trimEnd('/')
        val default = BuildConfig.API_BASE_URL.trimEnd('/')
        if (configured == default) {
            chain.proceed(request)
        } else {
            val overrideBase = configured.toHttpUrlOrNull()
            if (overrideBase == null) {
                chain.proceed(request)
            } else {
                val path = request.url.encodedPath
                val suffix = path.removePrefix(overrideBase.encodedPath).ifBlank { path }
                val newUrl = overrideBase.newBuilder()
                    .encodedPath(
                        overrideBase.encodedPath.trimEnd('/') + suffix,
                    )
                    .query(request.url.query)
                    .build()
                chain.proceed(request.newBuilder().url(newUrl).build())
            }
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .header("X-Device-Id", deviceId)
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("Accept", "application/json")
        sessionCache.accessToken?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        sessionCache.businessUnitId?.let { builder.header("X-Business-Unit-Id", it) }
        sessionCache.teamId?.let { builder.header("X-Team-Id", it) }
        sessionCache.warehouseId?.let { builder.header("X-Warehouse-Id", it) }
        val request = builder.build()
        val response = chain.proceed(request)
        if (BuildConfig.DEBUG) {
            AppLog.api(request.method, request.url.toString(), response.code)
        }
        response
    }

    private val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        if (response.code != 401) return@Authenticator null
        if (response.request.url.encodedPath.contains("auth/login")) return@Authenticator null
        if (responseCount(response) >= 2) return@Authenticator null
        if (!isRefreshing.compareAndSet(false, true)) return@Authenticator null
        try {
            val token = sessionCache.accessToken ?: return@Authenticator null
            val refreshResponse = runBlocking {
                refreshClient.newCall(
                    Request.Builder()
                        .url("${apiSettingsStore.baseUrlBlocking().trimEnd('/')}/auth/refresh")
                        .post("".toRequestBody(null))
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build(),
                ).execute()
            }
            if (!refreshResponse.isSuccessful) return@Authenticator null
            val body = refreshResponse.body?.string() ?: return@Authenticator null
            val newToken = com.google.gson.Gson()
                .fromJson(body, RefreshTokenResponse::class.java)
                .access_token ?: return@Authenticator null
            runBlocking { authStore.updateAccessToken(newToken) }
            sessionCache.updateToken(newToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        } catch (_: Exception) {
            null
        } finally {
            isRefreshing.set(false)
        }
    }

    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .authenticator(tokenAuthenticator)
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ensureTrailingSlash(BuildConfig.API_BASE_URL))
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ErpApiService = retrofit.create(ErpApiService::class.java)

    val database = AppDatabase.get(appContext)
    val networkSyncMonitor = NetworkSyncMonitor(appContext)
    val syncRepository = SyncRepository(
        dao = database.pendingSyncDao(),
        api = api,
        networkMonitor = networkSyncMonitor,
        baseUrlProvider = { apiSettingsStore.baseUrlBlocking() },
        sessionCache = sessionCache,
    )
    val workflowApi = WorkflowApiHelper(api, syncRepository, networkSyncMonitor)
    val rfidSettingsStore = RfidSettingsStore(appContext)
    val zebraFirmwareRepository = ZebraFirmwareRepository(appContext, api)
    val rfidManager = RfidManager(appContext, rfidSettingsStore, zebraFirmwareRepository)

    /** Reload ERP page permissions for mobile inventory (requires workspace headers when set). */
    suspend fun refreshMobilePermissions(): Boolean {
        return try {
            val response = api.currentUser()
            if (!response.isSuccessful) return false
            val permissions = response.body()?.mobile_permissions
            authStore.saveMobileInventoryPermissionsFromJson(permissions)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        fun newDeviceUuid(): String = UUID.randomUUID().toString()
    }
}
