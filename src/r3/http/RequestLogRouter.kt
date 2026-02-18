package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import r3.util.humanReadableSize
import java.text.SimpleDateFormat
import java.util.*

object RequestLogRouter : IRouter {
	val df = SimpleDateFormat("EEE-HH:mm")
	override fun findRoute(session: IHTTPSession): Response? {
		val method = session.method.name
		val queryPart = session.queryParameterString?.let { if (it.isNotEmpty()) "?:it" else "" } ?: ""
		val contentLength = session.headers["content-length"]?.toLong()?.let {
				if (it > 0L) ", " + it.humanReadableSize() else ""
			} ?: ""
		val ip = session.remoteInetSocketAddress.address.toString().drop(1)
		val time = df.format(Date(System.currentTimeMillis()))
		println("$ip, $time, $method:${session.uri}$queryPart $contentLength")
		return null
	}
}