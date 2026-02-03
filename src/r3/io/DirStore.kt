package r3.io

import r3.key.Key256
import r3.source.Source
import java.io.File

class DirStore<V>(
	val dir: File,
	val tfm: TempStorageManager = TempStorageManager(File(dir, "temp")),
	val store: (V) -> Source,
	val retrieve: (File) -> V
) {
	private val absoluteDir = dir.absoluteFile
	fun listKeys(): List<Key256> {
		val list = (absoluteDir.listFiles() ?: emptyArray()).toList()
		return (list.sortedBy { it.lastModified() }).map { Key256(it.name) }
	}

	fun has(k: Key256): Boolean {
		return File(absoluteDir, k.toString()).exists()
	}

	fun delete(k: Key256) {
		val file = File(absoluteDir, k.toString())
		if (file.exists()) {
			if (!file.delete()) {
				file.deleteOnExit()
			}
		}
	}

	operator fun get(k: Key256): V? {
		val file = File(absoluteDir, k.toString())
		return if (file.exists()) retrieve(file) else null
	}

	operator fun set(k: Key256, v: V) {
		val tmp = tfm.createTempStorage()
		store(v).createInputStream().use { istream ->
			tmp.use { ostream ->
				istream.copyTo(ostream)
			}
		}
		tmp.copyToFile(File(absoluteDir, k.toString()))
	}
}