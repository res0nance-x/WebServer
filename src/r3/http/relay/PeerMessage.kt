package r3.http.relay

import org.json.JSONObject

class PeerMessage(
	val peerId: String,
	val message: Message
) {
	override fun toString(): String {
		return "PeerMessage(peerId='$peerId', message=$message)"
	}

	fun toJson(): JSONObject {
		val json = JSONObject()
		json.put("action", "message")
		json.put("message", JSONObject().apply {
			put("key", message.key)
			put("name", message.name)
			put("contentType", message.contentType)
			put("length", message.length)
			put("topic", message.topic)
			put("alias", message.alias)
			put("timestamp", message.timestamp)
		})
		return json
	}
}