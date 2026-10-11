package com.deelabs.untisplus.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deelabs.untisplus.api.model.proxy.ProxyTimetableChangeRow
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.repository.TimetableChangeBus
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.helpers.isOnUnmeteredNetwork
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodically polls the untis-proxy for timetable changes for the active user's
 * class and posts a notification when substitutions are published
 * (`docs/APP-INTEGRATION.md` §5). The last seen class/event version is persisted
 * per user so the poll can resume across restarts.
 *
 * This is the background backstop; while the app is running the live
 * [com.deelabs.untisplus.services.TimetableChangeService] delivers changes
 * within seconds. Both share the persisted version, so a change is notified
 * only once.
 */
@HiltWorker
class TimetableChangeWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val userRepository: UserRepository,
	private val userSettingsRepository: UserSettingsRepository,
	private val proxyRepository: ProxyRepository,
	private val timetableChangeBus: TimetableChangeBus,
) : CoroutineWorker(context, params) {
	companion object {
		private const val LOG_TAG = "TimetableChangeWorker"
		private const val WORK_NAME = "TimetableChangeWork"

		/** Starts (or keeps) the periodic backstop poll. */
		fun schedule(context: Context) {
			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				WORK_NAME,
				ExistingPeriodicWorkPolicy.KEEP,
				PeriodicWorkRequestBuilder<TimetableChangeWorker>(15, TimeUnit.MINUTES)
					.setConstraints(
						Constraints.Builder()
							.setRequiredNetworkType(NetworkType.CONNECTED)
							.build()
					)
					.build()
			)
		}

		fun cancel(context: Context) {
			WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
		}
	}

	override suspend fun doWork(): Result {
		val user = userRepository.currentUser ?: return Result.success()
		if (!user.usesUntisPlusProxy) return Result.success()

		val classId = user.proxyClassId ?: return Result.success()
		val settings = userSettingsRepository.getSettings(user.id).first()
		if (!settings.timetableChangeNotifications) return Result.success()

		// Honour "Wi-Fi only": skip the poll on metered connections rather than
		// spending mobile data on a background check.
		if (settings.timetableChangeWifiOnly && !applicationContext.isOnUnmeteredNetwork()) {
			return Result.success()
		}

		// Cheap pre-flight: skip the poll while the proxy is unreachable.
		if (!proxyRepository.isReachable(user)) return Result.success()

		return try {
			val sinceEvents = settings.timetableChangeSinceEvents.takeIf { it > 0 }
			val changes = proxyRepository.timetableChanges(
				classId = classId,
				since = settings.timetableChangeSince,
				sinceEvents = sinceEvents
			) ?: return Result.success() // 304: nothing new

			userSettingsRepository.updateSettings {
				timetableChangeSince = changes.current
				timetableChangeSinceEvents = changes.eventVersion ?: timetableChangeSinceEvents
			}

			if (changes.changes.isNotEmpty()) {
				notifyChanges(changes.changes)
				timetableChangeBus.notifyChanged()
			}
			Result.success()
		} catch (e: Exception) {
			Log.w(LOG_TAG, "Timetable change poll failed", e)
			Result.retry()
		}
	}

	private fun notifyChanges(changes: List<ProxyTimetableChangeRow>) {
		TimetableChangeNotifications.ensureChannels(applicationContext)
		TimetableChangeNotifications.postChanges(applicationContext, changes)
	}
}
