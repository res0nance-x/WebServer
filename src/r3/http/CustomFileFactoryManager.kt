package r3.http

import org.nanohttpd.protocols.http.tempfiles.ITempFile
import org.nanohttpd.protocols.http.tempfiles.ITempFileManager
import org.nanohttpd.util.IFactory
import r3.io.TempStorageManager
import java.io.File
import java.io.OutputStream

class CustomFileFactoryManager(val tfm: TempStorageManager) : IFactory<ITempFileManager> {
	override fun create(): ITempFileManager {
		return object : ITempFileManager {
			val tempFileList = ArrayList<File>()
			override fun clear() {
				for (f in tempFileList) {
					try {
						f.delete()
					} catch (e: Exception) {
						println("Couldn't delete temp file $f")
					}
				}
			}

			override fun createTempFile(filename_hint: String?): ITempFile {
				val tf = tfm.createTempStorage()
				return object : ITempFile {
					override fun delete() {
					}

					override fun getName(): String {
						return tf.toString()
					}

					override fun open(): OutputStream {
						return tf
					}
				}
			}
		}
	}
}