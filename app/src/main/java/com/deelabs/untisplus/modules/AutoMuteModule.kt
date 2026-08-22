package com.deelabs.untisplus.modules

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.deelabs.untisplus.services.AutoMuteService
import com.deelabs.untisplus.services.AutoMuteServiceInterruptionFilterImpl
import com.deelabs.untisplus.services.AutoMuteServiceRingerModeImpl
import com.deelabs.untisplus.services.AutoMuteServiceZenRuleImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AutoMuteModule {
	@Provides
	fun provideNotificationManager(
		@ApplicationContext context: Context
	): NotificationManager? = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

	@Provides
	fun provideAutoMuteService(
		@ApplicationContext context: Context,
		notificationManager: NotificationManager?
	): AutoMuteService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		AutoMuteServiceZenRuleImpl(context, notificationManager!!)
	} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
		AutoMuteServiceInterruptionFilterImpl(notificationManager!!)
	} else {
		AutoMuteServiceRingerModeImpl(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
	}
}
