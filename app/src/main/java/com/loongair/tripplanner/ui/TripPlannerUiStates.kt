package com.loongair.tripplanner.ui

import com.loongair.tripplanner.data.local.InvalidatedRouteEntity
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.remote.ChinaProvincePath
import com.loongair.tripplanner.data.remote.CityMeta
import com.loongair.tripplanner.data.repository.NetworkRouteSummary
import com.loongair.tripplanner.data.repository.RoundTripCandidate
import com.loongair.tripplanner.util.ChinaClock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class AppReadiness {
    NEEDS_LOGIN,
    NEEDS_FIRST_CITY_SYNC,
    READY
}

data class NetworkUiState(
    val allRoutes: List<NetworkRouteSummary> = emptyList(),
    val filteredRoutes: List<NetworkRouteSummary> = emptyList(),
    val fromCityFilter: String = "",
    val toCityFilter: String = "",
    val onlyOfficialSynced: Boolean = false,
    val homeCity: String = "",
    val provinces: List<ChinaProvincePath> = emptyList(),
    val cityMetaList: List<CityMeta> = emptyList(),
    val allOriginCities: List<String> = emptyList(),
    val allDestCities: List<String> = emptyList(),
    val refreshingRouteKey: String? = null,
    val officialSyncing: Boolean = false,
    val syncingCityName: String = "",
    val syncDoneCount: Int = 0,
    val syncTotalCount: Int = 0,
    val officialSyncProgress: String = "",
    val officialSyncedCount: Int = 0,
    val bundledRouteCount: Int = 0,
    val invalidatedRouteCount: Int = 0,
    val invalidatedRoutes: List<InvalidatedRouteEntity> = emptyList(),
    val globalUnavailableDates: Set<String> = emptySet(),
    val lastOfficialSyncAt: Long? = null,
    val isLoading: Boolean = true
) {
    val hasSyncedData: Boolean
        get() = allRoutes.isNotEmpty() || officialSyncedCount > 0

    /**
     * 在状态更新时一次性预计算 filteredRoutes（优先展示常驻城市出发/到达的航线），
     * 避免在 Compose 树每次重组读取时重复遍历过滤列表。
     */
    fun withRecomputedFilter(): NetworkUiState {
        val filtered = if (fromCityFilter.isBlank() && toCityFilter.isBlank() && !onlyOfficialSynced) {
            allRoutes
        } else {
            allRoutes.filter { r ->
                (fromCityFilter.isBlank() || r.fromCity == fromCityFilter) &&
                    (toCityFilter.isBlank() || r.toCity == toCityFilter) &&
                    (!onlyOfficialSynced || r.isOfficialSynced)
            }
        }
        val sorted = filtered.sortedWith(
            compareBy<NetworkRouteSummary> { r ->
                when {
                    homeCity.isNotBlank() && r.fromCity == homeCity -> 0
                    homeCity.isNotBlank() && r.toCity == homeCity -> 1
                    else -> 2
                }
            }
                .thenByDescending { it.availableDaysCount }
                .thenByDescending { it.flights.size }
                .thenBy { it.fromCity }
                .thenBy { it.toCity }
        )
        return copy(filteredRoutes = sorted)
    }
}

/**
 * 按通航目的地数量（及有效可兑天数、班次密度）从高到低排序出发枢纽城市。
 */
fun rankOriginCitiesByConnectivity(routes: List<NetworkRouteSummary>): List<String> {
    if (routes.isEmpty()) return emptyList()
    val byOrigin = routes.groupBy { it.fromCity }
    return byOrigin.keys.sortedWith(
        compareByDescending<String> { city ->
            byOrigin[city].orEmpty().mapTo(HashSet()) { it.toCity }.size
        }
            .thenByDescending { city -> byOrigin[city].orEmpty().sumOf { it.availableDaysCount } }
            .thenByDescending { city -> byOrigin[city].orEmpty().sumOf { it.flights.size } }
            .thenBy { it }
    )
}

data class RouteCalendarDetailUiState(
    val route: NetworkRouteSummary? = null,
    val calendarByDate: Map<String, Int> = emptyMap(),
    val globalUnavailableDates: Set<String> = emptySet(),
    val seatsByDate: Map<String, List<SeatSnapshotEntity>> = emptyMap(),
    val selectedDate: String? = null,
    val isRefreshingCalendar: Boolean = false,
    val isCalibratingSchedule: Boolean = false,
    val queryingSeatsDate: String? = null
)

data class SearchUiState(
    val originCity: String = "",
    val destinationFilter: String = "",
    val outboundDate: LocalDate = defaultNextFriday(),
    val outStartMinute: Int = 18 * 60,
    val outEndMinute: Int = 23 * 60 + 59,
    val returnDate: LocalDate = defaultNextFriday().plusDays(2),
    val retStartMinute: Int = 12 * 60,
    val retEndMinute: Int = 23 * 60 + 59,
    val multiDatePairs: List<Pair<LocalDate, LocalDate>> = emptyList(),
    val multiDateLabel: String = "",
    val directOnly: Boolean = false,
    val onlyCalendarAvailable: Boolean = false,
    val activeTemplateId: Long? = null,
    val allOriginCities: List<String> = emptyList(),
    val reachableDestinations: List<String> = emptyList(),
    val cityMetaList: List<CityMeta> = emptyList(),
    val results: List<RoundTripCandidate> = emptyList(),
    val isLoading: Boolean = true,
    val verifyingDestination: String? = null,
    val batchScanning: Boolean = false,
    val batchProgressText: String = "",
    val officialAllowedDestinations: List<String>? = null
)

internal fun defaultNextFriday(): LocalDate {
    val start = ChinaClock.today().plusDays(1)
    return start.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
}
