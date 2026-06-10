// Faithfully implements the USA auth + command flow from:
//   egmp-bluelink-scriptable/src/lib/bluelink-regions/usa.ts
//
// Key behaviors carried over from the source:
//   - additionalHeaders block sent on every request (from/to/language/offset/
//     client_id/clientSecret/username/blueLinkServicePin/brandIndicator/User-Agent)
//   - accessToken sent in "accessToken" header (not Authorization: Bearer)
//   - Token refresh tried before falling back to full re-login
//   - Commands return tmsTid in response *headers*; poll getRunningStatus until
//     status == "SUCCESS" with 2-second sleep between attempts
//   - gen header derived from model year (>= 2025 → "3", else "2")

package com.accord.pebluelinkcompanion

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.TimeZone

private const val TAG = "BluelinkRepo"

// ── Result types ──────────────────────────────────────────────────────────────

sealed class BluelinkResult {
    data class Success(val message: String) : BluelinkResult()
    data class Error(val message: String)   : BluelinkResult()
}

data class VehicleStatusResult(
    val locked: Boolean,
    val range: Int,
    val soc: Int,              // HV battery %
    val charging: Boolean,
    val chargeTimeMins: Int,   // remaining charge time in minutes
    val twelveSoc: Int         // 12V battery %
)

class BluelinkRepository(context: Context) {

