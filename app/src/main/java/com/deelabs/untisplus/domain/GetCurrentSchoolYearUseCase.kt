package com.deelabs.untisplus.domain

import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.repository.MasterDataRepository
import java.time.LocalDate
import javax.inject.Inject

class GetCurrentSchoolYearUseCase @Inject constructor(
	private val masterDataRepository: MasterDataRepository
) {
	operator fun invoke(currentDate: LocalDate = LocalDate.now()): SchoolYearEntity? {
		return masterDataRepository.userData?.schoolYears?.find {
			currentDate.isAfter(it.startDate) && currentDate.isBefore(it.endDate)
		}
	}
}
