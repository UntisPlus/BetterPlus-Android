package com.deelabs.untisplus.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.deelabs.untisplus.api.client.ProxyStreamEvent
import com.deelabs.untisplus.api.model.proxy.ProxyTimetableChangeRow
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.repository.TimetableChangeBus
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.workers.TimetableChangeNotifications
import com.deelabs.untisplus.workers.TimetableChangeNotifications.NOTIFICATION_ID_SERVICE
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a live Server-Sent-Events connection to the untis-proxy
 * (`GET /api/timetable/stream`, `docs/APP-INTEGRATION.md` §5) so timetable
 * changes arrive within seconds instead of the 15-minute WorkManager window.
 *
 * Runs as a `dataSync` foreground service with an ongoing notification, so it is
 * only started while the user enables timetable change notifications. The
 * periodic [com.deelabs.untisplus.workers.TimetableChangeWorker] remains as a
 * backstop; both share the persisted change version, so nothing is announced
 * twice.
 */
@AndroidEntryPoint
class TimetableChangeService : Service() {
	companion object {
		private const val LOG_TAG = "TimetableChangeService"
		private const val RECONNECT_DELAY_MS = 15_000L

		fun start(context: Context) {
			ContextCompat.startForegroundService(context, Intent(context, TimetableChangeService::class.java))
		}

		fun stop(context: Context) {
			context.stopService(Intent(context, TimetableChangeService::class.java))
		}
	}

	@Inject
	lateinit var userRepository: UserRepository

	@Inject
	lateinit var userSettingsRepository: UserSettingsRepository

	@Inject
	lateinit var proxyRepository: ProxyRepository

	@Inject
	lateinit var changeBus: TimetableChangeBus

	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var watchJob: Job? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		TimetableChangeNotifications.ensureChannels(this)
		startForeground()

		if (watchJob?.isActive != true) {
			watchJob = scope.launch { watch() }
		}

		return START_STICKY
	}

	private fun startForeground() {
		val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
		} else {
			0
		}
		ServiceCompat.startForeground(
			this,
			NOTIFICATION_ID_SERVICE,
			TimetableChangeNotifications.serviceNotification(this),
			type
		)
	}

	private suspend fun watch() {
		while (scope.isActive) {
			val user = userRepository.currentUser
			if (user == null || !user.usesUntisPlusProxy ||
				!userSettingsRepository.getSettings(user.id).first().timetableChangeNotifications
			) {
				stopSelf()
				return
			}

			try {
				if (!proxyRepository.isReachable(user)) {
					delay(RECONNECT_DELAY_MS)
					continue
				}
				proxyRepository.timetableStream().collect { event ->
					when (event) {
						is ProxyStreamEvent.Snapshot -> persistVersions(event.current, event.eventVersion)
						is ProxyStreamEvent.Change -> {
							persistVersions(event.version, null)
							if (event.changes.isNotEmpty()) notify(event.changes)
							changeBus.notifyChanged()
						}
						is ProxyStreamEvent.StudentEvents -> {
							userSettingsRepository.updateSettings { timetableChangeSinceEvents = event.eventVersion }
							changeBus.notifyChanged()
						}
					}
				}
				// Flow completed without error (server closed): reconnect.
			} catch (e: Exception) {
				Log.w(LOG_TAG, "SSE stream dropped, reconnecting", e)
			}

			if (!scope.isActive) return
			delay(RECONNECT_DELAY_MS)
		}
	}

	private suspend fun persistVersions(current: Long, eventVersion: Long?) {
		userSettingsRepository.updateSettings {
			if (current > 0) timetableChangeSince = current
			eventVersion?.let { timetableChangeSinceEvents = it }
		}
	}

	private fun notify(changes: List<ProxyTimetableChangeRow>) {
		TimetableChangeNotifications.postChanges(this, changes)
	}

	override fun onDestroy() {
		scope.coroutineContext.cancel()
		super.onDestroy()
	}
}
