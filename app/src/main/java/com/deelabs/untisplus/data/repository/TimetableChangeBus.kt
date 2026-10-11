package com.deelabs.untisplus.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process signal that the proxy reported a timetable change. The SSE
 * foreground service ([com.deelabs.untisplus.services.TimetableChangeService])
 * and the periodic worker bump [updates]; open timetable views reload when it
 * changes.
 *
 * A monotonically increasing counter is used instead of `Unit` so that
 * `StateFlow` does not conflate repeated signals, and so an observer that
 * starts late still sees the latest value.
 */
@Singleton
class TimetableChangeBus @Inject constructor() {
	private val _updates = MutableStateFlow(0L)
	val updates: StateFlow<Long> = _updates

	fun notifyChanged() {
		_updates.value += 1
	}
}
