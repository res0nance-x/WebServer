package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import r3.io.consistentFile
import r3.io.consistentPath
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple directory listing router. If the requested path (resolved under [rootDir]) is a directory,
 * this router returns an HTML page listing the entries in that directory. Otherwise it returns null
 * so other routers can handle the request.
 *
 * Behaviour mirrors FileRouter for security checks (canonical path normalization and host binding).
 */
class DirectoryListingRouter(
	val rootDir: File
) : IRouter {
	private val absoluteRootDir: File = rootDir.consistentFile()
	private val customFormat: SimpleDateFormat = SimpleDateFormat("yyyy.MM.dd (EEE) HH:mm:ss", Locale.US)

	// Simple cache: key = normalizedDirPath + '|' + baseUri -> CachedListing
	private data class CachedListing(val lastModified: Long, val html: String)

	private val listingCache = ConcurrentHashMap<String, CachedListing>()
	override fun findRoute(session: IHTTPSession): Response? {
		// Only allow GET and HEAD
		if (session.method.name != "GET" && session.method.name != "HEAD") {
			return newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, "text/plain", "Method Not Allowed")
		}
		val rawPath = session.uri.substring(1) // remove leading '/'
		val requestedPath = if (rawPath == "") "" else rawPath
		val dir: File = try {
			// Fast, non-IO path check: resolve and normalize under the root.
			val rootNormalized = absoluteRootDir.consistentPath()
			val candidatePath = absoluteRootDir.resolve(requestedPath).consistentPath()

			if (!candidatePath.startsWith(rootNormalized)) {
				// Path traversal detected or outside root
				return newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Forbidden")
			}
			// Use the normalized path as the directory File. This avoids calling canonicalFile (which may trigger IO)
			// on the common success path. We keep the canonical fallback in case further checks are needed.
			File(candidatePath)
		} catch (_: Exception) {
			// IO error while resolving paths
			return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Server Error")
		}

		if (!dir.exists() || !dir.isDirectory) {
			// Not a directory -> let other routers try
			return null
		}
		// Build baseUri once
		val baseUri = if (session.uri.endsWith("/")) session.uri else session.uri + "/"
		// Cache key uses normalized dir path + baseUri since the render depends on baseUri
		val cacheKey = try {
			val keyPath = dir.toPath().normalize().toString()
			"$keyPath|$baseUri"
		} catch (_: Exception) {
			// fallback to absolute path string
			"${dir.absolutePath}|$baseUri"
		}
		// Check cache
		val lastMod = dir.lastModified()
		val cached = listingCache[cacheKey]
		if (cached != null && cached.lastModified == lastMod) {
			val html = cached.html
			val resp = newFixedLengthResponse(Status.OK, "text/html", if (session.method.name == "HEAD") "" else html)
			resp.addHeader("Last-Modified", customFormat.format(Date.from(Instant.ofEpochMilli(dir.lastModified()))))
			return resp
		}
		// Not cached or stale -> build listing
		val children = dir.listFiles() ?: arrayOf()
		children.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
		val entries = ArrayList<DirEntry>(children.size + 1)
		// Use normalized root/current comparison (no canonical unless needed)
		val canonicalRootPath = absoluteRootDir.toPath().normalize().toString()
		val currentNormalized = dir.toPath().normalize().toString()
		if (currentNormalized != canonicalRootPath) {
			// compute an absolute href to the parent directory (one level up) to avoid relative '../' ambiguities
			val parentPath = dir.toPath().normalize().parent
			val parentHref = if (parentPath == null) {
				"/"
			} else {
				try {
					val rel = absoluteRootDir.toPath().normalize().relativize(parentPath).toString()
						.replace(File.separatorChar, '/')
					if (rel.isEmpty()) "/" else "/$rel/"
				} catch (_: Exception) {
					// fallback to ../ if something unexpected happens
					"../"
				}
			}
			entries.add(DirEntry("..", parentHref, true, 0L, dir.parentFile?.lastModified() ?: 0L, "Parent Directory"))
		}

		for (c in children) {
			val name = c.name + if (c.isDirectory) "/" else ""
			val escName = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			// For directories, ensure href ends with '/' so browsers and the server treat it as a directory URL
			val href = if (c.isDirectory) {
				if (baseUri == "/") "/${c.name}/" else baseUri + c.name + "/"
			} else {
				if (baseUri == "/") "/${c.name}" else baseUri + c.name
			}
			entries.add(DirEntry(name, href, c.isDirectory, c.length(), c.lastModified(), escName))
		}
		val html = renderDirectoryListing(session.uri, baseUri, entries, customFormat)
		// store in cache
		listingCache[cacheKey] = CachedListing(lastMod, html)
		val resp = newFixedLengthResponse(Status.OK, "text/html", if (session.method.name == "HEAD") "" else html)
		resp.addHeader("Last-Modified", customFormat.format(Date.from(Instant.ofEpochMilli(dir.lastModified()))))
		return resp
	}
}
