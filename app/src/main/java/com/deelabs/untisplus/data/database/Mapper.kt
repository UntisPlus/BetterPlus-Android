package com.deelabs.untisplus.data.database

fun interface Mapper<in From, out To> {
	fun map(from: From, userId: Long): To
}
