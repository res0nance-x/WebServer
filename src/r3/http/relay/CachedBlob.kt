package r3.http.relay

import java.io.InputStream

/**
 * Represents a cached blob with its metadata and data.
 */
class CachedBlob(
	val message: Message,
	val getInputStream: () -> InputStream
) {
	val sizeBytes: Long get() = message.length

	override fun toString(): String {
		return "CachedBlob(key=${message.key}, size=$sizeBytes)"
	}
}

