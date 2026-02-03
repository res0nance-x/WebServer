package r3.http.relay

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import r3.io.log
import java.net.URI
import kotlin.concurrent.thread

class RelayClient(
	val remoteRelayUrl: String,      // e.g., "ws://remote-relay:1337"
) {
	private var webSocketClient: WebSocketClient? = null

	@Volatile
	private var isConnected = false

	@Volatile
	private var shouldReconnect = true

	companion object {
		private const val RECONNECT_DELAY_MS = 5000L
		private const val MAX_RECONNECT_DELAY_MS = 60000L
		private const val CONNECT_TIMEOUT_MS = 30000
	}

	/**
	 * Start the bridge connection to the remote relay
	 */
	fun start() {
		shouldReconnect = true
		connect()
	}

	/**
	 * Stop the bridge and disconnect from remote relay
	 */
	fun stop() {
		shouldReconnect = false
		disconnect()
	}

	private fun connect() {
		if (isConnected) {
			log("RelayClient: Already connected to $remoteRelayUrl")
			return
		}

		log("RelayClient: Connecting to remote relay at $remoteRelayUrl")

		try {
			val uri = URI(remoteRelayUrl)
			val client = object : WebSocketClient(uri) {
				override fun onOpen(handshakedata: ServerHandshake?) {
					log("RelayClient: WebSocket opened to $remoteRelayUrl")
					isConnected = true
					// Subscribe to topic now that connection is open
					subscribe()
				}

				override fun onMessage(message: String) {
					handleMessage(message)
				}

				override fun onClose(code: Int, reason: String?, remote: Boolean) {
					log("RelayClient: WebSocket closed: $code - $reason (remote: $remote)")
					isConnected = false
					if (shouldReconnect) {
						scheduleReconnect(RECONNECT_DELAY_MS)
					}
				}

				override fun onError(ex: Exception?) {
					log("RelayClient: WebSocket error: $ex")
					isConnected = false
					scheduleReconnect(RECONNECT_DELAY_MS)
				}
			}

			client.connectionLostTimeout = CONNECT_TIMEOUT_MS / 1000
			webSocketClient = client
			client.connect()
		} catch (e: Exception) {
			log("RelayClient: Failed to connect to $remoteRelayUrl: $e")
			scheduleReconnect(RECONNECT_DELAY_MS)
		}
	}

	private fun disconnect() {
		webSocketClient?.close()
		webSocketClient = null
		isConnected = false
		log("RelayClient: Disconnected from $remoteRelayUrl")
	}

	private fun scheduleReconnect(delayMs: Long = RECONNECT_DELAY_MS) {
		if (!shouldReconnect) {
			return
		}

		log("RelayClient: Scheduling reconnect in ${delayMs}ms")
		thread {
			Thread.sleep(delayMs)
			if (shouldReconnect && !isConnected) {
				val nextDelay = minOf(delayMs * 2, MAX_RECONNECT_DELAY_MS)
				try {
					connect()
				} catch (e: Exception) {
					// If connect fails, schedule another reconnect with exponential backoff
					scheduleReconnect(nextDelay)
				}
			}
		}
	}

	private fun subscribe() {
		val subscribeMsg = JSONObject()
//		subscribeMsg.put("action", "subscribe")
//		subscribeMsg.put("peerId", bridgePeerId)
//		subscribeMsg.put("topic", topic)
//
//		sendMessage(subscribeMsg.toString())
	}

	private fun sendMessage(message: String) {
		try {
			webSocketClient?.send(message)
		} catch (e: Exception) {
			log("RelayClient: Failed to send message: $e")
		}
	}

	private fun handleMessage(text: String) {
		try {
			val json = JSONObject(text)
			when (val action = json.getString("action")) {
				"message" -> {
					// Received message from remote relay - forward to local peers
					val message = Message(json.getJSONObject("message"))
				}

				"requestBlob" -> {
					// Remote relay wants a blob from us
					val key = json.getString("key")
					val requestId = json.getString("requestId")
					handleBlobRequest(key, requestId)
				}

				else -> {
					log("RelayClient: Unknown action from remote relay: $action")
				}
			}
		} catch (e: Exception) {
			log("RelayClient: Error processing message from remote relay: $e")
		}
	}

	/**
	 * Handle requestBlob from remote relay - fetch blob from local relay and send it
	 */
	private fun handleBlobRequest(key: String, requestId: String) {
	}
}

