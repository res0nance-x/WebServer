package r3.pack
//class FilesPack(val dir: File) : MutablePack {
//	private var _listener: (key: Key256, type: UpdateType) -> Unit = { _, _ -> }
//	override fun add(content: Content) {
//		val destFile = File(dir, content.key.toString())
//		val update = destFile.exists()
//		val tmpFile = File.createTempFile("tr3", "tmp")
//		FileOutputStream(tmpFile).buffered().use { out ->
//			val dos = DataOutputStream(out)
//			ContentMeta(content.key, content.name, content.contentType, content.length).write(dos)
//			content.createInputStream().use { istream ->
//				istream.copyTo(out)
//			}
//		}
//		tmpFile.renameTo(destFile)
//		if (update) {
//			_listener(content.key, UpdateType.CHANGE)
//		} else {
//			_listener(content.key, UpdateType.ADD)
//		}
//	}
//
//	fun set(key: Key256, value: Content) {
//		if (key != value.key) {
//			throw Exception("Content key ${value.key} does not match supplied key $key")
//		}
//		add(value)
//	}
//
//	fun remove(key: Key256) {
//		File(dir, key.toString()).delete()
//		_listener(key, UpdateType.REMOVE)
//	}
//
//	override val size: Int
//		get() = dir.list()?.size ?: 0
//	override val keys: Set<Key256>
//		get() {
//			val files = dir.list() ?: Array(0) { "" }
//			val list = HashSet<Key256>()
//			for (f in files) {
//				try {
//					val k = Key256(f.fromUBase64())
//					list.add(k)
//				} catch (_: Exception) {
//					// There can be extra files that are not entries but contain other information
//				}
//			}
//			return list
//		}
//
//	override fun get(key: Key256): Content? {
//		val file = File(dir, key.toString())
//		if (!file.exists()) {
//			return null
//		}
//		var pos: Long
//		val head = FileInputStream(file).use {
//			val cis = CountingInputStream(it)
//			val dis = DataInputStream(cis)
//			val h = ContentMeta.read(dis)
//			pos = cis.count
//			h
//		}
//		return object : Content {
//			val offset = pos
//			override val key: Key256
//				get() = head.key
//			override val name: String
//				get() = head.name
//			override val contentType: ContentType
//				get() = head.contentType
//
//			override fun createInputStream(): InputStream {
//				return FileInputStream(file).apply { skipFullBytes(offset) }
//			}
//
//			override val length: Long
//				get() = head.length
//
//			override fun toString(): String {
//				return head.toString()
//			}
//		}
//	}
//
//	override fun visit(visitor: (Key256, Content) -> Unit) {
//		val files = (dir.listFiles() ?: Array(0) { File("") }).sortedBy { it.lastModified() }
//		for (f in files) {
//			try {
//				val k = Key256(f.name.fromUBase64())
//				val c = get(k)
//				if (c != null) {
//					visitor(k, c)
//				}
//			} catch (_: Exception) {
//				// There can be extra files that are not entries but contain other information
//			}
//		}
//	}
//}