package r3.http.relay

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for managing r3.http.relay.PeerMessage objects with efficient lookups.
 * Maintains hash-based indexes for O(1) lookups by key and peerId.
 */
class PeerMessageRegistry {
	// Index: key -> List of PeerMessages
	private val messagesByKey = ConcurrentHashMap<String, MutableList<PeerMessage>>()

	// Index: peerId -> List of PeerMessages
	private val messagesByPeer = ConcurrentHashMap<String, MutableList<PeerMessage>>()

	/**
	 * Add a r3.http.relay.PeerMessage to the registry and update all indexes.
	 * Returns true if the message was added (didn't already exist).
	 */
	fun add(peerMessage: PeerMessage): Boolean {
		// Ensure uniqueness: do not add if already present in either index
		val alreadyPresent = messagesByPeer[peerMessage.peerId]?.any {
			it.message.key == peerMessage.message.key && it.message.topic == peerMessage.message.topic
		} == true
		if (alreadyPresent) return false
		// Add to key index
		messagesByKey.computeIfAbsent(peerMessage.message.key) {
			Collections.synchronizedList(mutableListOf())
		}.add(peerMessage)
		// Add to peer index
		messagesByPeer.computeIfAbsent(peerMessage.peerId) {
			Collections.synchronizedList(mutableListOf())
		}.add(peerMessage)
		return true
	}

	fun hasPeer(peerId: String): Boolean {
		return messagesByPeer.containsKey(peerId)
	}

	/**
	 * Remove all messages for a specific peerId and update all indexes.
	 * Returns the number of messages removed.
	 */
	fun removeByPeerId(peerId: String): Int {
		val messagesToRemove = messagesByPeer.remove(peerId) ?: return 0
		messagesToRemove.forEach { peerMessage ->
			// Update key index
			messagesByKey[peerMessage.message.key]?.let { list ->
				list.remove(peerMessage)
				if (list.isEmpty()) {
					messagesByKey.remove(peerMessage.message.key)
				}
			}
		}
		return messagesToRemove.size
	}

	/**
	 * Remove a specific r3.http.relay.PeerMessage and update all indexes.
	 * Returns true if the message was found and removed.
	 */
	fun remove(peerMessage: PeerMessage): Boolean {
		var removed = false
		messagesByKey[peerMessage.message.key]?.let { list ->
			removed = list.remove(peerMessage)
			if (list.isEmpty()) {
				messagesByKey.remove(peerMessage.message.key)
			}
		}
		messagesByPeer[peerMessage.peerId]?.let { list ->
			list.remove(peerMessage)
			if (list.isEmpty()) {
				messagesByPeer.remove(peerMessage.peerId)
			}
		}
		return removed
	}

	/**
	 * Find all PeerMessages that have the specified key.
	 * Returns an empty list if no matches found.
	 * O(1) lookup time.
	 */
	fun findByKey(key: String): List<PeerMessage> {
		return messagesByKey[key]?.toList() ?: emptyList()
	}

	/**
	 * Find all PeerMessages for a specific peer.
	 * Returns an empty list if no matches found.
	 * O(1) lookup time.
	 */
	fun findByPeerId(peerId: String): List<PeerMessage> {
		return messagesByPeer[peerId]?.toList() ?: emptyList()
	}

	/**
	 * Check if a peer has a message with the specified key.
	 * O(1) lookup time.
	 */
	fun hasMessage(peerId: String, key: String): Boolean {
		val peerMessages = messagesByPeer[peerId] ?: return false
		return peerMessages.any { it.message.key == key }
	}

	/**
	 * Get the total number of messages in the registry.
	 */
	fun size(): Int {
		// Count unique PeerMessages (all values in messagesByPeer are unique)
		return messagesByPeer.values.sumOf { it.size }
	}

	/**
	 * Check if the registry is empty.
	 */
	fun isEmpty(): Boolean {
		return messagesByPeer.isEmpty()
	}

	/**
	 * Clear all messages and indexes.
	 */
	fun clear() {
		messagesByKey.clear()
		messagesByPeer.clear()
	}

	/**
	 * Get all messages as a list (for iteration).
	 * Returns a copy to prevent concurrent modification.
	 */
	fun getAll(): List<PeerMessage> {
		// Aggregate all unique PeerMessages from messagesByPeer
		return messagesByPeer.values.flatten()
	}

	fun findByTopic(topic: String): List<PeerMessage> {
		val result = mutableListOf<PeerMessage>()
		for (peerMessages in messagesByPeer.values) {
			for (peerMessage in peerMessages) {
				if (peerMessage.message.topic == topic) {
					result.add(peerMessage)
				}
			}
		}
		// Sort by message timestamp, oldest to newest
		return result.sortedBy { it.message.timestamp }
	}
}
