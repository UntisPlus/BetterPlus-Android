package com.deelabs.untisplus.api.model

import com.deelabs.untisplus.api.model.untis.Person
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserDataTest {
	@Test
	fun `getPeriodElement returns element for valid account`() {
		val userData = userData(elemType = ElementType.STUDENT, elemId = 42)

		assertEquals(PeriodElement(ElementType.STUDENT, 42), userData.getPeriodElement())
	}

	@Test
	fun `getPeriodElement returns null when elemType is missing`() {
		assertNull(userData(elemType = null, elemId = 42).getPeriodElement())
	}

	@Test
	fun `getPeriodElement returns null when elemId is zero`() {
		assertNull(userData(elemType = ElementType.PARENT, elemId = 0).getPeriodElement())
	}

	@Test
	fun `getFallbackPeriodElements prefers children over classes`() {
		val userData = userData(
			elemType = ElementType.PARENT,
			elemId = 0,
			children = listOf(Person(7, "A", "B"), Person(8, "C", "D")),
			klassenIds = listOf(99)
		)

		assertEquals(
			listOf(
				PeriodElement(ElementType.STUDENT, 7),
				PeriodElement(ElementType.STUDENT, 8),
				PeriodElement(ElementType.CLASS, 99)
			),
			userData.getFallbackPeriodElements()
		)
	}

	@Test
	fun `getFallbackPeriodElements skips null children`() {
		val userData = userData(
			elemType = null,
			elemId = 5,
			children = listOf(null, Person(11, "E", "F"))
		)

		assertEquals(listOf(PeriodElement(ElementType.STUDENT, 11)), userData.getFallbackPeriodElements())
	}

	private fun userData(
		elemType: ElementType?,
		elemId: Long,
		children: List<Person?> = emptyList(),
		klassenIds: List<Long> = emptyList()
	) = UserData(
		elemId = elemId,
		elemType = elemType,
		displayName = "Test User",
		schoolName = "Test School",
		departmentId = 0,
		children = children,
		klassenIds = klassenIds
	)
}
