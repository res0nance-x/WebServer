package r3.misc

import r3.io.Writable
import r3.io.log
import r3.io.toUnixPath
import r3.source.StringWritable
import r3.util.humanReadableSize
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.*
import kotlin.concurrent.thread

val dirToSkip = listOf(
	"C:/Windows/",
	"C:/ProgramData/",
	"C:/Users/iansc/AppData/",
	"C:/Users/All Users/",
	"C:/Drivers/",
	"C:/Log/",
	"C:/Intel/",
	"C:/PerfLogs/",
	"C:/Recovery/",
	"E:/Steam/"
).map { it.lowercase() }

fun process(file: File): Boolean {
	if (file.parentFile != null && file.isHidden) {
		return false
	}
	val path = file.toUnixPath().lowercase()
	for (skip in dirToSkip) {
		if (path.contains(skip)) {
			return false
		}
	}
	return true
}

val set = TreeSet<File>()
fun watchThread(watchService: WatchService) {
	thread {
		while (true) {
			try {
				val key = watchService.take()
				if (key != null) {
					val events = key.pollEvents()
					for (x in events) {
						val dir = key.watchable() as Path
						val file = dir.resolve(x.context() as Path).toFile()
						when (x.kind()) {
							StandardWatchEventKinds.ENTRY_CREATE -> {
								println("New file $file")
								set.add(file)
							}

							StandardWatchEventKinds.ENTRY_DELETE -> {
								println("Deleted file $file")
								set.remove(file)
							}

							else -> {
								println("Unknown $file")
							}
						}
					}
					key.reset()
				}
			} catch (e: Exception) {
				log("YlaYRDVQ: $e")
			}
		}
	}
}

fun attachWatcher() {
	File.listRoots().forEach {
		val watchService = it.toPath().fileSystem.newWatchService()
		val iter = it.walk().onEnter { file ->
			process(file)
		}.iterator()
		while (iter.hasNext()) {
			val file = iter.next()
			if (file.isDirectory) {
				if (process(file))
					try {
						file.toPath()
							.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE)
					} catch (e: Exception) {
						log("u1MCPPiaIE: $e")
					}
			}
		}
		watchThread(watchService)
	}
}

class FileInfo(val path: String, val size: Long, val lastModified: Long) : Writable {
	override fun write(dos: DataOutputStream) {
		StringWritable(path).write(dos)
		dos.writeLong(size)
		dos.writeLong(lastModified)
	}

	override fun toString(): String {
		return "$path ${size.humanReadableSize()} ${Date(lastModified)}"
	}

	companion object {
		fun toFileInfo(file: File): FileInfo {
			return FileInfo(file.absoluteFile.toUnixPath(), file.length(), file.lastModified())
		}

		fun read(dis: DataInputStream): FileInfo {
			return FileInfo(StringWritable.read(dis).str, dis.readLong(), dis.readLong())
		}
	}
}

fun buildIndex() {
	DataOutputStream(File("e:/index.dat").outputStream().buffered()).use { dos ->
		File.listRoots().forEach {
			val iter = it.walk().onEnter { file ->
				process(file)
			}.iterator()
			while (iter.hasNext()) {
				val file = iter.next()
				FileInfo.toFileInfo(file).write(dos)
			}
		}
	}
}

fun readIndex(): ArrayList<FileInfo> {
	val index = ArrayList<FileInfo>()
	DataInputStream(File("e:/index.dat").readBytes().inputStream()).use { dis ->
		while (dis.available() > 0) {
			index.add(FileInfo.read(dis))
		}
	}
	return index
}

fun queryLoop() {
	while (true) {
		val line = readlnOrNull() ?: ""
		if (line.isNotBlank()) {
			val re = Regex(line.lowercase())
			for (f in set) {
				if (re.find(f.toUnixPath().lowercase()) != null) {
					println(f.toUnixPath().lowercase())
				}
			}
		}
	}
}

data class IntRef(var count: Int) {
	constructor() : this(0)
}