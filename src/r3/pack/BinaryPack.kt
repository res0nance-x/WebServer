package r3.pack

import r3.collection.HashSimpleMap
import r3.content.Content
import r3.content.ContentMeta
import r3.io.*
import r3.source.Sink
import r3.source.Source
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.time.Instant

open class BinaryPack(val src: Source) : Pack, Writable {
	protected val map = HashSimpleMap<String, Content>()

	init {
		val cis = CountingInputStream(src.createInputStream())
		DataInputStream(cis).use { stream ->
			try {
				while (cis.count < src.length - 1) {
					val header = ContentMeta.read(stream)
					if (header.length < 0) {
						throw Exception("Invalid ContentHeader length ${header.length}")
					} else if (header.length == 0L) {
						map.remove(header.name)
					} else {
						map[header.name] = object : Content {
							override val name: String = header.name
							override val type: String = header.type
							override val created: Long = Instant.now().toEpochMilli()
							private val pos = cis.count
							override fun createInputStream(): InputStream {
								val istream = src.createInputStream()
								istream.skipFullBytes(pos)
								return BoundedInputStream(
									istream,
									header.length
								)
							}

							override val length: Long = header.length
							override fun toString(): String {
								return "$name $type $length"
							}
						}
						cis.skipFullBytes(header.length)
					}
				}
			} catch (e: Exception) {
				log("BIdNCUxy5sw: $e")
			}
		}
	}

	override val size: Int
		get() {
			return map.size
		}
	override val keys: Set<String>
		get() = HashSet(map.keys)

	override fun get(key: String): Content? {
		return map[key]
	}

	override fun visit(visitor: (String, Content) -> Unit) {
		map.visit(visitor)
	}

	override fun write(dos: DataOutputStream) {
		src.createInputStream().use {
			it.copyTo(dos)
		}
	}

	companion object {
		fun create(
			content: Iterable<Content>, sink: Sink,
			progress: (Int) -> Unit = { _ -> }
		) {
			sink.createOutputStream().buffered().use { ostream ->
				val size = content.count().toDouble()
				DataOutputStream(ostream).use { dos ->
					var count = 0
					content.forEach { content ->
						ContentMeta(content).write(dos)
						content.createInputStream().use { it.copyTo(dos) }
						++count
						progress((count.toDouble() / size * 100).toInt())
					}
				}
			}
		}
	}
}