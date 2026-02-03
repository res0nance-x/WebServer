package r3.content

import r3.io.getExtension
import r3.util.humanReadableSize
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipEntryContent(val zipFile: ZipFile, val entry: ZipEntry) : Content {
	override val name: String = entry.name
	override val type: String = getExtension(entry.name)
	override val length: Long = entry.size
	override val created: Long = entry.creationTime.toMillis()
	override fun createInputStream(): InputStream {
		return zipFile.getInputStream(entry)
	}

	override fun toString(): String {
		return "$name $type ${length.humanReadableSize()}"
	}
}