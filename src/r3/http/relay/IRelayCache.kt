package r3.http.relay

import java.io.InputStream

/**
 * Interface for caching relay messages and blobs.
 *
 * The cache is time-based (not LRU) and can have a maximum size limit.
 * When the size limit is exceeded, older blobs are purged to make room.
 */
interface IRelayCache {
	/**
	 * Store a message in the cache.
	 * @param message The message to cache
	 */
	fun putMessage(message: Message)

	/**
	 * Store a blob with its message metadata in the cache.
	 * @param message The message metadata for the blob
	 * @param data Function that returns an InputStream for reading the blob data
	 */
	fun putBlob(message: Message, data: () -> InputStream)

	/**
	 * Retrieve a cached blob by its key.
	 * @param key The blob key
	 * @return The cached blob, or null if not found or expired
	 */
	fun getBlob(key: String): CachedBlob?

	/**
	 * Get all messages in the cache for a specific topic.
	 * @param topic The topic to filter by
	 * @return List of messages for the topic, ordered by timestamp (newest first)
	 */
	fun getMessages(topic: String): List<Message>

	/**
	 * Get all messages in the cache (across all topics).
	 * @return List of all cached messages, ordered by timestamp (newest first)
	 */
	fun getAllMessages(): List<Message>

	/**
	 * Get the total number of cached messages.
	 */
	fun getNumberOfMessages(): Int

	/**
	 * Remove entries to bring total cache size under the maximum.
	 * Removes oldest entries first until size is under the limit.
	 */
	fun purgeBySizeLimit()

	/**
	 * Get the current total size of cached blobs in bytes.
	 */
	fun getCurrentSize(): Long

	/**
	 * Clear all cached data.
	 */
	fun clear()

	/**
	 * Close the cache and release any resources.
	 */
	fun close()
}