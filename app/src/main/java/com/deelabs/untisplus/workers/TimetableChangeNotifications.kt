package com.deelabs.untisplus.workers

import android.app.Notification
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
import com.deelabs.untisplus.R
import com.deelabs.untisplus.activities.MainActivity
import com.deelabs.untisplus.api.model.proxy.ProxyTimetableChangeRow

/**
 * Shared notification plumbing for timetable change detection, used by both the
 * periodic [TimetableChangeWorker] and the live
 * [com.deelabs.untisplus.services.TimetableChangeService].
 */
object TimetableChangeNotifications {
	const val CHANNEL_ID_CHANGES = "notifications_channel_timetablechanges"
	const val CHANNEL_ID_SERVICE = "notifications_channel_timetablechanges_service"

	const val NOTIFICATION_ID_CHANGES = 5401
	const val NOTIFICATION_ID_SERVICE = 5402

	fun ensureChannels(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL_ID_CHANGES,
				context.getString(R.string.notifications_channel_timetablechanges),
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = context.getString(R.string.notifications_channel_timetablechanges_desc)
			}
		)

		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL_ID_SERVICE,
				context.getString(R.string.notifications_channel_timetablechanges_service),
				NotificationManager.IMPORTANCE_LOW
			).apply {
				description = context.getString(R.string.notifications_channel_timetablechanges_service_desc)
			}
		)
	}

	fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
		context,
		0,
		Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
		},
		FLAG_IMMUTABLE
	)

	/** Notification shown while the live change watcher is connected. */
	fun serviceNotification(context: Context): Notification =
		NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
			.setContentTitle(context.getString(R.string.notifications_timetable_changes_service_title))
			.setContentText(context.getString(R.string.notifications_timetable_changes_service_text))
			.setSmallIcon(R.drawable.notification_clock)
			.setContentIntent(contentIntent(context))
			.setOngoing(true)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build()

	/** "Timetable changed" alert for [changes]. */
	fun changeNotification(context: Context, changes: List<ProxyTimetableChangeRow>): Notification =
		NotificationCompat.Builder(context, CHANNEL_ID_CHANGES)
			.setContentTitle(context.getString(R.string.notifications_timetable_changes_title))
			.setContentText(context.getString(R.string.notifications_timetable_changes_text, changes.size))
			.setStyle(NotificationCompat.BigTextStyle().bigText(changes.summaryText()))
			.setSmallIcon(R.drawable.notification_clock)
			.setContentIntent(contentIntent(context))
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_STATUS)
			.build()

	/**
	 * Maps the proxy change rows to a compact, human-readable list. The proxy
	 * sends period rows (subject/room/kind), not full WebUntis
	 * [com.deelabs.untisplus.api.model.untis.timetable.Period]s, so this only
	 * extracts what the digest carries.
	 */
	fun List<ProxyTimetableChangeRow>.summaryText(): String = joinToString("\n") { row ->
		val subjectRoom = listOfNotNull(
			row.subject?.takeIf { it.isNotBlank() },
			row.room?.takeIf { it.isNotBlank() }
		).joinToString(" · ")
		val label = subjectRoom.ifBlank {
			row.description?.takeIf { it.isNotBlank() } ?: "#${row.periodId}"
		}
		when {
			row.kind.equals("ADDED", ignoreCase = true) -> "+ $label"
			row.kind.equals("REMOVED", ignoreCase = true) -> "– $label"
			else -> "~ $label"
		}
	}

	fun canPostNotifications(context: Context): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
			ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

	/** Posts the change notification, tolerating a missing/un-granted permission. */
	fun postChanges(context: Context, changes: List<ProxyTimetableChangeRow>) {
		if (!canPostNotifications(context)) return
		try {
			NotificationManagerCompat.from(context)
				.notify(NOTIFICATION_ID_CHANGES, changeNotification(context, changes))
		} catch (e: SecurityException) {
			Log.w("TimetableChangeNotifs", "Notification permission missing", e)
		}
	}
}
