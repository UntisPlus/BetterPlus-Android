package com.deelabs.untisplus.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager

/**
 * Whether the active network is unmetered (Wi-Fi/ethernet). Returns false when
 * the state cannot be determined, so a "Wi-Fi only" caller stays paused rather
 * than spending mobile data.
 *
 * Uses [ConnectivityManager.isActiveNetworkMetered] (API 16) rather than the
 * API 23 `activeNetwork`/`NetworkCapabilities` pair so it works on API 21.
 */
fun Context.isOnUnmeteredNetwork(): Boolean {
	val connectivityManager =
		getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
	return !connectivityManager.isActiveNetworkMetered
}

/** Whether the system is currently in battery saver mode. */
fun Context.isPowerSaveMode(): Boolean =
	(getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
