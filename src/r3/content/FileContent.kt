package r3.content

import r3.io.separatorsToUnix
import r3.source.FileSource
import r3.util.dateTime
import r3.util.humanReadableSize
import java.io.File

class FileContent(
	file: File,
	private val root: File = file.absoluteFile.parentFile!!,
	override val name: String = separatorsToUnix(file.absoluteFile.toRelativeString(root.absoluteFile)),
	override val type: String = file.extension.lowercase(),
	override val created: Long = file.lastModified()
) : Content, FileSource(file) {
	override fun toString(): String {
		return "$name $type ${length.humanReadableSize()} created:${created.dateTime()}"
	}
}