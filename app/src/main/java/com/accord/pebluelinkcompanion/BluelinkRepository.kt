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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.UUID
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val TAG = "BluelinkRepo"

enum class BluelinkRegion(
    val label: String,
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String,
    val brandIndicator: String = "H",
    val euAppId: String = "",
    val euAuthCfb: String = "",
    val euAuthHost: String = ""
) {
    USA(
        "USA",
        "https://api.telematics.hyundaiusa.com/",
        "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920",
        "v558o935-6nne-423i-baa8"
    ),
    CANADA(
        "Canada",
        "https://mybluelink.ca/tods/api/",
        "HATAHSPACA0232141ED9722C67715A0B",
        "CLISCR01AHSPA"
    ),
    EUROPE(
        "Europe",
        "https://prd.eu-ccapi.hyundai.com:8080",
        "6d477c38-3ca4-4cf3-9557-2a1929a94654",
        "KUy49XxPzLpLuoK0xhBC77W6VXhmtQR9iQhmIFjjoY4IpxsV",
        "H",
        "014d2225-8495-4735-812d-2616334fd15d",
        "RFtoRq/vDXJmRndoZaZQyfOot7OrIqGVFj96iY2WL3yyH5Z/pUvlUhqmCxD2t+D65SQ=",
        "idpconnect-eu.hyundai.com"
    )
}

sealed class BluelinkResult {
    data class Success(val message: String) : BluelinkResult()
    data class Error(val message: String)   : BluelinkResult()
}

data class VehicleStatusResult(
    val locked: Boolean,
    val range: Int,
    val soc: Int,
    val charging: Boolean,
    val chargeTimeMins: Int,
    val twelveSoc: Int
)

class BluelinkRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, "bluelink_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var region: String
        get()      = prefs.getString("region", BluelinkRegion.USA.name) ?: BluelinkRegion.USA.name
        set(value) = prefs.edit().putString("region", value).apply()

    val currentRegion: BluelinkRegion
        get() = try { BluelinkRegion.valueOf(region) } catch (e: Exception) { BluelinkRegion.USA }

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

    private var vehicleId: String
        get()      = prefs.getString("vehicle_id", "") ?: ""
        set(value) = prefs.edit().putString("vehicle_id", value).apply()

    private var modelYear: String
        get()      = prefs.getString("model_year", "2024") ?: "2024"
        set(value) = prefs.edit().putString("model_year", value).apply()

    private var deviceId: String
        get()      = prefs.getString("device_id", "") ?: ""
        set(value) = prefs.edit().putString("device_id", value).apply()

    private var ccs2Support: Int
        get()      = prefs.getInt("ccs2_support", 0)
        set(value) = prefs.edit().putInt("ccs2_support", value).apply()

    val isConfigured: Boolean
        get() = username.isNotBlank() && password.isNotBlank()
                && pin.isNotBlank() && vin.isNotBlank()

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

    private var cachedApi: Pair<BluelinkRegion, BluelinkApi>? = null

    private fun getApi(): BluelinkApi {
        val region = currentRegion
        AppLogger.log("Switching to API for region: ${region.label}")
        cachedApi?.let { (cachedRegion, api) ->
            if (cachedRegion == region) return api
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val r = currentRegion
                if (deviceId.isBlank()) {
                    deviceId = UUID.randomUUID().toString()
                }

                val builder = chain.request().newBuilder()
                    .header("from",               "SPA")
                    .header("to",                 "ISS")
                    .header("language",           "0")
                    .header("offset",             getTimezoneOffset().take(3))
                    .header("client_id",          r.clientId)
                    .header("clientSecret",       r.clientSecret)
                    .header("username",           username)
                    .header("blueLinkServicePin", pin)
                    .header("brandIndicator",     r.brandIndicator)
                    .header("deviceid",           deviceId)
                    .header("User-Agent",         "okhttp/3.14.9")
                    .header("Accept",             "application/json, text/plain, */*")
                    .header("Content-Type",       "application/json")
                
                if (r == BluelinkRegion.CANADA) {
                    builder.removeHeader("clientSecret")
                    builder.header("client_secret", r.clientSecret)
                    builder.removeHeader("to")
                    builder.header("User-Agent", "MyHyundai/2.0.25 (iPhone; iOS 18.3; Scale/3.00)")
                }
                
                if (r == BluelinkRegion.EUROPE) {
                    builder.removeHeader("from")
                    builder.removeHeader("to")
                    builder.header("ccsp-service-id", r.clientId)
                    builder.header("ccsp-application-id", r.euAppId)
                    builder.header("ccsp-device-id", deviceId)
                    
                    val stamp = getEuStamp(r.euAppId, r.euAuthCfb)
                    builder.header("Stamp", stamp)

                    cachedAccessToken?.let {
                        builder.header("Authorization", it)
                    }
                }
                
                val request = builder.build()
                AppLogger.log("Request: ${request.method} ${request.url}")
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val api = Retrofit.Builder()
            .baseUrl(region.baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(BluelinkApi::class.java)

        cachedApi = region to api
        return api
    }

    private fun getEuStamp(appId: String, cfb: String): String {
        val timestamp = System.currentTimeMillis().toString()
        val dataToSign = "$appId:$timestamp"
        val sha256HMAC = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(cfb.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        sha256HMAC.init(secretKey)
        val hash = sha256HMAC.doFinal(dataToSign.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun tokenValid(): Boolean {
        val expiry = tokenExpiry
        return expiry > 0 && (expiry - 30) > System.currentTimeMillis() / 1000
    }

    private suspend fun getValidToken(): String = tokenMutex.withLock {
        if (tokenValid()) return@withLock cachedAccessToken!!
        val fresh = doLogin()
        return@withLock fresh ?: throw Exception("Authentication failed")
    }

    private suspend fun getCanadaCookie(): String {
        try {
            val url = currentRegion.baseUrl.substringBefore("/tods/") + "/login"
            AppLogger.log("Fetching Canada session cookie from $url")
            val resp = getApi().getCanadaSession(url)
            val cookies = resp.headers().values("Set-Cookie")
            val bm = cookies.firstOrNull { it.contains("__cf_bm=") }
            if (bm != null) {
                val value = bm.substringBefore(";")
                AppLogger.log("Found session cookie: $value")
                return value
            }
        } catch (e: Exception) {
            AppLogger.log("Cookie fetch error: ${e.message}")
        }
        return ""
    }

    private suspend fun doLogin(): String? {
        AppLogger.log("Starting login for $username in ${currentRegion.label}")
        return try {
            if (currentRegion == BluelinkRegion.CANADA) {
                val cookie = getCanadaCookie()
                val resp = getApi().canadaLogin(cookie, CanadaLoginRequest(username, password))
                if (resp.isSuccessful && resp.body()?.result?.token != null) {
                    AppLogger.log("Canada Login successful")
                    val token = resp.body()!!.result!!.token!!
                    cachedAccessToken  = token.accessToken
                    cachedRefreshToken = token.refreshToken
                    tokenExpiry        = System.currentTimeMillis() / 1000 + (token.expireIn.toLongOrNull() ?: 3600L)
                    fetchRegistrationId(token.accessToken)
                    token.accessToken
                } else {
                    val body = resp.body()
                    val error = body?.error?.errorDesc ?: resp.errorBody()?.string() ?: "Unknown error"
                    if (error.contains("7110")) {
                        AppLogger.log("MFA Required! Please log in to the official app first to trust this device.")
                    }
                    AppLogger.log("Canada Login failed: HTTP ${resp.code()} - $error")
                    null
                }
            } else if (currentRegion == BluelinkRegion.EUROPE) {
                doEuLogin()
            } else {
                val resp = getApi().login(LoginRequest(username, password))
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    cacheTokens(body)
                    fetchRegistrationId(body.access_token)
                    body.access_token
                } else null
            }
        } catch (e: Exception) {
            AppLogger.log("Login exception: ${e.message}")
            null
        }
    }

    private suspend fun doEuLogin(): String? {
        val r = currentRegion
        val redirectUri = "${r.baseUrl}/api/v1/user/oauth2/token"
        val mobileUa = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36_CCS_APP_AOS"
        
        // 1. Authorize
        val authUrl = "https://${r.euAuthHost}/auth/api/v2/user/oauth2/authorize?" +
                "response_type=code&client_id=${r.clientId}&redirect_uri=$redirectUri&lang=en&state=ccsp&country=de"
        
        val respAuth = getApi().euAuthorize(authUrl, mobileUa)
        val cookies = respAuth.headers().values("Set-Cookie").joinToString("; ") { it.substringBefore(";") }

        // 2. Get Certs
        val respCerts = getApi().euGetCerts(mobileUa, cookies)
        val bodyStr = respCerts.body() ?: throw Exception("No body")
        @Suppress("DEPRECATION")
        val certJson = JsonParser().parse(bodyStr).asJsonObject
        val jwk = if (certJson.has("retValue")) certJson.getAsJsonObject("retValue") else certJson
        
        val n = jwk.get("n").asString
        val e = jwk.get("e").asString
        val kid = if (jwk.has("kid")) jwk.get("kid").asString else ""
        
        // 3. Signin
        val encPwd = EuCrypto.encryptPassword(password, n, e)
        val signinFields = mapOf(
            "client_id" to r.clientId,
            "encryptedPassword" to "true",
            "password" to encPwd,
            "redirect_uri" to redirectUri,
            "state" to "ccsp",
            "username" to username,
            "kid" to kid
        )
        
        val respSignin = getApi().euSignin(mobileUa, cookies, signinFields)
        if (respSignin.code() != 302) throw Exception("EU Signin failed: ${respSignin.code()}")
        
        val location = respSignin.headers()["Location"] ?: throw Exception("No redirect location")
        val code = location.substringAfter("code=").substringBefore("&")

        // 4. Token
        val tokenFields = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "client_id" to r.clientId,
            "client_secret" to r.clientSecret
        )
        
        val respToken = getApi().euGetToken(tokenFields)
        if (respToken.isSuccessful && respToken.body() != null) {
            val body = respToken.body()!!
            cachedAccessToken = "${body.token_type} ${body.access_token}"
            cachedRefreshToken = body.refresh_token
            tokenExpiry = System.currentTimeMillis() / 1000 + body.expires_in
            
            // Register Device
            if (deviceId.isBlank()) deviceId = UUID.randomUUID().toString()
            val regBody = mapOf(
                "pushRegId" to "${EuCrypto.genRanHex(22)}:${EuCrypto.genRanHex(63)}-${EuCrypto.genRanHex(55)}",
                "pushType" to "GCM",
                "uuid" to deviceId.lowercase()
            )
            val respReg = getApi().euRegisterDevice(regBody)
            if (respReg.isSuccessful) {
                deviceId = respReg.body()?.resMsg?.deviceId ?: deviceId
            }
            
            fetchRegistrationId(cachedAccessToken!!)
            return cachedAccessToken
        }
        return null
    }

    private fun cacheTokens(body: TokenResponse) {
        cachedAccessToken  = body.access_token
        cachedRefreshToken = body.refresh_token
        tokenExpiry        = System.currentTimeMillis() / 1000 + body.expires_in
    }

    private suspend fun fetchRegistrationId(token: String) {
        try {
            if (currentRegion == BluelinkRegion.CANADA) {
                val resp = getApi().canadaGetVehicles(token)
                if (resp.isSuccessful) {
                    val vehicles = resp.body()?.result?.vehicles ?: return
                    val match = vehicles.firstOrNull { it.vin.equals(vin, ignoreCase = true) } ?: vehicles.firstOrNull()
                    match?.let { v ->
                        vehicleId = v.vehicleId
                        modelYear = v.modelYear ?: "2024"
                    }
                }
            } else if (currentRegion == BluelinkRegion.EUROPE) {
                val resp = getApi().euGetVehicles()
                if (resp.isSuccessful) {
                    val vehicles = resp.body()?.resMsg?.vehicles ?: return
                    val match = vehicles.firstOrNull { it.vin.equals(vin, ignoreCase = true) } ?: vehicles.firstOrNull()
                    match?.let { v ->
                        vehicleId = v.vehicleId
                        modelYear = v.year ?: "2024"
                        ccs2Support = v.ccuCCS2ProtocolSupport ?: 0
                    }
                }
            } else {
                val resp = getApi().getVehicles(username, token)
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRegistrationId: ${e.message}")
        }
    }

    private fun gen() = if ((modelYear.toIntOrNull() ?: 2024) >= 2025) "3" else "2"

    suspend fun getStatus(forceUpdate: Boolean = false): Result<VehicleStatusResult> =
        runCatching {
            val token = getValidToken()
            if (currentRegion == BluelinkRegion.EUROPE) {
                val resp = getApi().euGetVehicleStatus(vehicleId, ccs2Support.toString())
                if (!resp.isSuccessful || resp.body()?.resMsg?.state?.Vehicle == null) {
                    throw Exception("EU Status failed: ${resp.code()}")
                }
                val vs = resp.body()!!.resMsg!!.state!!.Vehicle!!
                VehicleStatusResult(
                    locked = vs.Cabin?.Door?.Row1?.Driver?.Open == 0,
                    range = vs.Drivetrain?.FuelSystem?.DTE?.Total?.toInt() ?: 0,
                    soc = vs.Green?.BatteryManagement?.BatteryRemain?.Ratio ?: 0,
                    charging = (vs.Green?.ChargingInformation?.ConnectorFastening?.State ?: 0) > 0 && 
                               (vs.Green?.ChargingInformation?.Charging?.RemainTime ?: 0) > 0,
                    chargeTimeMins = vs.Green?.ChargingInformation?.Charging?.RemainTime ?: 0,
                    twelveSoc = vs.Electronics?.Battery?.Level ?: 0
                )
            } else if (currentRegion == BluelinkRegion.CANADA) {
                val resp = getApi().canadaGetVehicleStatus(token, vehicleId)
                if (!resp.isSuccessful || resp.body()?.result?.status == null) {
                    throw Exception("CA Status failed")
                }
                val vs = resp.body()!!.result!!.status!!
                VehicleStatusResult(
                    locked = vs.doorLock,
                    range = vs.evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.evModeRange?.value?.toInt() ?: 0,
                    soc = vs.evStatus?.batteryStatus ?: 0,
                    charging = vs.evStatus?.batteryCharge == true,
                    chargeTimeMins = vs.evStatus?.remainTime2?.atc?.value ?: 0,
                    twelveSoc = vs.battery?.batSoc ?: 0
                )
            } else {
                val resp = getApi().getVehicleStatus(token, registrationId, vin, vin, gen(), if (forceUpdate) "true" else "false")
                if (!resp.isSuccessful || resp.body()?.vehicleStatus == null) throw Exception("Status failed")
                val vs = resp.body()!!.vehicleStatus!!
                VehicleStatusResult(
                    locked = vs.doorLock,
                    range = vs.evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.evModeRange?.value?.toInt() ?: 0,
                    soc = vs.evStatus?.batteryStatus ?: 0,
                    charging = vs.evStatus?.batteryCharge == true,
                    chargeTimeMins = vs.evStatus?.remainTime2?.atc?.value ?: 0,
                    twelveSoc = vs.battery?.batSoc ?: 0
                )
            }
        }

    private suspend fun getEuControlToken(token: String): String {
        val body = EuPinRequest(pin, deviceId)
        val resp = getApi().euVerifyPin(vehicleId, ccs2Support.toString(), body)
        if (resp.isSuccessful && resp.body()?.controlToken != null) {
            return "Bearer ${resp.body()!!.controlToken}"
        }
        throw Exception("EU PIN verification failed")
    }

    suspend fun lock(): BluelinkResult = sendCommand("lock") { token ->
        if (currentRegion == BluelinkRegion.EUROPE) {
            val ct = getEuControlToken(token)
            val resp = getApi().euLockUnlock(vehicleId, ct, ccs2Support.toString(), mapOf("command" to "close"))
            pollFromResponse(resp.body()?.msgId, resp.code(), token)
        } else if (currentRegion == BluelinkRegion.CANADA) {
            val pAuth = getCanadaPauth(token)
            val resp = getApi().canadaLock(token, vehicleId, pAuth, CanadaPinRequest(pin))
            pollFromResponse(resp.headers()["transactionid"], resp.code(), token, pAuth)
        } else {
            val resp = getApi().lock(token, registrationId, vin, vin, gen(), LockUnlockRequest(username, vin))
            pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"], resp.code(), token)
        }
    }

    suspend fun unlock(): BluelinkResult = sendCommand("unlock") { token ->
        if (currentRegion == BluelinkRegion.EUROPE) {
            val ct = getEuControlToken(token)
            val resp = getApi().euLockUnlock(vehicleId, ct, ccs2Support.toString(), mapOf("command" to "open"))
            pollFromResponse(resp.body()?.msgId, resp.code(), token)
        } else if (currentRegion == BluelinkRegion.CANADA) {
            val pAuth = getCanadaPauth(token)
            val resp = getApi().canadaUnlock(token, vehicleId, pAuth, CanadaPinRequest(pin))
            pollFromResponse(resp.headers()["transactionid"], resp.code(), token, pAuth)
        } else {
            val resp = getApi().unlock(token, registrationId, vin, vin, gen(), LockUnlockRequest(username, vin))
            pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"], resp.code(), token)
        }
    }

    suspend fun start(climateTempF: Int = 70): BluelinkResult = sendCommand("start") { token ->
        if (currentRegion == BluelinkRegion.EUROPE) {
            val ct = getEuControlToken(token)
            val body = mapOf(
                "command" to "start",
                "hvacTemp" to climateTempF,
                "tempUnit" to "F",
                "hvacTempType" to 1
            )
            val resp = getApi().euClimate(vehicleId, ct, ccs2Support.toString(), body)
            pollFromResponse(resp.body()?.msgId, resp.code(), token)
        } else if (currentRegion == BluelinkRegion.CANADA) {
            val pAuth = getCanadaPauth(token)
            val body = mapOf("pin" to pin, "hvacInfo" to mapOf("airCtrl" to 1, "defrost" to false, "airTemp" to mapOf("value" to getCanadaTempCode(climateTempF), "unit" to 0, "hvacTempType" to 1), "igniOnDuration" to 10, "heating1" to 0))
            val resp = getApi().canadaClimateStart(token, vehicleId, pAuth, body)
            pollFromResponse(resp.headers()["transactionid"], resp.code(), token, pAuth)
        } else {
            val body = mapOf("airCtrl" to 1, "defrost" to false, "airTemp" to mapOf("value" to climateTempF.toString(), "unit" to 1), "igniOnDuration" to 10, "heating1"       to 0)
            val resp = getApi().climateStart(token, registrationId, vin, vin, gen(), body)
            pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"], resp.code(), token)
        }
    }

    suspend fun stop(): BluelinkResult = sendCommand("stop") { token ->
        if (currentRegion == BluelinkRegion.EUROPE) {
            val ct = getEuControlToken(token)
            val resp = getApi().euClimate(vehicleId, ct, ccs2Support.toString(), mapOf("command" to "stop"))
            pollFromResponse(resp.body()?.msgId, resp.code(), token)
        } else if (currentRegion == BluelinkRegion.CANADA) {
            val pAuth = getCanadaPauth(token)
            val resp = getApi().canadaClimateStop(token, vehicleId, pAuth, CanadaPinRequest(pin))
            pollFromResponse(resp.headers()["transactionid"], resp.code(), token, pAuth)
        } else {
            val resp = getApi().climateStop(token, registrationId, vin, vin, gen())
            pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"], resp.code(), token)
        }
    }

    suspend fun chargeStart(): BluelinkResult = sendCommand("chargestart") { token ->
        if (currentRegion == BluelinkRegion.EUROPE) {
            val ct = getEuControlToken(token)
            val resp = getApi().euCharge(vehicleId, ct, ccs2Support.toString(), mapOf("command" to "start"))
            pollFromResponse(resp.body()?.msgId, resp.code(), token)
        } else if (currentRegion == BluelinkRegion.CANADA) {
            val pAuth = getCanadaPauth(token)
            val resp = getApi().canadaChargeStart(token, vehicleId, pAuth, CanadaPinRequest(pin))
            pollFromResponse(resp.headers()["transactionid"], resp.code(), token, pAuth)
        } else {
            val resp = getApi().chargeStart(token, registrationId, vin, vin, gen(), ChargeRequest(username, vin))
            pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"], resp.code(), token)
        }
    }

    suspend fun chargeStop(): BluelinkResult = sendCommand("chargestop") { token ->
        if (currentRegion == BluelinkRegion.EUROPE) {
            val ct = getEuControlToken(token)
            val resp = getApi().euCharge(vehicleId, ct, ccs2Support.toString(), mapOf("command" to "stop"))
            pollFromResponse(resp.body()?.msgId, resp.code(), token)
        } else if (currentRegion == BluelinkRegion.CANADA) {
            val pAuth = getCanadaPauth(token)
            val resp = getApi().canadaChargeStop(token, vehicleId, pAuth, CanadaPinRequest(pin))
            pollFromResponse(resp.headers()["transactionid"], resp.code(), token, pAuth)
        } else {
            val resp = getApi().chargeStop(token, registrationId, vin, vin, gen(), ChargeRequest(username, vin))
            pollFromResponse(resp.headers()["tmsTid"] ?: resp.headers()["tmstid"], resp.code(), token)
        }
    }

    private suspend fun pollFromResponse(tid: String?, httpCode: Int, token: String, pAuth: String? = null): BluelinkResult {
        if (httpCode !in listOf(200, 202, 204)) return BluelinkResult.Error("HTTP $httpCode")
        if (tid == null) return BluelinkResult.Success("Sent")
        return pollForCompletion(token, tid, pAuth)
    }

    private suspend fun pollForCompletion(token: String, tid: String, pAuth: String? = null): BluelinkResult {
        repeat(BluelinkConstants.MAX_POLL_ATTEMPTS) { attempt ->
            try {
                if (currentRegion == BluelinkRegion.EUROPE) {
                    val resp = getApi().euGetRecords(vehicleId, ccs2Support.toString())
                    val records = resp.body()?.resMsg ?: emptyList()
                    val record = records.firstOrNull { it.recordId == tid }
                    when (record?.result) {
                        "success" -> return BluelinkResult.Success("Done!")
                        "fail" -> return BluelinkResult.Error("Command failed")
                    }
                } else if (currentRegion == BluelinkRegion.CANADA) {
                    val resp = getApi().canadaGetRunningStatus(token, vehicleId, pAuth ?: "", tid)
                    val res = resp.body()?.result
                    if (res?.transaction?.apiResult == "C") return BluelinkResult.Success("Done!")
                    if (res?.transaction?.apiResult == "F") return BluelinkResult.Error("Failed")
                } else {
                    val resp = getApi().getRunningStatus(token, registrationId, vin, vin, gen(), tid, username)
                    when (resp.body()?.status) {
                        "SUCCESS" -> return BluelinkResult.Success("Done!")
                        "ERROR" -> return BluelinkResult.Error("Failed")
                    }
                }
            } catch (e: Exception) {}
            delay(BluelinkConstants.POLL_INTERVAL_MS)
        }
        return BluelinkResult.Error("Timed out")
    }

    private suspend fun sendCommand(name: String, block: suspend (token: String) -> BluelinkResult): BluelinkResult {
        return try {
            val token = getValidToken()
            if ((currentRegion == BluelinkRegion.CANADA || currentRegion == BluelinkRegion.EUROPE) && vehicleId.isBlank()) {
                fetchRegistrationId(token)
            } else if (currentRegion == BluelinkRegion.USA && registrationId.isBlank()) {
                fetchRegistrationId(token)
            }
            block(token)
        } catch (e: Exception) {
            BluelinkResult.Error((e.message ?: "Error").take(40))
        }
    }

    private suspend fun getCanadaPauth(token: String): String {
        val resp = getApi().canadaVerifyPin(token, CanadaPinRequest(pin))
        if (resp.isSuccessful && resp.body()?.result?.pAuth != null) return resp.body()!!.result!!.pAuth!!
        throw Exception("PIN failed")
    }

    private fun getCanadaTempCode(tempF: Int): String {
        val fValues = listOf(62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82)
        val hValues = listOf("06H", "07H", "08H", "09H", "0AH", "0BH", "0CH", "0DH", "0EH", "0FH", "10H", "11H", "12H", "13H", "14H", "15H", "16H", "17H", "18H", "19H", "1AH")
        val index = fValues.indexOf(tempF).coerceIn(0, hValues.size - 1)
        return hValues[index]
    }

    private fun getTimezoneOffset(): String {
        val hours = TimeZone.getDefault().rawOffset / 3600000
        return if (hours >= 0) "+$hours" else "$hours"
    }
}
