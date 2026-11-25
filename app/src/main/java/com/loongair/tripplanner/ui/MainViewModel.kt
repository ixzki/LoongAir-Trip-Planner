package com.loongair.tripplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loongair.tripplanner.TripPlannerApp
import com.loongair.tripplanner.alarm.NotificationHelper
import com.loongair.tripplanner.data.local.OfficialAirportEntity
import com.loongair.tripplanner.data.local.ReminderEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.local.TripTemplateEntity
import com.loongair.tripplanner.data.local.UserSessionState
import com.loongair.tripplanner.data.remote.LoginResult
import com.loongair.tripplanner.data.repository.FlightCandidate
import com.loongair.tripplanner.data.repository.NetworkRouteSummary
import com.loongair.tripplanner.data.repository.RoundTripCandidate
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.data.repository.RoundTripQuery
import com.loongair.tripplanner.util.ChinaClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TripPlannerApp
    private val repo = app.repository
    val scheduler = app.reminderScheduler

    val sessionState: StateFlow<UserSessionState> = repo.authPrefs.state

    val templates: StateFlow<List<TripTemplateEntity>> = repo.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plans: StateFlow<List<TripPlanEntity>> = repo.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repo.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _networkState = MutableStateFlow(NetworkUiState())
    val networkState: StateFlow<NetworkUiState> = _networkState.asStateFlow()

    private val _routeDetailState = MutableStateFlow(RouteCalendarDetailUiState())
    val routeDetailState: StateFlow<RouteCalendarDetailUiState> = _routeDetailState.asStateFlow()

    private val _searchState = MutableStateFlow(
        SearchUiState(originCity = repo.authPrefs.current().homeCity)
    )
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _recheckingPlanId = MutableStateFlow<Long?>(null)
    val recheckingPlanId: StateFlow<Long?> = _recheckingPlanId.asStateFlow()

    private val _highlightedPlanId = MutableStateFlow<Long?>(null)
    val highlightedPlanId: StateFlow<Long?> = _highlightedPlanId.asStateFlow()

    private val _officialDestLoading = MutableStateFlow(false)
    val officialDestLoading: StateFlow<Boolean> = _officialDestLoading.asStateFlow()

    private val _cardSyncLoading = MutableStateFlow(false)
    val cardSyncLoading: StateFlow<Boolean> = _cardSyncLoading.asStateFlow()

    private val _officialAirports = MutableStateFlow<List<OfficialAirportEntity>>(emptyList())
    val officialAirports: StateFlow<List<OfficialAirportEntity>> = _officialAirports.asStateFlow()

    private val _departureAirportsLoading = MutableStateFlow(false)
    val departureAirportsLoading: StateFlow<Boolean> = _departureAirportsLoading.asStateFlow()

    private var batchJob: Job? = null
    private var officialSyncJob: Job? = null
    private var calibrateJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repo.ensureInitialized()
            val provinces = repo.getChinaProvinces()
            _networkState.update { it.copy(provinces = provinces) }
            reloadAllCityAndRouteData(initialLoad = true)
            if (sessionState.value.isLoggedIn && _officialAirports.value.isEmpty()) {
                refreshOfficialDepartureAirports(silent = true)
            }
        }
    }

    fun readinessOf(session: UserSessionState, net: NetworkUiState): AppReadiness = when {
        net.hasSyncedData -> AppReadiness.READY
        session.isLoggedIn -> AppReadiness.NEEDS_FIRST_CITY_SYNC
        else -> AppReadiness.NEEDS_LOGIN
    }

    private suspend fun reloadAllCityAndRouteData(initialLoad: Boolean = false) {
        val metas = repo.getSyncedCityMetadata()
        val airports = repo.getOfficialAirports()
        val origins = repo.getAllOriginCities()
        val allNetwork = repo.getAllNetworkRoutes()
        val officialRoutes = repo.getOfficialRoutes()

        // 首次登录同步前或清空缓存后，不保留任何旧常驻城市
        if (allNetwork.isEmpty() && officialRoutes.isEmpty()) {
            if (repo.authPrefs.current().homeCity.isNotBlank()) {
                repo.authPrefs.setHomeCity("")
            }
        }

        val homeCity = repo.authPrefs.current().homeCity.let { hc ->
            when {
                origins.isEmpty() -> ""
                hc in origins -> hc
                else -> origins.first()
            }
        }
        val effectiveOrigin = when {
            origins.isEmpty() -> ""
            initialLoad -> homeCity
            _searchState.value.originCity in origins -> _searchState.value.originCity
            else -> homeCity
        }
        val dests = repo.getDestinationsFrom(effectiveOrigin)
        val allNetworkOrigins = allNetwork.map { it.fromCity }.distinct().sorted()
        val allNetworkDests = allNetwork.map { it.toCity }.distinct().sorted()
        val lastSyncAt = officialRoutes.maxOfOrNull { it.updatedAt }
        val officialHomeDests = if (homeCity.isBlank()) {
            null
        } else {
            officialRoutes
                .filter { it.fromCity == homeCity }
                .map { it.toCity }
                .distinct()
                .sorted()
                .takeIf { it.isNotEmpty() }
        }

        _officialAirports.value = airports
        _networkState.update { st ->
            st.copy(
                allRoutes = allNetwork,
                cityMetaList = metas,
                allOriginCities = allNetworkOrigins,
                allDestCities = allNetworkDests,
                fromCityFilter = if (st.fromCityFilter in allNetworkOrigins) st.fromCityFilter else "",
                toCityFilter = if (st.toCityFilter in allNetworkDests) st.toCityFilter else "",
                officialSyncedCount = officialRoutes.size,
                lastOfficialSyncAt = lastSyncAt,
                isLoading = false
            ).withRecomputedFilter()
        }
        _searchState.update { st ->
            st.copy(
                originCity = effectiveOrigin,
                destinationFilter = if (st.destinationFilter in dests) st.destinationFilter else "",
                allOriginCities = origins,
                reachableDestinations = dests,
                cityMetaList = metas,
                officialAllowedDestinations = officialHomeDests
            )
        }
        runSearch()
    }

    /**
     * 单条航线增量更新：在日历刷新、单日查余票或单条班期校准后，仅更新该航线摘要并在必要时局部触发往返搜索。
     */
    private suspend fun refreshSingleRouteInState(
        fromCity: String,
        toCity: String,
        triggerSearchIfRelevant: Boolean = true
    ): NetworkRouteSummary? {
        val updated = repo.getSingleNetworkRouteSummary(fromCity, toCity) ?: return null
        _networkState.update { st ->
            val idx = st.allRoutes.indexOfFirst { it.fromCity == fromCity && it.toCity == toCity }
            val nextList = if (idx >= 0) {
                st.allRoutes.toMutableList().apply { this[idx] = updated }
            } else {
                st.allRoutes + updated
            }
            st.copy(allRoutes = nextList).withRecomputedFilter()
        }
        if (triggerSearchIfRelevant) {
            val curOrigin = _searchState.value.originCity
            if (curOrigin.isNotBlank() && (curOrigin == fromCity || curOrigin == toCity)) {
                runSearch()
            }
        }
        return updated
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun highlightPlan(planId: Long?) {
        _highlightedPlanId.value = planId
    }

    // ---- 第 1 栏：航线大盘控制 ----

    fun setNetworkFilter(fromCity: String? = null, toCity: String? = null, onlyOfficial: Boolean? = null) {
        _networkState.update { st ->
            val nextFrom = fromCity ?: st.fromCityFilter
            var nextTo = toCity ?: st.toCityFilter
            if (nextFrom.isNotBlank() && nextFrom == nextTo) {
                nextTo = ""
            }
            st.copy(
                fromCityFilter = nextFrom,
                toCityFilter = nextTo,
                onlyOfficialSynced = onlyOfficial ?: st.onlyOfficialSynced
            ).withRecomputedFilter()
        }
    }

    fun swapNetworkCities() {
        _networkState.update { st ->
            st.copy(
                fromCityFilter = st.toCityFilter,
                toCityFilter = st.fromCityFilter
            ).withRecomputedFilter()
        }
    }

    fun refreshNetworkRouteCalendar(fromCity: String, toCity: String) {
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先在「我的」页面登录长龙会员后再刷新放票日历")
            return
        }
        val key = "$fromCity|$toCity"
        viewModelScope.launch {
            _networkState.update { it.copy(refreshingRouteKey = key) }
            try {
                val cal = repo.refreshRouteCalendar(fromCity, toCity)
                val today = ChinaClock.today()
                val todayStr = today.toString()
                val maxDateStr = today.plusDays(39).toString()
                val avail = cal.count { (d, v) -> d in todayStr..maxDateStr && v == 1 }
                refreshSingleRouteInState(fromCity, toCity)
                showMessage("$fromCity → $toCity 日历已刷新：近40天共有 $avail 天可兑换")
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                showMessage("刷新日历失败：${e.message}")
            } finally {
                _networkState.update { it.copy(refreshingRouteKey = null) }
            }
        }
    }

    fun openRouteCalendarDetail(route: NetworkRouteSummary) {
        viewModelScope.launch {
            val cal = repo.getRouteCalendarMap(route.fromCity, route.toCity)
            val seats = repo.getRouteSeatSnapshotsByDate(route.fromCity, route.toCity)
            val today = ChinaClock.today()
            val todayStr = today.toString()
            val maxDateStr = today.plusDays(39).toString()
            val recentCal = cal.filterKeys { it in todayStr..maxDateStr }
            val defaultDate = route.nearestAvailableDate?.takeIf { it in todayStr..maxDateStr }
                ?: recentCal.entries.firstOrNull { it.value == 1 }?.key
                ?: recentCal.entries.firstOrNull { it.value == 0 }?.key
                ?: today.plusDays(3).toString()
            _routeDetailState.value = RouteCalendarDetailUiState(
                route = route,
                calendarByDate = cal,
                seatsByDate = seats,
                selectedDate = defaultDate,
                isRefreshingCalendar = false,
                queryingSeatsDate = null
            )
            if (cal.isEmpty() && sessionState.value.isLoggedIn) {
                refreshOpenedRouteCalendar()
            }
        }
    }

    fun closeRouteCalendarDetail() {
        calibrateJob?.cancel()
        calibrateJob = null
        _routeDetailState.value = RouteCalendarDetailUiState()
    }

    fun refreshOpenedRouteCalendar() {
        val currentRoute = _routeDetailState.value.route ?: return
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先在「我的」页登录长龙会员后再刷新放票日历")
            return
        }
        viewModelScope.launch {
            _routeDetailState.update { it.copy(isRefreshingCalendar = true) }
            try {
                repo.refreshRouteCalendar(currentRoute.fromCity, currentRoute.toCity)
                val cal = repo.getRouteCalendarMap(currentRoute.fromCity, currentRoute.toCity)
                val updatedSummary = refreshSingleRouteInState(currentRoute.fromCity, currentRoute.toCity)
                    ?: currentRoute
                val today = ChinaClock.today()
                val todayStr = today.toString()
                val maxDateStr = today.plusDays(39).toString()
                val avail = cal.count { (d, v) -> d in todayStr..maxDateStr && v == 1 }
                _routeDetailState.update { st ->
                    st.copy(
                        route = updatedSummary,
                        calendarByDate = cal,
                        selectedDate = st.selectedDate
                            ?: updatedSummary.nearestAvailableDate?.takeIf { it in todayStr..maxDateStr }
                    )
                }
                showMessage("${currentRoute.fromCity} → ${currentRoute.toCity} 近40天日历已更新：共 $avail 天可兑")
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                showMessage("刷新日历失败：${e.message}")
            } finally {
                _routeDetailState.update { it.copy(isRefreshingCalendar = false) }
            }
        }
    }

    fun selectRouteDetailDate(dateStr: String, autoQuerySeats: Boolean = true) {
        val st = _routeDetailState.value
        if (st.route == null) return
        _routeDetailState.update { it.copy(selectedDate = dateStr) }
        val status = st.calendarByDate[dateStr]
        val hasCached = !st.seatsByDate[dateStr].isNullOrEmpty()
        if (autoQuerySeats && sessionState.value.isLoggedIn && !hasCached && status != -4 && status != -2) {
            queryRouteDetailSeats(dateStr)
        } else if (autoQuerySeats && !sessionState.value.isLoggedIn && !hasCached && status != -4 && status != -2) {
            showMessage("登录长龙会员后点击日期即可实时查询具体航班余票")
        }
    }

    fun queryRouteDetailSeats(dateStr: String) {
        val currentRoute = _routeDetailState.value.route ?: return
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先在「我的」页登录长龙会员后再查实时余票")
            return
        }
        viewModelScope.launch {
            _routeDetailState.update { it.copy(selectedDate = dateStr, queryingSeatsDate = dateStr) }
            try {
                val live = repo.refreshRouteSeats(currentRoute.fromCity, currentRoute.toCity, dateStr)
                val allSeats = repo.getRouteSeatSnapshotsByDate(currentRoute.fromCity, currentRoute.toCity)
                val updatedSummary = refreshSingleRouteInState(currentRoute.fromCity, currentRoute.toCity)
                    ?: currentRoute
                _routeDetailState.update { it.copy(route = updatedSummary, seatsByDate = allSeats) }
                val totalLeft = live.sumOf { it.secCardLeft }
                if (live.isEmpty()) {
                    showMessage("$dateStr 当日暂无可查询的畅飞卡班次")
                } else {
                    showMessage("$dateStr 实时余票已更新：${live.size} 个班次/舱位，畅飞卡合计余 $totalLeft 张")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                showMessage("查询当日余票失败：${e.message}")
            } finally {
                _routeDetailState.update {
                    if (it.queryingSeatsDate == dateStr) it.copy(queryingSeatsDate = null) else it
                }
            }
        }
    }

    fun calibrateOpenedRouteWeeklySchedule() {
        val currentRoute = _routeDetailState.value.route ?: return
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先在「我的」页登录长龙会员后再校准每周真实班期")
            return
        }
        if (calibrateJob?.isActive == true || _routeDetailState.value.isCalibratingSchedule) return
        calibrateJob = viewModelScope.launch {
            _routeDetailState.update { it.copy(isCalibratingSchedule = true) }
            try {
                val updatedFlights = repo.calibrateRouteWeeklySchedule(currentRoute.fromCity, currentRoute.toCity)
                val cal = repo.getRouteCalendarMap(currentRoute.fromCity, currentRoute.toCity)
                val seats = repo.getRouteSeatSnapshotsByDate(currentRoute.fromCity, currentRoute.toCity)
                val updatedSummary = refreshSingleRouteInState(currentRoute.fromCity, currentRoute.toCity)
                    ?: currentRoute.copy(flights = updatedFlights)
                _routeDetailState.update {
                    it.copy(
                        route = updatedSummary,
                        calendarByDate = cal,
                        seatsByDate = seats
                    )
                }
                showMessage(
                    "${currentRoute.fromCity} → ${currentRoute.toCity} 周一至周日班期已精确校准：共 ${updatedFlights.size} 个航班"
                )
            } catch (_: CancellationException) {
                showMessage("已取消班期校准")
            } catch (e: Exception) {
                showMessage("校准每周班期失败：${e.message}")
            } finally {
                _routeDetailState.update { it.copy(isCalibratingSchedule = false) }
            }
        }
    }

    fun jumpToRoundTripSearch(fromCity: String, toCity: String) {
        closeRouteCalendarDetail()
        viewModelScope.launch {
            val dests = repo.getDestinationsFrom(fromCity)
            _searchState.update {
                it.copy(
                    originCity = fromCity,
                    destinationFilter = if (toCity in dests) toCity else "",
                    reachableDestinations = dests
                )
            }
            runSearch()
        }
    }

    // ---- 第 2 栏：往返搜索控制 ----

    fun setOriginCity(city: String) {
        viewModelScope.launch {
            val dests = repo.getDestinationsFrom(city)
            _searchState.update {
                it.copy(
                    originCity = city,
                    destinationFilter = if (it.destinationFilter in dests) it.destinationFilter else "",
                    reachableDestinations = dests
                )
            }
            runSearch()
        }
    }

    fun setHomeCity(city: String) {
        repo.authPrefs.setHomeCity(city)
        setOriginCity(city)
        showMessage("已将常驻出发城市设为「$city」")
    }

    fun setThemeStyle(styleKey: String) {
        repo.authPrefs.setThemeStyle(styleKey)
    }

    fun setDestinationFilter(dest: String) {
        _searchState.update { it.copy(destinationFilter = dest) }
        runSearch()
    }

    fun applyTemplate(template: TripTemplateEntity) {
        val pairs = RoundTripMatcher.resolveTemplateDatePairs(template)
        val (firstOut, firstRet) = pairs.first()
        val daysLabel = RoundTripMatcher.formatTemplateOutDaysLabel(template)
        val multiLabel = if (pairs.size > 1) {
            if (RoundTripMatcher.isAnyDayTemplate(template)) {
                "任意日去 · 停留${template.retOffsetDays}天（共${pairs.size}天）"
            } else {
                "$daysLabel 去 · 停留${template.retOffsetDays}天（共${pairs.size}天）"
            }
        } else ""
        _searchState.update {
            it.copy(
                outboundDate = firstOut,
                outStartMinute = template.outStartMinute,
                outEndMinute = template.outEndMinute,
                returnDate = firstRet,
                retStartMinute = template.retStartMinute,
                retEndMinute = template.retEndMinute,
                multiDatePairs = if (pairs.size > 1) pairs else emptyList(),
                multiDateLabel = multiLabel,
                directOnly = template.directOnly,
                activeTemplateId = template.id
            )
        }
        runSearch()
    }

    fun clearMultiDateMode() {
        _searchState.update {
            it.copy(
                multiDatePairs = emptyList(),
                multiDateLabel = "",
                activeTemplateId = null
            )
        }
        runSearch()
    }

    fun shiftDatesByWeeks(deltaWeeks: Int) {
        _searchState.update { st ->
            val newOut = st.outboundDate.plusWeeks(deltaWeeks.toLong())
            val newRet = st.returnDate.plusWeeks(deltaWeeks.toLong())
            if (newOut.isBefore(ChinaClock.today())) {
                showMessage("不能选择今天之前的出发日期")
                st
            } else {
                val shiftedMulti = st.multiDatePairs.map { (o, r) ->
                    o.plusWeeks(deltaWeeks.toLong()) to r.plusWeeks(deltaWeeks.toLong())
                }
                st.copy(
                    outboundDate = newOut,
                    returnDate = newRet,
                    multiDatePairs = shiftedMulti
                )
            }
        }
        runSearch()
    }

    fun updateDatesAndWindows(
        outboundDate: LocalDate? = null,
        returnDate: LocalDate? = null,
        outStartMinute: Int? = null,
        outEndMinute: Int? = null,
        retStartMinute: Int? = null,
        retEndMinute: Int? = null,
        directOnly: Boolean? = null,
        onlyCalendarAvailable: Boolean? = null
    ) {
        _searchState.update { st ->
            val nextOut = outboundDate ?: st.outboundDate
            var nextRet = returnDate ?: st.returnDate
            if (nextRet.isBefore(nextOut)) {
                nextRet = nextOut
            }
            val changedSingleDate = outboundDate != null || returnDate != null
            st.copy(
                outboundDate = nextOut,
                returnDate = nextRet,
                outStartMinute = outStartMinute ?: st.outStartMinute,
                outEndMinute = outEndMinute ?: st.outEndMinute,
                retStartMinute = retStartMinute ?: st.retStartMinute,
                retEndMinute = retEndMinute ?: st.retEndMinute,
                multiDatePairs = if (changedSingleDate) emptyList() else st.multiDatePairs,
                multiDateLabel = if (changedSingleDate) "" else st.multiDateLabel,
                directOnly = directOnly ?: st.directOnly,
                onlyCalendarAvailable = onlyCalendarAvailable ?: st.onlyCalendarAvailable,
                activeTemplateId = if (changedSingleDate ||
                    outStartMinute != null || outEndMinute != null ||
                    retStartMinute != null || retEndMinute != null
                ) null else st.activeTemplateId
            )
        }
        runSearch()
    }

    fun runSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val st = _searchState.value
            _searchState.update { it.copy(isLoading = true) }
            val results = repo.searchRoundTrips(
                RoundTripQuery(
                    originCity = st.originCity,
                    destinationFilter = st.destinationFilter,
                    outboundDate = st.outboundDate,
                    outStartMinute = st.outStartMinute,
                    outEndMinute = st.outEndMinute,
                    returnDate = st.returnDate,
                    retStartMinute = st.retStartMinute,
                    retEndMinute = st.retEndMinute,
                    directOnly = st.directOnly,
                    onlyCalendarAvailable = st.onlyCalendarAvailable,
                    datePairs = st.multiDatePairs
                )
            )
            _searchState.update { it.copy(results = results, isLoading = false) }
        }
    }

    fun saveTemplate(template: TripTemplateEntity) {
        viewModelScope.launch {
            repo.saveTemplate(template)
            showMessage("已保存往返模板「${template.name}」")
        }
    }

    fun deleteTemplate(template: TripTemplateEntity) {
        viewModelScope.launch {
            repo.deleteTemplate(template)
            if (_searchState.value.activeTemplateId == template.id) {
                _searchState.update { it.copy(activeTemplateId = null) }
            }
            showMessage("已删除模板「${template.name}」")
        }
    }

    fun resetDefaultTemplates() {
        viewModelScope.launch {
            repo.restoreDefaultTemplates()
            showMessage("已恢复默认往返模板（已自动去重）")
        }
    }

    fun verifyCandidateLiveSeats(candidate: RoundTripCandidate) {
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先在「我的」页登录长龙会员后再查实时余票")
            return
        }
        val candKey = "${candidate.destinationCity}|${candidate.outboundDate}|${candidate.returnDate}"
        viewModelScope.launch {
            _searchState.update { it.copy(verifyingDestination = candKey) }
            try {
                val (outSeats, retSeats) = repo.refreshRoundTripSeats(
                    originCity = candidate.originCity,
                    destCity = candidate.destinationCity,
                    outDateStr = candidate.outboundDate.toString(),
                    retDateStr = candidate.returnDate.toString()
                )
                val outAvail = outSeats.sumOf { it.secCardLeft }
                val retAvail = retSeats.sumOf { it.secCardLeft }
                runSearch()
                showMessage(
                    "${candidate.originCity} ⇄ ${candidate.destinationCity} 实时余票已更新：去程合计余 $outAvail 张，返程合计余 $retAvail 张"
                )
            } catch (e: Exception) {
                showMessage("查票失败：${e.message}")
            } finally {
                _searchState.update { it.copy(verifyingDestination = null) }
            }
        }
    }

    fun startBatchCalendarVerify() {
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先在「我的」页登录长龙会员后再批量核验放票日历")
            return
        }
        if (batchJob?.isActive == true) return
        val targets = _searchState.value.results.distinctBy { it.originCity to it.destinationCity }
        if (targets.isEmpty()) {
            showMessage("当前没有可核验的往返候选航线")
            return
        }

        batchJob = viewModelScope.launch {
            _searchState.update {
                it.copy(batchScanning = true, batchProgressText = "准备核验 0/${targets.size} 个目的地...")
            }
            var done = 0
            try {
                for (cand in targets) {
                    _searchState.update {
                        it.copy(batchProgressText = "核验中 ($done/${targets.size})：${cand.originCity} ⇄ ${cand.destinationCity}")
                    }
                    repo.refreshRouteCalendar(cand.originCity, cand.destinationCity)
                    delay(Random.nextLong(900, 1500))
                    repo.refreshRouteCalendar(cand.destinationCity, cand.originCity)
                    done++
                    refreshSingleRouteInState(cand.originCity, cand.destinationCity, triggerSearchIfRelevant = false)
                    refreshSingleRouteInState(cand.destinationCity, cand.originCity, triggerSearchIfRelevant = true)
                    if (done < targets.size) {
                        delay(Random.nextLong(1200, 2000))
                    }
                }
                showMessage("已完成 $done 个目的地的往返放票日历核验")
            } catch (_: CancellationException) {
                showMessage("已停止批量核验（已完成 $done/${targets.size}）")
            } catch (e: Exception) {
                showMessage("批量核验中断：${e.message}")
            } finally {
                _searchState.update { it.copy(batchScanning = false, batchProgressText = "") }
            }
        }
    }

    fun stopBatchCalendarVerify() {
        batchJob?.cancel()
        batchJob = null
    }

    // ---- 第 3 栏：行程计划与抢票提醒 ----

    fun saveTripPlan(
        candidate: RoundTripCandidate,
        outFlight: FlightCandidate,
        retFlight: FlightCandidate,
        note: String,
        onSaved: (TripPlanEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val plan = TripPlanEntity(
                fromCity = candidate.originCity,
                toCity = candidate.destinationCity,
                outboundDate = candidate.outboundDate.toString(),
                outboundFlightNo = outFlight.flightNo,
                outboundDepTime = outFlight.depTime,
                outboundArrTime = outFlight.arrTime,
                outboundStopover = outFlight.stopover,
                outboundLeftCount = outFlight.liveSecCardLeft,
                returnDate = candidate.returnDate.toString(),
                returnFlightNo = retFlight.flightNo,
                returnDepTime = retFlight.depTime,
                returnArrTime = retFlight.arrTime,
                returnStopover = retFlight.stopover,
                returnLeftCount = retFlight.liveSecCardLeft,
                note = note.trim(),
                lastVerifiedAt = outFlight.liveQueriedAt ?: retFlight.liveQueriedAt
            )
            val newId = repo.savePlan(plan)
            val persisted = plan.copy(id = newId)
            showMessage("已保存行程计划：${plan.fromCity} ⇄ ${plan.toCity}")
            onSaved(persisted)
        }
    }

    fun deleteTripPlan(plan: TripPlanEntity) {
        viewModelScope.launch {
            val linked = repo.getRemindersForPlan(plan.id)
            linked.forEach { scheduler.cancel(it.id) }
            repo.deletePlan(plan)
            showMessage("已删除行程计划及关联提醒")
        }
    }

    fun recheckPlanLiveSeats(plan: TripPlanEntity) {
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先在「我的」页登录长龙会员后再核验余票")
            return
        }
        viewModelScope.launch {
            _recheckingPlanId.value = plan.id
            try {
                val (outSeats, retSeats) = repo.refreshRoundTripSeats(
                    originCity = plan.fromCity,
                    destCity = plan.toCity,
                    outDateStr = plan.outboundDate,
                    retDateStr = plan.returnDate
                )
                val matchedOutLeft = RoundTripMatcher.matchPlannedFlightSecLeft(
                    seats = outSeats,
                    targetFlightNo = plan.outboundFlightNo,
                    targetDepTime = plan.outboundDepTime
                )
                val matchedRetLeft = RoundTripMatcher.matchPlannedFlightSecLeft(
                    seats = retSeats,
                    targetFlightNo = plan.returnFlightNo,
                    targetDepTime = plan.returnDepTime
                )
                val outLeft = matchedOutLeft ?: 0
                val retLeft = matchedRetLeft ?: 0

                repo.updatePlan(
                    plan.copy(
                        outboundLeftCount = outLeft,
                        returnLeftCount = retLeft,
                        lastVerifiedAt = System.currentTimeMillis()
                    )
                )
                val outDesc = if (matchedOutLeft != null) "去程 ${plan.outboundFlightNo} 余 $outLeft 张" else "去程 ${plan.outboundFlightNo} 当日未查到该班次"
                val retDesc = if (matchedRetLeft != null) "返程 ${plan.returnFlightNo} 余 $retLeft 张" else "返程 ${plan.returnFlightNo} 当日未查到该班次"
                showMessage("最新余票：$outDesc，$retDesc")
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                showMessage("复查余票失败：${e.message}")
            } finally {
                _recheckingPlanId.value = null
            }
        }
    }

    fun addReminder(
        planId: Long,
        triggerAtMillis: Long,
        label: String,
        title: String,
        message: String
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            showMessage("提醒时间必须晚于当前时间")
            return
        }
        viewModelScope.launch {
            val entity = ReminderEntity(
                planId = planId,
                triggerAtMillis = triggerAtMillis,
                title = title,
                message = message,
                label = label,
                enabled = true,
                fired = false
            )
            val id = repo.saveReminder(entity)
            scheduler.schedule(entity.copy(id = id))
            showMessage("已设定抢票提醒：$label")
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            val updated = reminder.copy(enabled = !reminder.enabled, fired = false)
            repo.updateReminder(updated)
            if (updated.enabled) {
                scheduler.schedule(updated)
                showMessage("已开启提醒：${updated.label}")
            } else {
                scheduler.cancel(updated.id)
                showMessage("已关闭提醒：${updated.label}")
            }
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            scheduler.cancel(reminder.id)
            repo.deleteReminder(reminder.id)
            showMessage("已删除提醒")
        }
    }

    fun sendTestNotification() {
        NotificationHelper.showReminderNotification(
            context = app,
            reminderId = 999999L,
            planId = plans.value.firstOrNull()?.id ?: -1L,
            title = "⏰ 长龙畅飞卡测试提醒",
            message = "通知权限正常！抢票闹钟到点时会以同样的高优先级提醒唤醒你。"
        )
        showMessage("已发送一条测试通知，请查看手机顶部状态栏")
    }

    // ---- 第 4 栏：我的（账号、卡包、官方航线拉取与设置） ----

    suspend fun sendSmsCode(mobile: String, yidunValidate: String) =
        repo.api.sendSmsCode(mobile, yidunValidate)

    suspend fun loginBySmsCode(mobile: String, code: String, verifyId: String): LoginResult {
        val res = repo.api.loginBySmsCode(mobile, code, verifyId)
        if (res.ok) {
            refreshOfficialDepartureAirports(silent = true)
        }
        return res
    }

    suspend fun importManualCredentials(sessionToken: String, scgTid: String): LoginResult {
        val res = repo.api.importManualCredentials(sessionToken, scgTid)
        if (res.ok) {
            refreshOfficialDepartureAirports(silent = true)
        }
        return res
    }

    fun refreshOfficialDepartureAirports(silent: Boolean = false) {
        if (!sessionState.value.isLoggedIn) {
            if (!silent) showMessage("请先登录长龙会员后再获取官方出发城市")
            return
        }
        viewModelScope.launch {
            _departureAirportsLoading.value = true
            try {
                val list = repo.syncOfficialDepartureAirports()
                val all = repo.getOfficialAirports()
                _officialAirports.value = all
                if (!silent) {
                    if (list.isNotEmpty()) {
                        showMessage("已获取 ${list.size} 座长龙官方出发城市")
                    } else {
                        showMessage("未拉取到出发城市目录，可直接手动输入城市名或机场三字码同步")
                    }
                }
            } catch (e: Exception) {
                if (!silent) showMessage("获取出发城市失败：${e.message}")
            } finally {
                _departureAirportsLoading.value = false
            }
        }
    }

    fun resyncUserCards() {
        viewModelScope.launch {
            _cardSyncLoading.value = true
            try {
                val res = repo.api.resyncUserCards()
                showMessage(res.message)
                if (res.ok) {
                    refreshOfficialDepartureAirports(silent = true)
                }
            } catch (e: Exception) {
                showMessage("同步卡包失败：${e.message}")
            } finally {
                _cardSyncLoading.value = false
            }
        }
    }

    fun selectActiveCard(grantTid: String, cardName: String) {
        val cleanTid = grantTid.trim()
        val prevTid = sessionState.value.secCardGrantTid.trim()
        repo.authPrefs.selectActiveCard(cleanTid, cardName)
        if (prevTid.isNotBlank() && prevTid != cleanTid) {
            cancelAllBackgroundJobs()
            viewModelScope.launch {
                repo.clearCardSpecificCaches()
                reloadAllCityAndRouteData(initialLoad = true)
                showMessage("已切换畅飞卡：$cardName，旧卡航线与日历缓存已清理，请同步新卡适用航线")
            }
        } else {
            showMessage("当前畅飞卡：$cardName ($cleanTid)")
        }
    }

    fun updateScgTidManually(newTid: String) {
        val clean = newTid.trim()
        if (clean.isEmpty()) {
            showMessage("授权 ID 不能为空")
            return
        }
        val prevTid = sessionState.value.secCardGrantTid.trim()
        repo.authPrefs.selectActiveCard(clean, sessionState.value.cardName)
        if (prevTid.isNotBlank() && prevTid != clean) {
            cancelAllBackgroundJobs()
            viewModelScope.launch {
                repo.clearCardSpecificCaches()
                reloadAllCityAndRouteData(initialLoad = true)
                showMessage("已更新畅飞卡授权 ID 并清理旧卡缓存，请重新同步适用航线")
            }
        } else {
            showMessage("已确认畅飞卡授权 ID：$clean")
        }
    }

    /**
     * 优先同步「一个城市的所有航线」（直连长龙官方接口，同步全部可达目的地 + 去返 60 天放票日历与真实航班时刻）
     */
    fun syncSingleCityAllRoutes(rawCityOrCode: String) {
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先登录长龙会员后再同步城市航线")
            return
        }
        val clean = rawCityOrCode.trim()
        if (clean.isBlank()) {
            showMessage("请先选择或输入一个出发城市（中文城市名或机场三字码）")
            return
        }
        if (officialSyncJob?.isActive == true) {
            showMessage("正在同步航线中，请稍候或先停止当前同步")
            return
        }

        val wasEmptyBefore = !_networkState.value.hasSyncedData
        officialSyncJob = viewModelScope.launch {
            _officialDestLoading.value = true
            _networkState.update {
                it.copy(
                    officialSyncing = true,
                    syncingCityName = clean,
                    syncDoneCount = 0,
                    syncTotalCount = 0,
                    officialSyncProgress = "正在拉取「$clean」官方可达目的地..."
                )
            }
            try {
                val summary = repo.syncCityFullRoutes(
                    rawCityOrCode = clean,
                    onDestinationsReady = { originCity, count ->
                        reloadAllCityAndRouteData()
                        _networkState.update {
                            it.copy(
                                syncingCityName = originCity,
                                syncTotalCount = count,
                                officialSyncProgress = "已获取「$originCity」$count 条航线，正在同步时刻与放票日历..."
                            )
                        }
                        if (wasEmptyBefore) {
                            showMessage("已获取「$originCity」$count 条官方航线并解锁全部功能，正在后台同步去返时刻...")
                        }
                    },
                    onRouteProgress = { done, total, destCity ->
                        _networkState.update {
                            it.copy(
                                syncDoneCount = done,
                                syncTotalCount = total,
                                officialSyncProgress = "同步「${it.syncingCityName}」时刻与日历 ($done/$total)：$destCity"
                            )
                        }
                        // 每同步完 4 个目的地增量刷新一次全网列表，避免每条航线双倍全表重载阻塞
                        if (done > 0 && (done % 4 == 0 || done == total)) {
                            reloadAllCityAndRouteData()
                        }
                    }
                )
                reloadAllCityAndRouteData()
                when {
                    summary.totalRoutes == 0 -> {
                        showMessage("未查到「${summary.originCity}」出发的畅飞卡航线，请确认城市名/三字码或检查当前畅飞卡")
                    }
                    summary.hasPartialFailure -> {
                        val failSample = summary.failedDestinations.take(3).joinToString("、")
                        val suffix = if (summary.failedDestinations.size > 3) "等 ${summary.failedDestinations.size} 条" else "${summary.failedDestinations.size} 条"
                        showMessage(
                            "「${summary.originCity}」已同步 ${summary.totalRoutes} 条航线（其中 $failSample $suffix 日历或时刻拉取失败，可在详情页单独刷新重试）"
                        )
                    }
                    else -> {
                        showMessage("已完成「${summary.originCity}」全部 ${summary.totalRoutes} 条官方航线及往返时刻同步")
                    }
                }
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    reloadAllCityAndRouteData()
                }
                showMessage("已停止同步（已保留当前已拉取的官方航线与时刻）")
            } catch (e: Exception) {
                showMessage("同步航线失败：${e.message}")
            } finally {
                _officialDestLoading.value = false
                _networkState.update {
                    it.copy(
                        officialSyncing = false,
                        syncingCityName = "",
                        syncDoneCount = 0,
                        syncTotalCount = 0,
                        officialSyncProgress = ""
                    )
                }
            }
        }
    }

    /**
     * 刷新当前常驻城市出发的全部官方畅飞卡航线与往返时刻
     */
    fun checkOfficialDestinationsForHome() {
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先登录长龙会员后再同步官方航线")
            return
        }
        val origin = sessionState.value.homeCity
        if (origin.isBlank()) {
            showMessage("请先优先选择并同步一个出发城市的所有航线")
            return
        }
        syncSingleCityAllRoutes(origin)
    }

    /**
     * 基于已发现的官方机场目录，扩展拉取其他枢纽城市的官方可达航线（零本地硬编码城市）
     */
    fun syncOfficialNetworkHubs() {
        if (!sessionState.value.isLoggedIn) {
            showMessage("请先登录长龙会员后再扩展同步全网航线")
            return
        }
        if (officialSyncJob?.isActive == true) return

        officialSyncJob = viewModelScope.launch {
            val knownAirports = repo.getOfficialAirports().map { it.cityName }
            val syncedOrigins = repo.getAllOriginCities().toSet()
            val hubs = (knownAirports.filter { it !in syncedOrigins } + syncedOrigins)
                .distinct()
                .take(20)
            if (hubs.isEmpty()) {
                showMessage("请先优先同步一个城市的所有航线，再扩展全网枢纽")
                return@launch
            }

            _networkState.update {
                it.copy(
                    officialSyncing = true,
                    syncingCityName = "全网枢纽",
                    syncDoneCount = 0,
                    syncTotalCount = hubs.size,
                    officialSyncProgress = "准备扩展同步官方机场 0/${hubs.size}..."
                )
            }
            var done = 0
            var totalRoutes = 0
            try {
                for (hub in hubs) {
                    _networkState.update {
                        it.copy(
                            syncDoneCount = done,
                            syncTotalCount = hubs.size,
                            officialSyncProgress = "拉取官方航线 ($done/${hubs.size})：$hub 出发..."
                        )
                    }
                    val saved = repo.syncOfficialRoutesForOrigin(hub)
                    totalRoutes += saved.size
                    done++
                    reloadAllCityAndRouteData()
                    if (done < hubs.size) {
                        delay(Random.nextLong(900, 1500))
                    }
                }
                showMessage("已完成 $done 座官方机场可达航线扩展（共更新 $totalRoutes 条）")
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    reloadAllCityAndRouteData()
                }
                showMessage("已停止扩展同步（已更新 $done/${hubs.size} 座机场）")
            } catch (e: Exception) {
                showMessage("扩展同步中断：${e.message}")
            } finally {
                _networkState.update {
                    it.copy(
                        officialSyncing = false,
                        syncingCityName = "",
                        syncDoneCount = 0,
                        syncTotalCount = 0,
                        officialSyncProgress = ""
                    )
                }
            }
        }
    }

    fun stopOfficialNetworkSync() {
        officialSyncJob?.cancel()
        officialSyncJob = null
    }

    fun cancelAllBackgroundJobs() {
        stopOfficialNetworkSync()
        stopBatchCalendarVerify()
        calibrateJob?.cancel()
        calibrateJob = null
        searchJob?.cancel()
        searchJob = null
    }

    fun logout() {
        cancelAllBackgroundJobs()
        repo.authPrefs.clearSession()
        _searchState.update { it.copy(officialAllowedDestinations = null) }
        showMessage("已退出登录并清空本地会话凭据")
    }

    fun clearLocalCaches() {
        cancelAllBackgroundJobs()
        viewModelScope.launch {
            repo.clearAllCaches()
            reloadAllCityAndRouteData(initialLoad = true)
            showMessage("已清空本地航线、机场、日历与余票缓存")
        }
    }
}
