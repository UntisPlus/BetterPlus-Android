package com.deelabs.untisplus.api.model.response

import com.deelabs.untisplus.api.model.untis.MasterData
import com.deelabs.untisplus.api.model.untis.messenger.MessengerSettings
import com.deelabs.untisplus.api.model.untis.Settings
import com.deelabs.untisplus.api.model.untis.UserData
import kotlinx.serialization.Serializable

@Serializable
data class UserDataResponse(
		val result: UserDataResult? = null
) : BaseResponse()

@Serializable
data class UserDataResult(
	val masterData: MasterData,
	val userData: UserData,
	val settings: Settings? = null,
	val messengerSettings: MessengerSettings? = null
)
