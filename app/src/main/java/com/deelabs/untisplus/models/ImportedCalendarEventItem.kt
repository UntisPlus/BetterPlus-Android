package com.deelabs.untisplus.models

import java.time.LocalDateTime

/**
 * A single event imported from an external iCalendar feed, ready to be overlaid
 * on the week view.
 */
data class ImportedCalendarEventItem(
	val calendarId: Long,
	val summary: String,
	val description: String?,
	val location: String?,
	val start: LocalDateTime,
	val end: LocalDateTime,
	val allDay: Boolean,
	val color: Int,
) : TimetableItem
