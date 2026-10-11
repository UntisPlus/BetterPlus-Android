package com.deelabs.untisplus.ui.pages.timetable

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sapuseven.compose.protostore.ui.preferences.convertRangeToPair
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.exception.UntisApiException
import com.deelabs.untisplus.api.model.response.UntisErrorCode
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.ImportedCalendarEventEntity
import com.deelabs.untisplus.data.repository.ImportedCalendarRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.TimetableChangeBus
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.helpers.BuildConfigFieldsProvider
import com.deelabs.untisplus.mappers.TimetableMapper
import com.deelabs.untisplus.models.PeriodItem
import com.deelabs.untisplus.models.ImportedCalendarEventItem
import com.deelabs.untisplus.models.TimetableItem
import com.deelabs.untisplus.services.WeekLogicService
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.preferences.toPeriodElement
import com.deelabs.untisplus.ui.weekview.Event
import com.deelabs.untisplus.ui.weekview.EventStyle
import com.deelabs.untisplus.ui.weekview.Holiday
import com.deelabs.untisplus.ui.weekview.WeekViewColorScheme
import com.deelabs.untisplus.ui.weekview.WeekViewEventStyle
import com.deelabs.untisplus.ui.weekview.WeekViewHour
import com.deelabs.untisplus.ui.weekview.startDateForPageIndex
import crocodile8.universal_cache.CachedSourceResult
import crocodile8.universal_cache.FromCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
	private val navigator: AppNavigator,
	private val userSettingsRepository: UserSettingsRepository,
	private val timetableMapper: TimetableMapper,
	private val importedCalendarRepository: ImportedCalendarRepository,
	internal val userRepository: UserRepository,
	internal val timetableRepository: TimetableRepository,
	internal val masterDataRepository: MasterDataRepository,
	internal val timetableChangeBus: TimetableChangeBus,
	internal val clock: Clock,
	internal val weekLogicService: WeekLogicService,
	buildConfigFieldsProvider: BuildConfigFieldsProvider,
	savedStateHandle: SavedStateHandle,
) : ViewModel() {
	private val args = savedStateHandle.toRoute<AppRoutes.Timetable>()

	val requestedElement = args.getElement()

	var profileManagementDialog by mutableStateOf(false)
	var timetableItemDetailsDialog by mutableStateOf<Pair<List<Event<TimetableItem>>, Int>?>(null)
	var loading by mutableStateOf(true)

	/** True when a load failed due to invalid/expired credentials. */
	var sessionError by mutableStateOf(false)
		private set

	/** True when a load failed for non-auth reasons (e.g. network). */
	var loadError by mutableStateOf(false)
		private set

	val currentUser: User = userRepository.currentUser!!
	val allUsersState: StateFlow<List<User>> = userRepository.allUsersState

	val isDebug = buildConfigFieldsProvider.get().isDebug

	private val _personalElement = MutableStateFlow<PeriodElement?>(null)

	private val _needsPersonalTimetable = MutableStateFlow(false)
	val needsPersonalTimetable: StateFlow<Boolean> = _needsPersonalTimetable

	private val _hourList = MutableStateFlow<List<WeekViewHour>>(emptyList())
	val hourList: StateFlow<List<WeekViewHour>> = _hourList

	private val _events = MutableStateFlow<Map<LocalDate, List<Event<TimetableItem>>>>(emptyMap())
	val events: StateFlow<Map<LocalDate, List<Event<TimetableItem>>>> = _events

	private val _holidays = MutableStateFlow<List<Holiday>>(emptyList())
	val holidays: StateFlow<List<Holiday>> = _holidays

	private val _lastRefresh = MutableStateFlow<Instant?>(null)
	val lastRefresh: StateFlow<Instant?> = _lastRefresh

	private val _weekViewColorScheme = MutableStateFlow(WeekViewColorScheme.default())
	val weekViewColorScheme: StateFlow<WeekViewColorScheme> = _weekViewColorScheme

	private val _weekViewScale = MutableStateFlow(1f)
	val weekViewScale: StateFlow<Float> = _weekViewScale

	private val _weekViewZoomEnabled = MutableStateFlow(true)
	val weekViewZoomEnabled: StateFlow<Boolean> = _weekViewZoomEnabled

	private val _weekViewEventStyle = MutableStateFlow(WeekViewEventStyle.default())
	val weekViewEventStyle: StateFlow<WeekViewEventStyle> = _weekViewEventStyle

	/** Whether imported `.ics` calendar events are overlaid on the timetable. */
	val showImportedCalendars: StateFlow<Boolean> = userSettingsRepository.getSettings()
		.map { it.timetableShowImportedCalendars }
		.stateIn(
			scope = viewModelScope,
			started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
			initialValue = true
		)

	val numDays: StateFlow<Int> = userSettingsRepository.getSettings().map { userSettings ->
		val customRangeDays = userSettings.weekCustomRangeList
			.mapNotNull { dayName -> runCatching { DayOfWeek.valueOf(dayName) }.getOrNull() }

		if (customRangeDays.isNotEmpty())
			customRangeDays.maxOf { it.value } // Columns are always aligned to the start of the week
		else
			currentUser.timeGrid.days.maxOfOrNull { it.day.value } ?: 5
	}.stateIn(
		scope = viewModelScope,
		started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
		initialValue = 5
	)

	private val _userSettings = userSettingsRepository.getSettings()

	private val _ready = combine(
		_hourList,
		_userSettings
	) { hourList, userSettings ->
		hourList.isNotEmpty()
	}
	val ready: StateFlow<Boolean> = _ready.stateIn(
		scope = viewModelScope,
		started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
		initialValue = false
	)

	private var currentPage = 0

	@Volatile
	private var showImportedEvents = true

	companion object {
		// Error codes that indicate the stored credentials/session are no longer valid
		private val SESSION_ERROR_CODES = setOf(
			UntisErrorCode.INVALID_PASSWORD,
			UntisErrorCode.NO_SPECIFIED_USER,
			UntisErrorCode.REQUIRED_AUTHENTICATION,
			UntisErrorCode.AUTHENTICATION_ERROR,
			UntisErrorCode.INVALID_USER_STATUS,
			UntisErrorCode.INVALID_USER_ROLE,
			UntisErrorCode.LOCKED_ACCESS,
			UntisErrorCode.ACCESS_DENIED,
			UntisErrorCode.ACCESS_DENIED_APP,
			UntisErrorCode.API_KEY_CHANGED_AND_WORKING_OFFLINE,
		)
	}

	private val loadingExceptionHandler: suspend FlowCollector<*>.(Throwable) -> Unit = { throwable ->
		Log.e("TimetableViewModel", "Failed to load timetable", throwable)
		if (throwable is UntisApiException && throwable.error?.code in SESSION_ERROR_CODES)
			sessionError = true
		else
			loadError = true
	}

	init {
		viewModelScope.launch {
			_userSettings.collect { userSettings ->
				// All properties that are based on preferences are set here
				showImportedEvents = userSettings.timetableShowImportedCalendars
				userSettings.timetablePersonalTimetable?.toPeriodElement()?.let {
					_personalElement.value = it
					_needsPersonalTimetable.emit(false)
					//_currentElement.update { prev -> prev ?: it } // Update only if null
			} ?: run {
				_personalElement.value = currentUser.userData.getPeriodElement()
					?: currentUser.userData.getFallbackPeriodElements().firstOrNull()?.also {
						Log.i(
							"TimetableViewModel",
							"Account has no usable personal element (elemType=${currentUser.userData.elemType}, elemId=${currentUser.userData.elemId}), " +
								"falling back to $it (children=${currentUser.userData.children.size}, klassenIds=${currentUser.userData.klassenIds.size})"
						)
					}
				_needsPersonalTimetable.emit(requestedElement == null && _personalElement.value == null)
			}
				_hourList.value = buildHourList(
					user = currentUser,
					range = userSettings.timetableRange.convertRangeToPair(),
					rangeIndexReset = userSettings.timetableRangeIndexReset
				)
				_weekViewColorScheme.value = WeekViewColorScheme(
					pastBackgroundColor = Color(userSettings.backgroundPast),
					futureBackgroundColor = Color(userSettings.backgroundFuture),
					indicatorColor = Color(userSettings.marker),
				)
				_weekViewEventStyle.value = WeekViewEventStyle(
					padding = userSettings.timetableItemPadding,
					cornerRadius = userSettings.timetableItemCornerRadius,
					lessonNameStyle = TextStyle(
						fontSize = userSettings.timetableLessonNameFontSize.sp,
						fontWeight = if (userSettings.timetableBoldLessonName) FontWeight.Bold else FontWeight.Normal
					),
					lessonInfoStyle = TextStyle(
						fontSize = userSettings.timetableLessonInfoFontSize.sp
					),
					lessonInfoCentered = userSettings.timetableCenteredLessonInfo,
				)
				_weekViewScale.value = userSettings.timetableZoomLevel
				_weekViewZoomEnabled.value = userSettings.timetableZoomEnabled
			}
		}

		viewModelScope.launch {
			// The proxy reported a timetable change (live SSE or backstop poll):
			// invalidate and re-fetch the visible week.
			timetableChangeBus.updates.drop(1).collect {
				onPageReload(currentPage)
			}
		}

		viewModelScope.launch {
			delay(1000) // FIXME How can I get the currentUserData after it is initialized in masterDataRepository?
			val holidays = (masterDataRepository.userData?.holidays ?: emptyList())
				.map { holiday ->
					Holiday(
						title = holiday.name,
						colorScheme = EventStyle.Transparent,
						start = holiday.startDate!!,
						end = holiday.endDate!!,
					)
				}
			_holidays.emit(holidays)
		}
	}

	fun switchUser(user: User) {
		userRepository.switchUser(user)
	}

	fun editUser(user: User?) {
		if (user == null)
			navigator.navigate(AppRoutes.Login)
		else
			navigator.navigate(AppRoutes.LoginDataInput(userId = user.id))
	}

	fun editUsers() {
		profileManagementDialog = true
	}

	fun onPageChange(pageOffset: Int = currentPage) {
		currentPage = pageOffset
		viewModelScope.launch {
			loading = true
			((pageOffset - 1)..(pageOffset + 1)).map { targetPage ->
				async {
					val startDate = weekLogicService.currentWeekStartDate().plusWeeks(targetPage.toLong())
					loadEvents(
						startDate,
						// Note: Right now this is a workaround to show the "last refresh" text when changing pages,
						//  since it isn't stored after emitting the events.
						//  For that reason the cache is queried more often than necessary.
						if (_events.value.contains(startDate)) FromCache.ONLY else FromCache.CACHED_THEN_LOAD
					)
						.catch(loadingExceptionHandler)
						.collectEvents(startDate, targetPage == pageOffset)
				}
			}.awaitAll()
			loading = false
		}
	}

	suspend fun onPageReload(pageOffset: Int) {
		val startDate = weekLogicService.currentWeekStartDate().plusWeeks(pageOffset.toLong())
		loadEvents(startDate, FromCache.NEVER)
			.catch(loadingExceptionHandler)
			.collectEvents(startDate)
	}

	fun dismissSessionError() {
		sessionError = false
	}

	fun reloginFromSessionError() {
		sessionError = false
		editUser(currentUser)
	}

	fun dismissLoadError() {
		loadError = false
	}

	fun retryLoad() {
		loadError = false
		viewModelScope.launch {
			onPageReload(currentPage)
		}
	}

	fun onItemClick(itemsWithIndex: Pair<List<Event<TimetableItem>>, Int>) {
		// Imported calendar events have no lesson details; only open the dialog
		// when at least one native lesson was tapped.
		if (itemsWithIndex.first.none { it.data is PeriodItem }) return
		timetableItemDetailsDialog = itemsWithIndex
	}

	/** Toggles the imported-calendar overlay and reloads the visible week. */
	fun toggleImportedCalendars() {
		val next = !showImportedCalendars.value
		showImportedEvents = next
		viewModelScope.launch {
			userSettingsRepository.updateSettings { timetableShowImportedCalendars = next }
			onPageReload(currentPage)
		}
	}

	suspend fun onZoom(zoomLevel: Float) {
		userSettingsRepository.updateSettings { timetableZoomLevel = zoomLevel }
	}

	private suspend fun loadEvents(startDate: LocalDate, fromCache: FromCache): Flow<CachedSourceResult<List<Period>>> {
		// Load the requested element (nav args) or the personal element
		val elementToLoad = requestedElement ?: _personalElement
			.combine(_needsPersonalTimetable) { element, _ -> element } // Emit a value if _personalElement or _needsPersonalTimetable changes
			.first { it != null || _needsPersonalTimetable.value } // Take the first non-null _personalElement or null if _needsPersonalTimetable

		return elementToLoad?.let {
			timetableRepository.timetableSource().getRaw(
				params = TimetableRepository.TimetableParams(
					elementId = it.id,
					elementType = it.type,
					startDate = startDate,
				),
				fromCache = fromCache,
				additionalKey = currentUser.id
			)
		} ?: emptyFlow()
	}

	private fun emitEvents(events: Map<LocalDate, List<Event<TimetableItem>>>) {
		_events.update {
			val newEvents = it.toMutableMap()
			events.forEach { (date, events) ->
				newEvents[date] = events
			}
			newEvents.toMap()
		}
	}

	private fun groupEventsByDate(events: List<Event<TimetableItem>>): Map<LocalDate, List<Event<TimetableItem>>> {
		val groupedEvents = mutableMapOf<LocalDate, MutableList<Event<TimetableItem>>>()

		for (event in events) {
			val eventDate = event.start.toLocalDate()
			groupedEvents.computeIfAbsent(eventDate) { mutableListOf() }.add(event)
		}

		// Convert the map to an immutable version if needed
		return groupedEvents.mapValues { it.value.toList() }
	}

	/** Loads imported `.ics` events for the week starting at [startDate]. */
	private suspend fun loadImportedEvents(startDate: LocalDate): List<Event<TimetableItem>> {
		if (!showImportedEvents) return emptyList()
		val days = maxOf(numDays.value, 7)
		return importedCalendarRepository.events(startDate, startDate.plusDays(days.toLong()))
			.mapNotNull { it.toWeekViewEvent() }
	}

	private fun ImportedCalendarEventEntity.toWeekViewEvent(): Event<TimetableItem>? {
		val start = ImportedCalendarEventEntity.parse(this.start) ?: return null
		val end = ImportedCalendarEventEntity.parse(this.end) ?: start
		val item = ImportedCalendarEventItem(
			calendarId = calendarId,
			summary = summary,
			description = description,
			location = location,
			start = start,
			end = end,
			allDay = allDay,
			color = color,
		)
		return Event<TimetableItem>(
			title = summary,
			top = location.orEmpty(),
			bottom = "",
			eventStyle = EventStyle.Custom(Color(color)),
			start = start,
			end = end,
			data = item,
		).apply { this.allDay = this@toWeekViewEvent.allDay }
	}

	// TODO: Extract to usecase
	private fun buildHourList(
		user: User, range: Pair<Int, Int>?, rangeIndexReset: Boolean
	): List<WeekViewHour> {
		val hourList = mutableListOf<WeekViewHour>()

		user.timeGrid.days.maxByOrNull { it.units.size }?.units?.forEachIndexed { index, hour ->
			// Check if outside configured range
			if (range?.let { index < it.first - 1 || index >= it.second } == true) return@forEachIndexed

			// If label is empty, fill it according to preferences
			val label = if (rangeIndexReset) {
				(index + 2 - (range?.first ?: 1)).toString()
			} else {
				hour.label.ifEmpty { (index + 1).toString() }
			}

			hourList.add(WeekViewHour(hour.startTime, hour.endTime, label))
		}

		return hourList
	}

	val onAnonymousSettingsClick = {
		navigator.navigate(AppRoutes.Settings.Timetable(highlightTitle = R.string.preference_timetable_personal_timetable))
	}

	fun getTitle(context: Context) = requestedElement?.let {
		if (it == _personalElement.value) null // Use Profile name for personal timetable
		else masterDataRepository.getLongName(it)
	}
		?: (currentUser.getDisplayedName(context) + (if (isDebug) " (${currentUser.id})" else ""))

	fun showElement(element: PeriodElement?) {
		if (requestedElement != element)
			navigator.navigate(AppRoutes.Timetable(element)) {
				if (element == null) {
					popUpTo(0)
				}
			}
	}

	private suspend fun Flow<CachedSourceResult<List<Period>>>.collectEvents(
		startDate: LocalDate,
		updateLastRefresh: Boolean = true
	) = collect { result ->
		val events = timetableMapper.mapTimetablePeriodsToWeekViewEvents(result.value, requestedElement?.type ?: ElementType.STUDENT) +
			loadImportedEvents(startDate)
		val refreshTimestamp = result.originTimeStamp?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
		emitEvents(mapOf(startDate to events))
		if (updateLastRefresh) {
			_lastRefresh.emit(refreshTimestamp)
			sessionError = false
			loadError = false
		}
	}
}
