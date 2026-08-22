package com.deelabs.untisplus.api.model.untis.masterdata

import com.deelabs.untisplus.api.serializer.Date
import kotlinx.serialization.Serializable

@Serializable
data class Student(
	val id: Long,
	val klasseId: Long? = null,
	val firstName: String,
	val lastName: String,
	val birthDate: Date? = null
) {
	fun fullName(): String = "$firstName $lastName"
}
