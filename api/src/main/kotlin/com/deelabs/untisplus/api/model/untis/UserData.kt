package com.deelabs.untisplus.api.model.untis

import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.enumeration.Right
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import kotlinx.serialization.Serializable

@Serializable
data class UserData(
	val elemId: Long,
	val elemType: ElementType?,
	val displayName: String,
	val schoolName: String,
	val departmentId: Long,
	val children: List<Person?> = emptyList(),
	val klassenIds: List<Long> = emptyList(),
	val rights: List<Right> = emptyList()
) {
	fun getPeriodElement() = elemType
		?.takeIf { elemId > 0 }
		?.let { PeriodElement(it, elemId) }

	fun getFallbackPeriodElements(): List<PeriodElement> = buildList {
		children.filterNotNull().forEach {
			add(PeriodElement(ElementType.STUDENT, it.id))
		}
		klassenIds.forEach {
			add(PeriodElement(ElementType.CLASS, it))
		}
	}
}
