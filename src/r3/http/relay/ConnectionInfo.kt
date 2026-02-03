package r3.http.relay

import org.nanohttpd.protocols.websocket.NanoWSD

class ConnectionInfo(
		val peerId: String,
		val topic: String,
		val socket: NanoWSD.WebSocket
	) {
		override fun toString(): String {
			return "r3.http.relay.ConnectionInfo(peerId='$peerId', topic='$topic', socket=${socket.remoteInetSocketAddress})"
		}
	}
