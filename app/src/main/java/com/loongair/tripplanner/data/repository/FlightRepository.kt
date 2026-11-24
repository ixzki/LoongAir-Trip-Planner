package com.loongair.tripplanner.data.repository

import android.content.Context
import com.loongair.tripplanner.data.local.AppDatabase
import com.loongair.tripplanner.data.local.AuthPrefs
import com.loongair.tripplanner.data.local.CalendarCacheEntity
import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.OfficialAirportEntity
import com.loongair.tripplanner.data.local.OfficialRouteEntity
import com.loongair.tripplanner.data.local.ReminderEntity
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.local.TripTemplateEntity
import com.loongair.tripplanner.data.remote.ApiRateLimitException
import com.loongair.tripplanner.data.remote.ChinaProvincePath
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.CityMeta
import com.loongair.tripplanner.data.remote.CitySyncSummary
import com.loongair.tripplanner.data.remote.LiveFlightSeat
import com.loongair.tripplanner.data.remote.LoongAirApi
import com.loongair.tripplanner.data.remote.SessionExpiredException
import com.loongair.tripplanner.util.ChinaClock
import com.loongair.tripplanner.util.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.random.Random

data class NetworkRouteSummary(
    val fromCity: String,
    val fromAirport: String,
    val fromIata: String,
    val toCity: String,
    val toAirport: String,
    val toIata: String,
    val flights: List<FlightRouteEntity>,
    val isOfficialSynced: Boolean,
    val availableDaysCount: Int,
    val nearestAvailableDate: String?,
    val officialUpdatedAt: Long?
)

