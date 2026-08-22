package com.deelabs.untisplus.models

import com.deelabs.untisplus.api.model.untis.masterdata.timegrid.Day
import com.deelabs.untisplus.api.model.untis.masterdata.timegrid.Unit
import com.deelabs.untisplus.data.database.entities.RoomEntity


data class RoomFinderHour(
	val timeGridDay: Day,
	val timeGridUnit: Unit
)

data class RoomFinderItem(
	val entity: RoomEntity,
	val states: List<Boolean>
) {
	fun freeHoursAt(hourIndex: Int): Int {
	    return states.drop(hourIndex).takeWhile { !it }.count()
	}
}
