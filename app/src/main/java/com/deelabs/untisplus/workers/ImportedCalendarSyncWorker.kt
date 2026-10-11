package com.deelabs.untisplus.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deelabs.untisplus.data.repository.ImportedCalendarRepository
import com.deelabs.untisplus.data.repository.UserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes calendars imported from external `.ics` feeds. The
 * on-demand refresh happens when a calendar is added; this is the background
 * backstop that keeps long-lived subscriptions up to date.
 */
@HiltWorker
class ImportedCalendarSyncWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val userRepository: UserRepository,
	private val importedCalendarRepository: ImportedCalendarRepository,
) : CoroutineWorker(context, params) {

	companion object {
		private const val WORK_NAME = "ImportedCalendarSyncWork"

		fun schedule(context: Context) {
			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				WORK_NAME,
				ExistingPeriodicWorkPolicy.KEEP,
				PeriodicWorkRequestBuilder<ImportedCalendarSyncWorker>(6, TimeUnit.HOURS)
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
		if (userRepository.currentUser == null) return Result.success()
		return try {
			importedCalendarRepository.refreshAll()
			Result.success()
		} catch (e: Exception) {
			Result.retry()
		}
	}
}
