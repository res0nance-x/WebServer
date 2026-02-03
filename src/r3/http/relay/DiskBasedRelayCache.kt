package r3.http.relay

import org.json.JSONObject
import r3.io.log
import r3.io.toDataInputStream
import r3.io.toFileSafe
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Disk-based implementation of IRelayCache.
 *
 * Stores blobs as files on disk and maintains an index of messages in memory.
 *
 * @param cacheDir Directory where cached blobs will be stored
 * @param maxSizeBytes Maximum total size of cached blobs in bytes. 0 means unlimited.
 */
class DiskBasedRelayCache(
	cacheDir: File,
	private val maxSizeBytes: Long = 0
) : IRelayCache {
	val blobDir = File(cacheDir, "blobs").apply { mkdirs() }
	val messageDir = File(cacheDir, "messages").apply { mkdirs() }
	private val lock = ReentrantReadWriteLock()
	private val msgMap = ConcurrentHashMap<String, Message>()

	init {
		for (msgFile in messageDir.listFiles() ?: emptyArray()) {
			try {
				val json = JSONObject(msgFile.readText())
				val msg = Message(json)
				msgMap[msg.key] = msg
			} catch (e: Exception) {
				log("Error reading message file: $e")
			}
		}
		log("DiskBasedRelayCache initialized at: ${cacheDir.absolutePath}")
	}

	override fun putMessage(message: Message) {
		msgMap[message.key] = message
		File(messageDir, message.key.toFileSafe()).writeText(message.toJson().toString())
		log("Cached message: ${message.key}")
	}

	override fun putBlob(message: Message, data: () -> InputStream) {
		lock.write {
			try {
				// Check if file is too large to cache (more than 1/10th of cache size)
				if (maxSizeBytes > 0 && message.length > maxSizeBytes / 10) {
					log("Skipping cache for ${message.key}: file size (${message.length} bytes) exceeds 1/10th of cache limit (${maxSizeBytes / 10} bytes)")
					return
				}
				val blobFile = File(blobDir, message.key.toFileSafe())
				// Write data to file
				var bytesWritten = 0L
				data().use { input ->
					FileOutputStream(blobFile).use { output ->
						val buffer = ByteArray(8192)
						var read: Int
						while (input.read(buffer).also { read = it } != -1) {
							output.write(buffer, 0, read)
							bytesWritten += read
						}
					}
				}
				// Double-check actual size matches expected size
				if (bytesWritten != message.length) {
					log("Warning: Actual size (${bytesWritten}) differs from expected size (${message.length}) for ${message.key}")
				}

				putMessage(message)

				log("Cached blob to disk: ${message.key} (${bytesWritten} bytes) at ${blobFile.absolutePath}")

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
			val message = msgMap[key]
			if (message == null) {
				log("Message file missing for key $key, cannot retrieve blob")
				return@read null
			}
			val blobFile = File(blobDir, key.toFileSafe())
			if (!blobFile.exists()) {
				log("Blob file missing for key $key, removing from cache")
				return@read null
			}

			CachedBlob(
				message = message,
				getInputStream = { FileInputStream(blobFile).buffered() }
			)
		}
	}

	override fun getMessages(topic: String): List<Message> {
		return lock.read {
			msgMap.values.toList().filter { it.topic == topic }.sortedBy { it.timestamp }  // Oldest to newest
		}
	}

	override fun getAllMessages(): List<Message> {
		return lock.read {
			msgMap.values.toList().sortedBy { it.timestamp }  // Oldest to newest
		}
	}

	override fun getNumberOfMessages(): Int {
		return msgMap.size
	}

	override fun purgeBySizeLimit() {
		if (maxSizeBytes <= 0) return

		lock.write {
			val allMessages = getAllMessages()  // Returns oldest to newest
			val currentSize = allMessages.sumOf { it.length }

			if (currentSize > maxSizeBytes) {
				val delSize = currentSize - maxSizeBytes
				var total = 0L
				var pos = 0
				// getAllMessages returns oldest first, so we can iterate directly
				for (msg in allMessages) {
					total += msg.length
					pos++
					if (total >= delSize) {
						break
					}
				}
				val toDelete = allMessages.take(pos)
				log("Cache size ($currentSize bytes) exceeds limit ($maxSizeBytes bytes), purging $pos oldest blobs...")

				for (msg in toDelete) {
					val blobFile = File(blobDir, msg.key.toFileSafe())
					if (blobFile.exists()) {
						blobFile.delete()
					}
					msgMap.remove(msg.key)
				}
				val newSize = getCurrentSize()
				log("Purged $pos blobs to reduce cache size to $newSize bytes")
			}
		}
	}

	override fun getCurrentSize(): Long {
		return lock.read {
			getAllMessages().sumOf { it.length }
		}
	}

	override fun clear() {
		lock.write {
			msgMap.clear()
			blobDir.listFiles()?.forEach { it.delete() }
			log("Cleared cache")
		}
	}

	override fun close() {
	}
}
