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
        OfficialAirportEntity::class,
        InvalidatedRouteEntity::class,
        NetworkSyncEntity::class
    ],
    version = 7,
    exportSchema = true
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
    abstract fun invalidatedRouteDao(): InvalidatedRouteDao
    abstract fun networkSyncDao(): NetworkSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
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

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
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
                db.execSQL("DELETE FROM flight_routes WHERE season != 'official'")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_templates ADD COLUMN outDaysOfWeek TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v4 -> v5 无损迁移：
         * 1. 将 seat_snapshots 复合主键扩展为包含 cabinCode，防止同一航班多舱位余票互相覆盖；
         * 2. 为 route_calendar、seat_snapshots、reminders 补齐高频查询二级索引。
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `seat_snapshots_new` (
                        `fromCity` TEXT NOT NULL,
                        `toCity` TEXT NOT NULL,
                        `flightDate` TEXT NOT NULL,
                        `flightNo` TEXT NOT NULL,
                        `depTime` TEXT NOT NULL,
                        `arrTime` TEXT NOT NULL,
                        `aircraft` TEXT NOT NULL,
                        `stopover` TEXT NOT NULL,
                        `secCardLeft` INTEGER NOT NULL,
                        `normalLeft` INTEGER NOT NULL,
                        `redeemPrice` REAL NOT NULL,
                        `cabinCode` TEXT NOT NULL,
                        `queriedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`fromCity`, `toCity`, `flightDate`, `flightNo`, `depTime`, `cabinCode`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `seat_snapshots_new` (
                        `fromCity`, `toCity`, `flightDate`, `flightNo`, `depTime`,
                        `arrTime`, `aircraft`, `stopover`, `secCardLeft`, `normalLeft`,
                        `redeemPrice`, `cabinCode`, `queriedAt`
                    )
                    SELECT
                        `fromCity`, `toCity`, `flightDate`, `flightNo`, `depTime`,
                        `arrTime`, `aircraft`, `stopover`, `secCardLeft`, `normalLeft`,
                        `redeemPrice`, `cabinCode`, `queriedAt`
                    FROM `seat_snapshots`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `seat_snapshots`")
                db.execSQL("ALTER TABLE `seat_snapshots_new` RENAME TO `seat_snapshots`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_seat_snapshots_flightDate` ON `seat_snapshots` (`flightDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_calendar_toCity_flightDate` ON `route_calendar` (`toCity`, `flightDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_calendar_flightDate` ON `route_calendar` (`flightDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_enabled_fired_triggerAtMillis` ON `reminders` (`enabled`, `fired`, `triggerAtMillis`)")
            }
        }

        /**
         * v5 -> v6 无损迁移：
         * 1. 为 official_routes 增加 dataSource 列区分内置基准 ("bundled") 与实时官方校验 ("official")；
         * 2. 新增 invalidated_routes 墓碑表，确保被检测失效并自动剔除的内置航线不会在重启时复活。
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `official_routes` ADD COLUMN `dataSource` TEXT NOT NULL DEFAULT 'official'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `invalidated_routes` (
                        `fromCity` TEXT NOT NULL,
                        `toCity` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `invalidatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`fromCity`, `toCity`)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Old seed rows and live rows shared timestamps. Verify freshness on the next sync.
                db.execSQL("CREATE TABLE IF NOT EXISTS network_sync (fromCity TEXT NOT NULL, toCity TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(fromCity, toCity))")
                db.execSQL("UPDATE invalidated_routes SET reason = '当前畅飞卡不适用该航线' WHERE reason LIKE '%当前卡ID:%'")
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loongair_trip_planner.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
