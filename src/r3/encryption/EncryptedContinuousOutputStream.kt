package r3.encryption

import r3.math.EncryptedSequence
import r3.pke.Password256
import java.io.OutputStream

class EncryptedContinuousOutputStream(val seq: EncryptedSequence, val ostream: OutputStream) : OutputStream() {
	private var pos = 0L

	constructor(pass: Password256, ostream: OutputStream) : this(EncryptedSequence.createSequence(pass), ostream)

	override fun write(b: ByteArray, off: Int, len: Int) {
		val arr = ByteArray(len) { (b[off + it].toInt() xor seq.get(pos++).toInt()).toByte() }
		ostream.write(arr)
	}

	override fun write(b: Int) {
		ostream.write(b xor seq.get(pos++).toInt())
	}

	override fun close() {
		ostream.close()
	}
}
