package com.deelabs.untisplus.modules

import com.deelabs.untisplus.services.WeekLogicService
import com.deelabs.untisplus.services.WeekLogicServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent


@Module
@InstallIn(SingletonComponent::class)
interface WeekLogicModule {
	@Binds
	fun bindWeekLogicService(
		implementation: WeekLogicServiceImpl
	): WeekLogicService
}
