// Backend for the Hyundai Bluelink US API.
// Endpoints and headers sourced directly from:
//   egmp-bluelink-scriptable/src/lib/bluelink-regions/usa.ts

package com.accord.pebluelinkcompanion

import retrofit2.Response
import retrofit2.http.*

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val username: String,
    val password: String
)

data class TokenRefreshRequest(
    val refresh_token: String
)

data class TokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val expires_in: Int
)

// ── Vehicle list ──────────────────────────────────────────────────────────────

data class EnrollmentResponse(
    val enrolledVehicleDetails: List<EnrolledVehicle>
)

data class EnrolledVehicle(
    val vehicleDetails: VehicleDetails
)

data class VehicleDetails(
    val vin: String,
    val regid: String,
    val nickName: String?,
    val modelCode: String?,
    val modelYear: String?,
    val odometer: Float?
)

// ── Vehicle status ────────────────────────────────────────────────────────────

data class VehicleStatusResponse(
    val vehicleStatus: VehicleStatus?
)

data class VehicleStatus(
    val doorLock: Boolean,
    val airCtrlOn: Boolean,
    val dateTime: String?,
    val battery: TwelveVoltBattery?,
    val odometer: Float?,
    val evStatus: EvStatus?
)

data class TwelveVoltBattery(
    val batSoc: Int?
)

data class EvStatus(
    val batteryStatus: Int,
    val batteryCharge: Boolean,
    val batteryPlugin: Int,
    val batteryFstChrgPower: Float?,
    val batteryStndChrgPower: Float?,
    val remainTime2: RemainTime2?,
    val drvDistance: List<DrvDistance>?,
    val reservChargeInfos: ReserveChargeInfos?
)

data class RemainTime2(val atc: TimeValue?)
data class TimeValue(val value: Int)
data class DrvDistance(val rangeByFuel: RangeByFuel?)
data class RangeByFuel(val evModeRange: RangeValue?)
data class RangeValue(val value: Float)
data class ReserveChargeInfos(val targetSOClist: List<TargetSoc>?)
data class TargetSoc(val plugType: Int, val targetSOClevel: Int)

data class RunningStatusResponse(
    val status: String
)

// ── Command request bodies ────────────────────────────────────────────────────

data class LockUnlockRequest(
    val userName: String,
    val vin: String
)

data class ChargeRequest(
    val userName: String,
    val vin: String
)

interface BluelinkApi {

    @POST("v2/ac/oauth/token")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("v2/ac/oauth/token/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<TokenResponse>

    @GET("ac/v2/enrollment/details/{username}")
    suspend fun getVehicles(
        @Path("username") username: String,
        @Header("accessToken") accessToken: String
    ): Response<EnrollmentResponse>

    @GET("ac/v2/rcs/rvs/vehicleStatus")
    suspend fun getVehicleStatus(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Header("refresh") refresh: String
    ): Response<VehicleStatusResponse>

    @POST("ac/v2/rcs/rdo/off")
    suspend fun lock(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Body request: LockUnlockRequest
    ): Response<Void>

    @POST("ac/v2/rcs/rdo/on")
    suspend fun unlock(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Body request: LockUnlockRequest
    ): Response<Void>

    @POST("ac/v2/evc/fatc/start")
    suspend fun climateStart(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<Void>

    @POST("ac/v2/evc/fatc/stop")
    suspend fun climateStop(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String
    ): Response<Void>

    // Charge start — mirrors ac/v2/evc/charge/start from usa.ts
    @POST("ac/v2/evc/charge/start")
    suspend fun chargeStart(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Body request: ChargeRequest
    ): Response<Void>

    // Charge stop — mirrors ac/v2/evc/charge/stop from usa.ts
    @POST("ac/v2/evc/charge/stop")
    suspend fun chargeStop(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Body request: ChargeRequest
    ): Response<Void>

    @GET("ac/v2/rmt/getRunningStatus")
    suspend fun getRunningStatus(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Header("tid") transactionId: String,
        @Header("login_id") loginId: String,
        @Header("service_type") serviceType: String = "REMOTE_POLL"
    ): Response<RunningStatusResponse>
}

object BluelinkConstants {
    const val BASE_URL      = "https://api.telematics.hyundaiusa.com/"
    const val CLIENT_ID     = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
    const val CLIENT_SECRET = "v558o935-6nne-423i-baa8"
    const val MAX_POLL_ATTEMPTS = 20
    const val POLL_INTERVAL_MS  = 3000L
}
