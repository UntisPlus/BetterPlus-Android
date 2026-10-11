package com.deelabs.untisplus.activities

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deelabs.untisplus.workers.DailyWorker
import com.deelabs.untisplus.workers.DailyWorker.Companion.TAG_DAILY_WORK
import com.deelabs.untisplus.workers.ImportedCalendarSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {
	@Inject lateinit var workerFactory: HiltWorkerFactory;

	@OptIn(DelicateCoroutinesApi::class)
	override fun onCreate() {
		super.onCreate()

		GlobalScope.launch {
			WorkManager.getInstance(applicationContext).apply {
				cancelAllWorkByTag(TAG_DAILY_WORK)
				enqueue(OneTimeWorkRequestBuilder<DailyWorker>().build())
			}
		}

		ImportedCalendarSyncWorker.schedule(applicationContext)
	}

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setMinimumLoggingLevel(Log.VERBOSE)
			.setWorkerFactory(workerFactory)
			.build()
}
