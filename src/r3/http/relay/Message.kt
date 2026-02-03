package r3.http.relay

import org.json.JSONObject

class Message(
	val key: String,
	val name: String,
	val contentType: String,
	val length: Long,
	val topic: String,
	val alias: String,
	val timestamp: Long
) : Comparable<Message> {
	constructor(json: JSONObject) : this(
		json.getString("key"),
		json.getString("name"),
		json.getString("contentType"),
		json.getLong("length"),
		json.getString("topic"),
		json.getString("alias"),
		json.getLong("timestamp")
	)

	override fun compareTo(other: Message): Int {
		val c = this.topic.compareTo(other.topic)
		if (c != 0) {
			return c
		}
		return this.key.compareTo(other.key)
	}

	fun toJson(): JSONObject {
		val json = JSONObject()
		json.put("key", key)
		json.put("name", name)
		json.put("contentType", contentType)
		json.put("length", length)
		json.put("topic", topic)
		json.put("alias", alias)
		json.put("timestamp", timestamp)
		return json
	}

	override fun toString(): String {
		return "Message(key='$key', name='$name', contentType='$contentType', length=$length, topic='$topic', alias='$alias', timestamp=$timestamp)"
	}
}