package r3.content

import r3.io.Writable
import r3.key.hash256
import r3.pke.ContentKey
import r3.source.StringWritable
import r3.util.dateTime
import r3.util.humanReadableSize
import java.io.DataInputStream
import java.io.DataOutputStream

data class ContentMeta(
	val hash: ContentKey,
	val name: String,
	val type: String,
	val length: Long,
	val created: Long
) : Writable {
	constructor(content: Content) : this(
		ContentKey(content.hash256()),
		content.name,
		content.type,
		content.length,
		content.created,
	)

	override fun write(dos: DataOutputStream) {
		hash.write(dos)
		StringWritable(name).write(dos)
		StringWritable(type).write(dos)
		dos.writeLong(length)
		dos.writeLong(created)
	}

	override fun toString(): String {
		return "Name:$name Type:$type Length:${length.humanReadableSize()} Hash:$hash Created:${created.dateTime()}"
	}

	companion object {
		fun read(dis: DataInputStream): ContentMeta {
			return ContentMeta(
				ContentKey.read(dis),
				StringWritable.read(dis).str,
				StringWritable.read(dis).str,
				dis.readLong(),
				dis.readLong()
			)
		}
	}
}