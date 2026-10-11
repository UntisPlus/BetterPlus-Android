package com.deelabs.untisplus.data.database.entities

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportedCalendarDao {
	@Query("SELECT * FROM ImportedCalendar WHERE userId = :userId ORDER BY name COLLATE NOCASE")
	fun calendarsFlow(userId: Long): Flow<List<ImportedCalendarEntity>>

	@Query("SELECT * FROM ImportedCalendar WHERE userId = :userId ORDER BY name COLLATE NOCASE")
	suspend fun calendars(userId: Long): List<ImportedCalendarEntity>

	@Query("SELECT * FROM ImportedCalendar WHERE id = :id")
	suspend fun calendar(id: Long): ImportedCalendarEntity?

	@Query("SELECT * FROM ImportedCalendar WHERE enabled = 1")
	suspend fun enabledCalendars(): List<ImportedCalendarEntity>

	@Query(
		"SELECT * FROM ImportedCalendarEvent WHERE calendarId = :calendarId " +
			"AND start <= :to AND end >= :from ORDER BY start"
	)
	suspend fun eventsForCalendar(calendarId: Long, from: String, to: String): List<ImportedCalendarEventEntity>

	@Query(
		"SELECT ImportedCalendarEvent.* FROM ImportedCalendarEvent " +
			"INNER JOIN ImportedCalendar ON ImportedCalendar.id = ImportedCalendarEvent.calendarId " +
			"WHERE ImportedCalendar.userId = :userId AND ImportedCalendar.enabled = 1 " +
			"AND ImportedCalendarEvent.start <= :to AND ImportedCalendarEvent.end >= :from " +
			"ORDER BY ImportedCalendarEvent.start"
	)
	fun eventsFlow(userId: Long, from: String, to: String): Flow<List<ImportedCalendarEventEntity>>

	@Query(
		"SELECT ImportedCalendarEvent.* FROM ImportedCalendarEvent " +
			"INNER JOIN ImportedCalendar ON ImportedCalendar.id = ImportedCalendarEvent.calendarId " +
			"WHERE ImportedCalendar.userId = :userId AND ImportedCalendar.enabled = 1 " +
			"AND ImportedCalendarEvent.start <= :to AND ImportedCalendarEvent.end >= :from " +
			"ORDER BY ImportedCalendarEvent.start"
	)
	suspend fun events(userId: Long, from: String, to: String): List<ImportedCalendarEventEntity>

	@Insert
	suspend fun insertCalendar(calendar: ImportedCalendarEntity): Long

	@Update
	suspend fun updateCalendar(calendar: ImportedCalendarEntity)

	@Delete
	suspend fun deleteCalendar(calendar: ImportedCalendarEntity)

	@Query("DELETE FROM ImportedCalendarEvent WHERE calendarId = :calendarId")
	suspend fun deleteEvents(calendarId: Long)

	@Insert
	suspend fun insertEvents(events: List<ImportedCalendarEventEntity>)

	@Query(
		"UPDATE ImportedCalendar SET lastRefresh = :lastRefresh, lastError = :lastError, " +
			"etag = :etag, lastModified = :lastModified WHERE id = :id"
	)
	suspend fun updateRefreshState(
		id: Long,
		lastRefresh: Long?,
		etag: String?,
		lastModified: String?,
		lastError: String?,
	)

	@Transaction
	suspend fun replaceEvents(calendarId: Long, events: List<ImportedCalendarEventEntity>) {
		deleteEvents(calendarId)
		insertEvents(events)
	}
}
