package com.deelabs.untisplus.modules

import com.deelabs.untisplus.domain.GetMessagesOfDayUseCase
import com.deelabs.untisplus.domain.GetMessagesOfDayUseCaseImpl
import com.deelabs.untisplus.domain.GetMessagesUseCase
import com.deelabs.untisplus.domain.GetMessagesUseCaseImpl
import com.deelabs.untisplus.domain.GetRoomFinderItemsUseCase
import com.deelabs.untisplus.domain.GetRoomFinderItemsUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface UseCaseModule {
	@Binds
	fun bindGetMessagesOfDayUseCase(
		implementation: GetMessagesOfDayUseCaseImpl
	): GetMessagesOfDayUseCase

	@Binds
	fun bindGetMessagesUseCase(
		implementation: GetMessagesUseCaseImpl
	): GetMessagesUseCase

	@Binds
	fun bindGetRoomFinderItemsUseCase(
		implementation: GetRoomFinderItemsUseCaseImpl
	): GetRoomFinderItemsUseCase
}
