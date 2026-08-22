package com.deelabs.untisplus.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.deelabs.untisplus.api.model.untis.SchoolInfo
import com.deelabs.untisplus.api.model.untis.Settings
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.data.database.entities.AbsenceReasonEntity
import com.deelabs.untisplus.data.database.entities.DepartmentEntity
import com.deelabs.untisplus.data.database.entities.DutyEntity
import com.deelabs.untisplus.data.database.entities.EventReasonEntity
import com.deelabs.untisplus.data.database.entities.EventReasonGroupEntity
import com.deelabs.untisplus.data.database.entities.ExcuseStatusEntity
import com.deelabs.untisplus.data.database.entities.HolidayEntity
import com.deelabs.untisplus.data.database.entities.KlasseEntity
import com.deelabs.untisplus.data.database.entities.RoomEntity
import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.database.entities.SubjectEntity
import com.deelabs.untisplus.data.database.entities.TeacherEntity
import com.deelabs.untisplus.data.database.entities.TeachingMethodEntity
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Database(
	version = 12,
	entities = [
		User::class,
		AbsenceReasonEntity::class,
		DepartmentEntity::class,
		DutyEntity::class,
		EventReasonEntity::class,
		EventReasonGroupEntity::class,
		ExcuseStatusEntity::class,
		HolidayEntity::class,
		KlasseEntity::class,
		RoomEntity::class,
		SubjectEntity::class,
		TeacherEntity::class,
		TeachingMethodEntity::class,
		SchoolYearEntity::class,
	],
	autoMigrations = [
		AutoMigration(from = 8, to = 9),
		AutoMigration(from = 9, to = 10),
		AutoMigration(from = 10, to = 11, spec = MigrationSpec10to11::class),
		AutoMigration(from = 11, to = 12, spec = MigrationSpec11to12::class)
	]
)
@TypeConverters(UserConverters::class)
abstract class UserDatabase : RoomDatabase() {
	abstract fun userDao(): UserDao
}

internal class UserConverters {
	private inline fun <reified T> encode(value: T?): String? =
		value?.let { Json.encodeToString<T>(it) }

	private inline fun <reified T> decode(string: String?): T? =
		string?.let { Json.decodeFromString<T>(it) }

	@TypeConverter
	fun encodeTimeGrid(timeGrid: TimeGrid?): String? = encode(timeGrid)

	@TypeConverter
	fun decodeTimeGrid(string: String?): TimeGrid? = decode(string)

	@TypeConverter
	fun encodeUntisUserData(untisUserData: UserData?): String? = encode(untisUserData)

	@TypeConverter
	fun decodeUntisUserData(string: String?): UserData? = decode(string)

	@TypeConverter
	fun encodeUntisSettings(untisSettings: Settings?): String? = encode(untisSettings)

	@TypeConverter
	fun decodeUntisSettings(string: String?): Settings? = decode(string)

	@TypeConverter
	fun encodeUntisSchoolInfo(untisSchoolInfo: SchoolInfo?): String? = encode(untisSchoolInfo)

	@TypeConverter
	fun decodeUntisSchoolInfo(string: String?): SchoolInfo? = decode(string)

	@TypeConverter
	fun encodeLongList(intList: List<Long>?): String? = encode(intList)

	@TypeConverter
	fun decodeLongList(string: String?): List<Long>? = decode(string)

	@TypeConverter
	fun encodeDate(date: LocalDate?): String? = date?.format(DateTimeFormatter.ISO_LOCAL_DATE)

	@TypeConverter
	fun decodeDate(date: String?): LocalDate? =
		date?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }

	@TypeConverter
	fun encodeTime(date: LocalTime?): String? = date?.format(DateTimeFormatter.ISO_LOCAL_TIME)

	@TypeConverter
	fun decodeTime(date: String?): LocalTime? =
		date?.let { runCatching { LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME) }.getOrNull() }
}
