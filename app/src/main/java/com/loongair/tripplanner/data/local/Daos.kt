package com.loongair.tripplanner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightRouteDao {
    @Query("SELECT COUNT(*) FROM flight_routes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<FlightRouteEntity>)

    @Query("DELETE FROM flight_routes WHERE fromCity = :fromCity AND toCity = :toCity")
    suspend fun deleteForRoute(fromCity: String, toCity: String)

    @Query("DELETE FROM flight_routes")
    suspend fun clearAll()

    @Query("SELECT * FROM flight_routes WHERE fromCity = :fromCity")
    suspend fun getOutboundFrom(fromCity: String): List<FlightRouteEntity>

    @Query("SELECT * FROM flight_routes WHERE toCity = :toCity")
    suspend fun getInboundTo(toCity: String): List<FlightRouteEntity>

    @Query("SELECT * FROM flight_routes WHERE fromCity = :fromCity AND toCity = :toCity")
    suspend fun getRoutes(fromCity: String, toCity: String): List<FlightRouteEntity>

    @Query("SELECT DISTINCT fromCity FROM flight_routes ORDER BY fromCity")
    suspend fun getAllOriginCities(): List<String>

    @Query("SELECT * FROM flight_routes ORDER BY fromCity ASC, toCity ASC, depTime ASC")
    suspend fun getAll(): List<FlightRouteEntity>

    @Query("SELECT DISTINCT toCity FROM flight_routes WHERE fromCity = :fromCity ORDER BY toCity")
    suspend fun getDestinationsFrom(fromCity: String): List<String>
}

@Dao
interface CalendarCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<CalendarCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(entries: List<CalendarCacheEntity>)

    @Query("SELECT * FROM route_calendar WHERE fromCity = :fromCity AND toCity = :toCity AND flightDate = :date LIMIT 1")
    suspend fun getStatus(fromCity: String, toCity: String, date: String): CalendarCacheEntity?

    @Query("SELECT * FROM route_calendar WHERE fromCity = :fromCity AND flightDate = :date")
    suspend fun getOutboundStatuses(fromCity: String, date: String): List<CalendarCacheEntity>

    @Query("SELECT * FROM route_calendar WHERE toCity = :toCity AND flightDate = :date")
    suspend fun getInboundStatuses(toCity: String, date: String): List<CalendarCacheEntity>

    @Query("SELECT * FROM route_calendar WHERE status = 1 AND flightDate >= :minDate ORDER BY flightDate ASC")
    suspend fun getAllAvailable(minDate: String): List<CalendarCacheEntity>

    @Query("SELECT * FROM route_calendar WHERE flightDate >= :minDate ORDER BY flightDate ASC")
    suspend fun getAllFromDate(minDate: String): List<CalendarCacheEntity>

    @Query("SELECT * FROM route_calendar WHERE fromCity = :fromCity AND toCity = :toCity AND flightDate >= :minDate ORDER BY flightDate ASC")
    suspend fun getAllForRoute(fromCity: String, toCity: String, minDate: String): List<CalendarCacheEntity>

    @Query("DELETE FROM route_calendar WHERE fromCity = :fromCity AND toCity = :toCity")
    suspend fun deleteForRoute(fromCity: String, toCity: String)

    @Query("DELETE FROM route_calendar WHERE flightDate < :minDate")
    suspend fun deleteExpired(minDate: String)

    @Query("DELETE FROM route_calendar")
    suspend fun clearAll()
}

@Dao
interface SeatSnapshotDao {
    companion object {
        const val VISIBLE_SNAPSHOTS = """SELECT s.* FROM seat_snapshots s WHERE NOT EXISTS (
            SELECT 1 FROM route_calendar c WHERE c.fromCity = s.fromCity AND c.toCity = s.toCity
            AND c.flightDate = s.flightDate AND c.status BETWEEN -4 AND 0 AND c.updatedAt > s.queriedAt)"""
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SeatSnapshotEntity>)

    @Query("DELETE FROM seat_snapshots WHERE fromCity = :fromCity AND toCity = :toCity")
    suspend fun deleteForRoute(fromCity: String, toCity: String)

    @Query("DELETE FROM seat_snapshots WHERE fromCity = :fromCity AND toCity = :toCity AND flightDate = :date")
    suspend fun deleteForRouteDate(fromCity: String, toCity: String, date: String)

    @Query("DELETE FROM seat_snapshots WHERE fromCity = :fromCity AND toCity = :toCity AND flightDate IN (:dates) AND queriedAt < :requestStartedAt")
    suspend fun deleteOlderForDates(fromCity: String, toCity: String, dates: List<String>, requestStartedAt: Long)

    @Query(VISIBLE_SNAPSHOTS + " AND s.fromCity = :fromCity AND s.toCity = :toCity AND s.flightDate = :date ORDER BY s.depTime ASC")
    suspend fun getForRouteDate(fromCity: String, toCity: String, date: String): List<SeatSnapshotEntity>