class FlightRepository(
    private val context: Context,
    private val db: AppDatabase,
    val authPrefs: AuthPrefs,
    val api: LoongAirApi
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cachedProvinces: List<ChinaProvincePath>? = null

    /**
     * 仅初始化默认往返时间模板与清理过期缓存；绝不预置任何城市或航线数据。
     */
    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        db.flightRouteDao().deleteLegacySeeded()
        if (db.tripTemplateDao().count() == 0) {
            db.tripTemplateDao().insertAll(RoundTripMatcher.defaultTemplates())
        }
        val todayStr = ChinaClock.today().toString()
        db.calendarCacheDao().deleteExpired(todayStr)
        db.seatSnapshotDao().deleteExpired(todayStr)

        // 若曾经同步过机场，向动态三字码与拼音表注册以支持后续搜索编解码
        db.officialAirportDao().getAll().forEach { ap ->
            CityIataMapper.registerDynamic(ap.cityName, ap.airportCode, ap.pinyin)
        }

        // 自动修复历史同步缓存中未解码的长龙混淆航班号（如 "168&248&..." -> "GJ8859"）
        repairObfuscatedFlightNumbers()
        // 将已缓存的双机场城市（如成都天府/成都双流、北京首都/大兴、上海浦东/虹桥、遵义新舟/茅台）分开标注
        upgradeDualAirportNames()
    }

    private suspend fun upgradeDualAirportNames() {
        val airports = db.officialAirportDao().getAll()
        var airportChanged = false
        val renamedCities = mutableMapOf<String, String>()
        val normalizedAirports = airports.map { ap ->
            val better = CityIataMapper.dualAirportLabelForCode(ap.airportCode)
            if (better != null && better != ap.cityName) {
                airportChanged = true
                renamedCities[ap.cityName] = better
                ap.copy(cityName = better)
            } else ap
        }
        if (airportChanged) {
            db.officialAirportDao().clearAll()
            db.officialAirportDao().upsertAll(normalizedAirports)
        }

        val officialRoutes = db.officialRouteDao().getAll()
        val fixedOfficial = officialRoutes.any { r ->
            val newFrom = CityIataMapper.dualAirportLabelForCode(r.fromIata) ?: r.fromCity
            val newTo = CityIataMapper.dualAirportLabelForCode(r.toIata) ?: r.toCity
            newFrom != r.fromCity || newTo != r.toCity
        }
        if (fixedOfficial) {
            db.officialRouteDao().clearAll()
            val merged = officialRoutes.map { r ->
                val newFrom = CityIataMapper.dualAirportLabelForCode(r.fromIata) ?: r.fromCity
                val newTo = CityIataMapper.dualAirportLabelForCode(r.toIata) ?: r.toCity
                r.copy(fromCity = newFrom, toCity = newTo)
            }
            db.officialRouteDao().upsertAll(merged)
        }

        val routes = db.flightRouteDao().getAll()
        val fixedFlights = routes.mapNotNull { r ->
            val newFrom = CityIataMapper.dualAirportLabelForCode(r.fromIata) ?: r.fromCity
            val newTo = CityIataMapper.dualAirportLabelForCode(r.toIata) ?: r.toCity
            if (newFrom == r.fromCity && newTo == r.toCity) null
            else r.copy(fromCity = newFrom, toCity = newTo)
        }
        if (fixedFlights.isNotEmpty()) {
            db.flightRouteDao().insertAll(fixedFlights)
        }
    }

    private suspend fun repairObfuscatedFlightNumbers() {
        val routesToFix = db.flightRouteDao().getAll().mapNotNull { r ->
            if (!r.flightNo.contains("&")) return@mapNotNull null
            val decoded = LoongAirApi.decodeFlightNo(r.flightNo)
                ?: "GJ${r.depTime.replace(":", "")}"
            r.copy(flightNo = decoded)
        }
        if (routesToFix.isNotEmpty()) {
            db.flightRouteDao().insertAll(routesToFix)
        }

        val snapsToFix = db.seatSnapshotDao().getAll().mapNotNull { s ->
            if (!s.flightNo.contains("&")) return@mapNotNull null
            val decoded = LoongAirApi.decodeFlightNo(s.flightNo)
                ?: "GJ${s.depTime.replace(":", "")}"
            s.copy(flightNo = decoded)
        }
        if (snapsToFix.isNotEmpty()) {
            db.seatSnapshotDao().upsertAll(snapsToFix)
            db.seatSnapshotDao().deleteObfuscated()
        }

        val plansToFix = db.tripPlanDao().getAll().mapNotNull { p ->
            val outFix = if (p.outboundFlightNo.contains("&")) {
                LoongAirApi.decodeFlightNo(p.outboundFlightNo) ?: "GJ${p.outboundDepTime.replace(":", "")}"
            } else p.outboundFlightNo
            val retFix = if (p.returnFlightNo.contains("&")) {
                LoongAirApi.decodeFlightNo(p.returnFlightNo) ?: "GJ${p.returnDepTime.replace(":", "")}"
            } else p.returnFlightNo
            if (outFix == p.outboundFlightNo && retFix == p.returnFlightNo) null
            else p.copy(outboundFlightNo = outFix, returnFlightNo = retFix)
        }
        plansToFix.forEach { db.tripPlanDao().update(it) }
    }

    suspend fun getChinaProvinces(): List<ChinaProvincePath> = withContext(Dispatchers.IO) {
        cachedProvinces?.let { return@withContext it }
        val raw = context.assets.open("china_provinces.json").bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString<List<ChinaProvincePath>>(raw)
        cachedProvinces = parsed
        parsed
    }

    /**
     * 仅从已同步入库的官方机场与官方航线动态构建城市元数据；首次登录同步前恒为空列表。
     */
    suspend fun getSyncedCityMetadata(): List<CityMeta> = withContext(Dispatchers.IO) {
        val airports = db.officialAirportDao().getAll()
        val routes = db.officialRouteDao().getAll()
        val byCity = linkedMapOf<String, CityMeta>()

        for (ap in airports) {
            val coords = CityIataMapper.geoCoordsFor(ap.cityName)
            byCity[ap.cityName] = CityMeta(
                city = ap.cityName,
                code = ap.airportCode,
                airport = ap.airportName.ifBlank { "${ap.cityName}机场" },
                lng = coords?.first ?: 114.0,
                lat = coords?.second ?: 34.0,
                pinyin = ap.pinyin
            )
        }
        for (r in routes) {
            if (r.fromCity !in byCity) {
                val coords = CityIataMapper.geoCoordsFor(r.fromCity)
                byCity[r.fromCity] = CityMeta(
                    city = r.fromCity,
                    code = r.fromIata,
                    airport = r.fromAirport.ifBlank { "${r.fromCity}机场" },
                    lng = coords?.first ?: 114.0,
                    lat = coords?.second ?: 34.0
                )
            }
            if (r.toCity !in byCity) {
                val coords = CityIataMapper.geoCoordsFor(r.toCity)
                byCity[r.toCity] = CityMeta(
                    city = r.toCity,
                    code = r.toIata,
                    airport = r.toAirport.ifBlank { "${r.toCity}机场" },
                    lng = coords?.first ?: 114.0,
                    lat = coords?.second ?: 34.0
                )
            }
        }
        byCity.values.sortedBy { it.city }
    }

    suspend fun getOfficialAirports(): List<OfficialAirportEntity> = withContext(Dispatchers.IO) {
        db.officialAirportDao().getAll()
    }

    suspend fun syncOfficialDepartureAirports(): List<OfficialAirportEntity> = withContext(Dispatchers.IO) {
        val endpoints = api.getOfficialDepartureAirports()
        val existing = db.officialAirportDao().getAll().associateBy { it.cityName }
        val now = System.currentTimeMillis()
        val entities = endpoints.map { ep ->
            val prev = existing[ep.cityName]
            val bestAirport = when {
                ep.airportName.isNotBlank() && ep.airportName != "${ep.cityName}机场" -> ep.airportName
                prev != null && prev.airportName.isNotBlank() && prev.airportName != "${ep.cityName}机场" -> prev.airportName
                else -> ep.airportName.ifBlank { "${ep.cityName}机场" }
            }
            val bestPinyin = ep.pinyin.ifBlank { prev?.pinyin.orEmpty() }
            OfficialAirportEntity(
                cityName = ep.cityName,
                airportName = bestAirport,
                airportCode = ep.airportCode,
                pinyin = bestPinyin,
                updatedAt = now
            )
        }
        if (entities.isNotEmpty()) {
            db.officialAirportDao().upsertAll(entities)
        }
        entities
    }

    /**
     * 获取已同步的出发城市列表（仅来源 official_routes 与 flight_routes；同步前为空）
     */
    suspend fun getAllOriginCities(): List<String> = withContext(Dispatchers.IO) {
        val official = db.officialRouteDao().getAll()
        val flights = db.flightRouteDao().getAllOriginCities()
        (official.map { it.fromCity } + flights).distinct().sorted()
    }

    suspend fun getDestinationsFrom(originCity: String): List<String> = withContext(Dispatchers.IO) {
        if (originCity.isBlank()) return@withContext emptyList()
        val official = db.officialRouteDao().getByOrigin(originCity)
        if (official.isNotEmpty()) {
            return@withContext official.map { it.toCity }.distinct().sorted()
        }
        db.flightRouteDao().getDestinationsFrom(originCity)
    }

    suspend fun getOfficialRoutes(): List<OfficialRouteEntity> = withContext(Dispatchers.IO) {
        db.officialRouteDao().getAll()
    }

    /**
     * 从长龙官方接口拉取指定出发城市的畅飞卡真实可达机场并持久化落库
     */
    suspend fun syncOfficialRoutesForOrigin(rawCityOrCode: String): List<OfficialRouteEntity> =
        withContext(Dispatchers.IO) {
            val fromCity = CityIataMapper.toCityName(rawCityOrCode.trim())
            val fromIata = CityIataMapper.toApiIata(rawCityOrCode.trim())
            val existingAirports = db.officialAirportDao().getAll().associateBy { it.cityName }
            val fromAirport = existingAirports[fromCity]?.airportName?.ifBlank { "${fromCity}机场" }
                ?: "${fromCity}机场"

            val endpoints = api.getOfficialArrivalAirports(fromCity)
            val now = System.currentTimeMillis()

            val airportEntities = mutableListOf<OfficialAirportEntity>()
            if (endpoints.isNotEmpty()) {
                airportEntities.add(
                    OfficialAirportEntity(
                        cityName = fromCity,
                        airportName = fromAirport,
                        airportCode = fromIata,
                        pinyin = existingAirports[fromCity]?.pinyin.orEmpty(),
                        updatedAt = now
                    )
                )
            }

            val routeEntities = endpoints.map { ep ->
                val toAirport = ep.airportName.ifBlank {
                    existingAirports[ep.cityName]?.airportName ?: "${ep.cityName}机场"
                }
                val toCode = ep.airportCode.ifBlank { CityIataMapper.toApiIata(ep.cityName) }
                airportEntities.add(
                    OfficialAirportEntity(
                        cityName = ep.cityName,
                        airportName = toAirport,
                        airportCode = toCode,
                        pinyin = ep.pinyin.ifBlank { existingAirports[ep.cityName]?.pinyin.orEmpty() },
                        updatedAt = now
                    )
                )
                OfficialRouteEntity(
                    fromCity = fromCity,
                    fromAirport = fromAirport,
                    fromIata = fromIata,
                    toCity = ep.cityName,
                    toAirport = toAirport,
                    toIata = toCode,
                    updatedAt = now
                )
            }

            if (airportEntities.isNotEmpty()) {
                db.officialAirportDao().upsertAll(airportEntities)
            }
            db.officialRouteDao().deleteForOrigin(fromCity)
            if (routeEntities.isNotEmpty()) {
                db.officialRouteDao().upsertAll(routeEntities)
            }
            routeEntities
        }

    private inline fun <T> runSyncStep(block: () -> T): Result<T> {
        return suspendRunCatching(block).onFailure { err ->
            if (err is SessionExpiredException || err is ApiRateLimitException) {
                throw err
            }
        }
    }

    /**
     * 优先同步「一个城市的所有航线」（直连长龙官方接口，不依赖任何本地种子）：
     * 1. 拉取该城市全部官方可达目的地机场 -> 写入 official_airports & official_routes
     * 2. 逐条拉取去程与返程放票日历 + 样本日真实班次时刻 -> 反推班期并写入 flight_routes
     * 3. 统计日历或班次失败的航线数并返回 CitySyncSummary，不静默吞错
     */
    suspend fun syncCityFullRoutes(
        rawCityOrCode: String,
        onDestinationsReady: suspend (originCity: String, count: Int) -> Unit = { _, _ -> },
        onRouteProgress: suspend (done: Int, total: Int, destCity: String) -> Unit = { _, _, _ -> }
    ): CitySyncSummary = withContext(Dispatchers.IO) {
        val originCity = CityIataMapper.toCityName(rawCityOrCode.trim())
        val officialDests = syncOfficialRoutesForOrigin(originCity)
        if (officialDests.isEmpty()) {
            return@withContext CitySyncSummary(originCity = originCity, totalRoutes = 0)
        }

        authPrefs.setHomeCity(originCity)
        onDestinationsReady(originCity, officialDests.size)

        val total = officialDests.size
        var done = 0
        var failedCal = 0
        var failedSched = 0
        val failedDests = linkedSetOf<String>()

        for (route in officialDests) {
            val destCity = route.toCity
            onRouteProgress(done, total, destCity)

            // 1) 去程：放票日历 + 双模式（一三五日 & 二四六）采样真实班次以区分隔日不同航班
            val outCalRes = runSyncStep { refreshRouteCalendar(originCity, destCity) }
            if (outCalRes.isFailure) {
                failedCal++
                failedDests.add(destCity)
            }
            val outCal = outCalRes.getOrDefault(emptyMap())
            val outCalDows = RoundTripMatcher.deriveFrequencyFromCalendar(outCal)
            val (outOddDate, outEvenDate) = RoundTripMatcher.pickDualPatternSampleDates(outCal)
            val outSchedOk = syncDirectionByDualSamples(
                fromCity = originCity,
                toCity = destCity,
                fromIata = route.fromIata,
                toIata = route.toIata,
                oddDate = outOddDate,
                evenDate = outEvenDate,
                calOperatingDows = outCalDows
            )
            if (!outSchedOk) {
                failedSched++
                failedDests.add(destCity)
            }

            // 2) 返程：放票日历 + 双模式采样真实班次（使往返配对立刻可用且不虚标每日）
            val retCalRes = runSyncStep { refreshRouteCalendar(destCity, originCity) }
            if (retCalRes.isFailure) {
                failedCal++
                failedDests.add(destCity)
            }
            val retCal = retCalRes.getOrDefault(emptyMap())
            val retCalDows = RoundTripMatcher.deriveFrequencyFromCalendar(retCal)
            val (retOddDate, retEvenDate) = RoundTripMatcher.pickDualPatternSampleDates(retCal)
            if (retOddDate != null || retEvenDate != null) {
                db.officialRouteDao().upsertAll(
                    listOf(
                        OfficialRouteEntity(
                            fromCity = destCity,
                            fromAirport = route.toAirport,
                            fromIata = route.toIata,
                            toCity = originCity,
                            toAirport = route.fromAirport,
                            toIata = route.fromIata,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                )
                val retSchedOk = syncDirectionByDualSamples(
                    fromCity = destCity,
                    toCity = originCity,
                    fromIata = route.toIata,
                    toIata = route.fromIata,
                    oddDate = retOddDate,
                    evenDate = retEvenDate,
                    calOperatingDows = retCalDows
                )
                if (!retSchedOk) {
                    failedSched++
                    failedDests.add(destCity)
                }
            }

            done++
            onRouteProgress(done, total, destCity)
            if (done < total) {
                delay(Random.nextLong(120, 260))
            }
        }
        CitySyncSummary(
            originCity = originCity,
            totalRoutes = officialDests.size,
            failedCalendarCount = failedCal,
            failedScheduleCount = failedSched,
            failedDestinations = failedDests.toList()
        )
    }

    private suspend fun syncDirectionByDualSamples(
        fromCity: String,
        toCity: String,
        fromIata: String,
        toIata: String,
        oddDate: String?,
        evenDate: String?,
        calOperatingDows: String
    ): Boolean {
        if (oddDate == null && evenDate == null) return true
        var allOk = true
        val oddSeats = if (oddDate != null) {
            val r = runSyncStep { refreshRouteSeats(fromCity, toCity, oddDate, updateFlightRoutes = false) }
            if (r.isFailure) allOk = false
            r.getOrDefault(emptyList())
        } else emptyList()
        val evenSeats = if (evenDate != null) {
            val r = runSyncStep { refreshRouteSeats(fromCity, toCity, evenDate, updateFlightRoutes = false) }
            if (r.isFailure) allOk = false
            r.getOrDefault(emptyList())
        } else emptyList()

        val oddMap = oddSeats.distinctBy { "${it.flightNo}|${it.depTime}" }.associateBy { it.flightNo.ifBlank { it.depTime } }
        val evenMap = evenSeats.distinctBy { "${it.flightNo}|${it.depTime}" }.associateBy { it.flightNo.ifBlank { it.depTime } }
        val allKeys = linkedSetOf<String>().apply {
            addAll(oddMap.keys)
            addAll(evenMap.keys)
        }
        if (allKeys.isEmpty()) return allOk

        val calSet = calOperatingDows.filter { it in '1'..'7' }.map { it - '0' }.toSet()
        val oddGroup = setOf(1, 3, 5, 7)
        val evenGroup = setOf(2, 4, 6)

        val entities = allKeys.mapNotNull { key ->
            val seat = oddMap[key] ?: evenMap[key] ?: return@mapNotNull null
            val inOdd = key in oddMap
            val inEven = key in evenMap
            val inferredSet = when {
                inOdd && inEven -> if (calSet.isNotEmpty()) calSet else (oddGroup + evenGroup)
                inOdd -> if (calSet.isNotEmpty()) calSet.intersect(oddGroup).ifEmpty { oddGroup } else oddGroup
                else -> if (calSet.isNotEmpty()) calSet.intersect(evenGroup).ifEmpty { evenGroup } else evenGroup
            }
            FlightRouteEntity(
                fromCity = fromCity,
                toCity = toCity,
                fromIata = fromIata,
                toIata = toIata,
                flightNo = seat.flightNo,
                depTime = seat.depTime,
                arrTime = seat.arrTime,
                duration = seat.duration,
                aircraft = seat.aircraft,
                frequency = inferredSet.sorted().joinToString(""),
                stopover = seat.stopover.ifBlank { "直飞" },
                season = "official"
            )
        }.sortedBy { it.depTime }

        if (entities.isNotEmpty()) {
            db.flightRouteDao().deleteForRoute(fromCity, toCity)
            db.flightRouteDao().insertAll(entities)
        }
        return allOk
    }

    /**
     * 针对单条航线执行「周一至周日」全 7 天精确采样校准，逐航班还原真实执飞星期（例如 GJ8124=一三五日、GJ8148=二四六）
     */
    suspend fun calibrateRouteWeeklySchedule(
        fromCity: String,
        toCity: String
    ): List<FlightRouteEntity> = withContext(Dispatchers.IO) {
        var calMap = getRouteCalendarMap(fromCity, toCity)
        if (calMap.isEmpty()) {
            calMap = runSyncStep { refreshRouteCalendar(fromCity, toCity) }.getOrDefault(emptyMap())
        }
        val sampleByDow = RoundTripMatcher.pickWeeklySampleDates(calMap)
        if (sampleByDow.isEmpty()) return@withContext db.flightRouteDao().getRoutes(fromCity, toCity)

        val flightsByDow = linkedMapOf<Int, List<LiveFlightSeat>>()
        for ((idx, entry) in sampleByDow.entries.withIndex()) {
            val (dow, dateStr) = entry
            val seats = runSyncStep { refreshRouteSeats(fromCity, toCity, dateStr, updateFlightRoutes = false) }
                .getOrDefault(emptyList())
            if (seats.isNotEmpty()) {
                flightsByDow[dow] = seats
            }
            if (idx < sampleByDow.size - 1) {
                delay(Random.nextLong(80, 180))
            }
        }
        if (flightsByDow.isEmpty()) return@withContext db.flightRouteDao().getRoutes(fromCity, toCity)

        val fromIata = CityIataMapper.toApiIata(fromCity)
        val toIata = CityIataMapper.toApiIata(toCity)
        val aggregated = RoundTripMatcher.aggregateWeeklyFlightSchedules(flightsByDow)
        val entities = aggregated.map { (seat, freq) ->
            FlightRouteEntity(
                fromCity = fromCity,
                toCity = toCity,
                fromIata = fromIata,
                toIata = toIata,
                flightNo = seat.flightNo,
                depTime = seat.depTime,
                arrTime = seat.arrTime,
                duration = seat.duration,
                aircraft = seat.aircraft,
                frequency = freq,
                stopover = seat.stopover.ifBlank { "直飞" },
                season = "official"
            )
        }
        db.flightRouteDao().deleteForRoute(fromCity, toCity)
        db.flightRouteDao().insertAll(entities)
        entities
    }

    private suspend fun persistSyncedFlights(
        fromCity: String,
        toCity: String,
        fromIata: String,
        toIata: String,
        seats: List<LiveFlightSeat>,
        frequency: String
    ) {
        if (seats.isEmpty()) return
        val deduped = seats.distinctBy { "${it.flightNo}|${it.depTime}" }
        val entities = deduped.map { s ->
            FlightRouteEntity(
                fromCity = fromCity,
                toCity = toCity,
                fromIata = fromIata,
                toIata = toIata,
                flightNo = s.flightNo,
                depTime = s.depTime,
                arrTime = s.arrTime,
                duration = s.duration,
                aircraft = s.aircraft,
                frequency = frequency,
                stopover = s.stopover.ifBlank { "直飞" },
                season = "official"
            )
        }
        db.flightRouteDao().deleteForRoute(fromCity, toCity)
        db.flightRouteDao().insertAll(entities)
    }

    /**
     * 构建第 1 栏「航线」全网聚合列表（100% 来源于已同步的官方航线、官方时刻与 60 天日历；未同步前为空）
     */
    suspend fun getAllNetworkRoutes(): List<NetworkRouteSummary> = withContext(Dispatchers.IO) {
        val allFlights = db.flightRouteDao().getAll()
        val officialRoutes = db.officialRouteDao().getAll()
        if (officialRoutes.isEmpty() && allFlights.isEmpty()) return@withContext emptyList()

        val metaMap = getSyncedCityMetadata().associateBy { it.city }
        val todayStr = ChinaClock.today().toString()
        val availCals = db.calendarCacheDao().getAllAvailable(todayStr)
            .groupBy { "${it.fromCity}|${it.toCity}" }

        val flightsByPair = allFlights.groupBy { "${it.fromCity}|${it.toCity}" }
        val officialByPair = officialRoutes.associateBy { "${it.fromCity}|${it.toCity}" }

        val allKeys = linkedSetOf<String>()
        officialByPair.keys.forEach { allKeys.add(it) }
        flightsByPair.keys.forEach { allKeys.add(it) }

        allKeys.map { key ->
            val fromCity = key.substringBefore("|")
            val toCity = key.substringAfter("|")
            val off = officialByPair[key]
            val flights = flightsByPair[key].orEmpty()
            val fromMeta = metaMap[fromCity]
            val toMeta = metaMap[toCity]
            val cals = availCals[key].orEmpty()

            val fromAirportName = listOfNotNull(
                off?.fromAirport?.takeIf { it.isNotBlank() && it != "${fromCity}机场" },
                fromMeta?.airport?.takeIf { it.isNotBlank() && it != "${fromCity}机场" },
                off?.fromAirport?.takeIf { it.isNotBlank() },
                fromMeta?.airport?.takeIf { it.isNotBlank() }
            ).firstOrNull() ?: "${fromCity}机场"
            val toAirportName = listOfNotNull(
                off?.toAirport?.takeIf { it.isNotBlank() && it != "${toCity}机场" },
                toMeta?.airport?.takeIf { it.isNotBlank() && it != "${toCity}机场" },
                off?.toAirport?.takeIf { it.isNotBlank() },
                toMeta?.airport?.takeIf { it.isNotBlank() }
            ).firstOrNull() ?: "${toCity}机场"

            NetworkRouteSummary(
                fromCity = fromCity,
                fromAirport = fromAirportName,
                fromIata = off?.fromIata?.ifBlank { CityIataMapper.toApiIata(fromCity) } ?: CityIataMapper.toApiIata(fromCity),
                toCity = toCity,
                toAirport = toAirportName,
                toIata = off?.toIata?.ifBlank { CityIataMapper.toApiIata(toCity) } ?: CityIataMapper.toApiIata(toCity),
                flights = flights,
                isOfficialSynced = off != null,
                availableDaysCount = cals.size,
                nearestAvailableDate = cals.firstOrNull()?.flightDate,
                officialUpdatedAt = off?.updatedAt
            )
        }.sortedWith(
            compareByDescending<NetworkRouteSummary> { it.availableDaysCount }
                .thenByDescending { it.flights.size }
                .thenBy { it.fromCity }
                .thenBy { it.toCity }
        )
    }

    /**
     * 单航线增量摘要构建：避免查单日余票、刷新单条日历或校准单条班期时重读全量数据库。
     */
    suspend fun getSingleNetworkRouteSummary(fromCity: String, toCity: String): NetworkRouteSummary? =
        withContext(Dispatchers.IO) {
            val off = db.officialRouteDao().getOne(fromCity, toCity)
            val flights = db.flightRouteDao().getRoutes(fromCity, toCity).sortedBy { it.depTime }
            if (off == null && flights.isEmpty()) return@withContext null

            val fromAp = db.officialAirportDao().getByCity(fromCity)
            val toAp = db.officialAirportDao().getByCity(toCity)
            val todayStr = ChinaClock.today().toString()
            val cals = db.calendarCacheDao().getAllForRoute(fromCity, toCity, todayStr)
                .filter { it.status == 1 }

            val fromAirportName = listOfNotNull(
                off?.fromAirport?.takeIf { it.isNotBlank() && it != "${fromCity}机场" },
                fromAp?.airportName?.takeIf { it.isNotBlank() && it != "${fromCity}机场" },
                off?.fromAirport?.takeIf { it.isNotBlank() },
                fromAp?.airportName?.takeIf { it.isNotBlank() }
            ).firstOrNull() ?: "${fromCity}机场"
            val toAirportName = listOfNotNull(
                off?.toAirport?.takeIf { it.isNotBlank() && it != "${toCity}机场" },
                toAp?.airportName?.takeIf { it.isNotBlank() && it != "${toCity}机场" },
                off?.toAirport?.takeIf { it.isNotBlank() },
                toAp?.airportName?.takeIf { it.isNotBlank() }
            ).firstOrNull() ?: "${toCity}机场"

            NetworkRouteSummary(
                fromCity = fromCity,
                fromAirport = fromAirportName,
                fromIata = off?.fromIata?.ifBlank { CityIataMapper.toApiIata(fromCity) } ?: CityIataMapper.toApiIata(fromCity),
                toCity = toCity,
                toAirport = toAirportName,
                toIata = off?.toIata?.ifBlank { CityIataMapper.toApiIata(toCity) } ?: CityIataMapper.toApiIata(toCity),
                flights = flights,
                isOfficialSynced = off != null,
                availableDaysCount = cals.size,
                nearestAvailableDate = cals.firstOrNull()?.flightDate,
                officialUpdatedAt = off?.updatedAt
            )
        }

    suspend fun getRouteCalendarMap(fromCity: String, toCity: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val todayStr = ChinaClock.today().toString()
            db.calendarCacheDao().getAllForRoute(fromCity, toCity, todayStr)
                .associate { it.flightDate to it.status }
        }

    suspend fun getRouteSeatSnapshotsByDate(fromCity: String, toCity: String): Map<String, List<SeatSnapshotEntity>> =
        withContext(Dispatchers.IO) {
            val todayStr = ChinaClock.today().toString()
            db.seatSnapshotDao().getAllForRoute(fromCity, toCity, todayStr)
                .groupBy { it.flightDate }
        }

    suspend fun searchRoundTrips(query: RoundTripQuery): List<RoundTripCandidate> = withContext(Dispatchers.IO) {
        if (query.originCity.isBlank()) return@withContext emptyList()
        val officialForOrigin = db.officialRouteDao().getByOrigin(query.originCity)
        val allowedOfficialDests = officialForOrigin.map { it.toCity }.toSet()

        val outRoutes = db.flightRouteDao().getOutboundFrom(query.originCity).let { list ->
            if (allowedOfficialDests.isNotEmpty()) {
                list.filter { it.toCity in allowedOfficialDests }
            } else {
                list
            }
        }
        val inRoutes = db.flightRouteDao().getInboundTo(query.originCity).let { list ->
            if (allowedOfficialDests.isNotEmpty()) {
                list.filter { it.fromCity in allowedOfficialDests }
            } else {
                list
            }
        }
        if (outRoutes.isEmpty() || inRoutes.isEmpty()) return@withContext emptyList()

        val dests = outRoutes.map { it.toCity }.toSet().intersect(inRoutes.map { it.fromCity }.toSet())
        val metaMap = getSyncedCityMetadata().associateBy { it.city }
        val pairs = query.datePairs.ifEmpty { listOf(query.outboundDate to query.returnDate) }.distinct()

        val allCandidates = mutableListOf<RoundTripCandidate>()
        for ((outDate, retDate) in pairs) {
            val outDateStr = outDate.toString()
            val retDateStr = retDate.toString()
            val outCals = db.calendarCacheDao().getOutboundStatuses(query.originCity, outDateStr)
                .associateBy { it.toCity }
            val inCals = db.calendarCacheDao().getInboundStatuses(query.originCity, retDateStr)
                .associateBy { it.fromCity }

            val seatOverlay = mutableMapOf<String, List<SeatSnapshotEntity>>()
            for (dest in dests) {
                val outSnap = db.seatSnapshotDao().getForRouteDate(query.originCity, dest, outDateStr)
                if (outSnap.isNotEmpty()) {
                    seatOverlay["${query.originCity}|$dest|$outDateStr"] = outSnap
                }
                val retSnap = db.seatSnapshotDao().getForRouteDate(dest, query.originCity, retDateStr)
                if (retSnap.isNotEmpty()) {
                    seatOverlay["$dest|${query.originCity}|$retDateStr"] = retSnap
                }
            }

            allCandidates.addAll(
                RoundTripMatcher.matchRoundTrips(
                    query = query.copy(outboundDate = outDate, returnDate = retDate),
                    allOutboundRoutes = outRoutes,
                    allInboundRoutes = inRoutes,
                    outboundCalendars = outCals,
                    inboundCalendars = inCals,
                    seatOverlay = seatOverlay,
                    cityMetaMap = metaMap
                )
            )
        }

        if (pairs.size <= 1) {
            allCandidates
        } else {
            allCandidates.sortedWith(
                compareByDescending<RoundTripCandidate> { it.isBothCalendarAvailable }
                    .thenByDescending { (it.outCalendarStatus == 1) || (it.retCalendarStatus == 1) }
                    .thenBy { it.hasAnyUnavailableCalendar }
                    .thenByDescending { (it.outMaxSeats ?: -1) > 0 && (it.retMaxSeats ?: -1) > 0 }
                    .thenBy { it.outboundDate }
                    .thenBy { it.destinationCity }
            )
        }
    }

    suspend fun refreshRouteCalendar(fromCity: String, toCity: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val calMap = api.getCalendar(fromCity, toCity)
            val now = System.currentTimeMillis()
            val entities = calMap.map { (dateStr, status) ->
                CalendarCacheEntity(
                    fromCity = fromCity,
                    toCity = toCity,
                    flightDate = dateStr,
                    status = status,
                    updatedAt = now
                )
            }
            if (entities.isNotEmpty()) {
                db.calendarCacheDao().upsertAll(entities)
            }
            calMap
        }

    suspend fun refreshRouteSeats(
        fromCity: String,
        toCity: String,
        dateStr: String,
        updateFlightRoutes: Boolean = true
    ): List<LiveFlightSeat> = withContext(Dispatchers.IO) {
        val knownRoutes = db.flightRouteDao().getRoutes(fromCity, toCity)
        val liveList = api.searchFlights(fromCity, toCity, dateStr, knownRoutes)
        val now = System.currentTimeMillis()
        db.seatSnapshotDao().deleteForRouteDate(fromCity, toCity, dateStr)
        val entities = liveList.map { s ->
            SeatSnapshotEntity(
                fromCity = fromCity,
                toCity = toCity,
                flightDate = dateStr,
                flightNo = s.flightNo,
                depTime = s.depTime,
                arrTime = s.arrTime,
                aircraft = s.aircraft,
                stopover = s.stopover,
                secCardLeft = s.secCardLeft,
                normalLeft = s.normalLeft,
                redeemPrice = s.redeemPrice,
                cabinCode = s.cabinCode,
                queriedAt = now
            )
        }
        if (entities.isNotEmpty()) {
            db.seatSnapshotDao().upsertAll(entities)
        }
        // 被动自学习：每次单日查票时自动补充未收录航班，并修正该星期未执飞的旧班期
        if (updateFlightRoutes && liveList.isNotEmpty()) {
            val dowChar = runCatching { LocalDate.parse(dateStr).dayOfWeek.value.toString() }.getOrNull()
            val observedNos = liveList.map { it.flightNo }.toSet()
            val merged = mutableListOf<FlightRouteEntity>()
            val existingByNo = knownRoutes.associateBy { it.flightNo }

            for (r in knownRoutes) {
                if (dowChar != null) {
                    val digits = r.frequency.filter { it in '1'..'7' }.toCollection(mutableSetOf())
                    if (r.flightNo in observedNos) {
                        digits.add(dowChar[0])
                    } else if (digits.size > 1) {
                        // 该日期确有实时航班但此班次未出现，从执飞星期中剔除该天
                        digits.remove(dowChar[0])
                    }
                    val nextFreq = digits.sorted().joinToString("")
                    merged.add(r.copy(frequency = nextFreq))
                } else {
                    merged.add(r)
                }
            }

            val fromIata = CityIataMapper.toApiIata(fromCity)
            val toIata = CityIataMapper.toApiIata(toCity)
            for (s in liveList.distinctBy { "${it.flightNo}|${it.depTime}" }) {
                if (s.flightNo !in existingByNo) {
                    merged.add(
                        FlightRouteEntity(
                            fromCity = fromCity,
                            toCity = toCity,
                            fromIata = fromIata,
                            toIata = toIata,
                            flightNo = s.flightNo,
                            depTime = s.depTime,
                            arrTime = s.arrTime,
                            duration = s.duration,
                            aircraft = s.aircraft,
                            frequency = dowChar.orEmpty(),
                            stopover = s.stopover.ifBlank { "直飞" },
                            season = "official"
                        )
                    )
                }
            }
            if (merged.isNotEmpty()) {
                db.flightRouteDao().deleteForRoute(fromCity, toCity)
                db.flightRouteDao().insertAll(merged.sortedBy { it.depTime })
            }
        }
        liveList
    }

    suspend fun refreshRoundTripSeats(
        originCity: String,
        destCity: String,
        outDateStr: String,
        retDateStr: String
    ): Pair<List<LiveFlightSeat>, List<LiveFlightSeat>> = withContext(Dispatchers.IO) {
        val outSeats = refreshRouteSeats(originCity, destCity, outDateStr)
        delay(Random.nextLong(600, 1100))
        val retSeats = refreshRouteSeats(destCity, originCity, retDateStr)
        outSeats to retSeats
    }

    fun observeTemplates(): Flow<List<TripTemplateEntity>> = db.tripTemplateDao().observeAll()

    suspend fun saveTemplate(template: TripTemplateEntity) = withContext(Dispatchers.IO) {
        if (template.id == 0L) {
            db.tripTemplateDao().insert(template)
        } else {
            db.tripTemplateDao().update(template)
        }
    }

    suspend fun deleteTemplate(template: TripTemplateEntity) = withContext(Dispatchers.IO) {
        db.tripTemplateDao().delete(template)
    }

    fun observePlans(): Flow<List<TripPlanEntity>> = db.tripPlanDao().observeAll()

    suspend fun getPlanById(id: Long): TripPlanEntity? = withContext(Dispatchers.IO) {
        db.tripPlanDao().getById(id)
    }

    suspend fun savePlan(plan: TripPlanEntity): Long = withContext(Dispatchers.IO) {
        db.tripPlanDao().insert(plan)
    }

    suspend fun updatePlan(plan: TripPlanEntity) = withContext(Dispatchers.IO) {
        db.tripPlanDao().update(plan)
    }

    suspend fun deletePlan(plan: TripPlanEntity) = withContext(Dispatchers.IO) {
        db.reminderDao().deleteForPlan(plan.id)
        db.tripPlanDao().delete(plan)
    }

    fun observeReminders(): Flow<List<ReminderEntity>> = db.reminderDao().observeAll()

    suspend fun getRemindersForPlan(planId: Long): List<ReminderEntity> = withContext(Dispatchers.IO) {
        db.reminderDao().getForPlan(planId)
    }

    suspend fun saveReminder(reminder: ReminderEntity): Long = withContext(Dispatchers.IO) {
        db.reminderDao().insert(reminder)
    }

    suspend fun updateReminder(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        db.reminderDao().update(reminder)
    }

    suspend fun deleteReminder(reminderId: Long) = withContext(Dispatchers.IO) {
        db.reminderDao().deleteById(reminderId)
    }

    suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
        db.calendarCacheDao().clearAll()
        db.seatSnapshotDao().clearAll()
        db.officialRouteDao().clearAll()
        db.flightRouteDao().clearAll()
        db.officialAirportDao().clearAll()
        authPrefs.setHomeCity("")
    }
}
