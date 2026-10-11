package com.deelabs.untisplus.ical

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Expands recurring [ICalRecurringEvent]s into concrete [ICalEvent] instances
 * within a requested date range. Only the recurrence rules commonly emitted by
 * calendar exports are supported; anything else is treated as a single event.
 */
object RecurrenceExpander {

	private const val MAX_INSTANCES = 1000

	fun expand(events: List<ICalRecurringEvent>, from: LocalDate, to: LocalDate): List<ICalEvent> {
		if (events.isEmpty()) return emptyList()

		val result = ArrayList<ICalEvent>()
		val overrides = events.filter { it.recurrenceId != null }

		for (event in events) {
			if (event.recurrenceId != null) continue

			val rule = event.rrule
			if (rule == null) {
				if (overlaps(event.start, event.end, from, to)) result.add(event.toInstance(event.start))
				continue
			}

			val exDates = event.exDates.toHashSet()
			var index = 0
			for (date in occurrences(event, rule, from, to)) {
				if (index++ >= MAX_INSTANCES) break
				val start = LocalDateTime.of(date, event.start.toLocalTime())
				if (start in exDates) continue
				if (overrides.any { it.uid == event.uid && it.recurrenceId == start }) continue
				val instance = event.toInstance(start)
				if (overlaps(instance.start, instance.end, from, to)) result.add(instance)
			}
		}

		for (override in overrides) {
			if (overlaps(override.start, override.end, from, to)) result.add(override.toInstance(override.start))
		}

		return result.sortedBy { it.start }
	}

	private fun overlaps(start: LocalDateTime, end: LocalDateTime, from: LocalDate, to: LocalDate): Boolean {
		val rangeStart = from.atStartOfDay()
		val rangeEnd = to.atTime(LocalTime.MAX)
		return !end.isBefore(rangeStart) && !start.isAfter(rangeEnd)
	}

	private fun occurrences(
		event: ICalRecurringEvent,
		rule: RecurrenceRule,
		from: LocalDate,
		to: LocalDate,
	): List<LocalDate> {
		val result = ArrayList<LocalDate>()
		val base = event.start.toLocalDate()
		val until = rule.until?.toLocalDate() ?: to.plusYears(1)
		val limit = if (until.isBefore(to)) until else to.plusDays(1)
		val countLimited = rule.count != null

		fun add(date: LocalDate): Boolean {
			if (date.isBefore(base) || date.isAfter(limit)) return false
			result.add(date)
			return result.size < MAX_INSTANCES
		}

		when (rule.frequency) {
			RecurrenceFrequency.DAILY -> {
				var date = if (countLimited) base else jumpDays(base, from, rule.interval)
				while (!date.isAfter(limit) && result.size < MAX_INSTANCES) {
					if (!add(date)) break
					date = date.plusDays(rule.interval.toLong())
				}
			}

			RecurrenceFrequency.WEEKLY -> {
				val days = rule.byDay.ifEmpty { listOf(base.dayOfWeek) }.sortedBy { it.value }
				val anchor = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				var week = if (countLimited) 0 else jumpWeeks(anchor, from, rule.interval)
				while (result.size < MAX_INSTANCES) {
					val weekMonday = anchor.plusWeeks((week * rule.interval).toLong())
					if (weekMonday.isAfter(limit)) break
					for (day in days) {
						val date = weekMonday.with(TemporalAdjusters.nextOrSame(day))
						if (!add(date)) {
							if (date.isAfter(limit)) break
						}
					}
					week++
				}
			}

			RecurrenceFrequency.MONTHLY -> {
				val monthDays = rule.byMonthDay.ifEmpty { listOf(base.dayOfMonth) }
				var month = if (countLimited) 0 else jumpMonths(YearMonth.from(base), from, rule.interval)
				while (result.size < MAX_INSTANCES) {
					val yearMonth = YearMonth.from(base).plusMonths((month * rule.interval).toLong())
					if (yearMonth.atEndOfMonth().isAfter(limit)) break
					for (dom in monthDays) {
						if (dom !in 1..yearMonth.lengthOfMonth()) continue
						if (!add(yearMonth.atDay(dom)) && yearMonth.atDay(dom).isAfter(limit)) break
					}
					month++
				}
			}

			RecurrenceFrequency.YEARLY -> {
				val months = rule.byMonth.ifEmpty { listOf(base.monthValue) }
				val days = rule.byMonthDay.ifEmpty { listOf(base.dayOfMonth) }
				var year = if (countLimited) 0 else maxOf(0, from.year - base.year)
				while (result.size < MAX_INSTANCES) {
					val y = base.year + year * rule.interval
					if (y > limit.year) break
					for (m in months) for (dom in days) {
						if (dom !in 1..YearMonth.of(y, m).lengthOfMonth()) continue
						if (!add(LocalDate.of(y, m, dom))) break
					}
					year++
				}
			}
		}

		return if (countLimited) result.take(rule.count!!) else result
	}

	private fun jumpDays(base: LocalDate, from: LocalDate, interval: Int): LocalDate {
		if (!from.isAfter(base)) return base
		val elapsed = ChronoUnit.DAYS.between(base, from)
		val steps = (elapsed / interval).toLong()
		return base.plusDays(steps * interval)
	}

	private fun jumpWeeks(anchor: LocalDate, from: LocalDate, interval: Int): Int {
		if (!from.isAfter(anchor)) return 0
		val elapsedWeeks = ChronoUnit.WEEKS.between(anchor, from)
		return ((elapsedWeeks / interval).toInt()).coerceAtLeast(0)
	}

	private fun jumpMonths(baseMonth: YearMonth, from: LocalDate, interval: Int): Int {
		val target = YearMonth.from(from)
		if (!target.isAfter(baseMonth)) return 0
		val elapsed = ChronoUnit.MONTHS.between(baseMonth, target)
		return ((elapsed / interval).toInt()).coerceAtLeast(0)
	}

	private fun ICalRecurringEvent.toInstance(start: LocalDateTime): ICalEvent {
		val duration = ChronoUnit.SECONDS.between(this.start, this.end).coerceAtLeast(0)
		return ICalEvent(
			uid = uid,
			summary = summary,
			description = description,
			location = location,
			start = start,
			end = if (allDay) start.toLocalDate().atTime(LocalTime.MAX) else start.plusSeconds(duration),
			allDay = allDay,
		)
	}
}
