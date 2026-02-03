package r3.io

import r3.util.srnd
import java.io.*
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.DirectoryStream
import java.nio.file.Path

class RamSinkFileOverflow(private val threshold: Int, private val overflowDir: File) : OutputStream() {
	private var currentSize = 0
	private var ramBuffer = ByteArrayOutputStream()
	private var fileOutputStream: OutputStream? = null
	private var overflowFile: File? = null
	private fun createTempFile(): File {
		val arr = srnd.getByteArray(32)
		val uid = BigInteger(1, arr).toString(32)
		val f = File(overflowDir, "tfm$uid.tmp")
		return f
	}

	fun checkSize() {
		if (currentSize > threshold && fileOutputStream == null) {
			// Switch to file overflow
			overflowFile = createTempFile()
			fileOutputStream = FileOutputStream(overflowFile!!).buffered()
			// Write existing RAM buffer to file
			fileOutputStream!!.write(ramBuffer.toByteArray())
			ramBuffer = ByteArrayOutputStream()
		}
	}

	override fun write(b: Int) {
		ramBuffer.write(b)
		currentSize += 1
		checkSize()
	}

	override fun write(data: ByteArray) {
		if (fileOutputStream != null) {
			// Already overflowing to file
			fileOutputStream!!.write(data)
		} else {
			ramBuffer.write(data)
			currentSize += data.size
			checkSize()
		}
	}

	fun getInputStream(): InputStream {
		return if (fileOutputStream != null) {
			// Flush and close file output stream before reading
			fileOutputStream!!.flush()
			fileOutputStream!!.close()
			fileOutputStream = null
			Files.newInputStream(overflowFile!!.toPath())
		} else {
			// Create InputStream from RAM buffer
			return ramBuffer.toByteArray().inputStream()
		}
	}

	fun copyToFile(file: File) {
		if (fileOutputStream != null) {
			// Flush and close file output stream before returning file
			fileOutputStream!!.flush()
			fileOutputStream!!.close()
			fileOutputStream = null
			overflowFile?.let { Files.move(it.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
		} else {
			Files.write(file.toPath(), ramBuffer.toByteArray())
		}
	}

	override fun close() {
		fileOutputStream?.close()
	}
}

class TempStorageManager(root: File) {
	private val absoluteRoot = root.absoluteFile

	init {
		if (!absoluteRoot.exists()) {
			absoluteRoot.mkdirs()
		} else {
			clean()
		}
	}

	fun clean() {
		// remove previous temp files
		Files.newDirectoryStream(absoluteRoot.toPath()).use { dir: DirectoryStream<Path> ->
			for (p in dir) {
				val name = p.fileName.toString()
				if (name.startsWith("tfm")) {
					try {
						Files.deleteIfExists(p)
					} catch (e: Throwable) {
						System.err.println("TempFileManager: $e")
					}
				}
			}
		}
	}

	fun createTempStorage(): RamSinkFileOverflow {
		return RamSinkFileOverflow(32768, absoluteRoot)
	}
}