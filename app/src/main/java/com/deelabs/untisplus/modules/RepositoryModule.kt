package com.deelabs.untisplus.modules

import com.deelabs.untisplus.data.repository.InfoCenterRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.MessagesRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UntisInfoCenterRepository
import com.deelabs.untisplus.data.repository.UntisMasterDataRepository
import com.deelabs.untisplus.data.repository.UntisMessagesRepository
import com.deelabs.untisplus.data.repository.UntisTimetableRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
	@Binds
	fun bindMasterDataRepository(
		implementation: UntisMasterDataRepository
	): MasterDataRepository

	@Binds
	fun bindTimetableRepository(
		implementation: UntisTimetableRepository
	): TimetableRepository

	@Binds
	fun bindInfoCenterRepository(
		implementation: UntisInfoCenterRepository
	): InfoCenterRepository

	@Binds
	fun bindMessagesRepository(
		implementation: UntisMessagesRepository
	): MessagesRepository
}
