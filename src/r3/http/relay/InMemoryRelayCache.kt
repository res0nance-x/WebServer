package r3.http.relay

import r3.io.log
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * In-memory implementation of IRelayCache.
 *
 * @param maxSizeBytes Maximum total size of cached blobs in bytes. 0 means unlimited.
 */
class InMemoryRelayCache(
	private val maxSizeBytes: Long = 0
) : IRelayCache {
	private val lock = ReentrantReadWriteLock()

	// Map of key -> Message for all messages
	private val messages = ConcurrentHashMap<String, MessageEntry>()

	// Map of key -> CachedBlob for blobs
	private val blobs = ConcurrentHashMap<String, BlobEntry>()

	private data class MessageEntry(
		val message: Message,
		val timestamp: Long = System.currentTimeMillis()
	)

	private data class BlobEntry(
		val data: ByteArray,
		val cachedBlob: CachedBlob
	)

	override fun putMessage(message: Message) {
		lock.write {
			messages[message.key] = MessageEntry(message)
			log("Cached message: ${message.key}")
		}
	}

	override fun putBlob(message: Message, data: () -> InputStream) {
		lock.write {
			try {
				// Check if file is too large to cache (more than 1/10th of cache size)
				if (maxSizeBytes > 0 && message.length > maxSizeBytes / 10) {
					log("Skipping cache for ${message.key}: file size (${message.length} bytes) exceeds 1/10th of cache limit (${maxSizeBytes / 10} bytes)")
					return
				}
				// Read the entire stream into memory
				val bytes = data().readAllBytes()
				// Double-check actual size matches expected size
				if (bytes.size.toLong() != message.length) {
					log("Warning: Actual size (${bytes.size}) differs from expected size (${message.length}) for ${message.key}")
				}
				// Store the message
				messages[message.key] = MessageEntry(message)
				// Store the blob
				val cachedBlob = CachedBlob(
					message = message,
					getInputStream = { ByteArrayInputStream(bytes) }
				)
				blobs[message.key] = BlobEntry(bytes, cachedBlob)

				log("Cached blob: ${message.key} (${bytes.size} bytes)")
				// Check if we need to purge by size
				if (maxSizeBytes > 0) {
					purgeBySizeLimit()
				}
			} catch (e: Exception) {
				log("Error caching blob for key ${message.key}: $e")
			}
		}
	}

	override fun getBlob(key: String): CachedBlob? {
		return lock.read {
			blobs[key]?.cachedBlob
		}
	}

	override fun getMessages(topic: String): List<Message> {
		return lock.read {
			messages.values
				.filter { it.message.topic == topic }
				.sortedBy { it.timestamp }  // Oldest to newest
				.map { it.message }
		}
	}

	override fun getAllMessages(): List<Message> {
		return lock.read {
			messages.values
				.sortedBy { it.timestamp }  // Oldest to newest
				.map { it.message }
		}
	}

	override fun getNumberOfMessages(): Int {
		return lock.read {
			messages.size
		}
	}

	override fun purgeBySizeLimit() {
		if (maxSizeBytes <= 0) return

		lock.write {
			val allMessages = getAllMessages()
			val currentSize = allMessages.sumOf { it.length }

			if (currentSize > maxSizeBytes) {
				val delSize = currentSize - maxSizeBytes
				var total = 0L
				var pos = 0
				// getAllMessages returns newest first, so we need to count from the END (oldest)
				val oldestFirst = allMessages.reversed()
				for (msg in oldestFirst) {
					total += msg.length
					pos++
					if (total >= delSize) {
						break
					}
				}
				val toDelete = oldestFirst.take(pos)
				for (msg in toDelete) {
					messages.remove(msg.key)
					blobs.remove(msg.key)
				}
			}
		}
	}

	override fun getCurrentSize(): Long {
		return lock.read {
			getCurrentSizeUnsafe()
		}
	}

	private fun getCurrentSizeUnsafe(): Long {
		return blobs.values.sumOf { it.data.size.toLong() }
	}

	override fun clear() {
		lock.write {
			messages.clear()
			blobs.clear()
			log("Cleared cache")
		}
	}

	override fun close() {
		clear()
	}
}

