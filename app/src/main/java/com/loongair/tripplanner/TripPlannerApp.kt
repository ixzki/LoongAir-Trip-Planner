package com.loongair.tripplanner

import android.app.Application
import com.loongair.tripplanner.alarm.NotificationHelper
import com.loongair.tripplanner.alarm.ReminderScheduler
import com.loongair.tripplanner.data.local.AppDatabase
import com.loongair.tripplanner.data.local.AuthPrefs
import com.loongair.tripplanner.data.remote.LoongAirApi
import com.loongair.tripplanner.data.remote.SessionKeepaliveScheduler
import com.loongair.tripplanner.data.repository.FlightRepository

class TripPlannerApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var authPrefs: AuthPrefs
        private set
    lateinit var api: LoongAirApi
        private set
    lateinit var repository: FlightRepository
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        database = AppDatabase.getInstance(this)
        authPrefs = AuthPrefs(this)
        api = LoongAirApi(authPrefs)
        repository = FlightRepository(this, database, authPrefs, api)
        reminderScheduler = ReminderScheduler(this)
        SessionKeepaliveScheduler.sync(this, authPrefs.current().isLoggedIn)
    }
}
