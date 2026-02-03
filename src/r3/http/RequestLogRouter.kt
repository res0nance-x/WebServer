package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import java.net.InetAddress

object RequestLogRouter : IRouter {
	override fun findRoute(session: IHTTPSession): Response? {
		val queryPart = session.queryParameterString?.ifEmpty { "" } ?: ""
		val contentLength = session.headers["content-length"] ?: ""
		val countryCode = session.headers["cf-ipcountry"] ?: ""
		val host = session.headers["host"] ?: ""
		val ip = session.headers["cf-connecting-ip"]?.let {
			InetAddress.getByName(it)
		} ?: session.remoteInetSocketAddress.address
		println("$ip $countryCode ${session.method} $host ${session.uri} $queryPart $contentLength")
		return null
	}
}