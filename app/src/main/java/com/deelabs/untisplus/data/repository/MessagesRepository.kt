package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.data.cache.DiskCache
import com.deelabs.untisplus.model.rest.Message
import com.deelabs.untisplus.model.rest.MessagesResponse
import com.deelabs.untisplus.modules.MessagesApiFactory
import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.serialization.serializer
import java.io.File
import javax.inject.Inject
import javax.inject.Named

interface MessagesRepository {
	fun messageSource(): CachedSource<Long, Message>

	fun messagesSource(): CachedSource<Unit, MessagesResponse>

	fun messagesSentSource(): CachedSource<Unit, List<Message>>

	fun messagesDraftsSource(): CachedSource<Unit, List<Message>>
}

class UntisMessagesRepository @Inject constructor(
	private val messagesApiFactory: MessagesApiFactory,
	@Named("cacheDir") private val cacheDir: File,
	private val timeProvider: TimeProvider,
) : MessagesRepository {
	override fun messageSource(): CachedSource<Long, Message> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessage(params).body()
			},
			cache = DiskCache(File(cacheDir, "messenger/message"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun messagesSource(): CachedSource<Unit, MessagesResponse> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessages().body()
			},
			cache = DiskCache(File(cacheDir, "messenger/messages"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun messagesSentSource(): CachedSource<Unit, List<Message>> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessagesSent().body().sentMessages ?: emptyList()
			},
			cache = DiskCache(File(cacheDir, "messenger/messagesSent"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun messagesDraftsSource(): CachedSource<Unit, List<Message>> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessagesDrafts().body().draftMessages ?: emptyList()
			},
			cache = DiskCache(File(cacheDir, "messenger/messagesDrafts"), serializer()),
			timeProvider = timeProvider
		)
	}
}
