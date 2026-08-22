package com.deelabs.untisplus.api.model.untis.timetable

import com.deelabs.untisplus.api.model.untis.enumeration.OfficeHourRegistrationTimeSlotState
import com.deelabs.untisplus.api.serializer.Time
import kotlinx.serialization.Serializable


@Serializable
data class OfficeHourRegistrationTimeSlot(
	val startTime: Time,
	val endTime: Time,
	val state: OfficeHourRegistrationTimeSlotState
)
