package com.deelabs.untisplus.ical

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * A single (already expanded) calendar entry. Recurring `VEVENT`s are expanded
 * into one [ICalEvent] per occurrence by [RecurrenceExpander].
 */
data class ICalEvent(
	val uid: String,
	val summary: String,
	val description: String?,
	val location: String?,
	val start: LocalDateTime,
	val end: LocalDateTime,
	val allDay: Boolean,
)

/**
 * The unexpanded representation of a `VEVENT` as read from the calendar file.
 * [rrule] and [exDates] describe the recurrence; [overrideEvents] holds the
 * concrete instances supplied via separate `RECURRENCE-ID` events.
 */
data class ICalRecurringEvent(
	val uid: String,
	val summary: String,
	val description: String?,
	val location: String?,
	val start: LocalDateTime,
	val end: LocalDateTime,
	val allDay: Boolean,
	val rrule: RecurrenceRule? = null,
	val exDates: List<LocalDateTime> = emptyList(),
	val recurrenceId: LocalDateTime? = null,
)

enum class RecurrenceFrequency {
	DAILY, WEEKLY, MONTHLY, YEARLY
}

data class RecurrenceRule(
	val frequency: RecurrenceFrequency,
	val interval: Int = 1,
	val count: Int? = null,
	val until: LocalDateTime? = null,
	val byDay: List<DayOfWeek> = emptyList(),
	val byMonthDay: List<Int> = emptyList(),
	val byMonth: List<Int> = emptyList(),
)
