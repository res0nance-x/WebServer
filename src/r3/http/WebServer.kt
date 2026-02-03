package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.protocols.websocket.NanoWSD
import r3.io.log
import java.util.logging.Filter
import java.util.logging.Logger

data class WebServerOptions(
	val disableCache: Boolean = false,
	val enableCORS: Boolean = false
)

class WebServer(
	host: String?,
	port: Int,
	val webSocketBuilder: (IHTTPSession) -> WebSocket,
	routers: List<IRouter>,
	val options: WebServerOptions = WebServerOptions()
) : NanoWSD(host, port) {
	val router: PipeRouter = PipeRouter(routers)
	override fun openWebSocket(handshake: IHTTPSession): WebSocket {
		return webSocketBuilder(handshake)
	}

	companion object {
		init {
			Logger.getLogger(NanoHTTPD::class.java.name).filter = Filter {
				it.message != "Could not send response to the client"
			}
		}
	}

	override fun serveHttp(session: IHTTPSession): Response? {
		try {
			// Handle CORS preflight
			if (options.enableCORS && session.method == Method.OPTIONS) {
				return newFixedLengthResponse(Status.OK, "text/plain", "").apply {
					addCORSHeaders(this)
				}
			}

			val r = router.findRoute(session)
			if (r != null) {
				if (options.disableCache) {
					r.addHeader("cache-control", "no-store, no-cache, must-revalidate")
				}
				if (options.enableCORS) {
					addCORSHeaders(r)
				}
				return r
			}
		} catch (e: Exception) {
			log("WebServer: $e")
			return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Server Error")
		}
		log("Warning: WebServer: ${session.uri} not found")
		return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "No file found for ${session.uri}")
	}

	private fun addCORSHeaders(response: Response) {
		response.addHeader("Access-Control-Allow-Origin", "*")
		response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
		response.addHeader("Access-Control-Max-Age", "86400")
	}
}