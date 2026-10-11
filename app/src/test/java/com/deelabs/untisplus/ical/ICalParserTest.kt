package com.deelabs.untisplus.ical

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ICalParserTest {

	private fun calendar(vararg lines: String) =
		("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + lines.joinToString("") { "$it\r\n" } + "END:VCALENDAR")

	@Test
	fun `parses a simple timed event`() {
		val text = calendar(
			"BEGIN:VEVENT",
			"UID:1",
			"SUMMARY:Math",
			"LOCATION:Room A",
			"DESCRIPTION:Homework\\, ch. 3",
			"DTSTART:20240115T080000",
			"DTEND:20240115T090000",
			"END:VEVENT",
		)

		val events = ICalParser.parse(text)
		assertEquals(1, events.size)
		val event = events.first()
		assertEquals("Math", event.summary)
		assertEquals("Room A", event.location)
		assertEquals("Homework, ch. 3", event.description)
		assertFalse(event.allDay)
		assertEquals(LocalDateTime.of(2024, 1, 15, 8, 0), event.start)
		assertEquals(LocalDateTime.of(2024, 1, 15, 9, 0), event.end)
	}

	@Test
	fun `parses an all-day event and makes the exclusive end inclusive`() {
		val text = calendar(
			"BEGIN:VEVENT",
			"UID:2",
			"SUMMARY:Holiday",
			"DTSTART;VALUE=DATE:20240115",
			"DTEND;VALUE=DATE:20240116",
			"END:VEVENT",
		)

		val event = ICalParser.parse(text).single()
		assertTrue(event.allDay)
		assertEquals(LocalDate.of(2024, 1, 15), event.start.toLocalDate())
		assertEquals(LocalDate.of(2024, 1, 15), event.end.toLocalDate())
	}

	@Test
	fun `unfolds folded content lines`() {
		val text = calendar(
			"BEGIN:VEVENT",
			"UID:3",
			"SUMMARY:This is a very long title that ",
			" continues on the next line",
			"DTSTART:20240115T080000",
			"DTEND:20240115T090000",
			"END:VEVENT",
		)

		assertEquals(
			"This is a very long title that continues on the next line",
			ICalParser.parse(text).single().summary,
		)
	}

	@Test
	fun `expands weekly recurrence by day`() {
		val text = calendar(
			"BEGIN:VEVENT",
			"UID:4",
			"SUMMARY:PE",
			"DTSTART:20240101T100000",
			"DTEND:20240101T110000",
			"RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4",
			"END:VEVENT",
		)

		val expanded = RecurrenceExpander.expand(
			ICalParser.parse(text),
			LocalDate.of(2024, 1, 1),
			LocalDate.of(2024, 1, 31),
		)

		assertEquals(
			listOf(
				LocalDate.of(2024, 1, 1),
				LocalDate.of(2024, 1, 3),
				LocalDate.of(2024, 1, 8),
				LocalDate.of(2024, 1, 10),
			),
			expanded.map { it.start.toLocalDate() },
		)
	}

	@Test
	fun `honours excluded dates`() {
		val text = calendar(
			"BEGIN:VEVENT",
			"UID:5",
			"SUMMARY:Club",
			"DTSTART:20240101T100000",
			"DTEND:20240101T110000",
			"RRULE:FREQ=DAILY;COUNT=3",
			"EXDATE:20240102T100000",
			"END:VEVENT",
		)

		val expanded = RecurrenceExpander.expand(
			ICalParser.parse(text),
			LocalDate.of(2024, 1, 1),
			LocalDate.of(2024, 1, 10),
		)

		assertEquals(
			listOf(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3)),
			expanded.map { it.start.toLocalDate() },
		)
	}

	@Test
	fun `keeps only occurrences inside the requested range`() {
		val text = calendar(
			"BEGIN:VEVENT",
			"UID:6",
			"SUMMARY:Daily",
			"DTSTART:20200101T100000",
			"DTEND:20200101T110000",
			"RRULE:FREQ=DAILY",
			"END:VEVENT",
		)

		val expanded = RecurrenceExpander.expand(
			ICalParser.parse(text),
			LocalDate.of(2024, 1, 1),
			LocalDate.of(2024, 1, 3),
		)

		assertEquals(3, expanded.size)
		assertEquals(LocalDate.of(2024, 1, 1), expanded.first().start.toLocalDate())
		assertEquals(LocalDate.of(2024, 1, 3), expanded.last().start.toLocalDate())
	}
}
