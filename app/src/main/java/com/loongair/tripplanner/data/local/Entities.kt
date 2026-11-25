package com.loongair.tripplanner.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flight_routes",
    indices = [
        Index(value = ["fromCity", "toCity"]),
        Index(value = ["fromCity"]),
        Index(value = ["toCity"])
    ]
)
data class FlightRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromCity: String,
    val toCity: String,
    val fromIata: String,
    val toIata: String,
    val flightNo: String,
    val depTime: String,      // HH:mm
    val arrTime: String,      // HH:mm or HH:mm+1
    val duration: String,
    val aircraft: String,
    val frequency: String,    // e.g. "1234567", "1357", "246"
    val stopover: String,     // "直飞" or "经停xx"
    val season: String        // "official" 表示由长龙官方接口实时同步
)

@Entity(
    tableName = "route_calendar",
    primaryKeys = ["fromCity", "toCity", "flightDate"],
    indices = [
        Index(value = ["toCity", "flightDate"]),
        Index(value = ["flightDate"])
    ]
)
data class CalendarCacheEntity(
    val fromCity: String,
    val toCity: String,
    val flightDate: String,   // YYYY-MM-DD
    val status: Int,          // 1=可兑, 0=已兑完, -1=未开放, -2=不适用, -3=已截止, -4=无航班
    val updatedAt: Long
)

@Entity(
    tableName = "seat_snapshots",
    primaryKeys = ["fromCity", "toCity", "flightDate", "flightNo", "depTime", "cabinCode"],
    indices = [
        Index(value = ["flightDate"])
    ]
)
data class SeatSnapshotEntity(
    val fromCity: String,
    val toCity: String,
    val flightDate: String,
    val flightNo: String,
    val depTime: String,
    val arrTime: String,
    val aircraft: String,
    val stopover: String,
    val secCardLeft: Int,
    val normalLeft: Int,
    val redeemPrice: Double,
    val cabinCode: String,
    val queriedAt: Long
)

/**
 * 用户可自由新增/编辑/删除的往返时间模板
 * @param outDayOfWeek 主去程星期几（1=周一 .. 7=周日；0 表示任意）
 * @param outDaysOfWeek 多选去程星期几（"ANY" 表示任意；"456" 表示周四/五/六；空串兼容旧单选字段）
 * @param retOffsetDays 返程距去程天数（如周五->周日为 2，周六->周日为 1）
 * @param weekOffset 0=最近一次满足条件的该周期，1=顺延一周（下周）
 */
@Entity(tableName = "trip_templates")
data class TripTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val outDayOfWeek: Int,
    val outDaysOfWeek: String = "",
    val outStartMinute: Int,  // e.g. 18*60 = 1080
    val outEndMinute: Int,    // e.g. 23*60+59 = 1439
    val retOffsetDays: Int,
    val retStartMinute: Int,
    val retEndMinute: Int,
    val weekOffset: Int = 0,
    val directOnly: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "trip_plans")
data class TripPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromCity: String,
    val toCity: String,
    val outboundDate: String,       // YYYY-MM-DD
    val outboundFlightNo: String,
    val outboundDepTime: String,
    val outboundArrTime: String,
    val outboundStopover: String,
    val outboundLeftCount: Int? = null,
    val returnDate: String,         // YYYY-MM-DD
    val returnFlightNo: String,
    val returnDepTime: String,
    val returnArrTime: String,
    val returnStopover: String,
    val returnLeftCount: Int? = null,
    val note: String = "",
    val lastVerifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["planId"]),
        Index(value = ["enabled", "fired", "triggerAtMillis"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
    val label: String,              // e.g. "去程开抢前5分钟" / "自定义提醒"
    val enabled: Boolean = true,
    val fired: Boolean = false
)

const val ROUTE_SOURCE_BUNDLED = "bundled"
const val ROUTE_SOURCE_OFFICIAL = "official"

/**
 * 畅飞卡可达航线与机场信息（dataSource = "bundled" 为应用内置基准，"official" 为用户本机实时校验）
 */
@Entity(
    tableName = "official_routes",
    primaryKeys = ["fromCity", "toCity"]
)
data class OfficialRouteEntity(
    val fromCity: String,
    val fromAirport: String,
    val fromIata: String,
    val toCity: String,
    val toAirport: String,
    val toIata: String,
    val updatedAt: Long,
    val dataSource: String = ROUTE_SOURCE_OFFICIAL
)

/**
 * 畅飞卡通航城市与机场目录
 */
@Entity(tableName = "official_airports")
data class OfficialAirportEntity(
    @PrimaryKey val cityName: String,
    val airportName: String,
    val airportCode: String,
    val pinyin: String = "",
    val updatedAt: Long
)

/**
 * 已检测到停航、下线或当前畅飞卡不适用的失效航线墓碑记录：
 * 保证被自动剔除的内置航线在应用重启后绝不会死灰复燃，直到长龙官方重新恢复该航线。
 */
@Entity(
    tableName = "invalidated_routes",
    primaryKeys = ["fromCity", "toCity"]
)
data class InvalidatedRouteEntity(
    val fromCity: String,
    val toCity: String,
    val reason: String,
    val invalidatedAt: Long
)
