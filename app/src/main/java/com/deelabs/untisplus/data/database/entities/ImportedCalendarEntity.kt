package com.deelabs.untisplus.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An external iCalendar feed imported by the user. Events are fetched from
 * [url] (source [SOURCE_URL]) or read from the copied local file at
 * [localPath] (source [SOURCE_FILE]) and stored as [ImportedCalendarEventEntity].
 */
@Entity(
	tableName = "ImportedCalendar",
	indices = [Index("userId")],
	foreignKeys = [ForeignKey(
		entity = User::class,
		parentColumns = ["id"],
		childColumns = ["userId"],
		onDelete = ForeignKey.CASCADE
	)]
)
data class ImportedCalendarEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val userId: Long = -1,
	val name: String = "",
	val url: String? = null,
	val localPath: String? = null,
	val source: String = SOURCE_URL,
	val color: Int = DEFAULT_COLOR,
	val enabled: Boolean = true,
	val refreshIntervalMinutes: Int = DEFAULT_REFRESH_MINUTES,
	val lastRefresh: Long? = null,
	val lastError: String? = null,
	val etag: String? = null,
	val lastModified: String? = null,
) {
	companion object {
		const val SOURCE_URL = "url"
		const val SOURCE_FILE = "file"
		const val DEFAULT_COLOR = 0xFF00897B.toInt()
		const val DEFAULT_REFRESH_MINUTES = 360
	}
}
