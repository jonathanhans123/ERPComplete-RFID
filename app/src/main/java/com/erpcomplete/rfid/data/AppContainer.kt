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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
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
import javax.net.ssl.HttpsURLConnection

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val authStore = AuthStore(appContext)
    val sessionCache = SessionCache(authStore)
    val localeSettingsStore = LocaleSettingsStore(appContext)
    val workflowDraftStore = WorkflowDraftStore(appContext)

    init {
        authStore.attachSessionCache(sessionCache)
    }

    val deviceId: String
        get() = authStore.deviceUuidBlocking()

    private val isRefreshing = AtomicBoolean(false)

    /*
     * POST auth/refresh revokes the token it was called with. Two refreshes with the same token
     * therefore race: the loser gets 401 and used to wipe the session (right after sign-in, the
     * login screen, the loggedIn effect and the ON_RESUME observer all fire at once). So:
     * refresh one at a time, don't rotate a token that was just issued, and never treat a 401
     * on a token someone else already rotated as "signed out".
     */
    private val sessionMutex = Mutex()
    @Volatile private var freshToken: String? = null
    @Volatile private var freshTokenAtMs = 0L

    /** Record a token just issued by login or refresh; it is not rotated again for a while. */
    fun markTokenFresh(token: String) {
        freshToken = token
        freshTokenAtMs = System.currentTimeMillis()
    }

    private fun isFresh(token: String): Boolean =
        token == freshToken && System.currentTimeMillis() - freshTokenAtMs < FRESH_TOKEN_WINDOW_MS

    /** VPS is reached by IPv4; TLS cert may list the cloud hostname. */
    private val hostnameVerifier = javax.net.ssl.HostnameVerifier { hostname, session ->
        hostname == "187.77.125.241" ||
            HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
    }

    private val apiBaseUrl: String
        get() = BuildConfig.API_BASE_URL.trimEnd('/')

    private val nginxHostInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host == "187.77.125.241") {
            chain.proceed(
                request.newBuilder()
                    .header("Host", BuildConfig.NGINX_HOST)
                    .build(),
            )
        } else {
            chain.proceed(request)
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
        val path = response.request.url.encodedPath
        if (path.contains("auth/login") || path.contains("auth/refresh")) return@Authenticator null
        if (responseCount(response) >= 2) return@Authenticator null
        // Another request already rotated the token: retry with the current one instead of refreshing again.
        val current = sessionCache.accessToken
        val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (!current.isNullOrBlank() && sent != null && sent != current) {
            return@Authenticator response.request.newBuilder()
                .header("Authorization", "Bearer $current")
                .build()
        }
        if (!isRefreshing.compareAndSet(false, true)) return@Authenticator null
        try {
            val token = sessionCache.accessToken ?: return@Authenticator null
            val refreshResponse = runBlocking {
                refreshClient.newCall(
                    Request.Builder()
                        .url("$apiBaseUrl/auth/refresh")
                        .post("".toRequestBody(null))
                        .header("Host", BuildConfig.NGINX_HOST)
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
            markTokenFresh(newToken)
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
        .hostnameVerifier(hostnameVerifier)
        .addInterceptor(nginxHostInterceptor)
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .hostnameVerifier(hostnameVerifier)
        .authenticator(tokenAuthenticator)
        .addInterceptor(nginxHostInterceptor)
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
        baseUrlProvider = { apiBaseUrl },
        sessionCache = sessionCache,
    )
    val workflowApi = WorkflowApiHelper(api, syncRepository, networkSyncMonitor)
    val rfidSettingsStore = RfidSettingsStore(appContext)
    val notificationSettingsStore = NotificationSettingsStore(appContext)
    val backgroundReliabilityStore = BackgroundReliabilityStore(appContext)
    val zebraFirmwareRepository = ZebraFirmwareRepository(appContext, api)
    val rfidManager = RfidManager(appContext, rfidSettingsStore, zebraFirmwareRepository)

    /** Rotate API token on cold start / app resume (same as Messenger). One refresh at a time. */
    suspend fun ensureValidSession(): Boolean = sessionMutex.withLock {
        val token = sessionCache.accessToken
        if (token.isNullOrBlank()) return@withLock false
        // Issued moments ago (login, or the caller just ahead of us in the lock): nothing to rotate.
        if (isFresh(token)) return@withLock true
        try {
            val response = api.refreshToken()
            if (response.isSuccessful) {
                val newToken = response.body()?.access_token
                if (newToken.isNullOrBlank()) return@withLock false
                authStore.updateAccessToken(newToken)
                sessionCache.updateToken(newToken)
                markTokenFresh(newToken)
                true
            } else {
                // Only a rejection of the token that is still current means the session is over.
                if (response.code() == 401 && sessionCache.accessToken == token) authStore.clear()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

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
        /** A token this new is not rotated again (covers sign-in plus the resume that follows). */
        private const val FRESH_TOKEN_WINDOW_MS = 60_000L

        fun newDeviceUuid(): String = UUID.randomUUID().toString()
    }
}