    // ── Encrypted prefs ───────────────────────────────────────────────────────
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, "bluelink_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Credentials ───────────────────────────────────────────────────────────
    var username: String
        get()      = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value).apply()

    var password: String
        get()      = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit().putString("password", value).apply()

    var pin: String
        get()      = prefs.getString("pin", "") ?: ""
        set(value) = prefs.edit().putString("pin", value).apply()

    var vin: String
        get()      = prefs.getString("vin", "") ?: ""
        set(value) = prefs.edit().putString("vin", value.uppercase()).apply()

    private var registrationId: String
        get()      = prefs.getString("regid", "") ?: ""
        set(value) = prefs.edit().putString("regid", value).apply()

    private var modelYear: String
        get()      = prefs.getString("model_year", "2024") ?: "2024"
        set(value) = prefs.edit().putString("model_year", value).apply()

    val isConfigured: Boolean
        get() = username.isNotBlank() && password.isNotBlank()
                && pin.isNotBlank() && vin.isNotBlank()

    // ── Token cache ───────────────────────────────────────────────────────────
    private var cachedAccessToken: String?
        get()      = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    private var cachedRefreshToken: String?
        get()      = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    private var tokenExpiry: Long
        get()      = prefs.getLong("token_expiry", 0L)
        set(value) = prefs.edit().putLong("token_expiry", value).apply()

    private val tokenMutex = Mutex()

    // ── Retrofit ──────────────────────────────────────────────────────────────
    private val additionalHeadersInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("from",               "SPA")
            .header("to",                 "ISS")
            .header("language",           "0")
            .header("offset",             getTimezoneOffset())
            .header("client_id",          BluelinkConstants.CLIENT_ID)
            .header("clientSecret",       BluelinkConstants.CLIENT_SECRET)
            .header("username",           username)
            .header("blueLinkServicePin", pin)
            .header("brandIndicator",     "H")
            .header("User-Agent",         "okhttp/3.14.9")
            .header("Accept",             "application/json, text/plain, */*")
            .header("Content-Type",       "application/json")
            .build()
        chain.proceed(req)
    }

    private val api: BluelinkApi = Retrofit.Builder()
        .baseUrl(BluelinkConstants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .addInterceptor(additionalHeadersInterceptor)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .build()
        )
        .build()
        .create(BluelinkApi::class.java)

    // ── Token management ──────────────────────────────────────────────────────
    private fun tokenValid(): Boolean {
        val expiry = tokenExpiry
        return expiry > 0 && (expiry - 30) > System.currentTimeMillis() / 1000
    }

    private suspend fun getValidToken(): String = tokenMutex.withLock {
        if (tokenValid()) return@withLock cachedAccessToken!!
        val refreshToken = cachedRefreshToken
        if (!refreshToken.isNullOrBlank()) {
            val refreshed = tryRefreshToken(refreshToken)
            if (refreshed != null) return@withLock refreshed
        }
        val fresh = doLogin()
        return@withLock fresh ?: throw Exception("Authentication failed")
    }

    private suspend fun tryRefreshToken(refreshToken: String): String? {
        return try {
            val resp = api.refreshToken(TokenRefreshRequest(refreshToken))
            if (resp.isSuccessful && resp.body() != null) {
                cacheTokens(resp.body()!!)
                resp.body()!!.access_token
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            null
        }
    }

    private suspend fun doLogin(): String? {
        return try {
            val resp = api.login(LoginRequest(username, password))
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!
                cacheTokens(body)
                fetchRegistrationId(body.access_token)
                body.access_token
            } else {
                Log.e(TAG, "Login HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}")
            null
        }
    }

    private fun cacheTokens(body: TokenResponse) {
        cachedAccessToken  = body.access_token
        cachedRefreshToken = body.refresh_token
        tokenExpiry        = System.currentTimeMillis() / 1000 + body.expires_in
    }

    private suspend fun fetchRegistrationId(token: String) {
        try {
            val resp = api.getVehicles(username, token)
            if (resp.isSuccessful) {
                val vehicles = resp.body()?.enrolledVehicleDetails ?: return
                val match = vehicles.firstOrNull {
                    it.vehicleDetails.vin.equals(vin, ignoreCase = true)
                } ?: vehicles.firstOrNull()
                match?.vehicleDetails?.let { v ->
                    registrationId = v.regid
                    modelYear      = v.modelYear ?: "2024"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRegistrationId: ${e.message}")
        }
    }

    // ── Car header helpers ────────────────────────────────────────────────────
    private fun gen() = if ((modelYear.toIntOrNull() ?: 2024) >= 2025) "3" else "2"

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun getStatus(forceUpdate: Boolean = false): Result<VehicleStatusResult> =
        runCatching {
            val token = getValidToken()
            val resp = api.getVehicleStatus(
                accessToken    = token,
                registrationId = registrationId,
                vin            = vin,
                appCloudVin    = vin,
                gen            = gen(),
                refresh        = if (forceUpdate) "true" else "false"
            )
            if (!resp.isSuccessful || resp.body()?.vehicleStatus == null) {
                throw Exception("Status HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
            }
            val vs = resp.body()!!.vehicleStatus!!

            val range = vs.evStatus?.drvDistance
                ?.firstOrNull()?.rangeByFuel?.evModeRange?.value?.toInt() ?: 0

            // Remaining charge time from remainTime2.atc.value (minutes)
            val chargeTimeMins = if (vs.evStatus?.batteryCharge == true)
                vs.evStatus?.remainTime2?.atc?.value ?: 0
            else 0

            // 12V battery SoC from battery.batSoc
            val twelveSoc = vs.battery?.batSoc ?: 0

            VehicleStatusResult(
                locked         = vs.doorLock,
                range          = range,
                soc            = vs.evStatus?.batteryStatus ?: 0,
                charging       = vs.evStatus?.batteryCharge == true,
                chargeTimeMins = chargeTimeMins,
                twelveSoc      = twelveSoc
            )
        }

    suspend fun lock(): BluelinkResult = sendCommand("lock") { token ->
        val resp = api.lock(token, registrationId, vin, vin, gen(),
            LockUnlockRequest(username, vin))
        pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"],
            resp.code(), token)
    }

    suspend fun unlock(): BluelinkResult = sendCommand("unlock") { token ->
        val resp = api.unlock(token, registrationId, vin, vin, gen(),
            LockUnlockRequest(username, vin))
        pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"],
            resp.code(), token)
    }

    // climateTempF: temperature in °F selected on the watch (60–90)
    suspend fun start(climateTempF: Int = 70): BluelinkResult =
        sendCommand("start") { token ->
            val body = mapOf<String, Any>(
                "airCtrl"        to 1,
                "defrost"        to false,
                "airTemp"        to mapOf("value" to climateTempF.toString(), "unit" to 1),
                "igniOnDuration" to 10,
                "heating1"       to 0
            )
            val resp = api.climateStart(token, registrationId, vin, vin, gen(), body)
            pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"],
                resp.code(), token)
        }

    suspend fun stop(): BluelinkResult = sendCommand("stop") { token ->
        val resp = api.climateStop(token, registrationId, vin, vin, gen())
        pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"],
            resp.code(), token)
    }

    suspend fun chargeStart(): BluelinkResult = sendCommand("chargestart") { token ->
        val resp = api.chargeStart(token, registrationId, vin, vin, gen(),
            ChargeRequest(username, vin))
        pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"],
            resp.code(), token)
    }

    suspend fun chargeStop(): BluelinkResult = sendCommand("chargestop") { token ->
        val resp = api.chargeStop(token, registrationId, vin, vin, gen(),
            ChargeRequest(username, vin))
        pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"],
            resp.code(), token)
    }

    // ── Polling ───────────────────────────────────────────────────────────────
    private suspend fun pollFromResponse(
        tid: String?,
        httpCode: Int,
        token: String
    ): BluelinkResult {
        if (httpCode != 200) return BluelinkResult.Error("HTTP $httpCode")
        if (tid == null)     return BluelinkResult.Success("Sent")
        return pollForCompletion(token, tid)
    }

    private suspend fun pollForCompletion(token: String, tid: String): BluelinkResult {
        repeat(BluelinkConstants.MAX_POLL_ATTEMPTS) { attempt ->
            Log.d(TAG, "Poll attempt ${attempt + 1} tid=$tid")
            try {
                val resp = api.getRunningStatus(
                    accessToken    = token,
                    registrationId = registrationId,
                    vin            = vin,
                    appCloudVin    = vin,
                    gen            = gen(),
                    transactionId  = tid,
                    loginId        = username
                )
                when (resp.body()?.status) {
                    "SUCCESS" -> return BluelinkResult.Success("Done!")
                    "ERROR"   -> return BluelinkResult.Error("Command failed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Poll exception: ${e.message}")
            }
            delay(BluelinkConstants.POLL_INTERVAL_MS)
        }
        return BluelinkResult.Error("Timed out")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private suspend fun sendCommand(
        name: String,
        block: suspend (token: String) -> BluelinkResult
    ): BluelinkResult {
        return try {
            if (registrationId.isBlank()) {
                fetchRegistrationId(getValidToken())
            }
            block(getValidToken())
        } catch (e: Exception) {
            BluelinkResult.Error((e.message ?: "Error").take(40))
        }
    }

    private fun getTimezoneOffset(): String {
        val hours = TimeZone.getDefault().rawOffset / 3600000
        return if (hours >= 0) "+$hours" else "$hours"
    }
}