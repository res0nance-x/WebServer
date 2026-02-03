package r3.pack

import java.io.File

interface PlatformAPI {
	fun createTemporaryFile(): File
}