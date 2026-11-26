package com.loongair.tripplanner.data.remote

import kotlinx.serialization.Serializable

/**
 * 仅在同步后用于地图投影与列表展示的城市元数据（从长龙官方同步的城市机场动态构建，登录前为空）
 */
data class CityMeta(
    val city: String,
    val code: String,
    val airport: String,
    val province: String = "",
    val region: String = "",
    val hub: String = "dest",
    val lng: Double = 114.0,
    val lat: Double = 34.0,
    val pinyin: String = "",
    val tags: List<String> = emptyList()
)

@Serializable
data class ChinaProvincePath(
    val name: String,
    val path: String
)

data class OfficialAirportEndpoint(
    val cityName: String,
    val airportName: String,
    val airportCode: String,
    val pinyin: String = ""
)

data class LiveFlightSeat(
    val flightNo: String,
    val depTime: String,
    val arrTime: String,
    val duration: String = "",
    val aircraft: String,
    val stopover: String,
    val redeemPrice: Double,
    val secCardLeft: Int,
    val normalLeft: Int,
    val cabinCode: String
)

data class SmsSendResult(
    val ok: Boolean,
    val verifyId: String = "",
    val message: String = ""
)

@Serializable
data class DetectedCardInfo(
    val grantTid: String,
    val cardName: String,
    val idField: String,
    val isSecCard: Boolean,
    val statusHint: String = ""
)

data class CardSyncResult(
    val ok: Boolean,
    val message: String,
    val cards: List<DetectedCardInfo> = emptyList(),
    val selectedCard: DetectedCardInfo? = null,
    val diagnostic: String = ""
)

data class LoginResult(
    val ok: Boolean,
    val message: String = "",
    val userName: String = "",
    val cardName: String = "",
    val detectedCardsCount: Int = 0
)

/**
 * 城市航线全量同步结果摘要（显式记录部分失败的航线数量，不静默吞掉日历或班次失败）
 */
data class CitySyncSummary(
    val originCity: String,
    val totalRoutes: Int,
    val failedCalendarCount: Int = 0,
    val failedScheduleCount: Int = 0,
    val failedDestinations: List<String> = emptyList()
) {
    val hasPartialFailure: Boolean
        get() = failedCalendarCount > 0 || failedScheduleCount > 0
}

data class FullNetworkSyncSummary(
    val queriedAirports: Int,
    val cachedAirports: Int,
    val queriedCalendars: Int,
    val cachedCalendars: Int,
    val totalOfficialRoutes: Int,
    val routesWithSchedules: Int,
    val failedCount: Int,
    val prunedInvalidCount: Int = 0
)

class RouteInvalidatedException(
    val fromCity: String,
    val toCity: String,
    val reason: String
) : IllegalStateException("检测到「$fromCity → $toCity」已失效（$reason），已自动移除")

@Serializable
data class BundledChangfeiNetwork(
    val version: Int = 0,
    val generatedAt: String = "",
    val cardEndDate: String = "2027-10-30",
    val unavailableDates: List<String> = emptyList(),
    val airports: List<BundledAirportItem> = emptyList(),
    val routes: List<BundledRouteItem> = emptyList(),
    val flights: List<BundledFlightItem> = emptyList()
)

@Serializable
data class BundledAirportItem(
    val cityName: String,
    val airportName: String,
    val airportCode: String,
    val pinyin: String = ""
)

@Serializable
data class BundledRouteItem(
    val fromCity: String,
    val fromAirport: String,
    val fromIata: String,
    val toCity: String,
    val toAirport: String,
    val toIata: String
)

@Serializable
data class BundledFlightItem(
    val fromCity: String,
    val toCity: String,
    val fromIata: String,
    val toIata: String,
    val flightNo: String,
    val depTime: String,
    val arrTime: String,
    val duration: String = "",
    val aircraft: String = "A320",
    val frequency: String = "",
    val stopover: String = "直飞"
)
