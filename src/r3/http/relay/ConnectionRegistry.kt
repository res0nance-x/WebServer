package r3.http.relay

import org.nanohttpd.protocols.websocket.NanoWSD
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for managing r3.http.relay.ConnectionInfo objects with efficient lookups.
 * Maintains a list for iteration and hash-based indexes for O(1) lookups by peerId and topic.
 */
class ConnectionRegistry {
	// Primary storage - synchronized list of connections
	private val connections = Collections.synchronizedList(mutableListOf<ConnectionInfo>())

	// Index: peerId -> List of r3.http.relay.ConnectionInfo
	private val connectionsByPeer = ConcurrentHashMap<String, MutableList<ConnectionInfo>>()

	// Index: topic -> List of r3.http.relay.ConnectionInfo
	private val connectionsByTopic = ConcurrentHashMap<String, MutableList<ConnectionInfo>>()

	// Index: peerId:topic -> r3.http.relay.ConnectionInfo (unique combination)
	private val connectionsByPeerAndTopic = ConcurrentHashMap<String, ConnectionInfo>()

	/**
	 * Add a r3.http.relay.ConnectionInfo to the registry and update all indexes.
	 * If a connection with the same peerId and topic already exists, it will be replaced.
	 * Returns the old connection if one was replaced, null otherwise.
	 */
	fun add(connection: ConnectionInfo): ConnectionInfo? {
		val key = "${connection.peerId}:${connection.topic}"
		// Remove old connection if exists
		val oldConnection = connectionsByPeerAndTopic.put(key, connection)
		if (oldConnection != null) {
			removeFromIndexes(oldConnection)
		}
		// Add to primary storage
		connections.add(connection)
		// Update peer index
		connectionsByPeer.computeIfAbsent(connection.peerId) {
			Collections.synchronizedList(mutableListOf())
		}.add(connection)
		// Update topic index
		connectionsByTopic.computeIfAbsent(connection.topic) {
			Collections.synchronizedList(mutableListOf())
		}.add(connection)

		return oldConnection
	}

	/**
	 * Remove a connection by WebSocket instance.
	 * Returns true if a connection was found and removed.
	 */
	fun removeBySocket(socket: NanoWSD.WebSocket): Boolean {
		var removed: ConnectionInfo? = null

		synchronized(connections) {
			val iterator = connections.iterator()
			while (iterator.hasNext()) {
				val conn = iterator.next()
				if (conn.socket == socket) {
					iterator.remove()
					removed = conn
					break
				}
			}
		}

		removed?.let { conn ->
			removeFromIndexes(conn)
			return true
		}

		return false
	}

	/**
	 * Remove a connection with specific peerId and topic.
	 * Returns true if a connection was found and removed.
	 */
	fun removeByPeerAndTopic(peerId: String, topic: String): Boolean {
		val key = "$peerId:$topic"
		val connection = connectionsByPeerAndTopic.remove(key) ?: return false

		synchronized(connections) {
			connections.remove(connection)
		}

		removeFromIndexes(connection)
		return true
	}

	/**
	 * Remove all connections for a specific peerId.
	 * Returns the number of connections removed.
	 */
	fun removeByPeerId(peerId: String): Int {
		val connectionsToRemove = connectionsByPeer.remove(peerId) ?: return 0

		synchronized(connections) {
			connectionsToRemove.forEach { conn ->
				connections.remove(conn)
				// Update topic index
				connectionsByTopic[conn.topic]?.let { list ->
					list.remove(conn)
					if (list.isEmpty()) {
						connectionsByTopic.remove(conn.topic)
					}
				}
				// Update peer+topic index
				connectionsByPeerAndTopic.remove("${conn.peerId}:${conn.topic}")
			}
		}

		return connectionsToRemove.size
	}

	/**
	 * Remove connection from all indexes (helper method).
	 */
	private fun removeFromIndexes(connection: ConnectionInfo) {
		// Update peer index
		connectionsByPeer[connection.peerId]?.let { list ->
			list.remove(connection)
			if (list.isEmpty()) {
				connectionsByPeer.remove(connection.peerId)
			}
		}
		// Update topic index
		connectionsByTopic[connection.topic]?.let { list ->
			list.remove(connection)
			if (list.isEmpty()) {
				connectionsByTopic.remove(connection.topic)
			}
		}
		// Update peer+topic index
		connectionsByPeerAndTopic.remove("${connection.peerId}:${connection.topic}")
	}

	/**
	 * Find all connections for a specific peerId.
	 * Returns an empty list if no matches found.
	 * O(1) lookup time.
	 */
	fun findByPeerId(peerId: String): List<ConnectionInfo> {
		return connectionsByPeer[peerId]?.toList() ?: emptyList()
	}

	/**
	 * Find all connections for a specific topic.
	 * Returns an empty list if no matches found.
	 * O(1) lookup time.
	 */
	fun findByTopic(topic: String): List<ConnectionInfo> {
		return connectionsByTopic[topic]?.toList() ?: emptyList()
	}

	/**
	 * Find connection for a specific peerId and topic combination.
	 * Returns null if no match found.
	 * O(1) lookup time.
	 */
	fun findByPeerAndTopic(peerId: String, topic: String): ConnectionInfo? {
		return connectionsByPeerAndTopic["$peerId:$topic"]
	}

	/**
	 * Check if a connection exists for the given peerId.
	 * O(1) lookup time.
	 */
	fun hasPeer(peerId: String): Boolean {
		return connectionsByPeer.containsKey(peerId)
	}

	/**
	 * Get the total number of connections in the registry.
	 */
	fun size(): Int {
		return connections.size
	}

	/**
	 * Check if the registry is empty.
	 */
	fun isEmpty(): Boolean {
		return connections.isEmpty()
	}

	/**
	 * Clear all connections and indexes.
	 */
	fun clear() {
		synchronized(connections) {
			connections.clear()
			connectionsByPeer.clear()
			connectionsByTopic.clear()
			connectionsByPeerAndTopic.clear()
		}
	}

	/**
	 * Get all connections as a list (for iteration).
	 * Returns a copy to prevent concurrent modification.
	 */
	fun getAll(): List<ConnectionInfo> {
		return synchronized(connections) {
			connections.toList()
		}
	}

	/**
	 * Execute an action for each connection in a thread-safe manner.
	 */
	fun forEach(action: (ConnectionInfo) -> Unit) {
		synchronized(connections) {
			connections.toList().forEach(action)
		}
	}
}