    @Query(VISIBLE_SNAPSHOTS + " AND s.fromCity = :fromCity AND s.toCity = :toCity AND s.flightDate >= :minDate ORDER BY s.flightDate ASC, s.depTime ASC")
    suspend fun getAllForRoute(fromCity: String, toCity: String, minDate: String): List<SeatSnapshotEntity>

    @Query("SELECT * FROM seat_snapshots")
    suspend fun getAll(): List<SeatSnapshotEntity>

    @Query("DELETE FROM seat_snapshots WHERE flightDate < :minDate")
    suspend fun deleteExpired(minDate: String)

    @Query("DELETE FROM seat_snapshots WHERE flightNo LIKE '%&%'")
    suspend fun deleteObfuscated()

    @Query("DELETE FROM seat_snapshots")
    suspend fun clearAll()
}

@Dao
interface TripTemplateDao {
    @Query("SELECT * FROM trip_templates ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TripTemplateEntity>>

    @Query("SELECT * FROM trip_templates ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<TripTemplateEntity>

    @Query("SELECT COUNT(*) FROM trip_templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TripTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<TripTemplateEntity>)

    @Update
    suspend fun update(template: TripTemplateEntity)

    @Delete
    suspend fun delete(template: TripTemplateEntity)
}

@Dao
interface TripPlanDao {
    @Query("SELECT * FROM trip_plans ORDER BY outboundDate ASC, createdAt DESC")
    fun observeAll(): Flow<List<TripPlanEntity>>

    @Query("SELECT * FROM trip_plans")
    suspend fun getAll(): List<TripPlanEntity>

    @Query("SELECT * FROM trip_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TripPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: TripPlanEntity): Long

    @Update
    suspend fun update(plan: TripPlanEntity)

    @Delete
    suspend fun delete(plan: TripPlanEntity)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE planId = :planId ORDER BY triggerAtMillis ASC")
    suspend fun getForPlan(planId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND fired = 0 AND triggerAtMillis > :nowMillis")
    suspend fun getPendingActive(nowMillis: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("UPDATE reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders WHERE planId = :planId")
    suspend fun deleteForPlan(planId: Long)
}

@Dao
interface OfficialRouteDao {
    @Query("SELECT * FROM official_routes ORDER BY fromCity ASC, toCity ASC")
    fun observeAll(): Flow<List<OfficialRouteEntity>>

    @Query("SELECT * FROM official_routes ORDER BY fromCity ASC, toCity ASC")
    suspend fun getAll(): List<OfficialRouteEntity>

    @Query("SELECT * FROM official_routes WHERE fromCity = :fromCity ORDER BY toCity ASC")
    suspend fun getByOrigin(fromCity: String): List<OfficialRouteEntity>

    @Query("SELECT * FROM official_routes WHERE fromCity = :fromCity AND toCity = :toCity LIMIT 1")
    suspend fun getOne(fromCity: String, toCity: String): OfficialRouteEntity?

    @Query("DELETE FROM official_routes WHERE fromCity = :fromCity")
    suspend fun deleteForOrigin(fromCity: String)

    @Query("DELETE FROM official_routes WHERE fromCity = :fromCity AND toCity = :toCity")
    suspend fun deleteOne(fromCity: String, toCity: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OfficialRouteEntity>)

    @Query("DELETE FROM official_routes")
    suspend fun clearAll()
}

@Dao
interface OfficialAirportDao {
    @Query("SELECT * FROM official_airports ORDER BY cityName ASC")
    suspend fun getAll(): List<OfficialAirportEntity>

    @Query("SELECT * FROM official_airports WHERE cityName = :cityName LIMIT 1")
    suspend fun getByCity(cityName: String): OfficialAirportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OfficialAirportEntity>)

    @Query("DELETE FROM official_airports WHERE cityName NOT IN (SELECT fromCity FROM official_routes UNION SELECT toCity FROM official_routes)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM official_airports")
    suspend fun clearAll()
}

@Dao
interface NetworkSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(sync: NetworkSyncEntity)

    @Query("SELECT * FROM network_sync WHERE fetchedAt >= :since")
    suspend fun since(since: Long): List<NetworkSyncEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM network_sync WHERE fromCity = :fromCity AND toCity = :toCity AND fetchedAt >= :since)")
    suspend fun hasFetched(fromCity: String, toCity: String, since: Long): Boolean

    @Query("DELETE FROM network_sync WHERE fromCity = :fromCity AND toCity = :toCity")
    suspend fun delete(fromCity: String, toCity: String)

    @Query("DELETE FROM network_sync")
    suspend fun clear()
}

@Dao
interface InvalidatedRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InvalidatedRouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<InvalidatedRouteEntity>)

    @Query("DELETE FROM invalidated_routes WHERE fromCity = :fromCity AND toCity = :toCity")
    suspend fun deleteOne(fromCity: String, toCity: String)

    @Query("SELECT * FROM invalidated_routes ORDER BY invalidatedAt DESC")
    suspend fun getAll(): List<InvalidatedRouteEntity>

    @Query("SELECT COUNT(*) FROM invalidated_routes")
    suspend fun count(): Int

    @Query("DELETE FROM invalidated_routes")
    suspend fun clearAll()
}
