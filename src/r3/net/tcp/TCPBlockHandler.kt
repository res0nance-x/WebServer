package r3.net.tcp

import r3.io.log
import r3.util.srnd
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigInteger

internal class TCPBlockHandler(private val baseDir: File, val contentHandler: (TCPNode, ByteArray, File?) -> Unit) :
	Closeable {
	class FileStream(val file: File, private val out: OutputStream) : Closeable {
		constructor(file: File) : this(file, FileOutputStream(file).buffered())

		override fun close() {
			out.close()
		}

		fun delete() {
			try {
				out.close()
			} catch (_: Exception) {
			}
			// Attempt to delete the file; on Windows this may fail if another handle is open.
			try {
				if (file.exists()) {
					// Try multiple times with short backoff to handle transient locks (antivirus, indexing, etc.)
					var deleted = false
					repeat(5) {
						if (!file.exists()) { deleted = true; return@repeat }
						if (file.delete()) { deleted = true; return@repeat }
						try { Thread.sleep(50) } catch (_: Exception) { }
					}
					if (!deleted) {
						// Schedule eventual cleanup via deleteOnExit as a last resort and log
						try { file.deleteOnExit() } catch (_: Exception) { }
						log("TCPBlockHandler: unable to delete temp file after retries: $file")
					}
				}
			} catch (e: Exception) {
				log("TCPBlockHandler: exception deleting temp file $file: $e")
			}
		}
		fun write(data: ByteArray) {
			out.write(data)
		}
	}

	private class StreamData(val header: ByteArray, val fileStream: FileStream, var lastWrite: Long)

	private val map = HashMap<Long, StreamData>()
	private val tempBase: File = run {
		val tb = baseDir.resolve(".r3tmp")
		try {
			if (!tb.exists()) tb.mkdirs()
		} catch (_: Exception) {
		}
		// Immediately try to delete any leftovers from previous runs (best-effort)
		try {
			val files = tb.listFiles()
			if (files != null) {
				for (f in files) {
					try {
						if (f.isFile) {
							if (!f.delete()) {
								log("TCPBlockHandler: unable to delete leftover temp file on startup: $f")
							}
						}
					} catch (_: Exception) {
					}
				}
			}
		} catch (_: Exception) {
		}
		// Register a shutdown hook to try and delete any remaining temp files on JVM exit
		try {
			Runtime.getRuntime().addShutdownHook(Thread {
				try {
					val files = tb.listFiles()
					if (files != null) {
						for (f in files) {
							try {
								if (f.isFile) {
									f.delete()
								}
							} catch (_: Exception) {
							}
						}
					}
				} catch (_: Exception) {
				}
			})
		} catch (_: Throwable) {
		}
		tb
	}
	private fun createTempFile(): File {
		// Use JVM temp file creation inside the dedicated temp base directory to avoid collisions
		return try {
			File.createTempFile("r3tmp_", null, tempBase)
		} catch (_: Exception) {
			// Fallback to previous approach if createTempFile fails
			val arr = srnd.getByteArray(32)
			val uid = BigInteger(1, arr).toString(32)
			val f = tempBase.resolve(uid)
			f
		}
	}

	fun handle(node: TCPNode, block: TCPBlock) {
		try {
			val streamData = synchronized(map) {
				map[block.id]
			}
			if (streamData == null) { // If null, this must be a new stream
				if (block.last) { // if last then stream only contains header
					contentHandler(node, block.data, null)
				} else { // if not last then we set out file for writing stream
					synchronized(map) {
						map[block.id] = StreamData(
							block.data, FileStream(createTempFile()),
							System.currentTimeMillis()
						)
					}
				}
			} else {
				// if here then we must have already received the header and so are now writing the stream
				streamData.fileStream.write(block.data)
				streamData.lastWrite = System.currentTimeMillis()
				if (block.last) { // if last block then done writing stream
					map.remove(block.id)
					try {
						streamData.fileStream.close()
						// Schedule deletion on JVM exit in case the content handler exits the process
						try {
							streamData.fileStream.file.deleteOnExit()
						} catch (_: Exception) {
						}
						contentHandler(node, streamData.header, streamData.fileStream.file)
						streamData.fileStream.delete()
					} catch (e: Exception) {
						log("TCPBlockHandler: error delivering final file to handler: $e")
						try {
							streamData.fileStream.delete()
						} catch (_: Exception) {
						}
					} finally {
						// If sending to handler fails, ensure file stream is closed and the temp file is deleted
						try {
							streamData.fileStream.delete()
						} catch (_: Exception) {
						}
					}
				}
			}
		} catch (e: Exception) {
			log("TCPBlockHandler: $e")
			// If an exception occurs while processing a block, attempt to clean up the associated stream
			try {
				val sd = synchronized(map) {
					map.remove(block.id)
				}
				if (sd != null) {
					try {
						sd.fileStream.close()
					} catch (_: Exception) {
					}
					try {
						sd.fileStream.file.delete()
					} catch (_: Exception) {
					}
				}
			} catch (_: Exception) {
			}
		}
	}

	override fun close() {
		// Close and remove all in-progress streams
		synchronized(map) {
			for ((_, sd) in map) {
				try {
					sd.fileStream.delete()
				} catch (_: Exception) {
				}
			}
			map.clear()
		}
		// Sweep old temp files in the temp base directory to catch leftovers (older than 10 minutes)
		try {
			val cutoff = System.currentTimeMillis() - (10 * 60 * 1000)
			val files = tempBase.listFiles()
			if (files != null) {
				for (f in files) {
					try {
						if (f.isFile && f.lastModified() < cutoff) {
							if (!f.delete()) {
								log("TCPBlockHandler: unable to delete stale temp file: $f")
							}
						}
					} catch (e: Exception) {
						log("TCPBlockHandler: error deleting stale temp file $f: $e")
					}
				}
			}
		} catch (e: Exception) {
			log("TCPBlockHandler: error sweeping temp files: $e")
		}
	}
}