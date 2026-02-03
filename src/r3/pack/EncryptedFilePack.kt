package r3.pack
//class EncryptedFilePack(file: File, userPass: ByteArray) : MutablePack {
//	val pack: MutableBinaryPack
//	private fun createReadWrite(file: File, pass: ByteArray): EncryptedSequence {
//		return if (file.length() == 0L) {
//			val seq = createSequence(pass)
//			file.createOutputStream().use {
//				EncryptedSequenceHeader(seq.pass, seq.m).write(pass, DataOutputStream(it))
//			}
//			seq
//		} else {
//			file.createInputStream().use { EncryptedSequenceHeader.read("test".toByteArray(), DataInputStream(it)) }
//				.getEncryptedSequence()
//		}
//	}
//
//	init {
//		val seq = createReadWrite(file, userPass)
//		pack = MutableBinaryPack(
//			file.createEncryptedSource(seq),
//			file.createEncryptedSink(seq)
//		)
//	}
//
//	override fun add(content: Content) {
//		pack.add(content)
//	}
//
//	override val size: Int
//		get() = pack.size
//	override val keys: Set<Key256>
//		get() = HashSet(pack.keys)
//
//	override fun get(key: Key256): Content? {
//		return pack[key]
//	}
//
//	override fun visit(visitor: (Key256, Content) -> Unit) {
//		pack.visit(visitor)
//	}
//
//	override fun iterator(): Iterator<Content> {
//		return pack.iterator()
//	}
//
//	fun close() {
//		pack.close()
//	}
//}