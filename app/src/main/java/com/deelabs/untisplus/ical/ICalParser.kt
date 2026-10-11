package com.deelabs.untisplus.ical

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal, dependency-free iCalendar (RFC 5545) reader.
 *
 * Only the subset needed to render subscribed calendars is supported: `VEVENT`
 * blocks with `DTSTART`/`DTEND`/`DURATION`, `UID`/`SUMMARY`/`DESCRIPTION`/
 * `LOCATION`, `RRULE` (a bounded subset), `EXDATE` and `RECURRENCE-ID`. Anything
 * unsupported is skipped rather than failing the whole file.
 */
object ICalParser {

	private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
	private val DURATION_REGEX = Regex(
		"^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$",
		RegexOption.IGNORE_CASE
	)

	/** Parses a calendar file into its (unexpanded) `VEVENT`s. */
	fun parse(text: String): List<ICalRecurringEvent> {
		val events = ArrayList<ICalRecurringEvent>()
		var current: MutableMap<String, Property>? = null

		for (line in unfold(text)) {
			val property = parseProperty(line) ?: continue
			when {
				property.name == "BEGIN" && property.value.equals("VEVENT", ignoreCase = true) ->
					current = LinkedHashMap()

				property.name == "END" && property.value.equals("VEVENT", ignoreCase = true) -> {
					current?.let { toEvent(it) }?.let(events::add)
					current = null
				}

				current != null -> current!!.putIfAbsent(property.name, property)
			}
		}

		return events
	}

	/** Unfolds content lines: a line continuation starts with a single space/tab. */
	internal fun unfold(text: String): List<String> {
		val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
		val result = ArrayList<String>(lines.size)
		for (line in lines) {
			if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
				result[result.lastIndex] = result.last() + line.substring(1)
			} else if (line.isNotBlank()) {
				result.add(line)
			}
		}
		return result
	}

	internal data class Property(
		val name: String,
		val params: Map<String, String>,
		val value: String,
	)

	internal fun parseProperty(line: String): Property? {
		var inQuotes = false
		var colonIndex = -1
		for (i in line.indices) {
			when (line[i]) {
				'"' -> inQuotes = !inQuotes
				':' -> if (!inQuotes) {
					colonIndex = i
					break
				}
			}
		}
		if (colonIndex < 0) return null

		val head = line.substring(0, colonIndex)
		val value = unescape(line.substring(colonIndex + 1))
		val segments = splitUnquoted(head, ';')
		val name = segments.firstOrNull()?.trim()?.uppercase() ?: return null

		val params = LinkedHashMap<String, String>()
		for (segment in segments.drop(1)) {
			val index = segment.indexOf('=')
			if (index <= 0) continue
			val key = segment.substring(0, index).trim().uppercase()
			val raw = segment.substring(index + 1).trim().removeSurrounding("\"")
			params[key] = raw
		}

		return Property(name, params, value)
	}

	private fun splitUnquoted(value: String, separator: Char): List<String> {
		val result = ArrayList<String>()
		val current = StringBuilder()
		var inQuotes = false
		for (c in value) {
			when {
				c == '"' -> {
					inQuotes = !inQuotes
					current.append(c)
				}

				c == separator && !inQuotes -> {
					result.add(current.toString())
					current.setLength(0)
				}

				else -> current.append(c)
			}
		}
		result.add(current.toString())
		return result
	}

	private fun toEvent(properties: Map<String, Property>): ICalRecurringEvent? {
		val startProperty = properties["DTSTART"] ?: return null
		val parsedStart = parseDateTime(startProperty) ?: return null
		val allDay = parsedStart.second

		val endProperty = properties["DTEND"]
		val end = when {
			endProperty != null -> {
				val parsedEnd = parseDateTime(endProperty)
				val rawEnd = parsedEnd?.first ?: defaultEnd(parsedStart.first, allDay)
				// All-day DTEND is exclusive; convert to an inclusive end.
				if (allDay && rawEnd.isAfter(parsedStart.first)) rawEnd.minusDays(1) else rawEnd
			}

			properties["DURATION"] != null ->
				parseDuration(properties.getValue("DURATION").value)
					?.let { parsedStart.first.plus(it) }
					?: defaultEnd(parsedStart.first, allDay)

			else -> defaultEnd(parsedStart.first, allDay)
		}

		return ICalRecurringEvent(
			uid = properties["UID"]?.value.orEmpty(),
			summary = properties["SUMMARY"]?.value.orEmpty(),
			description = properties["DESCRIPTION"]?.value?.takeIf { it.isNotBlank() },
			location = properties["LOCATION"]?.value?.takeIf { it.isNotBlank() },
			start = parsedStart.first,
			end = end,
			allDay = allDay,
			rrule = properties["RRULE"]?.value?.let(RecurrenceRuleParser::parse),
			exDates = properties["EXDATE"]?.let { exdate ->
				splitUnquoted(exdate.value, ',').mapNotNull { parseDateTimeValue(it, exdate.params) }
			}.orEmpty(),
			recurrenceId = properties["RECURRENCE-ID"]?.let { parseDateTime(it)?.first },
		)
	}

	private fun defaultEnd(start: LocalDateTime, allDay: Boolean): LocalDateTime =
		if (allDay) start.toLocalDate().atTime(LocalTime.MAX)
		else start.plusHours(1).minusNanos(1)

	private fun parseDateTime(property: Property): Pair<LocalDateTime, Boolean>? {
		val allDay = property.params["VALUE"].equals("DATE", ignoreCase = true) ||
			property.value.trim().length == 8
		return parseDateTimeValue(property.value, property.params)?.let { it to allDay }
	}

	private fun parseDateTimeValue(raw: String, params: Map<String, String>): LocalDateTime? {
		val value = raw.trim()
		if (value.isEmpty()) return null

		val isUtc = value.endsWith("Z", ignoreCase = true)
		val core = if (isUtc) value.dropLast(1) else value

		if (core.length == 8 && core.all(Char::isDigit)) {
			return runCatching { LocalDate.parse(core, DATE_FORMATTER).atStartOfDay() }.getOrNull()
		}
		if (core.length != 15 || core[8] != 'T') return null

		val local = runCatching {
			LocalDateTime.of(
				LocalDate.parse(core.substring(0, 8), DATE_FORMATTER),
				LocalTime.of(
					core.substring(9, 11).toInt(),
					core.substring(11, 13).toInt(),
					core.substring(13, 15).toInt(),
				)
			)
		}.getOrNull() ?: return null

		val zone = when {
			isUtc -> ZoneId.of("UTC")
			else -> params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
		} ?: return local

		return ZonedDateTime.of(local, zone)
			.withZoneSameInstant(ZoneId.systemDefault())
			.toLocalDateTime()
	}

	private fun parseDuration(value: String): Duration? {
		val match = DURATION_REGEX.matchEntire(value.trim()) ?: return null
		val days = (match.groupValues[3].toLongOrNull() ?: 0) + (match.groupValues[2].toLongOrNull() ?: 0) * 7
		val duration = Duration.ofDays(days)
			.plusHours(match.groupValues[4].toLongOrNull() ?: 0)
			.plusMinutes(match.groupValues[5].toLongOrNull() ?: 0)
			.plusSeconds(match.groupValues[6].toLongOrNull() ?: 0)
		return if (match.groupValues[1] == "-") duration.negated() else duration
	}

	private fun unescape(value: String): String {
		if (value.indexOf('\\') < 0) return value
		val builder = StringBuilder(value.length)
		var i = 0
		while (i < value.length) {
			val c = value[i]
			if (c == '\\' && i + 1 < value.length) {
				when (val next = value[i + 1]) {
					'n', 'N' -> builder.append('\n')
					',' -> builder.append(',')
					';' -> builder.append(';')
					'\\' -> builder.append('\\')
					else -> builder.append(next)
				}
				i += 2
			} else {
				builder.append(c)
				i++
			}
		}
		return builder.toString()
	}
}

