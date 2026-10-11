package com.deelabs.untisplus.mappers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.graphics.toColorInt
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.enumeration.PeriodState
import com.deelabs.untisplus.api.model.untis.masterdata.timegrid.Day
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.data.repository.withDefault
import com.deelabs.untisplus.data.settings.model.UserSettings
import com.deelabs.untisplus.helpers.BuildConfigFieldsProvider
import com.deelabs.untisplus.models.PeriodItem
import com.deelabs.untisplus.models.TimetableItem
import com.deelabs.untisplus.ui.weekview.DefaultLessonColors
import com.deelabs.untisplus.ui.weekview.Event
import com.deelabs.untisplus.ui.weekview.EventStyle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class TimetableMapper @Inject constructor(
	private val userRepository: UserRepository,
	private val userSettingsRepository: UserSettingsRepository,
	private val masterDataRepository: MasterDataRepository,
	private val buildConfigFieldsProvider: BuildConfigFieldsProvider,
) {
	/**
	 * Prepares periods for further processing or displaying.
	 *
	 * This function filters out items that should be hidden and merges multi-hour periods.
	 *
	 * @param items List of items that should be prepared
	 * @param hideCancelled Whether cancelled items should be removed
	 * @return A list of prepared items
	 */
	fun preparePeriods(
		items: List<Period>,
		hideCancelled: Boolean
	): List<Period> = items
		.filterPeriods(hideCancelled)
		.mergePeriods(userRepository.currentUser!!.timeGrid.days)

	suspend fun mapTimetablePeriodsToWeekViewEvents(
		items: List<Period>,
		contextType: ElementType
	): List<Event<TimetableItem>> {
		val settings = waitForSettings()
		return preparePeriods(items, settings.timetableHideCancelled)
			.mapToPeriodItems()
			.apply { markSubstitutionsIrregular(settings.timetableSubstitutionsIrregular) }
			.mapToEvents(settings, contextType)
	}

	private suspend fun waitForSettings() = userSettingsRepository.getSettings().filterNotNull().first()

	private fun List<Period>.mapToPeriodItems(): List<PeriodItem> =
		map { PeriodItem(masterDataRepository = masterDataRepository, originalPeriod = it) }

	/**
	 * Marks items as irregular when elements were substituted.
	 *
	 * Must run before events are styled, otherwise forced-irregular items are
	 * rendered with regular colors (upstream issues #489/#502).
	 */
	private fun List<PeriodItem>.markSubstitutionsIrregular(enabled: Boolean) {
		if (!enabled) return
		forEach { item ->
			//TODO|| item.forceIrregular = item.forceIrregular || item.originalPeriod.backColor != UNTIS_DEFAULT_COLOR
			item.forceIrregular =
				item.classes.any { it.id != it.orgId }
					|| item.teachers.any { it.id != it.orgId }
					|| item.subjects.any { it.id != it.orgId }
					|| item.rooms.any { it.id != it.orgId }
		}
	}

	private fun List<PeriodItem>.mapToEvents(
		userSettings: UserSettings,
		contextType: ElementType,
		includeOrgIds: Boolean = true,
	): List<Event<TimetableItem>> {
		return map { periodItem ->
			Event(
				title = periodItem.getShort(ElementType.SUBJECT),
				top = (
					if (contextType == ElementType.TEACHER)
						periodItem.getShortAnnotated(
							ElementType.CLASS,
							includeOrgIds = includeOrgIds
						)
					else
						periodItem.getShortAnnotated(
							ElementType.TEACHER,
							includeOrgIds = includeOrgIds
						)
					),
				bottom = (
					if (contextType == ElementType.ROOM)
						periodItem.getShortAnnotated(
							ElementType.CLASS,
							includeOrgIds = includeOrgIds
						)
					else
						periodItem.getShortAnnotated(
							ElementType.ROOM,
							includeOrgIds = includeOrgIds
						)
					),
				eventStyle = getColorScheme(userSettings, periodItem),
				start = periodItem.originalPeriod.startDateTime,
				end = periodItem.originalPeriod.endDateTime,
				data = periodItem
			)
		}
	}

	private fun getColorScheme(
		userSettings: UserSettings,
		periodItem: PeriodItem
	): EventStyle = with(userSettings) {
		val subjectEntity = masterDataRepository.userData?.subjects?.find { it.id == periodItem.subjects.firstOrNull()?.id }

		val defaultColor = EventStyle.Custom(
			color = Color((subjectEntity?.backColor ?: periodItem.originalPeriod.backColor).toColorInt()),
			textStyle = TextStyle(color = Color((subjectEntity?.foreColor ?: periodItem.originalPeriod.foreColor).toColorInt()))
		)

		val regularColor = EventStyle.Custom(Color(backgroundRegular)).withDefault(hasBackgroundRegular(), EventStyle.Custom(DefaultLessonColors.Regular))
		val examColor = EventStyle.Custom(Color(backgroundExam)).withDefault(hasBackgroundExam(), EventStyle.Custom(DefaultLessonColors.Exam))
		val cancelledColor = EventStyle.Custom(Color(backgroundCancelled)).withDefault(hasBackgroundCancelled(), EventStyle.Custom(DefaultLessonColors.Cancelled))
		val irregularColor = EventStyle.Custom(Color(backgroundIrregular)).withDefault(hasBackgroundIrregular(), EventStyle.Custom(DefaultLessonColors.Irregular))

		return when {
			periodItem.isExam() -> if (schoolBackgroundList.contains("exam")) defaultColor else examColor
			periodItem.isCancelled() -> (if (schoolBackgroundList.contains("cancelled")) defaultColor else cancelledColor)
			periodItem.isIrregular() -> if (schoolBackgroundList.contains("irregular")) defaultColor else irregularColor
			else -> if (schoolBackgroundList.contains("regular")) defaultColor else regularColor
		}.withTextStyle(TextStyle(textDecoration = if (periodItem.isCancelled()) TextDecoration.LineThrough else TextDecoration.None))
	}

	/**
	 * Prepares the items for the timetable.
	 *
	 * This function filters out items that should be hidden
	 *
	 * @param hideCancelled Whether cancelled items should be removed
	 * @return A list of prepared items
	 */
	private fun List<Period>.filterPeriods(
		hideCancelled: Boolean,
	): List<Period> = mapNotNull { item ->
		if (hideCancelled && item.`is`(PeriodState.CANCELLED)) return@mapNotNull null
		item
	}

	private fun List<Period>.mergePeriods(days: List<Day>): List<Period> {
		val itemGrid: Array<Array<MutableList<Period>>> =
			Array(days.size) { Array(days.maxByOrNull { it.units.size }!!.units.size) { mutableListOf() } }
		val leftover: MutableList<Period> = mutableListOf()

		// TODO: Check if the day from the Untis API is always an english string
		val firstDayOfWeek =
			DayOfWeek.MONDAY //DateTimeFormat.forPattern("EEE").withLocale(Locale.ENGLISH).parseDateTime(days.first().day).dayOfWeek

		// Put all items into a two dimensional array depending on day and hour
		forEach { item ->
			val startDateTime = item.startDateTime
			val endDateTime = item.endDateTime

			val day = endDateTime.dayOfWeek.value - firstDayOfWeek.value

			if (day < 0 || day >= days.size) return@forEach

			val thisUnitStartIndex = days[day].units.indexOfFirst {
				it.startTime.truncatedTo(ChronoUnit.MINUTES)
					.equals(startDateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
			}

			val thisUnitEndIndex = days[day].units.indexOfFirst {
				it.endTime.truncatedTo(ChronoUnit.MINUTES)
					.equals(endDateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
			}

			if (thisUnitStartIndex != -1 && thisUnitEndIndex != -1) itemGrid[day][thisUnitStartIndex].add(
				item
			)
			else leftover.add(item)
		}

		val newItems = itemGrid.flatMap { unitsOfDay ->
			unitsOfDay.flatMapIndexed { unitIndex, items ->
				items.onEach {
					var i = 1
					while (unitIndex + i < unitsOfDay.size && it.mergeWith(unitsOfDay[unitIndex + i])) i++
				}
			}
		}.toMutableList()

		newItems.addAll(leftover) // Add items that didn't fit inside the timegrid. These will always be single lessons.

		return newItems
	}

	private fun Period.mergeWith(items: MutableList<Period>): Boolean {
		items.toList().forEachIndexed { i, _ ->
			if (i >= items.size) return@forEachIndexed // Needed because the number of elements can change

			val candidate = items[i]

			if (candidate.startDateTime.dayOfYear != startDateTime.dayOfYear) return@forEachIndexed

			if (this.equalsIgnoreTime(candidate)) {
				endDateTime = candidate.endDateTime
				items.removeAt(i)
				return true
			}
		}
		return false
	}
}
