package r3.source

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class FileSink(val file: File, val append: Boolean = true) : Sink {
	override fun createOutputStream(): OutputStream {
		return FileOutputStream(file, append)
	}
}

fun File.createSink(append: Boolean = true): Sink {
	return FileSink(this, append)
}