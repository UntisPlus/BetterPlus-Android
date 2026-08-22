package com.deelabs.untisplus.api.exception

import com.deelabs.untisplus.api.model.response.Error

class UntisApiException(val error: Error?) : Throwable(error?.message) {
}
