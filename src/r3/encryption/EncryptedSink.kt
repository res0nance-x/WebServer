package r3.encryption

import r3.math.EncryptedSequence
import r3.source.Sink
import r3.source.createSink
import java.io.File
import java.io.OutputStream

class EncryptedSink(val seq: EncryptedSequence, val sink: Sink) : Sink {
	override fun createOutputStream(): OutputStream {
		return EncryptedContinuousOutputStream(seq, sink.createOutputStream())
	}
}

fun File.createEncryptedSink(seq: EncryptedSequence): EncryptedSink {
	return EncryptedSink(seq, this.createSink())
}