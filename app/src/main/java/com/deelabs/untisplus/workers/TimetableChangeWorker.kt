package com.deelabs.untisplus.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deelabs.untisplus.R
import com.deelabs.untisplus.activities.MainActivity
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
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
 * WorkManager's minimum period is 15 minutes; near-real-time delivery would need
 * the SSE feed in a foreground service (still TODO).
 */
@HiltWorker
class TimetableChangeWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val userRepository: UserRepository,
	private val userSettingsRepository: UserSettingsRepository,
	private val proxyRepository: ProxyRepository,
) : CoroutineWorker(context, params) {
	companion object {
		private const val LOG_TAG = "TimetableChangeWorker"
		private const val WORK_NAME = "TimetableChangeWork"

		const val CHANNEL_ID_TIMETABLE_CHANGES = "notifications.timetablechanges"
		private const val NOTIFICATION_ID = 4242

		fun schedule(context: Context) {
			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				WORK_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
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
				notifyChanges(changes.changes.size)
			}
			Result.success()
		} catch (e: Exception) {
			Log.w(LOG_TAG, "Timetable change poll failed", e)
			Result.retry()
		}
	}

	private fun notifyChanges(count: Int) {
		if (!canPostNotifications()) return

		val context = applicationContext
		val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			manager.createNotificationChannel(
				NotificationChannel(
					CHANNEL_ID_TIMETABLE_CHANGES,
					context.getString(R.string.notifications_channel_timetablechanges),
					NotificationManager.IMPORTANCE_DEFAULT
				).apply {
					description = context.getString(R.string.notifications_channel_timetablechanges_desc)
				}
			)
		}

		val pendingIntent = PendingIntent.getActivity(
			context,
			0,
			Intent(context, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
			},
			FLAG_IMMUTABLE
		)

		val builder = NotificationCompat.Builder(context, CHANNEL_ID_TIMETABLE_CHANGES)
			.setContentTitle(context.getString(R.string.notifications_timetable_changes_title))
			.setContentText(context.getString(R.string.notifications_timetable_changes_text, count))
			.setSmallIcon(R.drawable.notification_clock)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_STATUS)

		NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
	}

	private fun canPostNotifications(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return ContextCompat.checkSelfPermission(
				applicationContext,
				android.Manifest.permission.POST_NOTIFICATIONS
			) == PackageManager.PERMISSION_GRANTED
		}
		return true
	}
}
