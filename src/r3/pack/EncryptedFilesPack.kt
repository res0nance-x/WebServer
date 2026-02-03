package r3.pack
// TODO untested
//class EncryptedFilesPack(val dir: File, val cipherKey: CipherKey) : MutablePack {
//	private val r3.g.encrypt = cipherKey.createEncrypt()
//	private val decrypt = cipherKey.createEncrypt()
//	override fun add(content: Content) {
//		val destFile = File(dir, content.key.toString())
//		val update = destFile.exists()
//		val tmpFile = File.createTempFile("tr3", "tmp")
//		CipherOutputStream(FileOutputStream(tmpFile).buffered(), r3.g.encrypt).use { out ->
//			val dos = DataOutputStream(out)
//			ContentMeta(content.key, content.name, content.contentType, content.length).write(dos)
//			content.createInputStream().use { istream ->
//				istream.copyTo(out)
//			}
//		}
//		tmpFile.renameTo(destFile)
//	}
//
//	override val size: Int
//		get() = dir.list()?.size ?: 0
//	override val keys: Set<Key256>
//		get() {
//			val files = dir.list() ?: Array(0) { "" }
//			val set = HashSet<Key256>()
//			for (f in files) {
//				try {
//					val k = Key256(f.fromUBase64())
//					set.add(k)
//				} catch (_: Exception) {
//					// There can be extra files that are not entries but contain other information
//				}
//			}
//			return set
//		}
//
//	override fun get(key: Key256): Content? {
//		val file = File(dir, key.toString())
//		if (!file.exists()) {
//			return null
//		}
//		var pos: Long
//		val head = CipherInputStream(FileInputStream(file), decrypt).use {
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
//				return CipherInputStream(FileInputStream(file), decrypt).apply { skipFullBytes(offset) }
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