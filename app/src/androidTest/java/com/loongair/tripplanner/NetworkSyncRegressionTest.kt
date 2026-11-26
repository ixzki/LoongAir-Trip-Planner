package com.loongair.tripplanner

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.loongair.tripplanner.data.local.AppDatabase
import com.loongair.tripplanner.data.local.AuthPrefs
import com.loongair.tripplanner.data.local.CalendarCacheEntity
import com.loongair.tripplanner.data.local.InvalidatedRouteEntity
import com.loongair.tripplanner.data.local.ROUTE_SOURCE_BUNDLED
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.local.StringCipher
import com.loongair.tripplanner.data.remote.ApiRateLimiter
import com.loongair.tripplanner.data.remote.LoongAirApi
import com.loongair.tripplanner.data.repository.FlightRepository
import com.loongair.tripplanner.util.ChinaClock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class NetworkSyncRegressionTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferencesName = "network-sync-test-${UUID.randomUUID()}"
    private lateinit var db: AppDatabase
    private lateinit var repo: FlightRepository
    private val methods = mutableListOf<String>()
    private val hangzhou = """{"cityName":"杭州","airportCode":"HGH","airportName":"萧山机场"}"""
    private val beijing = """{"cityName":"北京首都","airportCode":"PEK","airportName":"首都机场"}"""
    private var hangzhouDirectory = "[$beijing]"
    private var calendar: (String) -> String = { """{"calenderMap":{"${ChinaClock.today().plusDays(8)}":1}}""" }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val cipher = object : StringCipher {
            override fun isEncrypted(stored: String) = stored.startsWith("fixture:")
            override fun encrypt(plain: String) = "fixture:$plain"
            override fun decrypt(stored: String) = stored.removePrefix("fixture:")
        }
        val prefs = AuthPrefs(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE), cipher)
        prefs.saveSession(token = "test-session", refreshToken = "", userName = "", mobile = "",
            memberLevel = "", cardName = "测试卡", secCardGrantTid = "test-card", encryptedUserId = "",
            certNumber = "", updatedAt = "")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            requireNotNull(request.body).writeTo(buffer)
            val form = URLDecoder.decode(buffer.readUtf8().removePrefix("paras="), "UTF-8")
            val envelope = Json.parseToJsonElement(form).jsonObject
            val method = envelope.getValue("MethodName").jsonPrimitive.content
            val params = Json.parseToJsonElement(envelope.getValue("Parameters").jsonPrimitive.content).jsonObject
            methods.add(method)
            // Always return here. An unexpected method fails locally before DNS or a socket is used.
            val payload = when (method) {
                DEPARTURES -> "[$hangzhou,$beijing]"
                ARRIVALS -> when (params.getValue("fromCode").jsonPrimitive.content) {
                    "HGH" -> hangzhouDirectory
                    "PEK" -> "[$hangzhou]"
                    else -> "[]"
                }
                CALENDAR -> calendar(params.getValue("departureCode").jsonPrimitive.content)
                else -> throw AssertionError("Unexpected gateway method: $method")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"ErrorCode":0,"Result":$payload}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val clock = AtomicLong()
        val limiter = ApiRateLimiter(timeProvider = { clock.addAndGet(1000) })
        repo = FlightRepository(context, db, prefs, LoongAirApi(prefs, limiter, client))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteSharedPreferences(preferencesName)
    }

    @Test
    fun summariesShareThe40DayWindowAndExpireAtTheCutoffWithoutNetwork() = runBlocking {
        repo.ensureInitialized()
        val today = LocalDate.of(2026, 11, 25)
        val cutoff = today.atTime(23, 40)
        val origin = "杭州"
        val dest = "北京首都"
        fun row(offset: Long, status: Int) =
            CalendarCacheEntity(origin, dest, today.plusDays(offset).toString(), status, 1L)
        suspend fun check(atCutoff: Boolean, expectedOpen: Int, expectedSoldOut: Int, nearest: Long) {
            val now = if (atCutoff) cutoff else cutoff.minusSeconds(1)
            val fromList = repo.getAllNetworkRoutes(now).single { it.fromCity == origin && it.toCity == dest }
            val fromDetail = requireNotNull(repo.getSingleNetworkRouteSummary(origin, dest, now))
            for (route in listOf(fromList, fromDetail)) {
                assertEquals(expectedOpen, route.availableDaysCount)
                assertEquals(expectedSoldOut, route.soldOutDaysCount)
                assertEquals(today.plusDays(nearest).toString(), route.nearestAvailableDate)
            }
        }
        db.calendarCacheDao().upsertAll(listOf(row(-1, 1), row(2, 0), row(3, 1), row(39, 1), row(40, 1), row(41, 0)))
        check(false, 2, 0, 3)
        check(true, 1, 0, 39)
        db.calendarCacheDao().upsertAll(listOf(row(3, 0)))
        check(false, 1, 1, 39)
        check(true, 1, 0, 39)
        assertTrue(methods.isEmpty())
    }

    @Test
    fun realBundledNetworkIsUnverifiedAndOnlySecondFullSyncReusesSuccessfulRequests() = runBlocking {
        repo.ensureInitialized()
        val routes = repo.getOfficialRoutes()
        assertEquals(410, routes.size)
        assertTrue(routes.all { it.dataSource == ROUTE_SOURCE_BUNDLED && it.updatedAt == 0L })
        assertEquals(445, db.flightRouteDao().count())
        val origin = "杭州"
        val destination = "北京首都"
        assertFalse(requireNotNull(repo.getSingleNetworkRouteSummary(origin, destination)).isCalendarSynced)
        assertTrue(db.calendarCacheDao().getAllForRoute(origin, destination, "").all { it.status == -2 && it.updatedAt == 0L })
        assertTrue(db.networkSyncDao().since(0).isEmpty())
        assertTrue(methods.isEmpty())

        val first = repo.syncFullChangfeiNetwork()
        assertEquals(0, first.failedCount)
        assertTrue(first.queriedAirports > 0)
        assertEquals(2, first.queriedCalendars)
        assertTrue(DEPARTURES in methods)
        assertEquals(first.queriedAirports, methods.count { it == ARRIVALS })
        assertEquals(first.queriedCalendars, methods.count { it == CALENDAR })
        assertTrue(db.networkSyncDao().hasFetched(origin, "", 1))
        assertTrue(db.networkSyncDao().hasFetched(origin, destination, 1))
        assertTrue(requireNotNull(repo.getSingleNetworkRouteSummary(origin, destination)).isCalendarSynced)

        methods.clear()
        val second = repo.syncFullChangfeiNetwork()
        assertTrue(methods.isEmpty())
        assertEquals(0, second.queriedAirports)
        assertEquals(0, second.queriedCalendars)
        assertEquals(2, second.cachedCalendars)
        assertEquals(0, second.failedCount)
    }

    @Test
    fun malformedDirectoryPreservesDataAndOnlyAnExplicitEmptyDirectoryRemovesIt() = runBlocking {
        repo.ensureBundledNetworkLoaded()
        val origin = "杭州"
        val dest = "北京首都"
        val routes = repo.syncOfficialRoutesForOrigin(origin)
        assertEquals(listOf(dest), routes.map { it.toCity })
        val date = repo.refreshRouteCalendar(origin, dest).keys.single()
        val seat = SeatSnapshotEntity(origin, dest, date, "GJ8887", "07:00", "09:20", "A320", "直飞", 1, 5, 0.0, "V", 123)
        db.seatSnapshotDao().upsertAll(listOf(seat))
        val markers = db.networkSyncDao().since(1).associateBy { it.fromCity to it.toCity }
        val flights = db.flightRouteDao().getRoutes(origin, dest)
        val calendars = db.calendarCacheDao().getAllForRoute(origin, dest, "")
        assertTrue(flights.isNotEmpty())
        assertTrue(calendars.any { it.flightDate == date && it.status == 1 })
        assertTrue(markers.keys.containsAll(listOf(origin to "", origin to dest)))
        for (malformed in listOf("""{"message":"missing rows"}""", """{"list":[$beijing,{"airportCode":"?"}]}""")) {
            hangzhouDirectory = malformed
            val failure = runCatching { repo.syncOfficialRoutesForOrigin(origin) }.exceptionOrNull()
            assertTrue("malformed directory should be rejected", failure is IllegalStateException)
            assertEquals(routes, db.officialRouteDao().getByOrigin(origin))
            assertEquals(flights, db.flightRouteDao().getRoutes(origin, dest))
            assertEquals(calendars, db.calendarCacheDao().getAllForRoute(origin, dest, ""))
            assertEquals(listOf(seat), db.seatSnapshotDao().getForRouteDate(origin, dest, date))
            assertEquals(markers, db.networkSyncDao().since(1).associateBy { it.fromCity to it.toCity })
        }

        hangzhouDirectory = """{"list":[],"total":0}"""
        assertTrue(repo.syncOfficialRoutesForOrigin(origin).isEmpty())
        assertTrue(db.officialRouteDao().getByOrigin(origin).isEmpty())
        assertTrue(db.flightRouteDao().getRoutes(origin, dest).isEmpty())
        assertTrue(db.calendarCacheDao().getAllForRoute(origin, dest, "").isEmpty())
        assertTrue(db.seatSnapshotDao().getForRouteDate(origin, dest, date).isEmpty())
        assertTrue(db.invalidatedRouteDao().getAll().any { it.fromCity == origin && it.toCity == dest })
        assertTrue(db.networkSyncDao().hasFetched(origin, "", 1))
        assertFalse(db.networkSyncDao().hasFetched(origin, dest, 1))
        assertNotNull(db.officialRouteDao().getOne(dest, origin))
    }

    @Test
    fun routeSpecificExclusionDoesNotHideTheSameDayOnAnotherRoute() = runBlocking {
        val globalBefore = repo.getGlobalUnavailableDates()
        val date = (6L..170L).map { ChinaClock.today().plusDays(it).toString() }.first { it !in globalBefore }
        calendar = { code -> """{"calenderMap":{"$date":${if (code == "HGH") -2 else 1}}}""" }
        repo.refreshRouteCalendar("杭州", "北京首都")
        repo.refreshRouteCalendar("北京首都", "杭州")
        assertEquals(-2, repo.getRouteCalendarMap("杭州", "北京首都")[date])
        assertEquals(1, repo.getRouteCalendarMap("北京首都", "杭州")[date])
        assertEquals(globalBefore, repo.getGlobalUnavailableDates())
    }

    @Test
    fun soldOutCalendarDropsOnlyOlderSeatsForItsDirectionAndDate() = runBlocking {
        val date = ChinaClock.today().plusDays(8).toString()
        val otherDate = ChinaClock.today().plusDays(9).toString()
        val old = SeatSnapshotEntity("杭州", "北京首都", date, "GJ8887", "07:00", "09:20",
            "A320", "直飞", 2, 5, 0.0, "V", 10L)
        val fresh = old.copy(flightNo = "GJ8000", queriedAt = Long.MAX_VALUE)
        val reverse = old.copy(fromCity = old.toCity, toCity = old.fromCity)
        val nextDay = old.copy(flightDate = otherDate)
        db.seatSnapshotDao().upsertAll(listOf(old, fresh, reverse, nextDay))
        calendar = { """{"calenderMap":{"$date":0,"$otherDate":1}}""" }
        repo.refreshRouteCalendar(old.fromCity, old.toCity)
        assertEquals(setOf(fresh, reverse, nextDay), db.seatSnapshotDao().getAll().toSet())
        assertEquals(listOf(fresh), repo.getRouteSeatSnapshotsByDate(old.fromCity, old.toCity)[date])
    }

    @Test
    fun v6MigrationPreservesCalendarAndStartsWithUnverifiedFreshness() = runBlocking {
        val legacy = CalendarCacheEntity("杭州", "北京首都", "2026-12-25", 1, 12345)
        db.calendarCacheDao().upsertAll(listOf(legacy))
        db.invalidatedRouteDao().upsert(InvalidatedRouteEntity("三亚", "杭州", "当前卡ID:fixture", 999))
        val sqlite = db.openHelper.writableDatabase
        // V6 has the same data tables, excluding the new network_sync table.
        sqlite.execSQL("DROP TABLE network_sync")
        AppDatabase.MIGRATION_6_7.migrate(sqlite)
        assertEquals(legacy, db.calendarCacheDao().getStatus(legacy.fromCity, legacy.toCity, legacy.flightDate))
        assertEquals("当前畅飞卡不适用该航线", db.invalidatedRouteDao().getAll().single().reason)
        assertTrue(db.networkSyncDao().since(0).isEmpty())
    }

    private companion object {
        const val DEPARTURES = "/coupon/client/getSecCardDepartureAirport"
        const val ARRIVALS = "/coupon/client/getSecCardArriveAirport"
        const val CALENDAR = "getSecCardRedeemedCalendar"
    }
}
