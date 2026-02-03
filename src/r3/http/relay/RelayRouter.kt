package r3.http.relay

import org.json.JSONObject
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.protocols.websocket.NanoWSD
import r3.http.DequeInputStream
import r3.http.IRouter
import r3.io.log
import r3.key.Key128
import java.io.IOException
import java.io.InputStream
import java.net.SocketException
import java.util.*
import java.util.concurrent.BlockingDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RelayRouter(
	val basePath: String,
	val cache: IRelayCache? = null
) : IRouter {
	companion object {
		private const val DEQUE_CAPACITY = 100
		private const val CHUNK_SIZE = 32768
		private const val OFFER_TIMEOUT_SECONDS = 120L
		private const val EOF_SIGNAL_TIMEOUT_SECONDS = 5L
		private const val MAX_PEER_RETRIES = 3
		private const val PEER_RESPONSE_TIMEOUT_SECONDS = 5L
		private const val CANCELLED_REQUESTS_MAX_SIZE = 1000
		private const val CANCELLED_REQUEST_TTL_MINUTES = 5L
	}

	fun methodNotAllowedResponse(): Response {
		return Response.newFixedLengthResponse(
			Status.METHOD_NOT_ALLOWED,
			"text/plain",
			"Method Not Allowed"
		)
	}

	fun badRequestResponse(message: String): Response {
		return Response.newFixedLengthResponse(
			Status.BAD_REQUEST,
			"text/plain",
			"Bad Request: $message"
		)
	}

	fun notFoundResponse(message: String): Response {
		return Response.newFixedLengthResponse(
			Status.NOT_FOUND,
			"text/plain",
			"Not Found: $message"
		)
	}

	private val peerMessageRegistry = PeerMessageRegistry()
	private val connectionRegistry = ConnectionRegistry()

	// Track keys currently being fetched for caching to prevent duplicate fetches
	private val fetchingKeys = Collections.synchronizedSet(mutableSetOf<String>())
	val relayWebSocketBuilder: (session: IHTTPSession) -> NanoWSD.WebSocket = { session: IHTTPSession ->
		object : NanoWSD.WebSocket(session) {
			var peerId: String? = null
			var topic: String? = null
			override fun onOpen() {
				log("WebSocket opened")
			}

			override fun onClose(
				code: NanoWSD.WebSocketFrame.CloseCode?,
				reason: String?,
				initiatedByRemote: Boolean
			) {
				log("WebSocket closed for peer $peerId")
				peerId?.let { id ->
					val removed = peerMessageRegistry.removeByPeerId(id)
					log("Removed $removed messages for peer $id")
				}
				connectionRegistry.removeBySocket(this)
			}

			override fun onMessage(message: NanoWSD.WebSocketFrame) {
				try {
					message.textPayload?.also { jsonStr ->
						val json = JSONObject(jsonStr)
						when (val action = json.getString("action")) {
							"subscribe" -> {
								val peerId = json.getString("peerId")
								val topic = json.getString("topic")
								this.peerId = peerId
								this.topic = topic
								log("Peer $peerId subscribed to topic $topic")
								if (topic.isNotEmpty() && peerId.isNotEmpty()) {
									// Remove old connection if exists and add new one
									val info = ConnectionInfo(peerId, topic, this)
									val old = connectionRegistry.add(info)
									if (old != null) {
										log("Replaced existing connection for $peerId:$topic")
									}
									if (!peerMessageRegistry.hasPeer(peerId)) {
										val keySet = mutableSetOf<String>()
										// Send existing messages for this topic to the new subscriber
										for (m in peerMessageRegistry.findByTopic(topic)) {
											if (keySet.add(m.message.key)) {
												send(m.toJson().toString())
											}
										}
										// Also send cached messages for this topic
										cache?.getMessages(topic)?.forEach { message ->
											if (keySet.add(message.key)) {
												// Create a PeerMessage-like JSON structure
												val json = JSONObject()
												json.put("action", "message")
												json.put("message", message.toJson())
												send(json.toString())
											}
										}
									}
								}
							}

							"message" -> {
								val message = Message(json.getJSONObject("message"))
								if (peerId == null || topic == null) {
									log("Received message before subscription, ignoring")
									return
								}
								peerMessageRegistry.add(PeerMessage(peerId ?: "", message))
								// Cache the message
								cache?.putMessage(message)
								// Try to fetch and cache the blob in the background (if cache is configured)
								// Only fetch if not already cached and not currently being fetched
								if (cache != null && cache.getBlob(message.key) == null && !fetchingKeys.contains(message.key)) {
									fetchAndCacheBlob(message, peerId ?: "")
								}
								// Relay message to other peers subscribed to the same topic
								val topicConnections = connectionRegistry.findByTopic(topic!!)
								for (info in topicConnections) {
									if (info.peerId != this.peerId) {
										// Check if this peer already has the message
										if (peerMessageRegistry.hasMessage(info.peerId, message.key)) {
											continue
										}
										try {
											info.socket.send(json.toString())
										} catch (e: IOException) {
											log("Failed to relay message to ${info.peerId}: $e")
										}
									}
								}
							}

							else -> {
								log("Unknown WebSocket message action: $action")
							}
						}
					}
				} catch (e: Exception) {
					log("Error processing WebSocket message: $e")
				}
			}

			override fun onPong(pong: NanoWSD.WebSocketFrame?) {
				log("WebSocket pong $pong")
			}

			override fun onException(exception: IOException?) {
				log("WebSocket exception $exception")
			}
		}
	}
	val requestBridgeMap: MutableMap<String, BlockingDeque<ByteArray>> = Collections.synchronizedMap(mutableMapOf())
	private val cancelledRequests = ConcurrentHashMap<String, Long>()
	private fun markRequestAsCancelled(requestId: String) {
		val now = System.currentTimeMillis()
		cancelledRequests[requestId] = now
		// Cleanup entries older than TTL
		if (cancelledRequests.size > CANCELLED_REQUESTS_MAX_SIZE) {
			val cutoff = now - TimeUnit.MINUTES.toMillis(CANCELLED_REQUEST_TTL_MINUTES)
			cancelledRequests.entries.removeIf { it.value < cutoff }
			log("Cleaned up old cancelled request IDs (size now: ${cancelledRequests.size})")
		}
	}

	private fun isRequestCancelled(requestId: String): Boolean {
		return cancelledRequests.containsKey(requestId)
	}

	private fun signalErrorToReceiver(deque: BlockingDeque<ByteArray>) {
		try {
			deque.offer(ByteArray(0), 1, TimeUnit.SECONDS)
		} catch (_: Exception) {
			// Ignore - receiver may have already disconnected
		}
	}

	fun getNumberOfConnectedPeers(): Int {
		return connectionRegistry.size()
	}

	override fun findRoute(session: IHTTPSession): Response? {
		if (!session.uri.startsWith(basePath)) {
			return null
		}
		val subPath = session.uri.substring(basePath.length)
		when (subPath) {
			"getBlob" -> {
				if (session.method.name != "GET" && session.method.name != "HEAD") {
					return methodNotAllowedResponse()
				}
				val key = session.parameters["key"]?.firstOrNull() ?: ""
				if (key.isEmpty()) {
					return badRequestResponse("Missing or empty key parameter")
				}
				// Check cache first
				cache?.getBlob(key)?.let { cachedBlob ->
					log("Serving blob from cache: $key")
					val inputStream: InputStream = cachedBlob.getInputStream()
					return Response.newFixedLengthResponse(
						Status.OK,
						cachedBlob.message.contentType,
						inputStream,
						cachedBlob.message.length
					)
				}
				val msgList = peerMessageRegistry.findByKey(key).toMutableList()
				if (msgList.isEmpty()) {
					log("No peers have the requested key: $key")
					return notFoundResponse("No peers found for content: $key")
				}
				msgList.shuffle()
				val maxRetries = minOf(msgList.size, MAX_PEER_RETRIES)
				var lastError = "Unknown error"
				for (attemptIndex in 0..<maxRetries) {
					val peerMsg = msgList[attemptIndex]
					val requestId = Key128.randomKey().toString()
					val deque = LinkedBlockingDeque<ByteArray>(DEQUE_CAPACITY)
					requestBridgeMap[requestId] = deque
					log("Requesting Blob: $key from peer: ${peerMsg.peerId} (attempt ${attemptIndex + 1}/$maxRetries)")
					var foundPeer = false
					val peerConnections = connectionRegistry.findByPeerId(peerMsg.peerId)
					if (peerConnections.isNotEmpty()) {
						val ws = peerConnections.first()
						try {
							val query = JSONObject()
							query.put("action", "requestBlob")
							query.put("key", key)
							query.put("requestId", requestId)
							ws.socket.send(query.toString())
							foundPeer = true
						} catch (e: IOException) {
							lastError = "Failed to send request: ${e.message}"
							log("Failed to send requestBlob to ${ws.peerId}: $e")
						}
					}
					if (!foundPeer) {
						requestBridgeMap.remove(requestId)
						if (lastError == "Unknown error") {
							lastError = "Peer connection not available"
						}
						log("Peer connection not found for peerId: ${peerMsg.peerId}, trying next peer...")
						continue
					}
					val data = deque.poll(PEER_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
					if (data == null) {
						requestBridgeMap.remove(requestId)
						lastError = "Peer did not respond in time"
						log("No response received from peer ${peerMsg.peerId}, trying next peer...")
						continue
					}
					deque.addFirst(data)
					val contentType = peerMsg.message.contentType
					val length = peerMsg.message.length
					val inputStream: InputStream = DequeInputStream(deque, 30000) {
						requestBridgeMap.remove(requestId)
					}
					return Response.newFixedLengthResponse(Status.OK, contentType, inputStream, length)
				}
				log("Failed to get content for key $key after $maxRetries attempts: $lastError")
				return Response.newFixedLengthResponse(
					Status.NOT_FOUND,
					"text/plain",
					"Not Found: $key (tried $maxRetries peer(s), last error: $lastError)"
				)
			}

			"sendBlob" -> {
				if (session.method.name != "PUT") {
					return methodNotAllowedResponse()
				}
				val contentType = session.headers["content-type"] ?: session.headers["x-content-type"]
				val contentLength = session.headers["content-length"] ?: session.headers["x-content-length"]
				val requestId = session.parameters["requestId"]?.firstOrNull()
				if (contentType == null || contentLength == null || requestId == null) {
					return badRequestResponse("Missing Content-Type $contentType Content-Length $contentLength or requestId $requestId")
				}
				if (isRequestCancelled(requestId)) {
					log("Rejecting sendBlob for cancelled requestId: $requestId")
					val response = Response.newFixedLengthResponse(
						Status.GONE,
						"text/plain",
						"Request $requestId was cancelled or already completed"
					)
					response.closeConnection(true)
					return response
				}
				val deque = requestBridgeMap[requestId]
					?: return badRequestResponse("There was no request for requestId: $requestId")
				var success = false
				try {
					val buffer = ByteArray(CHUNK_SIZE)
					var totalRead = 0L
					var errorMessage: String? = null
					while (true) {
						if (!requestBridgeMap.containsKey(requestId)) {
							log("Request $requestId cancelled (receiving peer disconnected), aborting upload")
							errorMessage = "Transfer cancelled: receiving peer disconnected"
							break
						}
						val toRead = minOf(buffer.size.toLong(), contentLength.toLong() - totalRead).toInt()
						if (toRead <= 0) {
							break
						}
						val read = session.inputStream.read(buffer, 0, toRead)
						if (read == -1) {
							log("Sender closed connection early for request $requestId (read $totalRead of $contentLength bytes)")
							errorMessage = "Transfer incomplete: connection closed early"
							break
						}
						totalRead += read
						val chunk = ByteArray(read)
						System.arraycopy(buffer, 0, chunk, 0, read)
						if (!deque.offer(chunk, OFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
							log("Failed to send chunk for request $requestId (receiver timeout), aborting")
							errorMessage = "Transfer failed: receiver timeout"
							break
						}
					}
					if (errorMessage != null) {
						markRequestAsCancelled(requestId)
						signalErrorToReceiver(deque)
						throw SocketException("Transfer interrupted: $errorMessage")
					}
					if (totalRead != contentLength.toLong()) {
						log("Incomplete read for request $requestId: read $totalRead of $contentLength bytes")
						markRequestAsCancelled(requestId)
						signalErrorToReceiver(deque)
						throw SocketException("Transfer incomplete: expected $contentLength bytes, received $totalRead bytes")
					}
					if (!deque.offer(ByteArray(0), EOF_SIGNAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
						log("Failed to send end-of-stream signal for request $requestId")
						markRequestAsCancelled(requestId)
						throw SocketException("Transfer failed: could not signal completion")
					}
					success = true
					log("Successfully relayed $totalRead bytes for request $requestId")
					return Response.newFixedLengthResponse(
						Status.OK,
						"text/plain",
						"File uploaded successfully"
					)
				} catch (e: Exception) {
					log("Error in sendBlob for request $requestId: $e")
					markRequestAsCancelled(requestId)
					if (requestBridgeMap.containsKey(requestId)) {
						signalErrorToReceiver(deque)
					}
					val response = Response.newFixedLengthResponse(
						Status.INTERNAL_ERROR,
						"text/plain",
						"Error uploading file: ${e.message}"
					)
					response.closeConnection(true)
					return response
				} finally {
					if (!success || !requestBridgeMap.containsKey(requestId)) {
						requestBridgeMap.remove(requestId)
						log("Cleaned up request $requestId in sendBlob finally block")
					}
				}
			}
		}
		return null
	}

	/**
	 * Fetch a blob from a peer and cache it in the background.
	 * This is a best-effort operation - failures are logged but don't affect the relay.
	 * Uses fetchingKeys to prevent duplicate concurrent fetches of the same blob.
	 */
	private fun fetchAndCacheBlob(message: Message, sourcePeerId: String) {
		// Mark this key as being fetched (prevents duplicate fetches)
		if (!fetchingKeys.add(message.key)) {
			// Already being fetched by another thread
			log("Blob ${message.key} is already being fetched, skipping duplicate fetch")
			return
		}

		thread {
			try {
				log("Fetching blob for caching: ${message.key} from peer: $sourcePeerId")
				val requestId = Key128.randomKey().toString()
				val deque = LinkedBlockingDeque<ByteArray>(DEQUE_CAPACITY)
				requestBridgeMap[requestId] = deque
				val peerConnections = connectionRegistry.findByPeerId(sourcePeerId)
				if (peerConnections.isEmpty()) {
					log("Cannot fetch blob for caching: peer $sourcePeerId not connected")
					requestBridgeMap.remove(requestId)
					return@thread
				}
				val ws = peerConnections.first()
				try {
					val query = JSONObject()
					query.put("action", "requestBlob")
					query.put("key", message.key)
					query.put("requestId", requestId)
					ws.socket.send(query.toString())
				} catch (e: IOException) {
					log("Failed to request blob for caching: $e")
					requestBridgeMap.remove(requestId)
					return@thread
				}
				// Wait for first chunk to arrive
				val firstChunk = deque.poll(PEER_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				if (firstChunk == null) {
					log("No response from peer when fetching blob for caching: ${message.key}")
					requestBridgeMap.remove(requestId)
					return@thread
				}
				// Read all chunks and cache the blob
				deque.addFirst(firstChunk)
				val stream = DequeInputStream(deque, 30000) {
					requestBridgeMap.remove(requestId)
				}

				cache?.putBlob(message) { stream }
				log("Successfully cached blob: ${message.key}")
			} catch (e: Exception) {
				log("Error fetching blob for caching: $e")
			} finally {
				// Always remove the key from fetchingKeys when done (success or failure)
				fetchingKeys.remove(message.key)
			}
		}
	}
}