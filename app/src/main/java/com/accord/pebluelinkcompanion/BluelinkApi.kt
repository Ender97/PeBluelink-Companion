package com.accord.pebluelinkcompanion

import retrofit2.Response
import retrofit2.http.*

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val username: String,
    val password: String
)

data class CanadaLoginRequest(
    val loginId: String,
    val password: String
)

data class TokenRefreshRequest(
    val refresh_token: String
)

data class TokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val expires_in: Int,
    val token_type: String?
)

data class CanadaTokenResponse(
    val result: CanadaLoginResult?,
    val responseHeader: CanadaResponseHeader?,
    val error: CanadaError?
)

data class CanadaResponseHeader(
    val responseCode: Int,
    val resMsg: String?
)

data class CanadaError(
    val errorCode: String?,
    val errorDesc: String?
)

data class CanadaLoginResult(
    val token: CanadaToken?
)

data class CanadaToken(
    val accessToken: String,
    val refreshToken: String,
    val expireIn: String
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

data class CanadaVehicleListResponse(
    val result: CanadaVehicleListResult?,
    val responseHeader: CanadaResponseHeader?,
    val error: CanadaError?
)

data class CanadaVehicleListResult(
    val vehicles: List<CanadaVehicle>?
)

data class CanadaVehicle(
    val vin: String,
    val vehicleId: String,
    val nickName: String?,
    val modelName: String?,
    val modelYear: String?
)

data class EuVehicleListResponse(
    val resMsg: EuVehicleListResult?
)

data class EuVehicleListResult(
    val vehicles: List<EuVehicle>?
)

data class EuVehicle(
    val vin: String,
    val vehicleId: String,
    val nickname: String?,
    val vehicleName: String?,
    val year: String?,
    val ccuCCS2ProtocolSupport: Int?,
    val detailInfo: EuVehicleDetail?
)

data class EuVehicleDetail(
    val outColor: String?,
    val saleCarmdlCd: String?
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

data class CanadaVehicleStatusResponse(
    val result: CanadaVehicleStatusResult?,
    val responseHeader: CanadaResponseHeader?,
    val error: CanadaError?
)

data class CanadaVehicleStatusResult(
    val status: CanadaStatus?,
    val transaction: CanadaTransaction?
)

data class CanadaTransaction(
    val apiResult: String?
)

data class CanadaStatus(
    val doorLock: Boolean,
    val airCtrlOn: Boolean,
    val battery: TwelveVoltBattery?,
    val evStatus: EvStatus?
)

data class EuVehicleStatusResponse(
    val resMsg: EuVehicleStatusResult?
)

data class EuVehicleStatusResult(
    val state: EuVehicleState?,
    val lastUpdateTime: Long?
)

data class EuVehicleState(
    val Vehicle: EuVehicleData?
)

data class EuVehicleData(
    val Cabin: EuCabin?,
    val Drivetrain: EuDrivetrain?,
    val Green: EuGreen?,
    val Electronics: EuElectronics?,
    val Location: EuLocation?
)

data class EuCabin(
    val Door: EuDoor?,
    val HVAC: EuHvac?
)

data class EuDoor(
    val Row1: EuDoorRow?,
    val Row2: EuDoorRow?
)

data class EuDoorRow(
    val Driver: EuDoorState?,
    val Passenger: EuDoorState?
)

data class EuDoorState(
    val Open: Int?
)

data class EuHvac(
    val Row1: EuHvacRow?
)

data class EuHvacRow(
    val Driver: EuHvacDriver?
)

data class EuHvacDriver(
    val Blower: EuBlower?
)

data class EuBlower(
    val SpeedLevel: Int?
)

data class EuDrivetrain(
    val Odometer: Float?,
    val FuelSystem: EuFuelSystem?
)

data class EuFuelSystem(
    val DTE: EuDte?
)

data class EuDte(
    val Total: Float?
)

data class EuGreen(
    val ChargingInformation: EuChargingInfo?,
    val BatteryManagement: EuBatteryManagement?,
    val Electric: EuElectric?
)

data class EuChargingInfo(
    val ConnectorFastening: EuConnectorState?,
    val Charging: EuCharging?,
    val TargetSoC: EuTargetSoc?
)

data class EuConnectorState(
    val State: Int?
)

data class EuCharging(
    val RemainTime: Int?
)

data class EuTargetSoc(
    val Standard: Int?,
    val Quick: Int?
)

data class EuBatteryManagement(
    val BatteryRemain: EuBatteryRemain?
)

data class EuBatteryRemain(
    val Ratio: Int?
)

data class EuElectric(
    val SmartGrid: EuSmartGrid?
)

data class EuSmartGrid(
    val RealTimePower: Float?
)

data class EuElectronics(
    val Battery: Eu12vBattery?
)

data class Eu12vBattery(
    val Level: Int?
)

data class EuLocation(
    val GeoCoord: EuGeoCoord?
)

data class EuGeoCoord(
    val Latitude: Double?,
    val Longitude: Long?
)

data class RunningStatusResponse(
    val status: String
)

data class EuCommandResponse(
    val msgId: String?
)

data class EuRecordsResponse(
    val resMsg: List<EuRecord>?
)

data class EuRecord(
    val recordId: String?,
    val result: String?
)

// ── Command request bodies ────────────────────────────────────────────────────

data class LockUnlockRequest(
    val userName: String,
    val vin: String
)

data class CanadaPinRequest(
    val pin: String
)

data class CanadaPinResponse(
    val result: CanadaPinResult?,
    val responseHeader: CanadaResponseHeader?,
    val error: CanadaError?
)

data class CanadaPinResult(
    val pAuth: String?
)

data class ChargeRequest(
    val userName: String,
    val vin: String
)

data class EuPinRequest(
    val pin: String,
    val deviceId: String
)

data class EuPinResponse(
    val expiresTime: String?,
    val controlToken: String?
)

interface BluelinkApi {

    // ── USA Endpoints ─────────────────────────────────────────────────────────

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

    @POST("ac/v2/evc/charge/start")
    suspend fun chargeStart(
        @Header("accessToken") accessToken: String,
        @Header("registrationId") registrationId: String,
        @Header("VIN") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("gen") gen: String,
        @Body request: ChargeRequest
    ): Response<Void>

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

    // ── Canada Endpoints ──────────────────────────────────────────────────────

    @GET
    suspend fun getCanadaSession(@Url url: String): Response<Void>

    @POST("v2/login")
    suspend fun canadaLogin(
        @Header("Cookie") cookie: String,
        @Body request: CanadaLoginRequest
    ): Response<CanadaTokenResponse>

    @POST("vhcllst")
    suspend fun canadaGetVehicles(
        @Header("Accesstoken") accessToken: String
    ): Response<CanadaVehicleListResponse>

    @POST("rltmvhclsts")
    suspend fun canadaGetVehicleStatus(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String
    ): Response<CanadaVehicleStatusResponse>

    @POST("vrfypin")
    suspend fun canadaVerifyPin(
        @Header("Accesstoken") accessToken: String,
        @Body request: CanadaPinRequest
    ): Response<CanadaPinResponse>

    @POST("drlck")
    suspend fun canadaLock(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String,
        @Header("Pauth") pAuth: String,
        @Body request: CanadaPinRequest
    ): Response<Void>

    @POST("drulck")
    suspend fun canadaUnlock(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String,
        @Header("Pauth") pAuth: String,
        @Body request: CanadaPinRequest
    ): Response<Void>

    @POST("evc/rfon")
    suspend fun canadaClimateStart(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String,
        @Header("Pauth") pAuth: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<Void>

    @POST("evc/rfoff")
    suspend fun canadaClimateStop(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String,
        @Header("Pauth") pAuth: String,
        @Body request: CanadaPinRequest
    ): Response<Void>

    @POST("evc/rcstrt")
    suspend fun canadaChargeStart(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String,
        @Header("Pauth") pAuth: String,
        @Body request: CanadaPinRequest
    ): Response<Void>

    @POST("evc/rcstp")
    suspend fun canadaChargeStop(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String,
        @Header("Pauth") pAuth: String,
        @Body request: CanadaPinRequest
    ): Response<Void>

    @POST("rmtsts")
    suspend fun canadaGetRunningStatus(
        @Header("Accesstoken") accessToken: String,
        @Header("Vehicleid") vehicleId: String,
        @Header("Pauth") pAuth: String,
        @Header("TransactionId") transactionId: String
    ): Response<CanadaVehicleStatusResponse>

    // ── Europe Endpoints ──────────────────────────────────────────────────────

    @GET
    suspend fun euAuthorize(
        @Url url: String,
        @Header("User-Agent") ua: String
    ): Response<String>

    @GET("auth/api/v1/accounts/certs")
    suspend fun euGetCerts(
        @Header("User-Agent") ua: String,
        @Header("Cookie") cookie: String?
    ): Response<String>

    @FormUrlEncoded
    @POST("auth/account/signin")
    suspend fun euSignin(
        @Header("User-Agent") ua: String,
        @Header("Cookie") cookie: String?,
        @FieldMap fields: Map<String, String>
    ): Response<Void>

    @FormUrlEncoded
    @POST("auth/api/v2/user/oauth2/token")
    suspend fun euGetToken(
        @FieldMap fields: Map<String, String>
    ): Response<TokenResponse>

    @POST("api/v1/spa/notifications/register")
    suspend fun euRegisterDevice(
        @Body body: Map<String, String>
    ): Response<EuRegisterResponse>

    @GET("api/v1/spa/vehicles")
    suspend fun euGetVehicles(): Response<EuVehicleListResponse>

    @GET("api/v1/spa/vehicles/{id}/ccs2/carstatus/latest")
    suspend fun euGetVehicleStatus(
        @Path("id") id: String,
        @Header("ccuCCS2ProtocolSupport") ccs2: String
    ): Response<EuVehicleStatusResponse>

    @PUT("api/v1/user/pin")
    suspend fun euVerifyPin(
        @Header("vehicleId") vehicleId: String,
        @Header("ccuCCS2ProtocolSupport") ccs2: String,
        @Body body: EuPinRequest
    ): Response<EuPinResponse>

    @POST("api/v2/spa/vehicles/{id}/ccs2/control/door")
    suspend fun euLockUnlock(
        @Path("id") id: String,
        @Header("Authorization") controlToken: String,
        @Header("ccuCCS2ProtocolSupport") ccs2: String,
        @Body body: Map<String, String>
    ): Response<EuCommandResponse>

    @POST("api/v2/spa/vehicles/{id}/ccs2/control/temperature")
    suspend fun euClimate(
        @Path("id") id: String,
        @Header("Authorization") controlToken: String,
        @Header("ccuCCS2ProtocolSupport") ccs2: String,
        @Body body: Map<String, Any>
    ): Response<EuCommandResponse>

    @POST("api/v2/spa/vehicles/{id}/ccs2/control/charge")
    suspend fun euCharge(
        @Path("id") id: String,
        @Header("Authorization") controlToken: String,
        @Header("ccuCCS2ProtocolSupport") ccs2: String,
        @Body body: Map<String, String>
    ): Response<EuCommandResponse>

    @GET("api/v1/spa/notifications/{id}/records")
    suspend fun euGetRecords(
        @Path("id") id: String,
        @Header("ccuCCS2ProtocolSupport") ccs2: String
    ): Response<EuRecordsResponse>
}

data class EuRegisterResponse(
    val resMsg: EuRegisterResult?
)

data class EuRegisterResult(
    val deviceId: String?
)

object BluelinkConstants {
    const val BASE_URL      = "https://api.telematics.hyundaiusa.com/"
    const val CLIENT_ID     = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
    const val CLIENT_SECRET = "v558o935-6nne-423i-baa8"
    const val MAX_POLL_ATTEMPTS = 20
    const val POLL_INTERVAL_MS  = 3000L
}