internal object RecurrenceRuleParser {
	fun parse(value: String): RecurrenceRule? {
		val parts = HashMap<String, String>()
		for (segment in value.split(';')) {
			val index = segment.indexOf('=')
			if (index > 0) parts[segment.substring(0, index).uppercase()] = segment.substring(index + 1)
		}

		val frequency = when (parts["FREQ"]?.uppercase()) {
			"DAILY" -> RecurrenceFrequency.DAILY
			"WEEKLY" -> RecurrenceFrequency.WEEKLY
			"MONTHLY" -> RecurrenceFrequency.MONTHLY
			"YEARLY" -> RecurrenceFrequency.YEARLY
			else -> return null
		}

		return RecurrenceRule(
			frequency = frequency,
			interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
			count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 },
			until = parts["UNTIL"]?.let(::parseUntil),
			byDay = parts["BYDAY"].orEmpty().split(',').mapNotNull(::parseByDay),
			byMonthDay = parts["BYMONTHDAY"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
			byMonth = parts["BYMONTH"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
		)
	}

	private fun parseUntil(value: String): LocalDateTime? {
		val core = value.trim().removeSuffix("Z")
		return runCatching {
			if (core.length == 8) {
				LocalDate.parse(core, DateTimeFormatter.BASIC_ISO_DATE).atTime(LocalTime.MAX)
			} else {
				LocalDateTime.of(
					LocalDate.parse(core.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE),
					LocalTime.of(
						core.substring(9, 11).toInt(),
						core.substring(11, 13).toInt(),
						core.substring(13, 15).toInt(),
					)
				)
			}
		}.getOrNull()
	}

	private fun parseByDay(value: String): java.time.DayOfWeek? =
		when (value.trim().takeLast(2).uppercase()) {
			"MO" -> java.time.DayOfWeek.MONDAY
			"TU" -> java.time.DayOfWeek.TUESDAY
			"WE" -> java.time.DayOfWeek.WEDNESDAY
			"TH" -> java.time.DayOfWeek.THURSDAY
			"FR" -> java.time.DayOfWeek.FRIDAY
			"SA" -> java.time.DayOfWeek.SATURDAY
			"SU" -> java.time.DayOfWeek.SUNDAY
			else -> null
		}
}
