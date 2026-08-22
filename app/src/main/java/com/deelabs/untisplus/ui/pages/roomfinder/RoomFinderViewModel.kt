package com.deelabs.untisplus.ui.pages.roomfinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.database.entities.RoomFinderDao
import com.deelabs.untisplus.data.database.entities.RoomFinderEntity
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.domain.GetRoomFinderItemsUseCase
import com.deelabs.untisplus.models.RoomFinderHour
import com.deelabs.untisplus.models.RoomFinderItem
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class RoomFinderViewModel @Inject constructor(
	private val userRepository: UserRepository,
	internal val masterDataRepository: MasterDataRepository,
	private val roomFinderDao: RoomFinderDao,
	private val navigator: AppNavigator,
	clock: Clock,
	getRoomFinderItems: GetRoomFinderItemsUseCase
) : ViewModel() {
	private val currentDateTime = LocalDateTime.now(clock)

	private val _hourList = userRepository.userState.map { userState ->
		(userState as? UserRepository.UserState.User)?.let {
			it.user.timeGrid.days.flatMap { day ->
				day.units.map { unit -> RoomFinderHour(day, unit) }
			}
		} ?: emptyList()
	}
	val hourList: StateFlow<List<RoomFinderHour>> = _hourList.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = emptyList()
	)

	private val _selectedHourIndex = MutableStateFlow(0)
	val selectedHourIndex: StateFlow<Int> = _selectedHourIndex

	val roomList: StateFlow<List<RoomFinderItem>> = combine(
		getRoomFinderItems(),
		selectedHourIndex,
		::sortRoomList
	)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000),
			initialValue = emptyList()
		)

	init {
		viewModelScope.launch {
			_hourList.collect { hourList ->
				_selectedHourIndex.value = hourList.indexOfFirst {
					it.timeGridDay.day == currentDateTime.dayOfWeek
						&& currentDateTime.toLocalTime().isBefore(it.timeGridUnit.endTime)
				}.coerceAtLeast(0)
			}
		}
	}

	private fun sortRoomList(roomList: List<RoomFinderItem>, selectedIndex: Int) = roomList.sortedWith(
		compareByDescending<RoomFinderItem> { it.freeHoursAt(selectedIndex) }
			.thenBy { it.entity.longName }
	)

	fun goBack() {
		navigator.popBackStack()
	}

	fun addRooms(rooms: List<PeriodElement>) = viewModelScope.launch {
		roomFinderDao.insertAll(*rooms.map { RoomFinderEntity(it.id, userRepository.currentUser!!.id) }.toTypedArray())
	}

	fun deleteRoom(room: RoomFinderItem) = viewModelScope.launch {
		roomFinderDao.delete(RoomFinderEntity(room.entity.id, userRepository.currentUser!!.id))
	}

	fun onRoomClick(room: RoomFinderItem) {
		navigator.navigate(AppRoutes.Timetable(ElementType.ROOM, room.entity.id)) {
			popUpTo(AppRoutes.RoomFinder) { inclusive = true }
		}
	}

	fun selectHour(hourIndex: Int?) = viewModelScope.launch {
		hourIndex?.let { _selectedHourIndex.emit(it) }
	}
}
