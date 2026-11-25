package com.loongair.tripplanner

import androidx.sqlite.db.SupportSQLiteDatabase
import com.loongair.tripplanner.data.local.AppDatabase
import com.loongair.tripplanner.data.remote.ApiRateLimiter
import com.loongair.tripplanner.data.repository.NetworkRouteSummary
import com.loongair.tripplanner.ui.NetworkUiState
import com.loongair.tripplanner.util.ChinaClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class RateLimiterAndMigrationTest {

    @Test
    fun apiRateLimiter_detectsThrottleAndComputesEscalatingBackoff() = runTest {
        var virtualClock = 1_000_000L
        val limiter = ApiRateLimiter(minQueryIntervalMs = 240L, timeProvider = { virtualClock })

        assertTrue(ApiRateLimiter.isThrottleSignal(429, -1, ""))
        assertTrue(ApiRateLimiter.isThrottleSignal(503, -1, ""))
        assertTrue(ApiRateLimiter.isThrottleSignal(200, 1002, "操作过于频繁，请稍后再试"))
        assertFalse(ApiRateLimiter.isThrottleSignal(200, 0, "ok"))

        val firstBackoff = limiter.recordThrottleAndComputeBackoffMs()
        val secondBackoff = limiter.recordThrottleAndComputeBackoffMs()
        assertTrue(firstBackoff >= 900L)
        assertTrue(secondBackoff > firstBackoff)

        limiter.onSuccess()
        virtualClock += 10_000L
        val resetBackoff = limiter.recordThrottleAndComputeBackoffMs()
        assertTrue(resetBackoff in 900L..1600L)
    }

    @Test
    fun appDatabaseMigrations_executeExpectedSchemaSqlInOrder() {
        val executedSql = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0].toString())
            }
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        val migrations = AppDatabase.ALL_MIGRATIONS
        assertEquals(4, migrations.size)
        assertEquals(1 to 2, migrations[0].startVersion to migrations[0].endVersion)
        assertEquals(2 to 3, migrations[1].startVersion to migrations[1].endVersion)
        assertEquals(3 to 4, migrations[2].startVersion to migrations[2].endVersion)
        assertEquals(4 to 5, migrations[3].startVersion to migrations[3].endVersion)

        migrations.forEach { it.migrate(fakeDb) }
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS official_routes") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS official_airports") })
        assertTrue(executedSql.any { it.contains("DELETE FROM flight_routes WHERE season != 'official'") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE trip_templates ADD COLUMN outDaysOfWeek") })
        assertTrue(executedSql.any { it.contains("PRIMARY KEY(`fromCity`, `toCity`, `flightDate`, `flightNo`, `depTime`, `cabinCode`)") })
        assertTrue(executedSql.any { it.contains("index_route_calendar_toCity_flightDate") })
        assertTrue(executedSql.any { it.contains("index_reminders_enabled_fired_triggerAtMillis") })
    }

    @Test
    fun networkUiState_precomputesFilteredRoutesAndChinaClockUsesShanghaiZone() {
        assertEquals("Asia/Shanghai", ChinaClock.ZONE.id)

        val r1 = NetworkRouteSummary("杭州", "萧山", "HGH", "成都天府", "天府", "TFU", emptyList(), true, 12, "2026-12-01", 1L)
        val r2 = NetworkRouteSummary("杭州", "萧山", "HGH", "成都双流", "双流", "CTU", emptyList(), true, 8, "2026-12-02", 1L)
        val r3 = NetworkRouteSummary("温州", "龙湾", "WNZ", "兰州", "中川", "LHW", emptyList(), true, 15, "2026-11-28", 1L)

        val state = NetworkUiState(
            allRoutes = listOf(r1, r2, r3),
            fromCityFilter = "杭州",
            toCityFilter = "成都天府"
        ).withRecomputedFilter()

        assertEquals(1, state.filteredRoutes.size)
        assertEquals("TFU", state.filteredRoutes.first().toIata)
    }
}
