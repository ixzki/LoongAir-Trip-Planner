package com.loongair.tripplanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FlightRouteEntity::class,
        CalendarCacheEntity::class,
        SeatSnapshotEntity::class,
        TripTemplateEntity::class,
        TripPlanEntity::class,
        ReminderEntity::class,
        OfficialRouteEntity::class,
        OfficialAirportEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flightRouteDao(): FlightRouteDao
    abstract fun calendarCacheDao(): CalendarCacheDao
    abstract fun seatSnapshotDao(): SeatSnapshotDao
    abstract fun tripTemplateDao(): TripTemplateDao
    abstract fun tripPlanDao(): TripPlanDao
    abstract fun reminderDao(): ReminderDao
    abstract fun officialRouteDao(): OfficialRouteDao
    abstract fun officialAirportDao(): OfficialAirportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS official_routes (
                        fromCity TEXT NOT NULL,
                        fromAirport TEXT NOT NULL,
                        fromIata TEXT NOT NULL,
                        toCity TEXT NOT NULL,
                        toAirport TEXT NOT NULL,
                        toIata TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(fromCity, toCity)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS official_airports (
                        cityName TEXT NOT NULL,
                        airportName TEXT NOT NULL,
                        airportCode TEXT NOT NULL,
                        pinyin TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(cityName)
                    )
                    """.trimIndent()
                )
                // 清理旧版本预置的本地种子航班，确保只保留登录并同步后的官方真实航线
                db.execSQL("DELETE FROM flight_routes WHERE season != 'official'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_templates ADD COLUMN outDaysOfWeek TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loongair_trip_planner.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
